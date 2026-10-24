package com.make.apk.logging

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

/**
 * Java 崩溃捕获：`Thread.UncaughtExceptionHandler`。
 *
 * 要点：
 * - 必须在 `Application.attachBaseContext()` 里**尽可能早**安装，
 *   否则启动期崩溃（最该抓的那类）会漏掉。
 * - 捕获后**必须转发给原 handler**，否则系统会失去默认的日志与杀进程行为，
 *   表现为"崩溃后卡死不退出"。
 * - 只记脱敏后的栈；栈里常含文件路径与参数，已由 [Sanitizer] 清洗。
 *
 * Native 崩溃需要信号处理器（SIGSEGV/SIGABRT 等），本项目暂不涉及 NDK，不实现。
 */
internal class CrashHandler(
    private val onCrash: (throwable: Throwable, thread: Thread, stack: String) -> Unit,
) : Thread.UncaughtExceptionHandler {

    private val previous = Thread.getDefaultUncaughtExceptionHandler()

    fun install() {
        if (Thread.getDefaultUncaughtExceptionHandler() !== this) {
            Thread.setDefaultUncaughtExceptionHandler(this)
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runCatching {
            onCrash(throwable, thread, Sanitizer.sanitizeStackTrace(throwable))
        }
        // 交还系统：保留默认日志与进程终止行为
        runCatching { previous?.uncaughtException(thread, throwable) }
    }
}

/**
 * ANR 看门狗：**主线程标记消息超时法**。
 *
 * 原理：子线程周期性向主线程 Looper 投递一个"自增计数"的标记消息；
 * 若一个周期后计数没变，说明主线程被阻塞（ANR），此时抓取主线程栈。
 * 无需权限、无需系统改动，是业界通用做法。
 *
 * 抓到后休眠一个周期再继续，避免同一次 ANR 被连续重复上报刷屏。
 */
internal class AnrWatchdog(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val onAnr: (mainThreadStack: String) -> Unit,
) : Thread("mma-anr-watchdog") {

    @Volatile
    private var running = true

    private val tick = AtomicLong(0L)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun run() {
        while (running) {
            val before = tick.get()
            mainHandler.post { tick.incrementAndGet() }
            SystemClock.sleep(timeoutMs)

            if (tick.get() == before) {
                val stack = Looper.getMainLooper().thread.stackTrace
                    .joinToString("\n") { element -> "    at $element" }
                runCatching { onAnr(stack) }
                // 抑制同一次 ANR 的连续刷屏
                SystemClock.sleep(timeoutMs)
            }
        }
    }

    fun shutdown() {
        running = false
        interrupt()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000L
    }
}
