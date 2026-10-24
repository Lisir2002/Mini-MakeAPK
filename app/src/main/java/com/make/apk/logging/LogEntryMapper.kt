package com.make.apk.logging

import com.make.apk.data.local.entity.LogEntryEntity

/**
 * [LogEvent] → [LogEntryEntity] 落库映射。
 *
 * 这里做两件事：
 * 1. 再过一遍 [Sanitizer]（即便门面已脱敏，也防住直接构造事件的调用点）；
 * 2. 把 [LogFields.DURATION_MS] / [LogFields.SUCCESS] 提升为独立列，
 *    使"耗时分布""失败率"可直接 SQL 聚合，无需解析 JSON。
 */
internal fun LogEvent.toEntity(): LogEntryEntity = LogEntryEntity(
    timestampMs = timestampMs,
    level = level.name,
    category = category.name,
    event = event,
    message = Sanitizer.sanitizeMessage(message),
    fieldsJson = if (fields.isEmpty()) null else JsonLite.encode(Sanitizer.sanitizeFields(fields)),
    stackTrace = stackTraceText(),
    traceId = traceId,
    sessionId = sessionId,
    sequence = sequence,
    threadName = threadName,
    durationMs = (fields[LogFields.DURATION_MS] as? Number)?.toLong(),
    success = fields[LogFields.SUCCESS] as? Boolean,
)
