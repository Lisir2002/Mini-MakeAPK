package com.make.apk.webcore.navigation

import android.webkit.WebViewClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SSL 协议错误降级策略单测（rc17）。
 *
 * 三条守卫每一条都对应一个真实事故形态，因此逐条独立断言：
 * 子资源坏了不能带走主帧、http 不需要再降、308 回跳不能无限弹。
 */
class SslDowngradePolicyTest {

    private val httpsUrl = "https://broken-site.example.com/a?b=1"
    private val protocolError = "net::ERR_SSL_PROTOCOL_ERROR"

    private fun decide(
        url: String? = httpsUrl,
        mainFrame: Boolean = true,
        description: String? = protocolError,
        errorCode: Int = WebViewClient.ERROR_FAILED_SSL_HANDSHAKE,
        lastKey: String? = null,
    ) = SslDowngradePolicy.decide(url, mainFrame, description, errorCode, lastKey)

    @Test
    fun `主帧 https 协议错误降级为明文 http`() {
        val decision = decide()

        assertTrue(decision is SslDowngradePolicy.Decision.RetryPlainHttp)
        val retry = decision as SslDowngradePolicy.Decision.RetryPlainHttp
        assertEquals("http://broken-site.example.com/a?b=1", retry.plainUrl)
        assertEquals("去 scheme 作为一次性 key", "broken-site.example.com/a?b=1", retry.key)
    }

    @Test
    fun `守卫一 子资源错误绝不改变主帧导航`() {
        // 少了这条，页面里一张图片证书坏掉就会把用户正在读的页面导航走
        assertEquals(SslDowngradePolicy.Decision.None, decide(mainFrame = false))
    }

    @Test
    fun `守卫二 http 地址不再降级`() {
        assertEquals(
            SslDowngradePolicy.Decision.None,
            decide(url = "http://broken-site.example.com/a"),
        )
    }

    @Test
    fun `守卫三 同一地址只降级一次`() {
        // 目标站 308 回 https 时，若无此守卫会在两个 scheme 间无限弹跳，
        // 历史栈被错误页填满且退不出去
        assertEquals(
            SslDowngradePolicy.Decision.None,
            decide(lastKey = "broken-site.example.com/a?b=1"),
        )
    }

    @Test
    fun `降级后 http 又被 308 回 https 仍被拦截`() {
        // http/https 视为同一地址：key 不带 scheme
        assertEquals(
            SslDowngradePolicy.Decision.None,
            decide(url = "https://broken-site.example.com/a?b=1", lastKey = "broken-site.example.com/a?b=1"),
        )
    }

    @Test
    fun `不同地址不受上一次降级影响`() {
        val decision = decide(lastKey = "other-site.example.com/x")
        assertTrue(decision is SslDowngradePolicy.Decision.RetryPlainHttp)
    }

    @Test
    fun `非协议错误不降级`() {
        assertEquals(
            SslDowngradePolicy.Decision.None,
            decide(description = "net::ERR_NAME_NOT_RESOLVED", errorCode = -2),
        )
    }

    @Test
    fun `证书类错误不走降级 由 onReceivedSslError 负责`() {
        // 降级只针对协议握手失败；证书错误必须留在证书链处理，绝不能借此绕过校验
        assertEquals(
            SslDowngradePolicy.Decision.None,
            decide(description = "net::ERR_CERT_DATE_INVALID", errorCode = -200),
        )
    }

    @Test
    fun `ROM 本地化描述失效时按数值码兜底`() {
        // 个别 ROM 把 description 本地化成中文，字符串匹配失效
        val decision = decide(description = "安全连接失败", errorCode = -11)
        assertTrue(decision is SslDowngradePolicy.Decision.RetryPlainHttp)
    }

    @Test
    fun `空地址不降级`() {
        assertEquals(SslDowngradePolicy.Decision.None, decide(url = null))
        assertEquals(SslDowngradePolicy.Decision.None, decide(url = "  "))
    }

    @Test
    fun `schemeless 保留查询串`() {
        assertEquals(
            "a.example.com/p?q=1#f",
            SslDowngradePolicy.schemeless("https://a.example.com/p?q=1#f"),
        )
        assertEquals("no-scheme/path", SslDowngradePolicy.schemeless("no-scheme/path"))
    }
}
