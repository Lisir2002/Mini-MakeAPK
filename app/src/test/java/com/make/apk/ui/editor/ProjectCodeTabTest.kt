package com.make.apk.ui.editor

import app.cash.paparazzi.Paparazzi
import com.make.apk.data.model.ProjectFile
import com.make.apk.ui.theme.MmaTheme
import org.junit.Rule
import org.junit.Test

/**
 * 「项目代码」卡片列表式目录浏览器截图回归（Paparazzi，纯 JVM）。
 *
 * 钉住本次重构后的**逐层卡片列表** UI：当前目录头、面包屑、圆角条目卡片、
 * 按扩展名映射的图标底色、入口徽标、空文件夹条目（`assets/img/`）。
 *
 * 样例刻意包含**三层嵌套**（`assets/img/logo.svg`）与**显式空目录条目**
 * （`assets/empty/`），覆盖目录进入与空目录约定。
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
            // 显式空目录条目（空文件夹约定：name 尾随 "/"、content 为空）
            ProjectFile(name = "assets/empty/", content = ""),
            ProjectFile(name = "README.md", content = "# MMA".repeat(30)),
        )
    }
}
