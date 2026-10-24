package com.make.apk.data.model

/**
 * 日志条目（**领域模型**）：UI / 日志查看页唯一可见的形态，不含 Room 注解。
 *
 * [level] 与 [category] 以字符串保存，避免领域层反向依赖持久化细节；
 * 需要枚举语义时可用 [levelEnum] / [categoryEnum] 安全解析（解析失败返回 null）。
 */
data class LogEntry(
    val id: Long = 0,
    val timestampMs: Long,
    val level: String,
    val category: String,
    val event: String,
    val message: String? = null,
    /** 结构化字段的 JSON（已脱敏）。 */
    val fieldsJson: String? = null,
    val stackTrace: String? = null,
    val traceId: String,
    val sessionId: String,
    val sequence: Long,
    val threadName: String,
    /** 量化维度：耗时。 */
    val durationMs: Long? = null,
    /** 量化维度：成败。 */
    val success: Boolean? = null,
) {
    val levelEnum: com.make.apk.logging.LogLevel?
        get() = runCatching { com.make.apk.logging.LogLevel.valueOf(level) }.getOrNull()

    val categoryEnum: com.make.apk.logging.LogCategory?
        get() = runCatching { com.make.apk.logging.LogCategory.valueOf(category) }.getOrNull()

    /** 日志查看页/导出行文本。 */
    fun formatLine(): String = buildString {
        append(timestampMs)
        append(' ').append(level).append('/').append(category)
        append(" [").append(event).append(']')
        message?.takeIf { it.isNotBlank() }?.let { append(' ').append(it) }
        durationMs?.let { append(" duration_ms=").append(it) }
        success?.let { append(" success=").append(it) }
        append(" trace=").append(traceId)
    }
}
