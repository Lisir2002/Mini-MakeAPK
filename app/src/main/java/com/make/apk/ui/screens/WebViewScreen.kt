package com.make.apk.ui.screens

import android.content.Context
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.WebRuntimeConfig

// WebView 渲染屏（本地注入架构）：消费 [WebRuntimeConfig]，统一渲染入口与 WebSettings。
// 不再区分"设备端 vs 云端"——全部走本地注入（设备端 WebView 封装 / 资产注入模板壳 + 本地重签），
// 与导出模板壳共用同一份 WebRuntimeConfig（见 docs/design/project-editor-design.md §2.5 / §11）。
// webViewFactory 默认构造真实 WebView；截图测试可注入轻量 View 桩，在 JVM 上渲染外壳
// （真实 WebView 无法在 JVM 渲染网页，完整网页视觉验证见 Roborazzi / 设备路径）。
@Composable
fun WebViewScreen(
    config: WebRuntimeConfig,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    webViewFactory: (Context) -> View = { WebView(it) },
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = webViewFactory,
        update = { view ->
            if (view !is WebView) return@AndroidView
            // 暂停/恢复：由壳抽屉的 pauseContentWhileOpen 契约驱动，去重避免每次重组都调用。
            applyPaused(view, paused)
            // 仅在配置（入口/关键设置）变化时重新应用并加载，避免每次重组都 reload。
            if (view.getTag(TAG_RUNTIME_KEY) == config) return@AndroidView
            view.setTag(TAG_RUNTIME_KEY, config)
            applyRuntime(view, config)
            loadEntry(view, config.entry)
        },
    )
}

/** 应用暂停/恢复（抽屉展开时省资源；已处于目标状态时跳过）。 */
private fun applyPaused(view: WebView, paused: Boolean) {
    if (view.getTag(TAG_PAUSED_KEY) == paused) return
    view.setTag(TAG_PAUSED_KEY, paused)
    if (paused) view.onPause() else view.onResume()
}

/** 应用 WebSettings 与第三方 Cookie 开关（本地与导出共用同一套配置）。 */
private fun applyRuntime(view: WebView, config: WebRuntimeConfig) {
    val web = config.webSettings
    view.settings.javaScriptEnabled = web.javascriptEnabled
    view.settings.domStorageEnabled = web.domStorageEnabled
    view.settings.loadWithOverviewMode = true
    view.settings.useWideViewPort = true
    view.settings.cacheMode = web.cacheMode
    if (web.userAgent != null) {
        view.settings.userAgentString = web.userAgent
    }
    // 第三方 Cookie：Android 11+ 默认关闭，需显式开启以支持 OAuth / 支付等场景
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, web.thirdPartyCookies)
    }
}

/** 依据入口形态加载内容（本地注入：URL 直连 / HTML 内联 / 资产解包后 file://）。 */
private fun loadEntry(view: WebView, entry: ProjectEntry) {
    when (entry) {
        is ProjectEntry.Url ->
            view.loadUrl(entry.url)
        is ProjectEntry.BundledHtml ->
            view.loadDataWithBaseURL(null, entry.html, "text/html", "utf-8", null)
        is ProjectEntry.Archive -> {
            // 本地注入：资产已解包到 assetRef 目录，直接以 file:// 加载解包后的入口。
            // 解包 / 重签管线在导出阶段实现（见 §11 本地注入架构）；此处为预览态默认加载 index.html。
            view.loadUrl("file://${entry.assetRef}/index.html")
        }
    }
}

/** 用于去重判等，避免重组时重复 reload（与 Compose 内部 tag 机制隔离，使用独立整型 key）。 */
private const val TAG_RUNTIME_KEY = 0x7E110000

/** 用于去重判等，避免重组时重复 onPause / onResume。 */
private const val TAG_PAUSED_KEY = 0x7E110001
