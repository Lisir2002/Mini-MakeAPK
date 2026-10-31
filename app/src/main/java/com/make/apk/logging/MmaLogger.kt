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
    /**
     * 会话化多文件布局（W3 ⑦）：true 时 [fileDirectory] 下建
     * `yyyy-MM-dd/session-HHmmss_<短码>/` 会话目录，并按类别路由
     * operations.log / preview.log / app.log（[SessionFileSink]）；
     * false（默认）保持单一大 `mma.log`（完全兼容旧路径）。
     */
    val sessionLogLayout: Boolean = false,
    /** 会话日期目录保留天数（W3 ⑦，启动清理用；仅清理合法日期目录名）。 */
    val logRetentionDays: Int = 14,
    /** 会话布局下单个路由文件的历史轮转数上限（主控拍板：20）。 */
    val maxFilesPerSessionFile: Int = 20,
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

    /** 文件出口重定向互斥锁：redirect 的「摘除→迁移→重建→挂载」必须整体原子。 */
    private val redirectLock = Any()

    @Volatile
    private var minLevel: LogLevel = LogLevel.INFO

    @Volatile
    private var installed = false

    @Volatile
    private var databaseSink: DatabaseSink? = null

    @Volatile
    private var fileSink: FileSink? = null

    /** FileSink 构造参数快照：redirectFileSink 迁移目录后按原配置重建，不改轮转策略。 */
    private var fileSinkSettings: FileSinkSettings? = null

    @Volatile
    private var sessionSink: SessionFileSink? = null

    /** SessionFileSink 构造参数快照（redirect 后按原 sessionDirName 重建，不改路由）。 */
    private var sessionSinkSettings: SessionSinkSettings? = null

    @Volatile
    private var crashHandler: CrashHandler? = null

    @Volatile
    private var anrWatchdog: AnrWatchdog? = null

    private var limiter = RateLimiter(windowMs = 60_000L, maxPerWindow = 30)
    private val droppedByLimit = AtomicLong(0)

    /** 门面自诊断的 Logcat 标签（见 [logW]）。 */
    private const val TAG_SELF = "mma-logger-self"

    // ── 安装 ──────────────────────────────────────────────────────────

    /** 安装日志系统。应在 `Application.attachBaseContext()` 中尽早调用。 */
    @Synchronized
    fun install(config: LogConfig) {
        if (installed) return
        minLevel = config.minLevel
        limiter = RateLimiter(config.rateLimitWindowMs, config.rateLimitMaxPerWindow)

        AsyncLogDispatcher.start()

        config.logcatMinLevel?.let { level -> sinks.addIfAbsent(LogcatSink(level)) }

        config.fileDirectory?.let { dir ->
            if (config.sessionLogLayout) {
                // 会话布局（W3 ⑦）：目录名在安装时刻定格（跨午夜归属启动日）。
                val sessionDirName = SessionLogPolicy.sessionDirName(
                    System.currentTimeMillis(),
                    TraceContext.sessionId,
                )
                val sink = SessionFileSink(
                    root = dir,
                    sessionDirName = sessionDirName,
                    maxFileSizeBytes = config.maxFileSizeBytes,
                    maxFilesPerFile = config.maxFilesPerSessionFile,
                    minLevel = config.fileMinLevel,
                )
                sessionSink = sink
                sinks.addIfAbsent(sink)
                sessionSinkSettings = SessionSinkSettings(
                    sessionDirName = sessionDirName,
                    maxFileSizeBytes = config.maxFileSizeBytes,
                    maxFilesPerFile = config.maxFilesPerSessionFile,
                    minLevel = config.fileMinLevel,
                )
            } else {
                val sink = FileSink(
                    directory = dir,
                    maxFileSizeBytes = config.maxFileSizeBytes,
                    maxFiles = config.maxFiles,
                    minLevel = config.fileMinLevel,
                )
                fileSink = sink
                sinks.addIfAbsent(sink)
                fileSinkSettings = FileSinkSettings(
                    maxFileSizeBytes = config.maxFileSizeBytes,
                    maxFiles = config.maxFiles,
                    minLevel = config.fileMinLevel,
                )
            }
        }

        val dbSink = DatabaseSink(config.minLevel)
        databaseSink = dbSink
        sinks.addIfAbsent(dbSink)

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

        // 防御性自愈（安装期兜底）：若已有重复文件出口（并发安装 / 未知路径重复挂载），立即收敛。
        // 注意：此处 prune 计数为 0 是常态——重定向在 install 之后才开始，这里只兜未知路径。
        reportPrunedFileSinks(pruneDuplicateSessionSinks())
    }

    /** 数据库就绪后接入，回灌启动期缓冲的日志。 */
    fun attachDao(dao: LogEntryDao) {
        databaseSink?.attach(dao)
    }

    /**
     * 把文件日志出口迁移到新目录（典型场景：用户刚授予「所有文件访问」，
     * 从私有兜底目录切到 `MMAFile/Log`）。
     *
     * 顺序是正确性关键：`FileSink.write` 提交给 AsyncLogDispatcher 的闭包
     * **捕获的是旧 sink 实例**，若先复制再换，在途事件仍写旧目录、迁移后即丢。故：
     * 1. 摘除旧 sink（此后新事件不再流向旧目录）；
     * 2. drain 排空在途事件；
     * 3. 旧日志文件合并进新目录，成功后删旧；
     * 4. 装新 sink 按原配置接管。
     *
     * 复制窗口（≤ 日志总量，通常 <1s）内新增事件只进 DB/Logcat、不进文件——
     * 可接受的损耗（崩溃类走 [flushBlocking] 不受影响）。
     *
     * @return 是否迁移成功；未安装文件出口或迁移失败返回 false（旧 sink 复位，不丢日志）。
     */
    fun redirectFileSink(newDirectory: File): Boolean {
        // 整体原子：并行调用方（db-init 后台线程 / Compose LaunchedEffect 的 IO 线程）交错时，
        // 各自 add 一个 newSink 而 remove(old) 只对第一个生效 → 同一条日志被写 N 份。
        val (redirected, pruned) = synchronized(redirectLock) {
            val session = sessionSink
            val redirectedNow = if (session != null) {
                // 会话布局（W3 ⑦）：整体搬迁当前会话目录（保留 sessionDirName，路由不变）。
                redirectSessionSink(session, newDirectory)
            } else {
                redirectSingleFileSink(newDirectory)
            }
            // 自愈兜底：即便仍有未知路径重复挂载，也在返回前把文件出口收敛到单实例。
            redirectedNow to pruneDuplicateSessionSinks()
        }
        // 锁外上报：持锁回调 log() 会把整条 Sink 链路拉进临界区（写盘可能长时间阻塞）。
        reportPrunedFileSinks(pruned)
        return redirected
    }

    /** 单一大文件出口（非会话布局）重定向；调用方必须已持有 [redirectLock]。 */
    private fun redirectSingleFileSink(newDirectory: File): Boolean {
        val old = fileSink ?: return false
        val settings = fileSinkSettings ?: return false

        // 同目录重定向是空操作（启动时已授权的典型场景：install 时就选了 MMAFile/Log）。
        // 必须短路：否则会走「合并 → rename 覆盖 → 删旧」把当前活跃日志误删。
        if (old.directory.canonicalPath == newDirectory.canonicalPath) return true

        sinks.remove(old)
        AsyncLogDispatcher.drain(1_000L)

        val migrated = runCatching { migrateLogFiles(old, newDirectory) }
            .onFailure { error("file_sink_redirect_failed", it) }
            .getOrDefault(false)
        if (!migrated) {
            // 迁移失败：旧 sink 复位保持原状（旧目录数据完整，应用继续用私有日志）
            sinks.addIfAbsent(old)
            return false
        }

        val newSink = FileSink(
            directory = newDirectory,
            maxFileSizeBytes = settings.maxFileSizeBytes,
            maxFiles = settings.maxFiles,
            minLevel = settings.minLevel,
        )
        sinks.addIfAbsent(newSink)
        fileSink = newSink
        return true
    }

    /**
     * 旧目录日志合并进新目录：先写暂存文件、全部成功后 rename 为活跃文件，
     * 任何一步失败即清理暂存并返回 false（**不删旧文件**——失败可安全重试）。
     */
    private fun migrateLogFiles(old: FileSink, newDirectory: File): Boolean {
        if (!newDirectory.mkdirs() && !(newDirectory.isDirectory && newDirectory.canWrite())) return false
        val staged = File(newDirectory, "${FileSink.ACTIVE_FILE_NAME}.migrating")
        val allCopied = old.logFiles().all { file ->
            !file.exists() || runCatching {
                file.inputStream().use { input ->
                    staged.outputStream().use { output -> input.copyTo(output) }
                }
            }.isSuccess
        }
        if (!allCopied) {
            runCatching { staged.delete() }
            return false
        }
        if (staged.exists()) {
            val target = File(newDirectory, FileSink.ACTIVE_FILE_NAME)
            if (!staged.renameTo(target)) {
                runCatching { staged.delete() }
                return false
            }
        }
        // 全部落定后才清旧目录
        old.logFiles().forEach { runCatching { it.delete() } }
        return true
    }

    /**
     * 门面自诊断出口（F2/F3）：直接走 android.util.Log，**绝不回调本门面**（防自吞递归）；
     * runCatching 兜底——纯 JVM 单测环境 android.util.Log 未打桩时静默跳过，不抛。
     */
    private fun logW(tag: String, msg: String) {
        runCatching { android.util.Log.w(tag, msg) }
    }

    /**
     * 历史日期目录搬迁（F3，QA Minor）：会话布局迁移只搬当前会话目录的话，
     * 旧根下**之前日期目录的历史会话**会永久滞留。主迁移成功后，对旧根下
     * `SessionLogPolicy.isValidDateDirName` 的目录逐一 rename 到新根（保留相对结构）；
     * - 非法名（用户自建 / 形似日期）**一律不动**；
     * - 单个目录搬迁失败（如新根同名冲突、跨设备）只留 Logcat 痕迹，**不阻断主迁移**；
     * - 本函数自身整体吞异常。
     */
    private fun migrateLegacyDateDirs(oldRoot: File, newRoot: File) {
        runCatching {
            oldRoot.listFiles()
                ?.filter { it.isDirectory && SessionLogPolicy.isValidDateDirName(it.name) }
                ?.forEach { dir ->
                    val target = File(newRoot, dir.name)
                    if (target.exists()) {
                        logW(TAG_SELF, "legacy_dir_move_skipped_exists: ${dir.name}")
                        return@forEach
                    }
                    if (!dir.renameTo(target)) {
                        logW(TAG_SELF, "legacy_dir_move_failed: ${dir.name}")
                    }
                }
        }.onFailure { logW(TAG_SELF, "legacy_dir_move_scan_failed: ${it.javaClass.simpleName}") }
    }

    /**
     * 会话布局迁移（W3 ⑦）：把**当前会话目录整体**搬到新根下（目录名保持不变）。
     * 优先 `renameTo`（同分区瞬时完成）；失败（跨挂载点等）逐文件追加拷贝。
     * 任何一步失败返回 false（**旧目录不动**，调用方复位旧 sink，可安全重试）。
     */
    private fun redirectSessionSink(session: SessionFileSink, newRoot: File): Boolean =
        // 可重入：redirectFileSink 已持锁时再次进入是安全的（synchronized 同线程重入）。
        synchronized(redirectLock) { redirectSessionSinkLocked(session, newRoot) }

    /**
     * [redirectSessionSink] 的锁内实现（**调用方必须已持有 [redirectLock]**）：
     * 「读 sessionSink → 摘除 → drain → 搬迁 → 挂载新 sink」必须整体原子，
     * 否则并发路径各自 `add` 一个新 sink，`remove(old)` 只对第一个生效 → 日志写 N 份。
     */
    private fun redirectSessionSinkLocked(session: SessionFileSink, newRoot: File): Boolean {
        val settings = sessionSinkSettings ?: return false
        // 同根重定向是空操作（安装时已授权的典型场景），短路防误搬。
        if (session.root.canonicalPath == newRoot.canonicalPath) return true
        // 并发守卫：另一个线程已完成重定向（sessionSink 已换新），本线程不得再建一个。
        // 同根短路优先（安装期已授权的典型场景，见上）。
        if (session !== sessionSink) return true

        sinks.remove(session)
        AsyncLogDispatcher.drain(1_000L)

        val moved = runCatching { migrateSessionDirectory(session.sessionDirectory, newRoot) }
            .onFailure { error("session_sink_redirect_failed", it) }
            .getOrDefault(false)
        if (!moved) {
            // 搬迁失败：旧 sink 复位（旧目录数据完整），应用继续用原根目录。
            sinks.addIfAbsent(session)
            return false
        }

        // F3（QA Minor）：主迁移成功后，旧根下历史日期目录一并随迁（失败不阻断）。
        migrateLegacyDateDirs(session.root, newRoot)

        val newSink = SessionFileSink(
            root = newRoot,
            sessionDirName = settings.sessionDirName,
            maxFileSizeBytes = settings.maxFileSizeBytes,
            maxFilesPerFile = settings.maxFilesPerFile,
            minLevel = settings.minLevel,
        )
        sinks.addIfAbsent(newSink)
        sessionSink = newSink
        return true
    }

    /** 会话目录整体搬迁：优先 rename；失败逐文件拷贝，全部成功后才删旧目录。 */
    private fun migrateSessionDirectory(oldDir: File, newRoot: File): Boolean {
        if (!newRoot.mkdirs() && !(newRoot.isDirectory && newRoot.canWrite())) return false
        val target = File(newRoot, oldDir.name)
        if (!target.exists() && oldDir.renameTo(target)) return true
        if (!target.isDirectory && !target.mkdirs()) return false
        val files = oldDir.listFiles() ?: return true // 空会话目录：无需搬运
        val allCopied = files.all { file ->
            !file.isFile || runCatching {
                file.inputStream().use { input ->
                    File(target, file.name).outputStream().use { output -> input.copyTo(output) }
                }
            }.isSuccess
        }
        if (!allCopied) return false
        runCatching { oldDir.deleteRecursively() }
        return true
    }

    fun addSink(sink: LogSink) {
        sinks.addIfAbsent(sink)
    }

    /**
     * 重复文件出口自愈：只保留 [sessionSink] 指向的最新实例，移除其余 SessionFileSink。
     * 每条日志被写 N 份的根因修复的兜底（并发 redirect 曾累积多个同目录实例）。
     * @return 移除数量
     */
    private fun pruneDuplicateSessionSinks(): Int {
        val keep = sessionSink ?: return 0
        val duplicates = sinks.filter { it is SessionFileSink && it !== keep }
        if (duplicates.isEmpty()) return 0
        sinks.removeAll(duplicates)
        return duplicates.size
    }

    /**
     * 上报重复出口自愈结果（自诊断）。**必须在 [redirectLock] 之外调用**——
     * 持锁回调 [log] 会把整条 Sink 链路拉进临界区，写盘慢时阻塞其它重定向。
     * @param pruned [pruneDuplicateSessionSinks] 返回的移除数量（0 表示无重复，不上报）
     */
    private fun reportPrunedFileSinks(pruned: Int) {
        if (pruned <= 0) return
        runCatching {
            log(
                level = LogLevel.WARN,
                category = LogCategory.ERROR,
                event = "logger_duplicate_file_sink",
                message = "检测到重复文件日志出口，已自动收敛",
                fields = mapOf("removed" to pruned),
            )
        }.onFailure { logW(TAG_SELF, "prune_report_failed: ${it.javaClass.simpleName}") }
    }

    /** 当前挂载的 SessionFileSink 数量（自愈/并发回归测试用）。 */
    internal fun sessionSinkCountForTest(): Int = sinks.count { it is SessionFileSink }

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

        // 设备 / 应用上下文（仅设备类别信息，符合脱敏红线），并入 fields 一并脱敏与落库。
        // 注意：LogEvent 自身已有默认 props 供 format() 展示，这里再往 fields 并入一份
        // 用于落库 / 查询（fieldsJson → 加密 DB），避免新增 Room 列与 Migration。
        val appVersion = LogContext.appVersion
        val appVersionCode = LogContext.appVersionCode
        val buildType = LogContext.buildType
        val deviceModel = LogContext.deviceModel
        val deviceManufacturer = LogContext.deviceManufacturer
        val osVersion = LogContext.osVersion
        val osSdk = LogContext.osSdk

        // F2（QA Minor）：脱敏在 runCatching 防线**之外**——fields 里的恶意对象
        // （如 toString() 抛异常）曾把异常逃逸出 log()，违背「日志永不抛」硬约束。
        // 现在脱敏失败降级为**空字段**（时间/级别/类别是 LogEvent 独立属性，天然保留），
        // 诊断走 android.util.Log（runCatching 兜底，纯 JVM 测试无桩也不炸；不回调本门面防自吞）。
        val sanitizedMessage = runCatching { Sanitizer.sanitizeMessage(message) }
            .onFailure {
                logW(TAG_SELF, "sanitize_message_failed: ${it.javaClass.simpleName}")
            }
            .getOrNull()
        val sanitizedFields = runCatching {
            Sanitizer.sanitizeFields(
                fields + mapOf(
                    LogFields.APP_VERSION to appVersion,
                    LogFields.APP_VERSION_CODE to appVersionCode,
                    LogFields.BUILD_TYPE to buildType,
                    LogFields.DEVICE_MODEL to deviceModel,
                    LogFields.DEVICE_MANUFACTURER to deviceManufacturer,
                    LogFields.OS_VERSION to osVersion,
                    LogFields.OS_SDK to osSdk,
                ),
            )
        }.getOrElse {
            logW(TAG_SELF, "sanitize_fields_failed: ${it.javaClass.simpleName}")
            emptyMap()
        }

        val logEvent = LogEvent(
            timestampMs = System.currentTimeMillis(),
            level = level,
            category = category,
            event = event,
            message = sanitizedMessage,
            fields = sanitizedFields,
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

    /** 日志文件列表（活跃 + 历史），供导出诊断包（会话布局时聚合全部路由文件）。 */
    fun logFiles(): List<File> = buildList {
        sessionSink?.let { addAll(it.logFiles()) }
        fileSink?.let { addAll(it.logFiles()) }
    }

    /** 当前会话日志目录（会话布局时非 null）；DB 回灌等落位用，未装文件出口返回 null。 */
    fun currentSessionLogDirectory(): File? = sessionSink?.sessionDirectory

    /**
     * 当前会话布局的**日志根**（W3 收口 F1）：会话目录、日期目录的公共父级——
     * 启动过期清理必须扫这一层（扫会话目录的 parent 会错位到单个日期目录，
     * 其下全是 session 目录、永不匹配日期正则，保留策略沦为死代码）。
     * 未装会话布局返回 null。
     */
    fun currentSessionLogRoot(): File? = sessionSink?.root

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
        fileSink = null
        fileSinkSettings = null
        sessionSink = null
        sessionSinkSettings = null
    }
}

/** FileSink 构造参数快照（仅供 [MmaLogger.redirectFileSink] 按原配置重建出口）。 */
private data class FileSinkSettings(
    val maxFileSizeBytes: Long,
    val maxFiles: Int,
    val minLevel: LogLevel,
)

/** [SessionFileSink] 构造参数快照（redirect 后保持同一 sessionDirName 与路由参数）。 */
private data class SessionSinkSettings(
    val sessionDirName: String,
    val maxFileSizeBytes: Long,
    val maxFilesPerFile: Int,
    val minLevel: LogLevel,
)
