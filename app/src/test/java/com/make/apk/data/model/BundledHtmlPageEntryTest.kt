package com.make.apk.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ProjectEntry.BundledHtml.pageEntryOf]（壳动作「打开本地页面」的消费入口）边界测试。
 *
 * QA 补的缺口（新增 `local_page` 改造评审）：
 *
 * - **可重复取页**：同一份源反复取同一页必须得到同样结果（源不被消费）；
 * - **自身剔除**：命中的页面不得同时留在附加文件集里（否则入口与附加同名，被重复供给）；
 * - **脏数据去重**：入口文件因历史数据同时出现在 `files` 里时，取入口页不得重复供给；
 * - **非页面 / 越界 / 网址形态**的 name 一律返回 `null`（**绝不**退化成 URL）。
 */
class BundledHtmlPageEntryTest {

    private val source = ProjectEntry.BundledHtml(
        html = ENTRY_CONTENT,
        entryFile = "index.html",
        files = listOf(
            ProjectFile("css/style.css", "body{}"),
            ProjectFile("page/about.html", ABOUT_CONTENT),
            ProjectFile("page/contact.html", CONTACT_CONTENT),
        ),
    )

    // ── 正常取页 ──────────────────────────────────────────────────────

    @Test
    fun pageEntryOf_subPage_promotesContentAndDropsItselfFromFiles() {
        val page = source.pageEntryOf("page/about.html")

        assertEquals("必须取到该页内容", ABOUT_CONTENT, page?.html)
        assertEquals("page/about.html", page?.entryFile)
        assertTrue(
            "命中页不得同时留在附加文件集（否则被重复供给）",
            page?.files?.none { it.name == "page/about.html" } == true,
        )
        assertTrue("附加资源必须随行走虚拟域名供给", page?.files?.any { it.name == "css/style.css" } == true)
    }

    @Test
    fun pageEntryOf_isRepeatable_sourceIsNotConsumed() {
        val first = source.pageEntryOf("page/about.html")
        val second = source.pageEntryOf("page/about.html")
        val third = source.pageEntryOf("page/about.html")

        assertEquals("同一源反复取页必须恒等（源不被消费）", first, second)
        assertEquals(first, third)
    }

    @Test
    fun pageEntryOf_siblingPages_areAllReachableFromTheSameSource() {
        // 多个本地页面 tab 的取值前提：任一页面都能从**同一份源**取到，互不挤掉。
        assertEquals(ABOUT_CONTENT, source.pageEntryOf("page/about.html")?.html)
        assertEquals(CONTACT_CONTENT, source.pageEntryOf("page/contact.html")?.html)
        assertEquals(ENTRY_CONTENT, source.pageEntryOf("index.html")?.html)
    }

    // ── 同名去重（脏数据防御）────────────────────────────────────────

    @Test
    fun pageEntryOf_entryFileAlsoPresentInFiles_hasNoDuplicateSupply() {
        val dirty = source.copy(files = source.files + ProjectFile("index.html", "<html>脏数据</html>"))
        val page = dirty.pageEntryOf("index.html")

        assertEquals("入口内容以 html 为单一事实源", ENTRY_CONTENT, page?.html)
        assertEquals(
            "入口同名项必须被剔除，不得重复供给",
            0,
            page?.files?.count { it.name == "index.html" },
        )
    }

    // ── 取不到即 null（绝不退化成 URL）──────────────────────────────

    @Test
    fun pageEntryOf_unknownOrUnsafeNames_returnsNull() {
        listOf(
            "ghost.html", // 文件不存在
            "../outside.html", // 越界相对路径
            "docs/", // 目录条目（尾斜杠约定）
            "", // 空串
            "https://example.com", // 网址形态（手动改 JSON 的脏数据）
        ).forEach { name ->
            assertNull(
                "name=$name 必须取不到（本地页面绝不退化成 URL）",
                source.pageEntryOf(name),
            )
        }
    }

    private companion object {
        const val ENTRY_CONTENT: String = "<html>入口</html>"
        const val ABOUT_CONTENT: String = "<html>关于</html>"
        const val CONTACT_CONTENT: String = "<html>联系</html>"
    }
}
