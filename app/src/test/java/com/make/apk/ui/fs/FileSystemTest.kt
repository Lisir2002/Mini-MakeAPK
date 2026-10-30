package com.make.apk.ui.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 文件系统中性模型与纯函数单测（纯 JVM，无 Compose 依赖）。
 *
 * 覆盖：树构建（含空目录约定 / 嵌套 / 入口哨兵 / childCount / 排序）、
 * 命名校验、同名冲突自动改名、路径前缀替换。
 */
class FileSystemTest {

    // ── buildFsItems ──────────────────────────────────────────────────

    @Test
    fun `扁平文件构建为根的直接子项，目录优先排序`() {
        val items = buildFsItems(
            listOf(
                RawFsEntry("index.html", 10),
                RawFsEntry("README.md", 20),
                RawFsEntry("css/style.css", 30),
            ),
            entryPath = "index.html",
        )
        val roots = childItemsOf(items, "")
        assertEquals(3, roots.size)
        // 目录优先：css 在文件之前
        assertEquals("css", roots[0].name)
        assertEquals(true, roots[0].isDirectory)
        // 入口标记
        assertEquals(true, roots.first { it.name == "index.html" }.isEntry)
        // childCount：css 下 1 个文件
        assertEquals(1, roots.first { it.name == "css" }.childCount)
    }

    @Test
    fun `空目录约定：name 尾斜杠条目被识别为目录且 childCount 为 0`() {
        val items = buildFsItems(
            listOf(
                RawFsEntry("index.html", 10),
                RawFsEntry("assets/img/", 0), // 显式空目录
            ),
            entryPath = "index.html",
        )
        val roots = childItemsOf(items, "")
        val assets = roots.first { it.name == "assets" }
        assertEquals(true, assets.isDirectory)
        assertEquals(1, assets.childCount) // 含子目录 img
        val img = childItemsOf(items, "assets").first { it.name == "img" }
        assertEquals(true, img.isDirectory)
        assertEquals(0, img.childCount) // 空目录
    }

    @Test
    fun `嵌套三层目录正确归集 childCount`() {
        val items = buildFsItems(
            listOf(
                RawFsEntry("a/b/c/file.txt", 5),
                RawFsEntry("a/b/other.txt", 5),
                RawFsEntry("a/top.txt", 5),
            ),
        )
        val a = childItemsOf(items, "").first { it.name == "a" }
        assertEquals(2, a.childCount) // b + top.txt
        val b = childItemsOf(items, "a").first { it.name == "b" }
        assertEquals(2, b.childCount) // c + other.txt
        val c = childItemsOf(items, "a/b").first { it.name == "c" }
        assertEquals(1, c.childCount) // file.txt
    }

    @Test
    fun `父链隐式补成目录，即使仅有深层文件`() {
        val items = buildFsItems(listOf(RawFsEntry("x/y/z.txt", 1)))
        // 根应有 x，x 下有 y，y 下有 z.txt
        assertEquals(listOf("x"), childItemsOf(items, "").map { it.name })
        assertEquals(listOf("y"), childItemsOf(items, "x").map { it.name })
        assertEquals(listOf("z.txt"), childItemsOf(items, "x/y").map { it.name })
    }

    @Test
    fun `无入口路径时不标记任何 entry`() {
        val items = buildFsItems(listOf(RawFsEntry("index.html", 1)))
        assertEquals(false, childItemsOf(items, "").first().isEntry)
    }

    // ── directoryPathsOf ──────────────────────────────────────────────

    @Test
    fun `多级隐式目录全部推导：a b c 三级`() {
        val dirs = directoryPathsOf(
            listOf(
                RawFsEntry("a/b/c.txt", 5),
                RawFsEntry("a/b/other.txt", 5),
                RawFsEntry("a/top.txt", 5),
            ),
        )
        assertEquals(setOf("a", "a/b"), dirs)
    }

    @Test
    fun `显式空目录条目自身与父链都纳入`() {
        val dirs = directoryPathsOf(listOf(RawFsEntry("assets/img/", 0)))
        assertEquals(setOf("assets", "assets/img"), dirs)
    }

    @Test
    fun `无目录（单文件）返回空集`() {
        val dirs = directoryPathsOf(listOf(RawFsEntry("index.html", 1)))
        assertEquals(emptySet<String>(), dirs)
    }

