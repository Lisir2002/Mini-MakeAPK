package com.make.apk.logging

import com.make.apk.data.local.dao.LogEntryDao
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * 日志配置。按构建类型差异化：`debug` 开 Logcat 且级别降到 DEBUG，
 * `release` 只保留 INFO 以上且不开 Logcat。
 */
data class LogConfig(
    /** 全局最低级别，低于此级别的事件直接丢弃。 */
    val minLevel: LogLevel = LogLevel.INFO,
    /** Logcat 出口的最低级别；null 表示不启用 Logcat。 */
    val logcatMinLevel: LogLevel? = null,
    /** 轮转文件目录（应为 App 私有目录）；null 表示不落文件。 */
    val fileDirectory: File? = null,
    /** 文件出口的最低级别。 */
    val fileMinLevel: LogLevel = LogLevel.INFO,
    val maxFileSizeBytes: Long = 2L * 1024 * 1024,
    val maxFiles: Int = 5,
    /** 是否安装 Java 崩溃处理器。 */
    val enableCrashHandler: Boolean = true,
    /** 是否启动 ANR 看门狗。 */
    val enableAnrWatchdog: Boolean = true,
    val anrTimeoutMs: Long = AnrWatchdog.DEFAULT_TIMEOUT_MS,
    /** 同一事件在窗口内允许通过的最大条数（防日志风暴）。 */
    val rateLimitMaxPerWindow: Int = 30,
    val rateLimitWindowMs: Long = 60_000L,
)

/**
 * **MMA 统一日志门面**（自研，零第三方依赖）。
 *
 * 职责边界：
 * - 只做三件事：**构造结构化事件 → 强制脱敏 → 分发给 Sink**。
 * - 不决定落哪里（Sink 决定）、不做网络上报（当前阶段纯本地，见 `docs/logging.md`）。
 *
 * 全类别语义化入口对应 [LogCategory] 的九大类，避免调用方记错级别与类别。
 * 所有可量化维度（耗时/次数/大小/成败）通过 [LogFields] 的约定键传入。
 *
 * 未 `install()` 时所有调用都是安全的空操作——保证日志不会成为启动崩溃的原因。
 */
object MmaLogger {

    private val sinks = CopyOnWriteArrayList<LogSink>()

    @Volatile
    private var minLevel: LogLevel = LogLevel.INFO

    @Volatile
    private var installed = false

    @Volatile
    private var databaseSink: DatabaseSink? = null

    @Volatile
    private var fileSink: FileSink? = null

    @Volatile
    private var crashHandler: CrashHandler? = null

    @Volatile
    private var anrWatchdog: AnrWatchdog? = null

    private var limiter = RateLimiter(windowMs = 60_000L, maxPerWindow = 30)
    private val droppedByLimit = AtomicLong(0)

    // ── 安装 ──────────────────────────────────────────────────────────

    /** 安装日志系统。应在 `Application.attachBaseContext()` 中尽早调用。 */
    fun install(config: LogConfig) {
        if (installed) return
        minLevel = config.minLevel
        limiter = RateLimiter(config.rateLimitWindowMs, config.rateLimitMaxPerWindow)

        AsyncLogDispatcher.start()

        config.logcatMinLevel?.let { level -> sinks.add(LogcatSink(level)) }

        config.fileDirectory?.let { dir ->
            val sink = FileSink(
                directory = dir,
                maxFileSizeBytes = config.maxFileSizeBytes,
                maxFiles = config.maxFiles,
                minLevel = config.fileMinLevel,
            )
            fileSink = sink
            sinks.add(sink)
        }

        val dbSink = DatabaseSink(config.minLevel)
        databaseSink = dbSink
        sinks.add(dbSink)

        if (config.enableCrashHandler) {
            val handler = CrashHandler { throwable, thread, stack ->
                crash(throwable, thread, stack)
            }
            crashHandler = handler
            handler.install()
        }

        if (config.enableAnrWatchdog) {
            val watchdog = AnrWatchdog(timeoutMs = config.anrTimeoutMs) { stack ->
                anr(stack)
            }
            anrWatchdog = watchdog
            watchdog.start()
        }

        installed = true
    }

    /** 数据库就绪后接入，回灌启动期缓冲的日志。 */
    fun attachDao(dao: LogEntryDao) {
        databaseSink?.attach(dao)
    }

    fun addSink(sink: LogSink) {
        sinks.add(sink)
    }

    // ── 通用入口 ──────────────────────────────────────────────────────

    /**
     * 写一条日志。
     * @return 是否真正写出；false 表示被级别过滤、限流丢弃，或尚未安装。
     */
    fun log(
        level: LogLevel,
        category: LogCategory,
        event: String,
        message: String? = null,
        fields: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
        bypassRateLimit: Boolean = false,
    ): Boolean {
        if (!installed) return false
        if (!level.isEnabledFor(minLevel)) return false

        // 崩溃与安全审计**绝不因限流丢失**（后者是合规要求）
        val limitable = category != LogCategory.CRASH && category != LogCategory.SECURITY
        if (!bypassRateLimit && limitable && !limiter.allow("${category.name}/$event")) {
            droppedByLimit.incrementAndGet()
            return false
        }

        val logEvent = LogEvent(
            timestampMs = System.currentTimeMillis(),
            level = level,
            category = category,
            event = event,
            message = Sanitizer.sanitizeMessage(message),
            fields = Sanitizer.sanitizeFields(fields),
            throwable = throwable,
        )

        sinks.forEach { sink -> runCatching { sink.write(logEvent) } }
        return true
    }

