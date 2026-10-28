package com.make.apk.ui.screens

import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
            // 导航拦截必须先于一切装配：未设 WebViewClient 时，页面内任何跳转（点击链接、
            // 站点重定向）都会被交给系统处理——表现为"跳出应用、打开外部浏览器"，
            // 壳的顶栏 / 底栏瞬间失效。预览与导出壳共用本文件，一处修复两边生效。
            ensureClient(view)
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

/** 装配「应用内导航」客户端（幂等，只装一次）。 */
private fun ensureClient(view: WebView) {
    if (view.getTag(TAG_CLIENT_KEY) == true) return
    view.setTag(TAG_CLIENT_KEY, true)
    view.webViewClient = InAppWebViewClient()
}

/**
 * 应用内导航客户端：网页类协议一律交回本 WebView 加载，**绝不逃逸到系统浏览器**。
 *
 * 未设置 WebViewClient 时，WebView 会把页面内跳转交给 ActivityManager 选择处理器——
 * 用户点一个链接就被甩进 Chrome / 系统浏览器，壳的顶栏、底栏、抽屉全部失效，
 * "预览 == 导出"也随之失去意义（导出的 APK 同样会逃逸）。
 *
 * 拦截策略（返回 true = 消费掉、不加载也不外跳）：
 * - 放行（返回 false，本 WebView 自行加载）：http/https/file/data/about/javascript/blob/content；
 *   其中 `file` 是 Archive 项目预览的入口形态（`file://{assetRef}/index.html`），不可拦。
 * - 消费：`tel` / `mailto` / `sms` / `intent` / `market` 等外部应用协议——预览与壳内
 *   不应把用户甩出应用；后续如需支持可在此扩展为"可解析则外跳、否则降级"。
 */
private class InAppWebViewClient : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        !isInAppScheme(request.url.scheme)

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
        !isInAppScheme(Uri.parse(url).scheme)

    /** 是否由本 WebView 自行加载（留在应用内）；未知 / 外部协议返回 false 走拦截。 */
    private fun isInAppScheme(scheme: String?): Boolean = when (scheme?.lowercase()) {
        "http", "https", "file", "data", "about", "javascript", "blob", "content" -> true
        else -> false
    }
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

/** 用于去重判等，避免重复装配 WebViewClient（客户端无状态，装一次即可）。 */
private const val TAG_CLIENT_KEY = 0x7E110002
