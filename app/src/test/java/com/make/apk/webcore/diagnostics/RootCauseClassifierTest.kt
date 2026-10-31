package com.make.apk.webcore.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 白屏根因分级纯函数测试（W4 T05，拍板 5 契约）：与旧单体 reportBlankRootCause
 * 三级判定逐条对齐的等价性证明。
 */
class RootCauseClassifierTest {

    // ── 第一级：语法能力探测（Chrome 80 门槛）──

    @Test
    fun `null syntax probe means probe failed`() {
        // 探测链路本身失败（回调 null）：优雅降级，不误诊。
        assertEquals(BlankRootCause.ProbeFailed, RootCauseClassifier.fromSyntaxProbe(null))
    }

    @Test
    fun `syntax probe failure means engine too old`() {
        assertEquals(BlankRootCause.EngineTooOld, RootCauseClassifier.fromSyntaxProbe("\"0\""))
        assertEquals(BlankRootCause.EngineTooOld, RootCauseClassifier.fromSyntaxProbe("0"))
        assertEquals(BlankRootCause.EngineTooOld, RootCauseClassifier.fromSyntaxProbe(""))
    }

    @Test
    fun `syntax probe success defers to js error reading`() {
        assertNull(RootCauseClassifier.fromSyntaxProbe("\"1\""))
        assertNull(RootCauseClassifier.fromSyntaxProbe("1"))
    }

    // ── 第二/三级：JS 错误读取 ──

    @Test
    fun `js error is truncated to 80 chars`() {
        val long = "E".repeat(200)
        val cause = RootCauseClassifier.fromJsError("\"$long\"", null)
        assertTrue(cause is BlankRootCause.JsError)
        assertEquals(80, (cause as BlankRootCause.JsError).detail.length)
    }

    @Test
    fun `blank and sentinel js results fall back to native cache`() {
        // "null" / "no-hook" / 空白 = 页面侧无有效错误 → 读原生 console 缓存（旧 TAG_JS_ERROR_KEY 语义）。
        assertEquals(
            BlankRootCause.JsError("TypeError: x is not defined"),
            RootCauseClassifier.fromJsError("\"null\"", "TypeError: x is not defined"),
        )
        assertEquals(
            BlankRootCause.JsError("console-cache"),
            RootCauseClassifier.fromJsError("\"no-hook\"", "console-cache"),
        )
        assertEquals(
            BlankRootCause.JsError("console-cache"),
            RootCauseClassifier.fromJsError(null, "console-cache"),
        )
    }

    @Test
    fun `no js error at all means unknown`() {
        assertEquals(BlankRootCause.Unknown, RootCauseClassifier.fromJsError("\"null\"", null))
        assertEquals(BlankRootCause.Unknown, RootCauseClassifier.fromJsError(null, null))
        assertEquals(BlankRootCause.Unknown, RootCauseClassifier.fromJsError("\"\"", "  "))
    }

    // ── W3 埋点契约 reason 字段 ──

    @Test
    fun `event reasons match w3 contract`() {
        assertEquals("PROBE_FAILED", BlankRootCause.ProbeFailed.eventReason)
        assertEquals("ENGINE_TOO_OLD", BlankRootCause.EngineTooOld.eventReason)
        assertEquals("JS_ERROR", BlankRootCause.JsError("x").eventReason)
        assertEquals("UNKNOWN", BlankRootCause.Unknown.eventReason)
    }

    // ── W4 拍板 5：分级中文文案 ──

    @Test
    fun `messages are graded chinese copy`() {
        val fallback = "页面已加载但内容为空，可能是脚本被拦截或需要登录，可尝试刷新页面"
        assertEquals(fallback, BlankRootCause.ProbeFailed.message)
        assertEquals(fallback, BlankRootCause.Unknown.message)
        assertTrue(BlankRootCause.EngineTooOld.message.startsWith("系统 WebView 内核过旧"))
        assertTrue(BlankRootCause.EngineTooOld.message.contains("Chrome 80"))
        assertTrue(BlankRootCause.JsError("boom").message.startsWith("页面脚本执行出错："))
        assertTrue(BlankRootCause.JsError("boom").message.endsWith("boom"))
    }

    @Test
    fun `js error message unescapes newline and quote then truncates`() {
        // 轻反转义：evaluateJavascript 读回的 JSON 转义以字面残留（\n、\"），仅还原最常见两种再截断。
        val raw = "a\\nb\\\"c" + "d".repeat(90)
        val message = BlankRootCause.JsError(raw).message
        assertTrue(message.contains("a b"))
        // `\"` 反转义为字面引号（序列 b"c 由 b\"c 还原而来）。
        assertTrue(message.contains("b\"c"))
        // 反转义后仍截断到 80 字符（含前缀之外的正文）。
        assertTrue(message.length <= "页面脚本执行出错：".length + 80)
    }
}
