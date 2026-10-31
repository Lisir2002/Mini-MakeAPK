package com.make.apk.logging

import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * 轮转文件出口：**高频诊断日志的落盘载体**。
 *
 * 性能设计（直接决定会不会拖慢 App）：
 * - 调用线程只做一次 `queue.offer`，**绝不碰磁盘**；
 * - 单后台线程顺序追加写入，避免多线程竞争与乱序；
 * - 队列有界，满了直接丢弃并计数（日志系统不能成为崩溃源）；
 * - 按大小滚动 + 按数量裁剪，磁盘占用有上界。
 *
 * 目录应选 App 私有目录（`Context.getExternalFilesDir`）：免存储权限、卸载即清理。
 */
internal class FileSink(
    internal val directory: File,
    private val maxFileSizeBytes: Long = DEFAULT_MAX_FILE_SIZE_BYTES,
    private val maxFiles: Int = DEFAULT_MAX_FILES,
    private val minLevel: LogLevel = LogLevel.INFO,
    /**
     * 活跃文件名（W3 ⑦ 参数化）：默认 `mma.log` 与历史路径**完全兼容**；
     * SessionFileSink 按类别路由时传入 `operations.log` / `preview.log` / `app.log`。
     */
    private val activeFileName: String = ACTIVE_FILE_NAME,
) : LogSink {

    private val lock = Any()
    private val dropped = AtomicLong(0)

    init {
        runCatching { directory.mkdirs() }
    }

    override fun write(event: LogEvent) {
        if (!event.level.isEnabledFor(minLevel)) return
        if (!event.category.persistToFile) return

        val accepted = AsyncLogDispatcher.submit { appendLocked(event) }
        if (!accepted) dropped.incrementAndGet()
    }

    override fun flush() {
        AsyncLogDispatcher.drain()
    }

    /** 当前活跃文件 + 历史文件（旧的排前面），供导出诊断包使用。 */
    fun logFiles(): List<File> {
        val active = File(directory, activeFileName)
        val rotated = rotatedFiles()
        return buildList {
            addAll(rotated.sortedBy { RotationPolicy.indexOf(it.name) })
            if (active.exists()) add(active)
        }
    }

    private fun appendLocked(event: LogEvent) = synchronized(lock) {
        runCatching {
            // 目录自愈（用户需求：外部日志目录被删后自动补全）：写前发现目录不在，
            // 幂等重建（mkdirs），失败不抛——写不进去只丢这一条，绝不拖垮日志系统。
            if (!directory.isDirectory) runCatching { directory.mkdirs() }
            val payload = buildPayload(event).toByteArray(Charsets.UTF_8)
            val active = File(directory, activeFileName)
            val currentSize = if (active.exists()) active.length() else 0L
            if (RotationPolicy.shouldRotate(currentSize, payload.size.toLong(), maxFileSizeBytes)) {
                rotateLocked(active)
            }
            FileOutputStream(active, true).use { stream -> stream.write(payload) }
        }
    }

    private fun buildPayload(event: LogEvent): String {
        val stack = event.stackTraceText()
        return if (stack.isNullOrBlank()) {
            event.format() + "\n"
        } else {
            event.format() + "\n" + stack + "\n"
        }
    }

    private fun rotateLocked(active: File) {
        if (!active.exists()) return
        val target = File(directory, RotationPolicy.rotatedName(activeFileName, nextRotationIndex()))
        if (active.renameTo(target)) pruneLocked()
    }

    private fun nextRotationIndex(): Int {
        val max = rotatedFiles()
            .mapNotNull { RotationPolicy.indexOf(it.name) }
            .maxOrNull() ?: 0
        return max + 1
    }

    private fun rotatedFiles(): List<File> =
        directory.listFiles { file ->
            file.isFile && file.name.startsWith("$activeFileName.")
        }?.toList() ?: emptyList()

    private fun pruneLocked() {
        val names = rotatedFiles().map { it.name }
        val doomed = RotationPolicy.filesToPrune(names, maxFiles).toSet()
        rotatedFiles().filter { it.name in doomed }.forEach { runCatching { it.delete() } }
    }

    companion object {
        const val ACTIVE_FILE_NAME = "mma.log"
        private const val DEFAULT_MAX_FILE_SIZE_BYTES = 2L * 1024 * 1024
        private const val DEFAULT_MAX_FILES = 5
    }
}

/** 待写入队列容量：数据库就绪前的早期日志缓冲。 */
internal const val PENDING_LOG_CAPACITY = 128

/** 早期日志的有界缓冲：满则丢弃最旧，保证启动期不会被日志拖住。 */
internal fun createPendingBuffer(): ArrayBlockingQueue<LogEvent> =
    ArrayBlockingQueue(PENDING_LOG_CAPACITY)
