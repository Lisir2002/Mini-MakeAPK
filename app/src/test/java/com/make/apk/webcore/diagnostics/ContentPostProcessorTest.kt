package com.make.apk.webcore.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UA 指纹自检解析单测（rc17）。
 *
 * 这组断言是「某些链接永远白」是否由 UA 引起的**判定依据**：
 * 只要 `hasWv` / `hasVersion4` / `hasWebViewBrand` 任一为真，站点仍可识别出内嵌浏览器。
 */
class ContentPostProcessorTest {

    @Test
    fun `完整指纹串解析正确`() {
        val parsed = ContentPostProcessor.parseProbe("\"1#0#0#Google Chrome|Chromium|Not:A-Brand\"")

        assertEquals("1", parsed!!.mobile)
        assertFalse("wv 已清除", parsed.hasWv)
        assertFalse("Version/4.0 已清除", parsed.hasVersion4)
        assertFalse("brand 列表已无 Android WebView", parsed.hasWebViewBrand)
        assertTrue("应判定为真 Chrome", parsed.looksLikeRealChrome)
    }

    @Test
    fun `残留 wv 标记即判为未归一化`() {
        val parsed = ContentPostProcessor.parseProbe("\"1#1#0#Google Chrome\"")!!

        assertTrue(parsed.hasWv)
        assertFalse("有 wv 就不算真 Chrome", parsed.looksLikeRealChrome)
    }

    @Test
    fun `残留 Version 4点0 即判为未归一化`() {
        val parsed = ContentPostProcessor.parseProbe("\"1#0#1#Google Chrome\"")!!

        assertTrue(parsed.hasVersion4)
        assertFalse(parsed.looksLikeRealChrome)
    }

    @Test
    fun `brand 里残留 Android WebView 即判为未归一化`() {
        val parsed = ContentPostProcessor.parseProbe("\"1#0#0#Chromium|Android WebView\"")!!

        assertTrue("Client Hints 泄漏：brand 列表仍含 Android WebView", parsed.hasWebViewBrand)
        assertFalse(parsed.looksLikeRealChrome)
    }

    @Test
    fun `brand 匹配大小写不敏感`() {
        val parsed = ContentPostProcessor.parseProbe("\"1#0#0#android webview\"")!!
        assertTrue(parsed.hasWebViewBrand)
    }

    @Test
    fun `内核不支持 userAgentData 时 mobile 为问号`() {
        val parsed = ContentPostProcessor.parseProbe("\"?#0#0#\"")!!

        assertEquals("?", parsed.mobile)
        assertTrue("指纹干净仍应为真（只是 unknown）", parsed.looksLikeRealChrome)
    }

    @Test
    fun `畸形返回值返回 null 不崩`() {
        assertNull(ContentPostProcessor.parseProbe(null))
        assertNull(ContentPostProcessor.parseProbe(""))
        assertNull("字段不足", ContentPostProcessor.parseProbe("\"1#0\""))
        // JS 异常时脚本返回空串（不是占位符），同样按无效处理
        assertNull("JS 异常空串", ContentPostProcessor.parseProbe("\"\""))
    }

    @Test
    fun `evaluateJavascript 的转义引号被正确剥离`() {
        // evaluateJavascript 返回的是 JSON 编码字符串，外层带引号
        val parsed = ContentPostProcessor.parseProbe("\"1#0#0#Google Chrome\"")
        assertEquals("1", parsed!!.mobile)
    }

    @Test
    fun `空 brand 列表不误判`() {
        val parsed = ContentPostProcessor.parseProbe("\"1#0#0#\"")!!
        assertFalse(parsed.hasWebViewBrand)
    }
}
