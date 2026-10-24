package com.make.apk.data.mapper

import com.make.apk.data.local.entity.LogEntryEntity
import com.make.apk.data.model.LogEntry

/** 持久化形态 ⇄ 领域模型映射（与构建记录同一套分层约定）。 */
fun LogEntryEntity.toDomain(): LogEntry = LogEntry(
    id = id,
    timestampMs = timestampMs,
    level = level,
    category = category,
    event = event,
    message = message,
    fieldsJson = fieldsJson,
    stackTrace = stackTrace,
    traceId = traceId,
    sessionId = sessionId,
    sequence = sequence,
    threadName = threadName,
    durationMs = durationMs,
    success = success,
)

fun LogEntry.toEntity(): LogEntryEntity = LogEntryEntity(
    id = id,
    timestampMs = timestampMs,
    level = level,
    category = category,
    event = event,
    message = message,
    fieldsJson = fieldsJson,
    stackTrace = stackTrace,
    traceId = traceId,
    sessionId = sessionId,
    sequence = sequence,
    threadName = threadName,
    durationMs = durationMs,
    success = success,
)
