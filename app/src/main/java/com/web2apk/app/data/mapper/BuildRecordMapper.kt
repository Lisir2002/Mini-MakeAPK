package com.web2apk.app.data.mapper

import com.web2apk.app.data.local.entity.BuildRecordEntity
import com.web2apk.app.data.model.BuildRecord

/**
 * 持久化形态 ⇄ 领域模型映射。
 *
 * 存在的意义：Room 注解、表名、索引等持久化细节被锁在 `data.local` 内，
 * 上层只依赖 [BuildRecord]；将来若拆表 / 改列，只需改这里，UI 无感。
 */

fun BuildRecordEntity.toDomain(): BuildRecord = BuildRecord(
    id = id,
    createdAt = createdAt,
    updatedAt = updatedAt,
    inputType = inputType,
    sourceLabel = sourceLabel,
    sourceRef = sourceRef,
    engine = engine,
    status = status,
    appName = appName,
    packageName = packageName,
    outputApkPath = outputApkPath,
    apkSizeBytes = apkSizeBytes,
    durationMs = durationMs,
    errorMessage = errorMessage,
)

fun BuildRecord.toEntity(): BuildRecordEntity = BuildRecordEntity(
    id = id,
    createdAt = createdAt,
    updatedAt = updatedAt,
    inputType = inputType,
    sourceLabel = sourceLabel,
    sourceRef = sourceRef,
    engine = engine,
    status = status,
    appName = appName,
    packageName = packageName,
    outputApkPath = outputApkPath,
    apkSizeBytes = apkSizeBytes,
    durationMs = durationMs,
    errorMessage = errorMessage,
)
