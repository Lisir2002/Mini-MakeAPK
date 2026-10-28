package com.make.apk.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.Paparazzi
import com.make.apk.data.model.ShellSpec
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * 「预览 vs 导出」一致性对照（Paparazzi，纯 JVM）。
 *
 * 用与 `TemplateShellSnapshotTest` **完全相同**的 `ShellHost` 调用 + 同一份注入壳
 * `shell_spec.json`（绝对路径读取，保证与导出侧用同一 spec），渲染壳，**逐像素对比**
 * 两侧输出（`compare_preview_export.py`）。两侧源码同源、spec 同源、env 派生同源，
 * 故像素应一致 —— 这是「预览 == 导出」公式在渲染层面的可视化证据。
 *
 * 注：本测试刻意读取 template 模块的注入文件（跨模块耦合），因为对照的就是
 * 「导出侧注入的那份 shell_spec」，复制一份会失去对照意义。
 */
class ShellHostExportParityTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun previewSideShell_light() = paparazzi.snapshot {
        val spec = loadInjectedSpec()
        MaterialTheme {
            ShellHost(
                spec = spec,
                projectThemeColor = THEME_COLOR,
                env = ShellRenderEnvFactory.fromSpec(spec, THEME_COLOR),
                title = "模板示例",
                onBack = null,
                onTopTabSelected = {},
                onBottomBarSelected = {},
                onDrawerOpenChanged = {},
                onDrawerItemSelected = {},
                onShellAction = {},
                pagedContent = { _, mod -> PlaceholderBox(mod) },
            ) { PlaceholderBox(it) }
        }
    }

    private companion object {
        val THEME_COLOR = 0xFF2196F3.toInt()

        fun loadInjectedSpec(): ShellSpec {
            val userDir = checkNotNull(System.getProperty("user.dir")) { "user.dir 未设置" }
            val repoRoot = File(userDir).parentFile
            val jsonFile = repoRoot.resolve("template/src/main/res/raw/shell_spec.json")
            return Json { ignoreUnknownKeys = true }.decodeFromString(jsonFile.readText())
        }
    }
}

@Composable
private fun PlaceholderBox(modifier: Modifier) {
    Box(modifier.fillMaxSize().background(Color(0xFFEDEDED)))
}
