package com.make.apk.webcore.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 错误页表现层单测（rc17）。
 *
 * 只测纯函数部分：文案映射、HTML 转义、地址截断、配色推导。
 * 渲染路径（loadDataWithBaseURL）依赖 WebView，由截图基线覆盖。
 */
class WebErrorPresenterTest {

    // ── 文案映射 ──

    @Test
    fun `错误串映射为用户可读文案`() {
        assertEquals(
            "网络已断开，请检查 Wi-Fi 或移动数据。",
            WebErrorPresenter.friendlyReason("net::ERR_INTERNET_DISCONNECTED"),
        )
        assertEquals(
            "找不到这个网站，请检查地址是否正确。",
            WebErrorPresenter.friendlyReason("net::ERR_NAME_NOT_RESOLVED"),
        )
        assertEquals(
            "连接超时，服务器响应太慢。",
            WebErrorPresenter.friendlyReason("net::ERR_CONNECTION_TIMED_OUT"),
        )
        assertEquals(
            "网站的安全证书有问题，已阻止加载。",
            WebErrorPresenter.friendlyReason("net::ERR_CERT_AUTHORITY_INVALID"),
        )
    }

    @Test
    fun `不把网络栈错误名原样抛给用户`() {
        val reason = WebErrorPresenter.friendlyReason("net::ERR_SOMETHING_WEIRD")
        assertFalse("兜底文案不得含 net:: 前缀：$reason", reason.contains("net::"))
        assertTrue(reason.isNotBlank())
    }

    @Test
    fun `空描述给可操作兜底文案`() {
        assertEquals("请检查网络连接后重试。", WebErrorPresenter.friendlyReason(null))
        assertEquals("请检查网络连接后重试。", WebErrorPresenter.friendlyReason("   "))
    }

    @Test
    fun `大小写不敏感`() {
        assertEquals(
            WebErrorPresenter.friendlyReason("net::ERR_CONNECTION_REFUSED"),
            WebErrorPresenter.friendlyReason("NET::err_connection_refused"),
        )
    }

    // ── HTML 转义（错误页靠字符串拼接，必须防注入）──

    @Test
    fun `五个危险字符全部转义`() {
        assertEquals(
            "&amp;&lt;&gt;&quot;&#39;",
            WebErrorPresenter.escapeHtml("&<>\"'"),
        )
    }

    @Test
    fun `转义防止属性逃逸`() {
        // 失败地址直接进 href，未转义时一个引号就能闭合属性注入脚本
        val escaped = WebErrorPresenter.escapeHtml("https://a.com/\" onload=\"alert(1)")
        assertFalse(escaped.contains("\""))
        assertTrue(escaped.contains("&quot;"))
    }

    @Test
    fun `普通中文不被改动`() {
        assertEquals("页面无法打开", WebErrorPresenter.escapeHtml("页面无法打开"))
    }

    // ── 地址展示 ──

    @Test
    fun `展示地址去掉 scheme`() {
        assertEquals("a.com/x", WebErrorPresenter.displayUrl("https://a.com/x"))
        assertEquals("a.com/x", WebErrorPresenter.displayUrl("http://a.com/x"))
    }

    @Test
    fun `超长地址截断中段`() {
        val long = "https://a.com/" + "x".repeat(200)
        val shown = WebErrorPresenter.displayUrl(long)
        assertTrue("必须截断：${shown.length}", shown.length <= 60)
        assertTrue("截断标记应存在：$shown", shown.contains("…"))
    }

    // ── 配色推导 ──

    @Test
    fun `深色底判为暗色`() {
        assertTrue(ErrorPalette.isDark(0xFF121212.toInt()))
        assertTrue(ErrorPalette.isDark(0xFF000000.toInt()))
    }

    @Test
    fun `浅色底判为亮色`() {
        assertFalse(ErrorPalette.isDark(0xFFFFFFFF.toInt()))
        assertFalse(ErrorPalette.isDark(0xFFF7F8FA.toInt()))
    }

    @Test
    fun `暗色底用亮文字 亮色底用深文字`() {
        val dark = ErrorPalette.of(0xFF121212.toInt())
        val light = ErrorPalette.of(0xFFFFFFFF.toInt())

        assertTrue("暗色主题下错误页文字必须提亮", isLightColor(dark.foreground))
        assertFalse("亮色主题下错误页文字必须压暗", isLightColor(light.foreground))
    }

    @Test
    fun `底色转为不透明十六进制`() {
        // alpha 必须丢弃：CSS 底色透明会露出下面的 WebView 旧帧
        assertEquals("#121212", ErrorPalette.hex(0xFF121212.toInt()))
        assertEquals("#FFFFFF", ErrorPalette.hex(0x80FFFFFF.toInt()))
    }

    /** `#RRGGBB` 是否属于亮色（各通道均值 > 0x80）。 */
    private fun isLightColor(hex: String): Boolean {
        val v = hex.removePrefix("#").toInt(16)
        val r = (v shr 16) and 0xFF
        val g = (v shr 8) and 0xFF
        val b = v and 0xFF
        return (r + g + b) / 3 > 0x80
    }
}
