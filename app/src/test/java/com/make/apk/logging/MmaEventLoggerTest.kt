package com.make.apk.logging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 业务事件埋点门面（W3）单测：
 * - 事件名拼接契约（`<domain>_<action>`）与字段词表（target_id / success / reason）；
 * - **未安装时空操作**（install 前全部安全返回 false，零副作用）；
 * - 类别路由（CRUD=OPERATION、preview_*=PREVIEW）与失败升 WARN。
 */
class MmaEventLoggerTest {

    private class RecordingSink : LogSink {
        val events = mutableListOf<LogEvent>()
        override fun write(event: LogEvent) {
            synchronized(events) { events.add(event) }
        }
    }

    private lateinit var sink: RecordingSink

    @Before
    fun setUp() {
        // 每个用例从「未安装」态出发：先保证卸载干净。
        MmaLogger.shutdown()
    }

    @After
    fun tearDown() {
        MmaLogger.shutdown()
    }

    private fun install() {
        sink = RecordingSink()
        MmaLogger.install(
            LogConfig(
                minLevel = LogLevel.DEBUG,
                logcatMinLevel = null,
                fileDirectory = null,
                enableCrashHandler = false,
                enableAnrWatchdog = false,
                rateLimitMaxPerWindow = 1_000,
            ),
        )
        MmaLogger.addSink(sink)
    }

    @Test
    fun beforeInstall_allCallsAreSafeNoOps() {
        // 未安装：全部安全空操作（返回 false、不抛异常、无副作用）。
        assertFalse(MmaEventLogger.crud("project", "create", targetId = 1L, success = true))
        assertFalse(MmaEventLogger.previewLoaded(durationMs = 10, success = true))
        assertFalse(MmaEventLogger.previewBlankConfirmed(pageUrlPresent = true))
        assertFalse(MmaEventLogger.previewSelfHeal("blank_content"))
        assertFalse(MmaEventLogger.previewBlankRootCause(MmaEventLogger.BlankCause.JS_ERROR))
        assertFalse(MmaEventLogger.settingChanged("fitToApp", false))
        assertFalse(MmaEventLogger.exportRunRecorded(1L, 1L, "RUNNING"))
        assertFalse(MmaEventLogger.webViewEvent("preview_loaded"))
    }

    @Test
    fun crud_buildsDomainActionEventName() {
        install()
        assertTrue(MmaEventLogger.crud("project", "create", targetId = 7L, success = true))

        val event = sink.events.single()
        assertEquals("project_create", event.event)
        assertEquals(LogCategory.OPERATION, event.category)
        assertEquals(LogLevel.INFO, event.level)
        assertEquals(7L, event.fields["target_id"])
        assertEquals(true, event.fields[LogFields.SUCCESS])
    }

    @Test
    fun crud_failureEscalatesToWarn() {
        install()
        assertTrue(MmaEventLogger.crud("project", "delete", targetId = 7L, success = false))
        assertEquals(LogLevel.WARN, sink.events.single().level)
    }

    @Test
    fun crud_rejectsBlankDomainOrAction() {
        install()
        assertFalse("空 domain 应拒发", MmaEventLogger.crud("", "create"))
        assertFalse("空 action 应拒发", MmaEventLogger.crud("project", "  "))
    }

    @Test
    fun crud_carriesExtraFields() {
        install()
        assertTrue(
            MmaEventLogger.crud(
                "project_file", "save", targetId = 3L, success = true,
                extra = mapOf("file" to "index.html"),
            ),
        )
        val event = sink.events.single()
        assertEquals("project_file_save", event.event)
        assertEquals("index.html", event.fields["file"])
    }

    @Test
    fun previewEvents_routeToPreviewCategory() {
        install()
        assertTrue(MmaEventLogger.previewLoaded(durationMs = 2_500, success = true))
        assertTrue(MmaEventLogger.previewBlankConfirmed(pageUrlPresent = true))
        assertTrue(MmaEventLogger.previewSelfHeal("blank_content"))
        assertTrue(
            MmaEventLogger.previewBlankRootCause(
                MmaEventLogger.BlankCause.ENGINE_TOO_OLD,
                detail = "syntax probe failed",
            ),
        )

        val byEvent = sink.events.associateBy { it.event }
        assertEquals(setOf("preview_loaded", "preview_blank_confirmed", "preview_self_heal", "preview_blank_root_cause"), byEvent.keys)
        byEvent.values.forEach { assertEquals(LogCategory.PREVIEW, it.category) }
        // 量化字段：loaded 带 duration/success；根因带 reason 枚举名；级别语义（loaded=INFO / 根因=ERROR）
        assertEquals(2_500L, byEvent.getValue("preview_loaded").fields[LogFields.DURATION_MS])
        assertEquals(LogLevel.INFO, byEvent.getValue("preview_loaded").level)
        assertEquals("ENGINE_TOO_OLD", byEvent.getValue("preview_blank_root_cause").fields[LogFields.REASON])
        assertEquals(LogLevel.ERROR, byEvent.getValue("preview_blank_root_cause").level)
        assertEquals(LogLevel.WARN, byEvent.getValue("preview_self_heal").level)
    }

    @Test
    fun webViewEvent_onlyAcceptsPreviewPrefixedNames() {
        install()
        assertTrue(MmaEventLogger.webViewEvent("preview_loaded", mapOf("duration_ms" to 5L)))
        assertFalse("非 preview_ 前缀一律拒绝（防事件名漂移）", MmaEventLogger.webViewEvent("page_started"))
        assertEquals("preview_loaded", sink.events.single().event)
    }

    @Test
    fun settingAndExportEvents_useOperationCategory() {
        install()
        assertTrue(MmaEventLogger.settingChanged("fitToApp", false))
        assertTrue(MmaEventLogger.exportRunRecorded(runId = 9L, projectId = 3L, status = "RUNNING"))
        assertTrue(
            MmaEventLogger.exportRunStatus(
                runId = 9L, status = "FAILED", durationMs = 1_200L, success = false, reason = "boom",
            ),
        )

        val byEvent = sink.events.associateBy { it.event }
        assertEquals(
            setOf("setting_changed", "export_run_recorded", "export_run_status"),
            byEvent.keys,
        )
        byEvent.values.forEach {
            assertEquals(LogCategory.OPERATION, it.category)
        }
        assertEquals(false, byEvent.getValue("export_run_status").fields[LogFields.SUCCESS])
        assertEquals(LogLevel.WARN, byEvent.getValue("export_run_status").level)
    }
}