    // ── 九大类别的语义化入口 ──────────────────────────────────────────

    /** 操作 / 审计：用户做了什么。合规保留期最长，不受限流影响。 */
    fun operation(event: String, message: String? = null, fields: Map<String, Any?> = emptyMap()): Boolean =
        log(LogLevel.INFO, LogCategory.OPERATION, event, message, fields)

    /** 启动：冷/温/热型 + 阶段耗时。 */
    fun startup(event: String, message: String? = null, fields: Map<String, Any?> = emptyMap()): Boolean =
        log(LogLevel.INFO, LogCategory.STARTUP, event, message, fields)

    /** 生命周期：前后台切换等（不落盘）。 */
    fun lifecycle(event: String, fields: Map<String, Any?> = emptyMap()): Boolean =
        log(LogLevel.DEBUG, LogCategory.LIFECYCLE, event, null, fields)

    /** 性能：必须带 [LogFields.DURATION_MS]。 */
    fun performance(event: String, durationMs: Long, fields: Map<String, Any?> = emptyMap()): Boolean =
        log(
            level = LogLevel.INFO,
            category = LogCategory.PERFORMANCE,
            event = event,
            fields = fields + (LogFields.DURATION_MS to durationMs),
        )

    /** 网络：只记端点路径 / 状态码 / 耗时 / 重试，**不记 body 与完整 URL**。 */
    fun network(
        event: String,
        endpoint: String,
        statusCode: Int? = null,
        durationMs: Long? = null,
        attempt: Int? = null,
        success: Boolean? = null,
    ): Boolean = log(
        level = if (success == false) LogLevel.WARN else LogLevel.INFO,
        category = LogCategory.NETWORK,
        event = event,
        fields = buildMap {
            put(LogFields.ENDPOINT, endpoint)
            statusCode?.let { put(LogFields.STATUS_CODE, it) }
            durationMs?.let { put(LogFields.DURATION_MS, it) }
            attempt?.let { put(LogFields.ATTEMPT, it) }
            success?.let { put(LogFields.SUCCESS, it) }
        },
    )

    /** 错误：已捕获但操作失败，用户可感知 → 可告警。 */
    fun error(
        event: String,
        throwable: Throwable? = null,
        message: String? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): Boolean = log(
        level = LogLevel.ERROR,
        category = LogCategory.ERROR,
        event = event,
        message = message ?: throwable?.javaClass?.simpleName,
        fields = fields + (LogFields.SUCCESS to false),
        throwable = throwable,
    )

    /** 安全审计：口令生成、加解密异常、日志导出等。不受限流影响（合规）。 */
    fun security(event: String, message: String? = null, fields: Map<String, Any?> = emptyMap()): Boolean =
        log(LogLevel.WARN, LogCategory.SECURITY, event, message, fields)

    /** 调试：仅 debug 生效。 */
    fun debug(event: String, message: String? = null, fields: Map<String, Any?> = emptyMap()): Boolean =
        log(LogLevel.DEBUG, LogCategory.TRACE, event, message, fields)

    // ── 崩溃 / ANR ────────────────────────────────────────────────────

    /** Java 崩溃：写完后**尽力同步刷盘**，因为进程即将终止。 */
    fun crash(throwable: Throwable, thread: Thread = Thread.currentThread(), stack: String? = null): Boolean {
        val written = log(
            level = LogLevel.FATAL,
            category = LogCategory.CRASH,
            event = "uncaught_exception",
            message = throwable.javaClass.simpleName,
            fields = mapOf("thread" to thread.name, LogFields.SUCCESS to false),
            throwable = throwable,
            bypassRateLimit = true,
        )
        if (stack != null) {
            log(
                level = LogLevel.FATAL,
                category = LogCategory.CRASH,
                event = "crash_stack",
                message = stack,
                bypassRateLimit = true,
            )
        }
        flushBlocking()
        return written
    }

    /** ANR：主线程栈。 */
    fun anr(mainThreadStack: String): Boolean {
        val written = log(
            level = LogLevel.FATAL,
            category = LogCategory.CRASH,
            event = "anr_detected",
            message = "主线程无响应",
            fields = mapOf(LogFields.SUCCESS to false),
            bypassRateLimit = true,
        )
        log(
            level = LogLevel.FATAL,
            category = LogCategory.CRASH,
            event = "anr_stack",
            message = mainThreadStack,
            bypassRateLimit = true,
        )
        flushBlocking()
        return written
    }

    // ── 运维 ──────────────────────────────────────────────────────────

    /** 尽力把缓冲落盘（崩溃前 / 导出诊断包前调用）。 */
    fun flushBlocking(timeoutMs: Long = 1_000L) {
        AsyncLogDispatcher.drain(timeoutMs)
    }

    /** 日志文件列表（活跃 + 历史），供导出诊断包。 */
    fun logFiles(): List<File> = fileSink?.logFiles() ?: emptyList()

    /** 被限流丢弃的条数：定期自检，避免"静默丢日志"。 */
    fun droppedByRateLimit(): Long = droppedByLimit.get()

    /** 异步队列丢弃的条数。 */
    fun droppedByOverflow(): Long = AsyncLogDispatcher.dropped()

    /** 关闭（一般只在测试或进程收尾时调用）。 */
    fun shutdown() {
        anrWatchdog?.shutdown()
        sinks.forEach { runCatching { it.close() } }
        sinks.clear()
        AsyncLogDispatcher.stop()
        installed = false
    }
}
