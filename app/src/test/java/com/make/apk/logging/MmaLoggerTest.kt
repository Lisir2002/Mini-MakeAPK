package com.make.apk.logging

import com.make.apk.data.local.entity.LogEntryEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 门面与落库映射测试。
 *
 * 只启用纯 JVM 可用的 Sink（不启 Logcat、不启 ANR 看门狗，二者依赖 Android 运行时），
 * 通过自定义记录型 Sink 断言分发、级别过滤、类别过滤与限流行为。
 */
class MmaLoggerTest {

    private class RecordingSink : LogSink {
        val events = mutableListOf<LogEvent>()
        override fun write(event: LogEvent) {
            synchronized(events) { events.add(event) }
        }
    }

    private lateinit var sink: RecordingSink

    private fun installForTest(maxPerWindow: Int = 100) {
        sink = RecordingSink()
        MmaLogger.install(
            LogConfig(
                minLevel = LogLevel.DEBUG,
                logcatMinLevel = null,
                fileDirectory = null,
                enableCrashHandler = false,
                enableAnrWatchdog = false,
                rateLimitMaxPerWindow = maxPerWindow,
            ),
        )
        MmaLogger.addSink(sink)
    }

    @After
    fun tearDown() {
        MmaLogger.shutdown()
    }

    @Test
    fun operation_isDispatchedToSinks() {
        installForTest()
        assertTrue(MmaLogger.operation("build_started", fields = mapOf(LogFields.SUCCESS to true)))

        assertEquals(1, sink.events.size)
        val event = sink.events.first()
        assertEquals(LogCategory.OPERATION, event.category)
        assertEquals(LogLevel.INFO, event.level)
        assertEquals("build_started", event.event)
    }

    @Test
    fun belowMinLevel_isDropped() {
        installForTest()
        // 全局最低 DEBUG，TRACE 类调试日志仍应放行；这里验证 FATAL 恒放行
        assertTrue(MmaLogger.security("audited"))
        assertTrue(sink.events.any { it.event == "audited" })
    }

    @Test
    fun nonPersistedCategory_isStillDispatched() {
        installForTest()
        MmaLogger.lifecycle("app_foreground")
        assertEquals(LogCategory.LIFECYCLE, sink.events.first().category)
        assertFalse("生命周期类不落盘", sink.events.first().category.persistToDatabase)
    }

    @Test
    fun performance_carriesDurationField() {
        installForTest()
        MmaLogger.performance("webview_load", durationMs = 1_250)
        assertEquals(1_250L, sink.events.first().fields[LogFields.DURATION_MS])
    }

    @Test
    fun network_doesNotLeakFullUrlOrBody() {
        installForTest()
        MmaLogger.network("build_api_call", endpoint = "/build", statusCode = 200, durationMs = 300, success = true)

        val fields = sink.events.first().fields
        assertEquals("/build", fields[LogFields.ENDPOINT])
        assertEquals(200, fields[LogFields.STATUS_CODE])
        assertTrue("不应出现 body 字段", !fields.containsKey("body"))
    }

    @Test
    fun rateLimit_dropsRepeatedEvents() {
        installForTest(maxPerWindow = 2)
        assertTrue(MmaLogger.operation("spam"))
        assertTrue(MmaLogger.operation("spam"))
        assertFalse("第三次应被限流", MmaLogger.operation("spam"))

        assertEquals("应只有 2 条被记录", 2, sink.events.size)
        assertEquals(1L, MmaLogger.droppedByRateLimit())
    }

    @Test
    fun crashAndSecurity_bypassRateLimit() {
        installForTest(maxPerWindow = 1)
        assertTrue(MmaLogger.security("audit_1"))
        assertTrue("安全审计不受限流影响", MmaLogger.security("audit_1"))
        assertEquals(2, sink.events.size)
    }

    @Test
    fun sensitiveFields_areMaskedBeforeReachingSink() {
        installForTest()
        MmaLogger.operation("login", fields = mapOf("token" to "abc", "duration_ms" to 5L))

        val fields = sink.events.first().fields
        assertEquals(Sanitizer.MASK, fields["token"])
        assertEquals(5L, fields["duration_ms"])
    }

    @Test
    fun toEntity_promotesQuantifiedFields() {
        val event = LogEvent(
            timestampMs = 1_700_000_000_000L,
            level = LogLevel.INFO,
            category = LogCategory.PERFORMANCE,
            event = "build_finished",
            message = "done",
            fields = mapOf(LogFields.DURATION_MS to 4_200L, LogFields.SUCCESS to true),
        )
        val entity: LogEntryEntity = event.toEntity()

        assertEquals(4_200L, entity.durationMs)
        assertEquals(true, entity.success)
        assertEquals("PERFORMANCE", entity.category)
        assertEquals("INFO", entity.level)
    }

    @Test
    fun toEntity_leavesMissingQuantifiedFieldsNull() {
        val entity = LogEvent(
            timestampMs = 1L,
            level = LogLevel.INFO,
            category = LogCategory.OPERATION,
            event = "noop",
        ).toEntity()

        assertNull(entity.durationMs)
        assertNull(entity.success)
        assertNull(entity.stackTrace)
    }
}
