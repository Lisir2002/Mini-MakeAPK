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
 *
 * **自研分级展示**（rc9，用户需求"分级一目了然"）：[label] 是用户向中文名、
 * [severityWeight] 是 UI 徽标的语义权重（越大越重，日志页按此配色排序）——
 * 展示语义**只在这一处定义**，日志查看页 / 导出格式一律引用，不允许各自再拼。
 */
enum class LogLevel(
    val priority: Int,
    /** 用户向级别名（日志页徽标 / 筛选芯片共用，单一事实源）。 */
    val label: String,
    /** 展示权重（越大越严重；VERBOSE=0 → FATAL=5）。 */
    val severityWeight: Int,
) {
    VERBOSE(2, "详细", 0),
    DEBUG(3, "调试", 1),
    INFO(4, "信息", 2),
    WARN(5, "警告", 3),
    ERROR(6, "错误", 4),
    FATAL(7, "致命", 5),
    ;

    /** 该级别是否被 [threshold] 放行。 */
    fun isEnabledFor(threshold: LogLevel): Boolean = priority >= threshold.priority
}
