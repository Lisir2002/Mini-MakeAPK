package com.make.apk.logging

/**
 * 日志出口（Sink）。
 *
 * 门面 [MmaLogger] 只负责"构造事件 + 分发"，具体落到哪里由 Sink 决定：
 * Logcat / 轮转文件 / 加密数据库 / 崩溃兜底，可并行多个。
 */
interface LogSink {

    /** 写出一条事件。**必须快速返回**，耗时 I/O 应丢给后台线程。 */
    fun write(event: LogEvent)

    /** 尽力把缓冲落盘（崩溃前、导出前调用）。 */
    fun flush() {}

    /** 释放资源（进程结束前调用）。 */
    fun close() {}
}
