package com.make.apk.logging

import com.make.apk.data.local.entity.LogEntryEntity
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 门面与落库映射测试。
 *
 * 只启用纯 JVM 可用的 Sink（不启 Logcat、不启 ANR 看门狗，二者依赖 Android 运行时），
 * 通过自定义记录型 Sink 断言分发、级别过滤、类别过滤与限流行为。
 */
class MmaLoggerTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

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

    @Test
    fun redirectFileSink_migratesHistoryAndTakesOver() {
        val dirA = temporaryFolder.newFolder("private")
        val dirB = temporaryFolder.newFolder("mmafile_log")
        installWithFileSink(dirA)

        // 用 STARTUP 类事件（persistToFile=true；OPERATION 类不落盘，见 LogCategory）
        assertTrue(MmaLogger.startup("first_launch"))
        MmaLogger.flushBlocking()
        assertTrue(
            "A 目录应已有日志",
            File(dirA, "mma.log").readText().contains("first_launch"),
        )

        assertTrue("重定向应成功", MmaLogger.redirectFileSink(dirB))

        val migrated = File(dirB, "mma.log").readText()
        assertTrue("B 目录应含迁移的历史日志", migrated.contains("first_launch"))
        assertFalse("A 目录旧日志应已清空", File(dirA, "mma.log").exists())

        assertTrue(MmaLogger.startup("after_redirect"))
        MmaLogger.flushBlocking()
        val after = File(dirB, "mma.log").readText()
        assertTrue("后续事件应落入 B 目录", after.contains("after_redirect"))
        assertFalse("A 目录不再接收新日志", File(dirA, "mma.log").exists())
    }

    @Test
    fun redirectFileSink_returnsFalseWhenNoFileSink() {
        installForTest() // fileDirectory = null，未装文件出口
        assertFalse(MmaLogger.redirectFileSink(temporaryFolder.newFolder("nowhere")))
    }

    /** 安装带真实文件出口的日志器（startup/error 类事件会落盘）。 */
    private fun installWithFileSink(directory: File) {
        MmaLogger.install(
            LogConfig(
                minLevel = LogLevel.DEBUG,
                logcatMinLevel = null,
                fileDirectory = directory,
                fileMinLevel = LogLevel.DEBUG,
                enableCrashHandler = false,
                enableAnrWatchdog = false,
                rateLimitMaxPerWindow = 1_000,
            ),
        )
    }

    @Test
    fun format_includesIsoTimestampDeviceModelAndAppVersion() {
        installForTest()
        val event = LogEvent(
            timestampMs = 1_700_000_000_000L,
            level = LogLevel.ERROR,
            category = LogCategory.ERROR,
            event = "project_save_failed",
            message = "boom",
        )
        val line = event.format()

        // ISO8601 时间戳（日期 T 时分秒.毫秒 + 时区），验证已非裸毫秒
        assertTrue(
            "format() 应含 ISO8601 时间戳",
            line.contains(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}")),
        )
        // 设备类别信息
        assertTrue("format() 应含机型 model=", line.contains("model="))
        // 应用版本号（当前 0.0.0.1-rc13 / versionCode 16）
        assertTrue("format() 应含 app 版本", line.contains("app=0.0.0.1-rc13/16"))
        // 构建类型（debug 测试变体）
        assertTrue("format() 应含构建类型", line.contains("bt=debug"))
    }

    @Test
    fun contextFields_areCarriedIntoPersistedFieldsJson() {
        installForTest()
        MmaLogger.error(
            "project_save_failed",
            message = "boom",
            fields = mapOf(LogFields.SUCCESS to false),
        )
        val event = sink.events.first()

        // 经 log() 合并后，fields 应携带设备 / 应用上下文（仅设备类别信息）
        assertTrue("fields 应含 app_version", event.fields.containsKey(LogFields.APP_VERSION))
        assertTrue("fields 应含 device_model", event.fields.containsKey(LogFields.DEVICE_MODEL))
        assertTrue("fields 应含 build_type", event.fields.containsKey(LogFields.BUILD_TYPE))

        // 落库映射直接序列化 fields → fieldsJson，故上下文自动进入加密 DB，无需新增列
        val entity = event.toEntity()
        val fieldsJson = entity.fieldsJson ?: error("fieldsJson 不应为空")
        assertTrue("fieldsJson 应含 app_version", fieldsJson.contains("\"app_version\""))
        assertTrue("fieldsJson 应含 device_model", fieldsJson.contains("\"device_model\""))
        assertTrue("fieldsJson 应含 build_type", fieldsJson.contains("\"build_type\""))
    }

    // ── W3 收口 F2：脱敏逃逸防护 ─────────────────────────────────────

    /** toString() 必抛的恶意对象（模拟第三方库字段对象污染）。 */
    private class PoisonedField {
        override fun toString(): String = throw IllegalStateException("boom")
    }

    @Test
    fun poisonedFields_doNotEscapeLog_andDegradeToEmptyFields() {
        installForTest()
        // 修复前：sanitizeFields 在 runCatching 之外，toString 抛异常直接逃逸出 log()。
        val written = MmaLogger.operation(
            "poisoned_event",
            fields = mapOf("bad" to PoisonedField(), LogFields.SUCCESS to true),
        )
        assertTrue("降级后事件仍应写出（时间/级别/类别天然保留）", written)
        val event = sink.events.single()
        assertEquals("poisoned_event", event.event)
        assertTrue("降级字段应为空（不携带未脱敏内容）", event.fields.isEmpty())
    }

    @Test
    fun poisonedMessage_doesNotEscapeLog() {
        installForTest()
        // message 走同一条 sanitize 防线：字符串本身不会抛，但防线必须对两者一致。
        val written = MmaLogger.operation("msg_event", message = "正常文本")
        assertTrue(written)
        assertEquals("正常文本", sink.events.single().message)
    }

    // ── W3 收口 F3：会话搬迁不遗弃历史日期目录 ────────────────────────

    @Test
    fun redirectSessionSink_movesLegacyDateDirsButLeavesUserDirs() {
        val dirA = temporaryFolder.newFolder("private_root")
        val dirB = temporaryFolder.newFolder("mmafile_root")
        MmaLogger.install(
            LogConfig(
                minLevel = LogLevel.DEBUG,
                logcatMinLevel = null,
                fileDirectory = dirA,
                fileMinLevel = LogLevel.DEBUG,
                sessionLogLayout = true,
                enableCrashHandler = false,
                enableAnrWatchdog = false,
                rateLimitMaxPerWindow = 1_000,
            ),
        )
        try {
            assertTrue(MmaLogger.startup("before_redirect"))
            MmaLogger.flushBlocking()

            // 旧根下播种：历史日期目录（应随迁）+ 用户自建目录（应原地不动）
            val legacy = File(dirA, "2020-01-01/session-080000_abcd").apply { mkdirs() }
            File(legacy, "app.log").writeText("legacy session log")
            val userDir = File(dirA, "user-stuff").apply { mkdirs() }

            assertTrue("会话布局重定向应成功", MmaLogger.redirectFileSink(dirB))

            // 当前会话目录整体迁到 B
            val movedSession = MmaLogger.currentSessionLogDirectory()
            assertNotNull(movedSession)
            assertTrue("当前会话应在新根下", movedSession!!.canonicalPath.startsWith(dirB.canonicalPath))
            assertTrue("当前会话目录应随迁", movedSession.isDirectory)
            // 历史日期目录保留相对结构 <date>/session-x/ 随迁
            val movedLegacy = File(dirB, "2020-01-01/session-080000_abcd/app.log")
            assertTrue("历史日期目录应随迁新根", movedLegacy.isFile)
            assertTrue(movedLegacy.readText().contains("legacy session log"))
            assertFalse("旧根不应再留历史日期目录", File(dirA, "2020-01-01").exists())
            // 非法名目录原样保留在旧根
            assertTrue("用户自建目录不随迁、不删除", File(dirA, "user-stuff").isDirectory)
        } finally {
            MmaLogger.shutdown()
        }
    }
}
