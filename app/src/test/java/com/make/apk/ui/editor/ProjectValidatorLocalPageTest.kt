package com.make.apk.ui.editor

import com.make.apk.data.model.ShellAction
import com.make.apk.data.model.ShellBottomBar
import com.make.apk.data.model.ShellDrawer
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.ShellTopBar
import com.make.apk.data.model.ShellTopTabs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 壳条目引用的**本地页面**体检（[ProjectValidator.validateShellLocalPages]）单测。
 *
 * 钉三件事：
 * 1. 四区条目**全部纳入体检**（漏一区 = 一类静默漏检，表现为"点了没反应"）；
 * 2. 只警告、不阻断（删文件可能是有意为之），且提示里点名是哪个页面没了；
 * 3. 页面都在时**不产生**任何提示（不制造噪音）。
 */
class ProjectValidatorLocalPageTest {

    private val validator = ProjectValidator()

    @Test
    fun allFourRegions_areCovered() {
        // 四区各挂一条本地页面引用：聚合必须覆盖全部四区。
        val spec = ShellSpec(
            topBar = ShellTopBar(enabled = true, actions = listOf(item("a", "top.html"))),
            topTabs = ShellTopTabs(enabled = true, items = listOf(item("b", "tabs.html"))),
            bottomBar = ShellBottomBar(enabled = true, items = listOf(item("c", "bottom.html"))),
            drawer = ShellDrawer(enabled = true, items = listOf(item("d", "drawer.html"))),
        )
        assertEquals(
            listOf("a", "b", "c", "d"),
            spec.allItems.map { it.id },
        )
        assertEquals(4, spec.allItems.count { it.action is ShellAction.OpenLocalPage })
    }

    @Test
    fun missingPages_produceWarningAndNameThem() {
        val spec = ShellSpec(
            bottomBar = ShellBottomBar(
                enabled = true,
                items = listOf(item("ok", "index.html"), item("gone", "page/ghost.html")),
            ),
        )
        val verdict = validator.validateShellLocalPages(spec, listOf("index.html"))

        assertNull("必须是警告而不是错误（不阻断保存）", verdict.error)
        val warning = verdict.warning
        assertNotNull("缺失页面必须给出可见警告", warning)
        assertEquals(FieldMessage.Severity.WARNING, warning?.severity)
        assertNotNull(
            "提示必须点名是哪个页面没了",
            warning?.text?.contains("page/ghost.html"),
        )
        assertEquals(true, warning?.text?.contains("page/ghost.html"))
        assertEquals(false, warning?.text?.contains("index.html"))
    }

    @Test
    fun allPagesPresent_producesNoMessage() {
        val spec = ShellSpec(
            bottomBar = ShellBottomBar(
                enabled = true,
                items = listOf(item("ok", "index.html"), item("sub", "page/about.html")),
            ),
        )
        val verdict = validator.validateShellLocalPages(
            spec,
            listOf("index.html", "page/about.html", "css/style.css"),
        )

        assertNull(verdict.error)
        assertNull("页面都在时不得产生噪音提示", verdict.warning)
    }

    @Test
    fun urlOnlyShell_isSilent() {
        // 纯网址壳（没有任何本地页面引用）：体检必须是安静的。
        val spec = ShellSpec(
            topBar = ShellTopBar(
                enabled = true,
                actions = listOf(
                    ShellMenuItem(id = "u", title = "网页", action = ShellAction.NavigateUrl("https://example.com")),
                ),
            ),
        )
        val verdict = validator.validateShellLocalPages(spec, emptyList())

        assertNull(verdict.error)
        assertNull(verdict.warning)
    }

    private fun item(id: String, path: String): ShellMenuItem =
        ShellMenuItem(id = id, title = "条目", action = ShellAction.OpenLocalPage(path))
}
