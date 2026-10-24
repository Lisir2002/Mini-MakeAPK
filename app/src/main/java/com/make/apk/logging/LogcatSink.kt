package com.make.apk.logging

import android.util.Log

/**
 * Logcat 出口：**只在 debug 构建启用**，release 下级别阈值应设为 WARN 以上。
 *
 * 标签取 `MMA-<类别>`，便于按类别过滤；长度控制在 23 字符内（旧 API 的硬限制）。
 * 异常栈单独一行输出，避免超长单行被 Logcat 截断。
 */
internal class LogcatSink(
    private val minLevel: LogLevel,
) : LogSink {

    override fun write(event: LogEvent) {
        if (!event.level.isEnabledFor(minLevel)) return

        val tag = "MMA-" + event.category.name
        val text = event.format()

        when (event.level) {
            LogLevel.VERBOSE -> Log.v(tag, text)
            LogLevel.DEBUG -> Log.d(tag, text)
            LogLevel.INFO -> Log.i(tag, text)
            LogLevel.WARN -> Log.w(tag, text)
            LogLevel.ERROR -> Log.e(tag, text)
            LogLevel.FATAL -> Log.wtf(tag, text)
        }

        val stack = event.stackTraceText()
        if (!stack.isNullOrBlank()) Log.e(tag, stack)
    }
}
