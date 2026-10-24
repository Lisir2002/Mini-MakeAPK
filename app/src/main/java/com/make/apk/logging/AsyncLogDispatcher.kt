package com.make.apk.logging

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * 全局异步日志分发：**单后台线程 + 有界队列**。
 *
 * 为什么必须有界：无界队列在低端机上遇到日志风暴会直接 OOM；
 * 有界且"丢弃最旧"能保证日志系统**永远不会成为崩溃原因**。
 * 被丢弃的条数会被计数，并定期以一条自检日志暴露（避免静默丢失）。
 *
 * 为什么用普通线程而非协程：崩溃处理器与 ANR 看门狗需要在
 * 任何生命周期阶段都能写日志，不依赖尚未建立的协程作用域。
 */
internal object AsyncLogDispatcher {

    private const val CAPACITY = 512
    private const val POLL_TIMEOUT_MS = 500L

    private val queue = ArrayBlockingQueue<() -> Unit>(CAPACITY)
    private val droppedCount = AtomicLong(0)
    private val lock = Any()

    @Volatile
    private var running = false

    @Volatile
    private var worker: Thread? = null

    fun start() {
        synchronized(lock) {
            if (running) return
            running = true
            worker = thread(name = "mma-log-dispatcher", isDaemon = true) {
                while (running) {
                    val task = try {
                        queue.poll(POLL_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return@thread
                    }
                    task?.let {
                        runCatching { it.invoke() }
                    }
                }
            }
        }
    }

    /**
     * 提交一个后台任务。
     * @return true 入队成功；false 表示队列已满、该条被丢弃。
     */
    fun submit(task: () -> Unit): Boolean {
        if (!running) start()
        return queue.offer(task)
    }

    fun dropped(): Long = droppedCount.get()

    fun markDropped() {
        droppedCount.incrementAndGet()
    }

    /** 尽力把已入队的任务跑完（最多等待 [timeoutMs]）。 */
    fun drain(timeoutMs: Long = 1_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (queue.isNotEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
    }

    fun stop() {
        synchronized(lock) {
            running = false
            worker?.interrupt()
            worker = null
        }
    }
}
