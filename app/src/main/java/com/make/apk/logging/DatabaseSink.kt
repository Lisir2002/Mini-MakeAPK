package com.make.apk.logging

import com.make.apk.data.local.dao.LogEntryDao
import java.util.concurrent.atomic.AtomicLong

/**
 * 加密数据库出口：**高价值结构化日志的载体**（操作/错误/崩溃/启动/性能/网络/安全）。
 *
 * 关键问题与解法：**崩溃处理器与启动期日志先于数据库就绪**。
 * 数据库需要 Keystore 口令 + SQLCipher 打开，必然晚于最早期的日志调用。
 * 因此在 `attach()` 之前，事件进有界内存缓冲（满则丢最旧）；`attach()` 后
 * 立即回灌缓冲，保证启动期日志不丢。
 */
internal class DatabaseSink(
    private val minLevel: LogLevel = LogLevel.INFO,
) : LogSink {

    @Volatile
    private var dao: LogEntryDao? = null

    private val pending = createPendingBuffer()
    private val dropped = AtomicLong(0)

    /** 数据库就绪后接入；回灌早期缓冲。 */
    fun attach(dao: LogEntryDao) {
        this.dao = dao
        while (true) {
            val event = pending.poll() ?: break
            dispatch(event, dao)
        }
    }

    override fun write(event: LogEvent) {
        if (!event.level.isEnabledFor(minLevel)) return
        if (!event.category.persistToDatabase) return

        val target = dao
        if (target == null) {
            bufferEarly(event)
            return
        }
        dispatch(event, target)
    }

    override fun flush() {
        AsyncLogDispatcher.drain()
    }

    private fun bufferEarly(event: LogEvent) {
        // 缓冲满则丢弃最旧：宁可少几条，也不能让启动被日志阻塞或撑爆内存
        if (!pending.offer(event)) {
            pending.poll()
            pending.offer(event)
        }
    }

    private fun dispatch(event: LogEvent, dao: LogEntryDao) {
        val entity = event.toEntity()
        val accepted = AsyncLogDispatcher.submit {
            runCatching { dao.insertBlocking(entity) }
        }
        if (!accepted) dropped.incrementAndGet()
    }

    /** 被丢弃条数（自检用）。 */
    fun droppedCount(): Long = dropped.get()
}
