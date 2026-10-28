package com.make.apk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.make.apk.R
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.ShellAction
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.ShellTopTabs
import com.make.apk.data.model.VersionScheme
import com.make.apk.data.model.WebRuntimeConfig
import com.make.apk.data.model.WebSettings
import com.make.apk.ui.screens.WebViewScreen
import com.make.apk.ui.shell.ShellHost
import com.make.apk.ui.shell.ShellRenderEnvFactory
import com.make.apk.ui.shell.visibleTopTabItems
import kotlinx.serialization.json.Json

/**
 * 子模板（插件式注入）落地验证入口（方向 1）。
 *
 * ## 它证明什么
 *
 * 本 Activity 与 app 的预览屏 [com.make.apk.ui.screens.ShellPreviewScreen] **消费同一份**
 * `ShellHost` 渲染器与同一套 `ShellSpec` 数据模型（源码经 sourceSets 指向 app，零副本）。
 * 区别仅在于：
 * - app 预览屏的壳来自「用户实时编辑的 ShellSpec」；
 * - 本模板的壳来自**编译期注入**的 `res/raw/shell_spec.json`（导出壳的最小化等价形态）。
 *
 * 两者喂给同一个 `ShellHost` → 渲染结果天然一致，从而实证
 * 「预览 == 导出 ⟺ 同一份 ShellSpec + 同一份 ShellHost」在真实 APK 产物上成立。
 *
 * ## 不变量
 *
 * 模板只作为壳的「消费方」：不修改壳源码、不污染 ShellSpec 运行时状态
 * （选中态/抽屉开合由本地 remember 持有，不回写 spec）。
 */
class TemplateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 模板自带 Compose Material3 默认主题（不依赖 app 的 MmaTheme）。
            MaterialTheme {
                TemplateApp()
            }
        }
    }
}

/** 模板应用根：读注入的壳契约 → 构造示例运行时 → 交给唯一渲染入口 [ShellHost]。 */
@Composable
private fun TemplateApp() {
    val spec: ShellSpec = rememberInjectedShellSpec()
    val themeColor: Int = 0xFF2196F3.toInt()

    // 运行态选中（顶栏 tab）：本地持有，不回写 spec（壳「不变量」）。
    var selectedTopTabId: String? by remember { mutableStateOf<String?>(null) }

    // 示例运行时配置：与 app 预览共用同一份 WebRuntimeConfig 结构。
    val config = WebRuntimeConfig(
        projectType = ProjectType.URL,
        apkName = "模板示例",
        packageName = "com.make.apk.template",
        versionScheme = VersionScheme.THREE_SEGMENT,
        versionName = "0.0.0.1",
        versionCode = 1,
        themeColor = themeColor,
        entry = ProjectEntry.Url("https://example.com"),
        webSettings = WebSettings(),
        isolationId = "webapp_template",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        ShellHost(
            spec = spec,
            projectThemeColor = themeColor,
            env = ShellRenderEnvFactory.fromSpec(spec, themeColor),
            title = "模板示例",
            selectedTopTabId = selectedTopTabId,
            // 抽屉入口来自顶栏汉堡；返回键交给宿主（模板无上级栈，故 onBack = null）。
            onBack = null,
            onTopTabSelected = { selectedTopTabId = it.id },
            // 滑动切页开启时（spec.topTabs.swipeEnabled）逐页装 WebView；
            // 未开启时 ShellHost 回落到单页 content（见 ShellHost 实现）。
            pagedContent = { page, pageModifier ->
                WebViewScreen(
                    config = pagedRuntimeConfig(config, spec.topTabs, page),
                    modifier = pageModifier,
                )
            },
        ) { contentModifier ->
            WebViewScreen(config = config, modifier = contentModifier.fillMaxSize())
        }
    }
}

/**
 * 读取编译期注入的壳契约 `res/raw/shell_spec.json`。
 *
 * R 来自同 namespace（com.make.apk）的 app res（模板已 srcDirs 共享），
 * 故 [R.raw.shell_spec] 可解析。解码用 `ignoreUnknownKeys = true`，
 * 与 app 端 RuntimeJson 的宽容策略一致（老壳读到新字段不炸）。
 */
@Composable
private fun rememberInjectedShellSpec(): ShellSpec {
    val context = LocalContext.current
    return remember {
        val jsonText = context.resources
            .openRawResource(R.raw.shell_spec)
            .bufferedReader()
            .use { it.readText() }
        Json { ignoreUnknownKeys = true }.decodeFromString<ShellSpec>(jsonText)
    }
}

/**
 * 第 [page] 页的运行时配置：与项目配置同源（主题 / WebSettings 不变），
 * 只把入口换成该顶栏 tab 的目标地址（与 app 预览屏 pagedRuntimeConfig 同语义）。
 *
 * 页序取自 [visibleTopTabItems] —— 与 [ShellHost] 内部的 pager、tab 区**同一份**可见条目顺序，
 * 故隐藏条目既不会被渲染成 tab，也不会被摊成页。目标地址为空时回落项目自身入口。
 */
private fun pagedRuntimeConfig(
    base: WebRuntimeConfig,
    topTabs: ShellTopTabs,
    page: Int,
): WebRuntimeConfig {
    val item = visibleTopTabItems(topTabs).getOrNull(page) ?: return base
    val url = (item.action as? ShellAction.NavigateUrl)
        ?.url
        ?.takeIf { it.isNotBlank() }
        ?: (base.entry as? ProjectEntry.Url)?.url
        ?: ""
    return base.copy(entry = ProjectEntry.Url(url))
}
