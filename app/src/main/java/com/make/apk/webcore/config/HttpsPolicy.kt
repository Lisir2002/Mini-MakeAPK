package com.make.apk.webcore.config

/**
 * HTTPS / 明文 HTTP 策略（W4 内核重写，纯 Kotlin、零 android 依赖，JVM 可单测）。
 *
 * W4 主控拍板 3：**明文 http 主帧放行 + `plain_http_loaded` 埋点（渐进收紧）**。
 * 行为上与旧单体一致（http 本就属于"应用内协议"放行加载），新增的是**可观测**：
 * 主帧命中明文 http 时上报 [EVENT_PLAIN_HTTP_LOADED]，为后续收紧（强制升级 https）
 * 留下数据依据——先看清存量、再动行为，避免一刀切断掉内网 / 老旧站点。
 *
 * 「https 优先」以 [ENFORCE_HTTPS_UPGRADE] 常量预留收紧开关：当前恒 false（放行），
 * 待埋点数据证明存量可忽略后再翻为 true（一处翻开关、全端生效）。
 */
object HttpsPolicy {

    /** 主帧明文 http 加载埋点事件名（W4 拍板 3 的诊断契约，W3 门面侧无需预注册）。 */
    const val EVENT_PLAIN_HTTP_LOADED: String = "plain_http_loaded"

    /**
     * https 强制升级开关（渐进收紧预留位）：true 时主帧 http 拦截/升级，当前恒 false。
     * 翻开前必须先看 `plain_http_loaded` 埋点存量，避免断网式收紧。
     */
    const val ENFORCE_HTTPS_UPGRADE: Boolean = false

    /**
     * 应用内放行的 scheme 集合（与旧单体 `InAppWebViewClient.isInAppScheme` 等价迁移）：
     * http/https/file/data/about/javascript/blob/content。
     * 其中 `file` 是 Archive 项目预览的入口形态（`file://{assetRef}/index.html`），不可拦。
     */
    val IN_APP_SCHEMES: Set<String> = setOf(
        "http", "https", "file", "data", "about", "javascript", "blob", "content",
    )

    /** 网页协议（W2 ④ IN_PAGE_LINK 外跳分支的适用域；file/blob 等本地协议不外跳）。 */
    val WEB_SCHEMES: Set<String> = setOf("http", "https")

    /** 提取 URL 的 scheme（小写；无冒号 / 空串返回空串，不抛异常）。 */
    fun schemeOf(url: String): String {
        if (url.isBlank()) return ""
        val raw = url.substringBefore(':', "")
        // 无冒号：substringBefore 返回原串本身 → 视为无 scheme。
        return if (raw == url || raw.isBlank()) "" else raw.lowercase()
    }

    /** 是否明文 http（主帧埋点判定；虚拟域 / 本地协议不算）。 */
    fun isPlainTextHttp(url: String): Boolean = schemeOf(url) == "http"

    /** 是否由 WebView 自行加载（留在应用内）；未知 / 外部协议返回 false 走拦截。 */
    fun isInAppScheme(scheme: String?): Boolean = scheme?.lowercase() in IN_APP_SCHEMES

    /** 是否网页协议（http/https，IN_PAGE_LINK 外跳分支适用域）。 */
    fun isWebScheme(scheme: String?): Boolean = scheme?.lowercase() in WEB_SCHEMES
}
