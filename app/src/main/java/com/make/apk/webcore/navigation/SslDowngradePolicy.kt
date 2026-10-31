package com.make.apk.webcore.navigation

import android.webkit.WebViewClient

/**
 * https → http 降级重试决策（rc17「某些链接永远白」专项，纯 Kotlin、JVM 可单测）。
 *
 * ## 为什么需要
 *
 * 一部分小站的 TLS 配置是坏的（协议版本/密码套件不兼容），服务端却仍能正常提供
 * 明文 http。桌面 Chrome 对这类地址会给出可继续的提示，而 WebView 直接回调
 * `onReceivedError` → 宿主不处理就是**干净的白屏**，用户完全不知道发生了什么。
 *
 * 做法与 EinkBro `WebErrorPagePresenter.onReceivedError` 一致：识别到
 * `net::ERR_SSL_PROTOCOL_ERROR` 时，把同一地址换成 http 重试**一次**。
 *
 * ## 三重守卫（缺一不可，每条都是踩过的坑）
 *
 * 1. **仅主帧**：API 23+ 起 `onReceivedError` 对子资源也会触发。少了这条，页面里
 *    一张图片证书坏了就会把整个 WebView 导航走，用户正在读的页面凭空消失。
 * 2. **仅 https**：对 http 地址再降级没有意义。
 * 3. **一次性**：目标站若是「http 又 308 回 https」，无限降级会让导航在两个 scheme
 *    之间反复弹跳，历史栈被错误页填满且退不出去。同一地址每次访问至多降级一次，
 *    由 [lastDowngradedKey] 保证（key 用去掉 scheme 的地址，避免 http/https 互相视为不同地址）。
 *
 * 降级**只在协议错误上发生**，证书错误（[android.webkit.SslError]）不走这里——
 * 那是 `onReceivedSslError` 的职责，默认 cancel，绝不因降级而绕过证书校验。
 */
internal object SslDowngradePolicy {

    /** 协议错误标识（Chromium 错误码字符串，不随系统语言变化）。 */
    const val ERR_SSL_PROTOCOL: String = "net::ERR_SSL_PROTOCOL_ERROR"

    /**
     * 握手失败的数值码（[WebViewClient.ERROR_FAILED_SSL_HANDSHAKE]）。
     *
     * 个别 ROM 会把 description 本地化，字符串匹配失效，故同时接受数值码。
     */
    private const val ERROR_FAILED_SSL_HANDSHAKE: Int = -11

    /** 决策结果。 */
    sealed interface Decision {

        /** 不降级：按常规错误链处理（上报 + 可见提示）。 */
        data object None : Decision

        /**
         * 降级为重试明文 http。
         *
         * @param plainUrl 换成 http 后的地址。
         * @param key 去 scheme 的地址，调用方需存为下一次的 lastDowngradedKey。
         */
        data class RetryPlainHttp(val plainUrl: String, val key: String) : Decision
    }

    /**
     * 判定一次主帧错误是否应当降级重试。
     *
     * @param failingUrl 失败地址。
     * @param isForMainFrame 是否主帧（子资源一律不降级）。
     * @param description 系统给的错误描述（可能是 `net::ERR_...` 或本地化文案）。
     * @param errorCode [android.webkit.WebResourceError.getErrorCode]。
     * @param lastDowngradedKey 本页已降级过的 key（一次性守卫）。
     */
    fun decide(
        failingUrl: String?,
        isForMainFrame: Boolean,
        description: String?,
        errorCode: Int,
        lastDowngradedKey: String?,
    ): Decision {
        // 守卫 1：子资源的证书/协议错误绝不改变主帧导航
        if (!isForMainFrame) return Decision.None
        val url = failingUrl?.takeIf { it.isNotBlank() } ?: return Decision.None
        // 守卫 2：只从 https 降级
        if (!url.startsWith("https://", ignoreCase = true)) return Decision.None
        // 协议错误判定：字符串优先（精确），数值码兜底（ROM 本地化场景）
        if (!isProtocolError(description, errorCode)) return Decision.None
        // 守卫 3：同一地址只降级一次
        val key = schemeless(url)
        if (key == lastDowngradedKey) return Decision.None
        val plain = "http://" + url.substring("https://".length)
        return Decision.RetryPlainHttp(plainUrl = plain, key = key)
    }

    /** 协议错误判定（公开给单测断言）。 */
    fun isProtocolError(description: String?, errorCode: Int): Boolean {
        if (description != null && description.contains("ERR_SSL_PROTOCOL", ignoreCase = true)) {
            return true
        }
        return errorCode == ERROR_FAILED_SSL_HANDSHAKE
    }

    /** 去掉 scheme 的地址（用于一次性守卫的 key，避免 http/https 被视为不同地址）。 */
    fun schemeless(url: String): String = url.substringAfter("://", url)
}