    @Test
    fun `显式目录条目与隐式目录去重合并`() {
        val dirs = directoryPathsOf(
            listOf(
                RawFsEntry("css/style.css", 1), // 隐式 css
                RawFsEntry("css/", 0), // 显式 css（尾斜杠），应与隐式去重
            ),
        )
        assertEquals(setOf("css"), dirs)
    }

    @Test
    fun `多级显式目录条目逐层补父链`() {
        // 仅显式深层空目录，父链 a / a/b 需隐式补全
        val dirs = directoryPathsOf(listOf(RawFsEntry("a/b/c/", 0)))
        assertEquals(setOf("a", "a/b", "a/b/c"), dirs)
    }

    @Test
    fun `空名或无效条目被安全跳过`() {
        val dirs = directoryPathsOf(listOf(RawFsEntry("   ", 0), RawFsEntry("root.txt", 1)))
        assertEquals(emptySet<String>(), dirs)
    }

    // ── validateFsName ────────────────────────────────────────────────

    @Test
    fun `空名不通过`() {
        assertEquals(FS_NAME_EMPTY, validateFsName("   ", setOf()))
    }

    @Test
    fun `含反斜杠或点点段不通过`() {
        assertEquals(FS_NAME_ILLEGAL, validateFsName("a\\b", setOf()))
        assertEquals(FS_NAME_ILLEGAL, validateFsName("a/../b", setOf("a")))
    }

    @Test
    fun `与兄弟精确重名不通过`() {
        assertEquals(FS_NAME_DUPLICATE, validateFsName("style.css", setOf("style.css")))
    }

    @Test
    fun `互为父子前缀冲突不通过`() {
        // 已存在文件 css，再建 css/style.css 非法
        assertEquals(FS_NAME_DUPLICATE, validateFsName("css/style.css", setOf("css")))
        // 已存在目录 css，再建同名文件 css 非法
        assertEquals(FS_NAME_DUPLICATE, validateFsName("css", setOf("css/style.css")))
    }

    @Test
    fun `合法子路径通过`() {
        assertNull(validateFsName("css/style.css", setOf("js", "README.md")))
    }

    // ── resolveCopyName ───────────────────────────────────────────────

    @Test
    fun `无冲突直接返回`() {
        assertEquals("style.css", resolveCopyName("style.css", setOf("a.txt")))
    }

    @Test
    fun `冲突依次递推 副本 与 序号`() {
        val taken = setOf("style.css", "style 副本.css", "style(2).css")
        assertEquals("style(3).css", resolveCopyName("style.css", taken))
    }

    @Test
    fun `带扩展名时插入在扩展名之前`() {
        val taken = setOf("style.css")
        assertEquals("style 副本.css", resolveCopyName("style.css", taken))
    }

    @Test
    fun `无扩展名也正确递推`() {
        val taken = setOf("Makefile", "Makefile 副本")
        assertEquals("Makefile(2)", resolveCopyName("Makefile", taken))
    }

    // ── renamePathPrefix ─────────────────────────────────────────────

    @Test
    fun `文件路径前缀替换`() {
        assertEquals(
            "assets/x/b.txt",
            renamePathPrefix("css/a/b.txt", "css/a", "assets/x"),
        )
    }

    @Test
    fun `同名文件重命名`() {
        assertEquals("assets/x.txt", renamePathPrefix("css/a.txt", "css/a.txt", "assets/x.txt"))
    }

    @Test
    fun `空目录条目尾斜杠正确替换`() {
        assertEquals("assets/x/", renamePathPrefix("css/a/", "css/a", "assets/x"))
    }

    @Test
    fun `不命中前缀保持不变`() {
        assertEquals("css/a/b.txt", renamePathPrefix("css/a/b.txt", "css/x", "assets/x"))
    }

    // ── countDescendantFiles ─────────────────────────────────────────

    @Test
    fun `统计目录下的文件总数`() {
        val items = buildFsItems(
            listOf(
                RawFsEntry("css/style.css", 1),
                RawFsEntry("css/theme.css", 1),
                RawFsEntry("css/img/logo.svg", 1),
            ),
        )
        val css = childItemsOf(items, "").first { it.name == "css" }
        // css 下 style.css / theme.css / img/logo.svg = 3 个文件
        assertEquals(3, countDescendantFiles(items, css.path))
    }
}
