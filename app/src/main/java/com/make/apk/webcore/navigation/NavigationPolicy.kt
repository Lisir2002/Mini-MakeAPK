package com.make.apk.webcore.navigation

import com.make.apk.data.model.UrlOpenOrigin
import com.make.apk.data.model.UrlOpenTarget
import com.make.apk.data.model.WebSettings
import com.make.apk.data.model.resolveUrlOpenTarget

/**
 * 导航决策（W4 内核重写，纯 Kotlin、零 android 依赖，JVM 可单测）。
 *
 * 与旧单体 `InAppWebViewClient.shouldOverrideUrlLoading / handleExternal`
 * （WebViewScreen.kt :323-389）的判定分支**逐条等价迁移**：判定与执行分离——
 * 本层只产出 [NavDecision]，执行统一交 [ExternalSchemeDispatcher]（含 openExternal 收口）。
 *
 * 「唯一事实源」复用（W2 ④ 契约，不改其文件）：IN_PAGE_LINK 外跳判定直接调用
 * [resolveUrlOpenTarget]，`fitToApp=false` 时主帧 http/https 链接交系统浏览器，
 * 骨架（入口 / tab / 底栏）与本地协议永不外跳。
 */
sealed interface NavDecision {

    /** 由本 WebView 自行加载（返回 false 放行）。 */
    data class InPlace(val url: String) : NavDecision

    /** 交系统浏览器打开（http/https，仅 fitToApp=false 的主帧页内链接会走到）。 */
    data class External(val url: String) : NavDecision

    /** `intent://` 深链：由 dispatcher 解析外跳，失败回落 browser_fallback_url。 */
    data class Intent(val url: String) : NavDecision

    /** 拦截并给出可见提示（tel/mailto/sms/market 等外部协议；rc12：绝不静默）。 */
    data class BlockWithNotice(val url: String, val message: String) : NavDecision
}

/**
 * 应用内导航判定（唯一入口）：网页类协议一律交回本 WebView，**绝不逃逸到系统浏览器**。
 *
 * 未设置 WebViewClient 时，WebView 会把页面内跳转交给 ActivityManager 选择处理器——
 * 用户点一个链接就被甩进 Chrome / 系统浏览器，壳的顶栏、底栏、抽屉全部失效。
 */
object NavigationPolicy {

    /** 拦截提示文案（与旧单体 handleExternal 等值迁移；`%s` = scheme）。 */
    const val MSG_EXTERNAL_BLOCKED: String = "应用内无法打开 %s 链接，已阻止跳转"

    /** scheme 非法时的兜底展示名（与旧单体 ifBlank 分支等值）。 */
    const val UNKNOWN_SCHEME: String = "未知协议"

    /**
     * 判定一个待加载 URL 的去向。
     *
     * @param url 待判定地址。
     * @param isMainFrame 是否主帧（iframe / 子资源不外跳）。
     * @param fitToApp 「应用内打开方式」开关（WebSettings.fitToApp 透传）。
     */
    fun decide(url: String, isMainFrame: Boolean, fitToApp: Boolean): NavDecision {
        if (url.isBlank()) return NavDecision.InPlace(url)
        val scheme = url.substringBefore(':', "").lowercase()
        // intent:// 深链：解析后外跳（带 browser_fallback_url 兜底），与旧单体 handleExternal 等价。
        if (scheme == "intent") return NavDecision.Intent(url)
        if (com.make.apk.webcore.config.HttpsPolicy.isInAppScheme(scheme)) {
            // W2 ④（IN_PAGE_LINK 分支）：fitToApp=false 时主帧 http/https 链接交系统浏览器。
            // 只拦主帧；骨架（入口 / tab / 底栏）不经此处；file/blob 等本地协议不外跳。
            if (isMainFrame && com.make.apk.webcore.config.HttpsPolicy.isWebScheme(scheme)) {
                val target = resolveUrlOpenTarget(
                    WebSettings(fitToApp = fitToApp),
                    UrlOpenOrigin.IN_PAGE_LINK,
                )
                if (target == UrlOpenTarget.EXTERNAL) return NavDecision.External(url)
            }
            return NavDecision.InPlace(url)
        }
        // 其余外部应用协议（tel/mailto/sms/market/...）：可见提示（rc12：过去静默消费 = 白屏）。
        val shown = scheme.ifBlank { UNKNOWN_SCHEME }
        return NavDecision.BlockWithNotice(url, MSG_EXTERNAL_BLOCKED.format(shown))
    }
}
