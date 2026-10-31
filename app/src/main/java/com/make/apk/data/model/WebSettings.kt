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
 * 加载进度条色彩来源（序列化枚举，与 [WebSettings] 同契约）。
 *
 * 进度条颜色不直接存 ARGB 字面量（避免数据层硬编码、且预览 == 导出两侧需同源取色），
 * 而是存一个 **Material3 设计令牌选择器**：渲染时由 `MaterialTheme.colorScheme` 解析为具体色，
 * 从而自动跟随明/暗主题与整体设计语言，永不出现"硬编码色块"。
 *
 * 旧数据（无该字段）反序列化回落到 [ProgressColorToken.PRIMARY]（默认主色，与原行为一致）。
 */
@Serializable
enum class ProgressColorToken {
    /** 主色（默认，与原进度条 `color = colorScheme.primary` 一致）。 */
    PRIMARY,

    /** 表面次要色（onSurfaceVariant），低调中性。 */
    ON_SURFACE_VARIANT,

    /** 第三色（tertiary），强调但不同于主蓝点缀。 */
    TERTIARY,

    /** 描边色（outline），最弱存在感。 */
    OUTLINE,
}

/**
 * 加载进度条粗细（序列化枚举，预设三档，暂不支持数值化）。
 *
 * 取值对应固定 dp（见 [ProgressThickness.strokeDp]），均落在设计令牌尺寸白名单内，
 * 渲染时直接取用，无需宿主再计算。旧数据（无该字段）回落到 [ProgressThickness.MEDIUM]。
 */
@Serializable
enum class ProgressThickness {
    /** 细。 */
    THIN,

    /** 中（默认）。 */
    MEDIUM,

    /** 粗。 */
    THICK,
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
     * 应用内打开方式：true=WebView 内嵌（默认）；false=交系统浏览器打开（Intent.ACTION_VIEW，
     * **零依赖**——明确不引入 androidx.browser，W2 ④ 主控拍板）。
     *
     * 触发语义（唯一判定见 [resolveUrlOpenTarget]）："壳骨架留应用内，内容跳转交外部"：
     * 关（false）时仅顶栏动作按钮 / 抽屉菜单项 / 页内主帧链接外开；入口、顶栏 tab、
     * 底栏条目（导航骨架）与 OpenLocalPage 本地页面**恒内嵌**，否则 pager / 选中态 /
     * 回入口全部失效、壳不成立。
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
     * 加载进度条色彩令牌（[ProgressColorToken]）：仅当 [showLoadingProgress] 为 true 时生效。
     *
     * 存令牌选择而非色值，保证预览与导出两侧都从同一套 `colorScheme` 取色、
     * 且自动跟随主题，不引入硬编码色块（设计令牌铁律）。
     */
    val progressColorToken: ProgressColorToken = ProgressColorToken.PRIMARY,
    /**
     * 加载进度条粗细（[ProgressThickness] 三档预设）：仅当 [showLoadingProgress] 为 true 时生效。
     */
    val progressThickness: ProgressThickness = ProgressThickness.MEDIUM,
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
