package com.make.apk.logging

/**
 * 日志级别（与 android.util.Log 优先级对齐，便于 Logcat 输出）。
 *
 * 级别契约（团队必须一致，否则级别通胀会让告警失效）：
 * - [VERBOSE] / [DEBUG]：仅本地诊断。**release 构建默认关闭**。
 * - [INFO]：生命周期 / 业务结果，对运维有价值，可采样。
 * - [WARN]：降级但已自愈（重试成功、回退命中），**不告警但始终记录**。
 * - [ERROR]：操作失败且用户可感知，**必须可告警**。
 * - [FATAL]：崩溃 / ANR，进程级故障。
 *
 * 判据：如果一条 "error" 不值得人看一眼，它就是 WARN。
 */
enum class LogLevel(val priority: Int) {
    VERBOSE(2),
    DEBUG(3),
    INFO(4),
    WARN(5),
    ERROR(6),
    FATAL(7),
    ;

    /** 该级别是否被 [threshold] 放行。 */
    fun isEnabledFor(threshold: LogLevel): Boolean = priority >= threshold.priority
}
