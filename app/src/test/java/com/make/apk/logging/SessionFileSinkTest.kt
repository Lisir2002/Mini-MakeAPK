package com.make.apk.logging

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 会话化多文件出口（W3 ⑦）单测（纯 JVM，复用 AsyncLogDispatcher + flush 语义）：
 * - 类别路由（operations / preview / app 三文件，含兜底 app.log）；
 * - 会话目录 `mkdirs` 自愈（被删后自动重建，rc9 需求）；
 * - 单文件轮转 20 上限；
 * - 文件名安全铁律（非法路由名拒绝写入）。
 */
class SessionFileSinkTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var sink: SessionFileSink

    private fun newSink(sessionDirName: String = SessionLogPolicy.sessionDirName(0L, "test0001")): SessionFileSink =
        SessionFileSink(
            root = tmp.root,
            sessionDirName = sessionDirName,
            maxFileSizeBytes = 64L,
            maxFilesPerFile = 20,
            minLevel = LogLevel.DEBUG,
        )

    private fun event(category: LogCategory, event: String) = LogEvent(
        timestampMs = System.currentTimeMillis(),
        level = LogLevel.INFO,
        category = category,
        event = event,
    )

    @After
    fun tearDown() {
        if (::sink.isInitialized) sink.close()
    }

    @Test
    fun routing_operationsSecurityPreviewAndRest() {
        // 路由语义测试用大阈值（4KB）避免轮转干扰——每条事件约 170B，5 条共 ~850B 单文件容纳；
        // 轮转语义由 rotation_capsRotatedFilesAtTwenty 专门覆盖。
        sink = SessionFileSink(
            root = tmp.root,
            sessionDirName = SessionLogPolicy.sessionDirName(0L, "test0001"),
            maxFileSizeBytes = 4_096L,
            maxFilesPerFile = 20,
            minLevel = LogLevel.DEBUG,
        )
        sink.write(event(LogCategory.OPERATION, "op_event"))
        sink.write(event(LogCategory.SECURITY, "sec_event"))
        sink.write(event(LogCategory.PREVIEW, "preview_event"))
        sink.write(event(LogCategory.STARTUP, "startup_event"))
        sink.write(event(LogCategory.ERROR, "error_event"))
        sink.flush()

        val dir = sink.sessionDirectory
        val operations = File(dir, "operations.log").readText()
        val preview = File(dir, "preview.log").readText()
        val app = File(dir, "app.log").readText()

        assertTrue("OPERATION 应落 operations.log", operations.contains("op_event"))
        assertTrue("SECURITY 应落 operations.log", operations.contains("sec_event"))
        assertFalse("OPERATION 不得串到 app.log", app.contains("op_event"))
        assertTrue("PREVIEW 应落 preview.log", preview.contains("preview_event"))
        assertTrue("STARTUP 应落 app.log", app.contains("startup_event"))
        assertTrue("ERROR 应落 app.log", app.contains("error_event"))
    }

    @Test
    fun sessionDirectory_selfHealsAfterDeletion() {
        sink = newSink()
        sink.write(event(LogCategory.STARTUP, "before_delete"))
        sink.flush()
        assertTrue(sink.sessionDirectory.isDirectory)

        // 模拟用户删除会话目录（rc9 需求：删除后自动补全）
        sink.sessionDirectory.deleteRecursively()
        sink.write(event(LogCategory.STARTUP, "after_delete"))
        sink.flush()

        assertTrue("会话目录应自愈重建", sink.sessionDirectory.isDirectory)
        assertTrue(
            "删除后的写入应继续落盘",
            File(sink.sessionDirectory, "app.log").readText().contains("after_delete"),
        )
    }

    @Test
    fun rotation_capsRotatedFilesAtTwenty() {
        sink = newSink()
        // 64B 阈值 + 每条约 40B：连写 60 条必然触发 20+ 次轮转
        repeat(60) { index -> sink.write(event(LogCategory.STARTUP, "rotation_fill_event_$index")) }
        sink.flush()

        val rotated = sink.sessionDirectory
            .listFiles { f -> f.isFile && f.name.startsWith("app.log.") }
            ?.size ?: 0
        assertTrue("轮转历史不得超 20（实际 $rotated）", rotated <= 20)
    }

    @Test
    fun blankRouting_fallsBackToDefaultAppLog() {
        sink = SessionFileSink(
            root = tmp.root,
            sessionDirName = SessionLogPolicy.sessionDirName(0L, "test0001"),
            maxFileSizeBytes = 64L,
            routing = { "" }, // 路由未登记 → 兜底
            minLevel = LogLevel.DEBUG,
        )
        sink.write(event(LogCategory.STARTUP, "fallback_event"))
        sink.flush()
        assertTrue(
            "空白路由名应兜底 app.log",
            File(sink.sessionDirectory, SessionFileSink.APP_FILE).readText().contains("fallback_event"),
        )
    }

    @Test
    fun unsafeRoutingName_isRejectedWithoutWrite() {
        sink = SessionFileSink(
            root = tmp.root,
            sessionDirName = SessionLogPolicy.sessionDirName(0L, "test0001"),
            maxFileSizeBytes = 64L,
            routing = { "../evil.log" }, // 注入路径穿越：必须拒绝
            minLevel = LogLevel.DEBUG,
        )
        sink.write(event(LogCategory.STARTUP, "should_not_persist"))
        sink.flush()
        assertFalse(
            "路径穿越路由名不得产生文件",
            sink.sessionDirectory.listFiles()?.isNotEmpty() == true,
        )
    }

    @Test
    fun defaultRoute_matchesDecidedPolicy() {
        // 主控拍板的路由表（与 docs/logging.md §3.1 一致）
        assertEquals(SessionFileSink.OPERATIONS_FILE, SessionFileSink.defaultRoute(LogCategory.OPERATION))
        assertEquals(SessionFileSink.OPERATIONS_FILE, SessionFileSink.defaultRoute(LogCategory.SECURITY))
        assertEquals(SessionFileSink.PREVIEW_FILE, SessionFileSink.defaultRoute(LogCategory.PREVIEW))
        assertEquals(SessionFileSink.APP_FILE, SessionFileSink.defaultRoute(LogCategory.STARTUP))
        assertEquals(SessionFileSink.APP_FILE, SessionFileSink.defaultRoute(LogCategory.CRASH))
        assertEquals(SessionFileSink.APP_FILE, SessionFileSink.defaultRoute(LogCategory.LIFECYCLE))
    }
}
