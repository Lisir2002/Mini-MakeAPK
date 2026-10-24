package com.make.apk.logging

/**
 * 结构化日志事件（**事件，而非句子**）。
 *
 * 设计要点：
 * - [event] 用 snake_case 稳定事件名（如 `build_failed`），像 API 一样演进；
 *   语义靠它表达，[message] 只是可选的人类可读补充。
 * - [fields] 承载**可量化维度**（duration_ms / attempt / size_bytes / success …），
 *   便于日后聚合查询，而不是在字符串里做正则考古。
 * - 所有内容在构造时即经 [Sanitizer] 处理；[throwable] 的栈在落库时再脱敏。
 * - [traceId] 为**不透明、临时**的关联 ID，绝不使用账号/设备标识。
 */
data class LogEvent(
    /** 事件发生时间（epoch ms）。 */
    val timestampMs: Long,
    val level: LogLevel,
    val category: LogCategory,
    /** 稳定事件名（snake_case）。 */
    val event: String,
    /** 可选的人类可读描述（已脱敏）。 */
    val message: String? = null,
    /** 结构化键值对，值应为可序列化标量（Number / Boolean / String / null）。 */
    val fields: Map<String, Any?> = emptyMap(),
    /** 关联异常；其栈信息在持久化时按类别处理。 */
    val throwable: Throwable? = null,
    /** 同一次操作的关联 ID（不透明、临时）。 */
    val traceId: String = TraceContext.currentTraceId(),
    /** 本次进程会话 ID（不透明、临时，进程内唯一）。 */
    val sessionId: String = TraceContext.sessionId,
    /** 进程内单调递增序号，用于排序与去重。 */
    val sequence: Long = TraceContext.nextSequence(),
    val threadName: String = Thread.currentThread().name,
) {

    /** 供文件输出的一行文本（已脱敏、已截断）。 */
    fun format(): String = buildString {
        append(timestampMs)
        append(' ').append(level.name).append('/').append(category.name)
        append(" [").append(event).append(']')
        if (!message.isNullOrBlank()) append(' ').append(message)
        if (fields.isNotEmpty()) {
            append(' ').append(JsonLite.encode(fields))
        }
        append(" trace=").append(traceId)
        append(" seq=").append(sequence)
        append(" thread=").append(threadName)
    }

    /** 异常栈（可能为 null）。栈中可能含敏感信息，导出时需再过一遍脱敏。 */
    fun stackTraceText(): String? =
        throwable?.let { Sanitizer.sanitizeStackTrace(it) }
}
