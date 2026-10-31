package com.make.apk.webcore.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HTTPS / 明文 HTTP 策略纯函数测试（W4 T05，拍板 3 契约）。
 */
class HttpsPolicyTest {

    @Test
    fun `plain http is detected`() {
        assertTrue(HttpsPolicy.isPlainTextHttp("http://example.com/"))
        assertTrue(HttpsPolicy.isPlainTextHttp("HTTP://EXAMPLE.COM/"))
        assertFalse(HttpsPolicy.isPlainTextHttp("https://example.com/"))
        assertFalse(HttpsPolicy.isPlainTextHttp("file:///data/index.html"))
        assertFalse(HttpsPolicy.isPlainTextHttp(""))
    }

    @Test
    fun `scheme extraction handles missing colon and case`() {
        assertEquals("https", HttpsPolicy.schemeOf("https://example.com/"))
        assertEquals("intent", HttpsPolicy.schemeOf("intent://x/#Intent;end"))
        assertEquals("tel", HttpsPolicy.schemeOf("tel:10086"))
        // 无冒号 → 无 scheme（旧 substringBefore 行为的显式化）。
        assertEquals("", HttpsPolicy.schemeOf("not-a-url"))
        assertEquals("", HttpsPolicy.schemeOf(""))
    }

    @Test
    fun `in-app schemes match legacy whitelist`() {
        // 与旧单体 isInAppScheme 白名单逐项等值：http/https/file/data/about/javascript/blob/content。
        val whitelist = setOf("http", "https", "file", "data", "about", "javascript", "blob", "content")
        whitelist.forEach { scheme ->
            assertTrue("scheme=$scheme", HttpsPolicy.isInAppScheme(scheme))
            assertTrue("scheme=${scheme.uppercase()}", HttpsPolicy.isInAppScheme(scheme.uppercase()))
        }
        assertFalse(HttpsPolicy.isInAppScheme("tel"))
        assertFalse(HttpsPolicy.isInAppScheme("intent"))
        assertFalse(HttpsPolicy.isInAppScheme(null))
    }

    @Test
    fun `web schemes are exactly http and https`() {
        assertTrue(HttpsPolicy.isWebScheme("http"))
        assertTrue(HttpsPolicy.isWebScheme("https"))
        assertFalse(HttpsPolicy.isWebScheme("file"))
        assertFalse(HttpsPolicy.isWebScheme("data"))
        assertFalse(HttpsPolicy.isWebScheme(null))
    }

    @Test
    fun `plain http loaded event name is the agreed contract`() {
        // W4 拍板 3 的事件名契约（渐进收紧的数据依据）。
        assertEquals("plain_http_loaded", HttpsPolicy.EVENT_PLAIN_HTTP_LOADED)
    }

    @Test
    fun `enforce https upgrade stays off for now`() {
        // 渐进收紧预留位：翻开前必须先看 plain_http_loaded 存量。
        assertFalse(HttpsPolicy.ENFORCE_HTTPS_UPGRADE)
    }
}
