package com.make.apk.logging

import com.make.apk.data.model.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 详情页文本格式化的单测。
 *
 * 跑在 **Robolectric** 下：本类用 `org.json.JSONObject` 解析 `fieldsJson`，
 * 而纯 JVM 单元测试里 org.json 是未打桩的空壳（方法体直接抛
 * `RuntimeException("Stub!")`）。Robolectric 提供 Android 真实实现，
 * 才能覆盖"合法 JSON / 非法 JSON / 空值"三条路径。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogDetailFormatterTest {

    // ── formatFields ─────────────────────────────────────────────────

    @Test
    fun formatFields_null_returnsEmpty() {
        assertTrue(LogDetailFormatter.formatFields(null).isEmpty())
    }

    @Test
    fun formatFields_blank_returnsEmpty() {
        assertTrue(LogDetailFormatter.formatFields("").isEmpty())
        assertTrue(LogDetailFormatter.formatFields("   ").isEmpty())
    }

    @Test
    fun formatFields_object_sortedByKeyLexicographically() {
        val fields = LogDetailFormatter.formatFields("""{"b":"x","a":1}""")

        assertEquals(listOf("a" to "1", "b" to "x"), fields)
    }

    @Test
    fun formatFields_nullValue_rendersAsNullLiteral() {
        val fields = LogDetailFormatter.formatFields("""{"a":null}""")

        assertEquals(listOf("a" to "null"), fields)
    }

    @Test
    fun formatFields_nestedObject_rendersAsJsonText() {
        val fields = LogDetailFormatter.formatFields("""{"meta":{"x":1}}""")

        val value = fields.single().second
        assertTrue("嵌套对象应原样呈现 JSON 文本，实际=$value", value.contains("\"x\""))
    }

    @Test
    fun formatFields_invalidJson_returnsRawFallback() {
        val fields = LogDetailFormatter.formatFields("{not json")

        assertEquals(listOf(LogDetailFormatter.RAW_KEY to "{not json"), fields)
    }

    @Test
    fun formatFields_stdoutSample_producesOrderedPairs() {
        // 与 JsonLite 产出一致（标准 JSON object，无空格）。
        val fields = LogDetailFormatter.formatFields("""{"duration_ms":120,"success":true}""")

        assertEquals(listOf("duration_ms" to "120", "success" to "true"), fields)
    }

    // ── fullText ─────────────────────────────────────────────────────

    @Test
    fun fullText_containsEveryKeySection() {
        val entry = LogEntry(
            id = 42L,
            timestampMs = 1_700_000_000_123L,
            level = "ERROR",
            category = "ERROR",
            event = "build_failed",
            message = "构建失败：依赖解析超时",
            fieldsJson = """{"step":"resolve","retry":2}""",
            stackTrace = "java.lang.IllegalStateException: boom\n\tat com.make.apk.Foo.bar(Foo.kt:12)",
            traceId = "trace-abc",
            sessionId = "session-xyz",
            sequence = 7L,
            threadName = "mma-log-writer",
            durationMs = 512L,
            success = false,
        )

        val text = LogDetailFormatter.fullText(entry)

        assertTrue("缺消息", text.contains("构建失败：依赖解析超时"))
        assertTrue("缺事件", text.contains("build_failed"))
        assertTrue("缺级别", text.contains("ERROR"))
        assertTrue("缺字段 key", text.contains("step"))
        assertTrue("缺字段 value", text.contains("resolve"))
        assertTrue("缺堆栈首行", text.contains("java.lang.IllegalStateException: boom"))
        assertTrue("缺堆栈帧", text.contains("at com.make.apk.Foo.bar(Foo.kt:12)"))
        assertTrue("缺 traceId", text.contains("trace-abc"))
        assertTrue("缺 sessionId", text.contains("session-xyz"))
        assertTrue("缺 sequence", text.contains("7"))
        assertTrue("缺 threadName", text.contains("mma-log-writer"))
        assertTrue("缺 durationMs", text.contains("512"))
        assertTrue("缺 success", text.contains("false"))
        assertTrue("缺 id", text.contains("42"))
        // 完整时间戳（毫秒级），排障时必须能对上文件日志。
        assertTrue("缺完整时间", text.contains(LogDetailFormatter.formatTimestamp(entry.timestampMs)))
    }

    @Test
    fun fullText_minimalEntry_doesNotLeakNullWords() {
        val entry = LogEntry(
            timestampMs = 1L,
            level = "INFO",
            category = "OPERATION",
            event = "logs_opened",
            traceId = "t",
            sessionId = "s",
            sequence = 1L,
            threadName = "main",
        )

        val text = LogDetailFormatter.fullText(entry)

        assertTrue("空字段不应出现 null 噪音", !text.contains("null"))
        assertTrue(!text.contains("异常栈"))
    }

    @Test
    fun formatTimestamp_isMillisecondPrecision() {
        val formatted = LogDetailFormatter.formatTimestamp(0L)

        // yyyy-MM-dd HH:mm:ss.SSS
        assertEquals(23, formatted.length)
        assertTrue("应为毫秒格式，实际=$formatted", formatted.endsWith(".000"))
    }
}
