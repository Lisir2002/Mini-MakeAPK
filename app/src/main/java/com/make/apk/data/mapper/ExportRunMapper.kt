package com.make.apk.data.mapper

import com.make.apk.data.local.entity.ExportRunEntity
import com.make.apk.data.model.ExportRun

/**
 * 导出运行持久化形态 ⇄ 领域模型映射。
 *
 * 与项目映射同理：持久化细节锁在 `data.local`，上层只见 [ExportRun]。
 */
fun ExportRunEntity.toDomain(): ExportRun = ExportRun(
    id = id,
    projectId = projectId,
    status = status,
    outputApkPath = outputApkPath,
    apkSizeBytes = apkSizeBytes,
    durationMs = durationMs,
    errorMessage = errorMessage,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ExportRun.toEntity(): ExportRunEntity = ExportRunEntity(
    id = id,
    projectId = projectId,
    status = status,
    outputApkPath = outputApkPath,
    apkSizeBytes = apkSizeBytes,
    durationMs = durationMs,
    errorMessage = errorMessage,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
