package com.make.apk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.Paparazzi
import com.make.apk.data.model.ShellSpec
import com.make.apk.ui.shell.ShellHost
import com.make.apk.ui.shell.ShellRenderEnvFactory
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * 子模板（导出壳）截图回归（Paparazzi，纯 JVM）。
 *
 * 本测试渲染**编译期注入**的 [ShellSpec]（`res/raw/shell_spec.json` 经 sourceSets 落入模板），
 * 用与 `TemplateActivity` 相同的 `ShellHost` 入口 + 相同示例运行时，证明「导出」这一侧
 * 在真实渲染管线上的壳表现。内容区用灰色占位盒（真实 WebView 无法在 JVM 渲染），
 * 与 app 侧 `ShellHostExportParityTest` 的占位盒**逐像素一致**，供 `compare_preview_export.py`
 * 做「预览 vs 导出」并排像素对比。
 */
class TemplateShellSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun exportedShell_light() = paparazzi.snapshot {
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

        /** 读取编译期注入的壳契约（与 TemplateActivity.rememberInjectedShellSpec 同源）。 */
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
