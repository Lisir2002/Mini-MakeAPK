package com.make.apk.data.model

import kotlinx.serialization.Serializable

/**
 * Web 渲染配置（见 §2.4）。
 *
 * 本地渲染器（[com.make.apk.ui.screens.WebViewScreen]）与导出模板壳**共用**同一份配置，
 * 因此它是统一内核契约的一部分，可 JSON 序列化。
 */
@Serializable
data class WebSettings(
    /** 是否启用 JavaScript（默认开）。 */
    val javascriptEnabled: Boolean = true,
    /** 是否启用 DOM Storage（localStorage / sessionStorage，默认开）。 */
    val domStorageEnabled: Boolean = true,
    /**
     * 是否接受第三方 Cookie。
     * Android 11+ 默认关，但 OAuth / 支付等场景关了会陷入死循环，故默认开。
     */
    val thirdPartyCookies: Boolean = true,
    /** 缓存模式（见 [CacheMode] 常量，值与 [android.webkit.WebSettings] 对齐）。 */
    val cacheMode: Int = CACHE_MODE_DEFAULT,
    /** 自定义 User-Agent；为 null 时沿用系统默认 UA。 */
    val userAgent: String? = null,
    /**
     * 应用内打开方式：true=自有域用 WebView 内嵌；false=外链改用 Custom Tabs 跳出。
     */
    val fitToApp: Boolean = true,
) {
    /** 缓存模式常量：值与 [android.webkit.WebSettings] 同名常量一致，故可直接赋给 `settings.cacheMode`。 */
    companion object {
        /** [android.webkit.WebSettings.LOAD_DEFAULT]：默认（按 HTTP 头缓存指令）。 */
        const val CACHE_MODE_DEFAULT = -1

        /** [android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK]：优先缓存，无缓存才联网。 */
        const val CACHE_MODE_CACHE_ELSE_NETWORK = 1

        /** [android.webkit.WebSettings.LOAD_NO_CACHE]：不读缓存，也不写缓存。 */
        const val CACHE_MODE_NO_CACHE = 2

        /** [android.webkit.WebSettings.LOAD_CACHE_ONLY]：只走缓存，完全不联网。 */
        const val CACHE_MODE_CACHE_ONLY = 3
    }
}
