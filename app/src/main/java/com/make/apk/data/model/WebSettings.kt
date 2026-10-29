package com.make.apk.data.model

import kotlinx.serialization.Serializable

/**
 * 加载进度条位置（序列化枚举，与 [WebSettings] 同契约）。
 *
 * 仅用于「进阶设置 · 功能加载进度条」：进度条可贴顶栏下方（TOP）或贴底栏上方（BOTTOM），
 * 由用户自行选择，避免遮挡关键内容。
 */
@Serializable
enum class ProgressPosition {
    /** 顶部：贴内容区上沿。 */
    TOP,

    /** 底部：贴内容区下沿。 */
    BOTTOM,
}

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
    /**
     * 是否显示功能加载进度条（进阶设置开关，默认关）。
     * 开启后由渲染内核驱动，进度条贴顶/贴底显示（见 [loadingProgressPosition]）。
     */
    val showLoadingProgress: Boolean = false,
    /**
     * 加载进度条位置（[ProgressPosition]）：仅当 [showLoadingProgress] 为 true 时生效。
     */
    val loadingProgressPosition: ProgressPosition = ProgressPosition.TOP,
    /**
     * 是否忽略 SSL 证书错误（默认关）。
     *
     * WebView 对证书异常的**默认行为是静默取消加载**（`onReceivedSslError` 的 super 实现
     * 即 `handler.cancel()`）——表现为"干净白屏、零线索"。开启后改为放行并给出可见提示，
     * 用于打开证书过期 / 自签名 / 证书链不完整的内网与老旧站点。
     *
     * 安全权衡：放行意味着**不再校验证书**，中间人风险由用户自行承担，故默认关闭，
     * 且开启后每次命中都会在界面上提示"连接未加密校验"。
     */
    val ignoreSslErrors: Boolean = false,
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
