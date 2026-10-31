package com.make.apk.webcore.navigation

import android.content.Intent
import android.util.Log
import android.webkit.WebView
import com.make.apk.ui.screens.openExternal
import com.make.apk.webcore.engine.TAG_WEBVIEW_RUNTIME

/**
 * 外部 scheme 执行边缘（W4 内核重写）：[NavDecision] 的唯一执行者。
 *
 * 与旧单体 `InAppWebViewClient.handleExternal`（WebViewScreen.kt :365-389）**逐行等价迁移**：
 * - `intent://` → [Intent.parseUri]（清 component + 加 CATEGORY_BROWSABLE 防组件劫持），
 *   启动失败回落 `browser_fallback_url` 由本 WebView 打开；
 * - 其余 Block 决策只回传提示，由调用方上抛；
 * - http/https 外开**委托 [openExternal]**（W2 ④ 单点白名单 {http,https}，不改其文件）。
 *
 * 判定（[NavigationPolicy]）与执行（本对象）分离：判定可 JVM 单测，执行依赖 android 运行时。
 */
object ExternalSchemeDispatcher {

    /**
     * 执行 `intent://` 深链：解析并外跳；失败回落 browser_fallback_url。
     *
     * @return true = 已消费（外跳成功或已回落加载）；false = 无法处理（调用方给可见提示）。
     */
    fun dispatchIntent(view: WebView, url: String): Boolean {
        val intent = runCatching {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
            }
        }.getOrNull()
        if (intent != null && runCatching { view.context.startActivity(intent) }.isSuccess) {
            Log.i(TAG_WEBVIEW_RUNTIME, "external_intent_launched")
            return true
        }
        // 兜底：intent 里通常带 browser_fallback_url，回落到本 WebView 打开。
        val fallback = intent?.getStringExtra("browser_fallback_url")
        if (!fallback.isNullOrBlank()) {
            view.loadUrl(fallback)
            Log.i(TAG_WEBVIEW_RUNTIME, "external_intent_fallback")
            return true
        }
        return false
    }

    /**
     * 执行系统浏览器外开（http/https）：委托 [openExternal] 单点（scheme 白名单收口）。
     *
     * @return true = 已拉起系统浏览器；false = 无法打开（调用方给可见提示）。
     */
    fun dispatchExternal(view: WebView, url: String): Boolean = openExternal(view.context, url)
}
