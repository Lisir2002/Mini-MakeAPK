package com.make.apk.ui.editor

import app.cash.paparazzi.Paparazzi
import com.make.apk.data.model.ProjectFile
import com.make.apk.ui.theme.MmaTheme
import org.junit.Rule
import org.junit.Test

/**
 * 「项目代码」目录树截图回归（Paparazzi，纯 JVM）。
 *
 * 钉住 rc12 重建的**真实目录结构**：面包屑路径条、文件夹开 / 闭两态图标、
 * 逐层缩进、文件行右对齐大小、入口徽标——以及"文件夹优先、文件其次"的排序。
 *
 * 样例刻意包含**三层嵌套**（`assets/img/logo.svg`），验证缩进封顶与目录折叠。
 */
class ProjectCodeTabTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun treeLight() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            ProjectCodeTab(
                entryFile = "index.html",
                files = SAMPLE_FILES,
                onOpenFile = {},
                onFileAdded = {},
                onFileDeleted = {},
            )
        }
    }

    @Test
    fun treeDark() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) {
            ProjectCodeTab(
                entryFile = "index.html",
                files = SAMPLE_FILES,
                onOpenFile = {},
                onFileAdded = {},
                onFileDeleted = {},
            )
        }
    }

    private companion object {
        val SAMPLE_FILES: List<ProjectFile> = listOf(
            ProjectFile(
                name = "index.html",
                content = "<!doctype html><html><body><h1>MMA</h1></body></html>".repeat(8),
            ),
            ProjectFile(name = "css/style.css", content = "body{margin:0}".repeat(40)),
            ProjectFile(name = "css/theme.css", content = ":root{--brand:#2563EB}"),
            ProjectFile(name = "js/app.js", content = "console.log('mma')".repeat(120)),
            ProjectFile(name = "assets/img/logo.svg", content = "<svg/>"),
            ProjectFile(name = "README.md", content = "# MMA".repeat(30)),
        )
    }
}
