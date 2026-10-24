package com.web2apk.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.web2apk.app.data.model.BuildEngine
import com.web2apk.app.data.model.BuildInputType
import com.web2apk.app.data.model.BuildStatus

/**
 * 构建记录表（SQLite 持久化形态）。
 *
 * 高性能索引设计：
 * - `createdAt` 单列索引：历史列表按时间倒序分页扫描，避免全表排序。
 * - `status + createdAt` 复合索引：按状态筛选（如"仅看失败"）时仍走索引有序扫描。
 *
 * 枚举经 [com.web2apk.app.data.local.Converters] 以 TEXT 存储，可读性与迁移友好。
 */
@Entity(
    tableName = "build_records",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["status", "createdAt"]),
    ],
)
data class BuildRecordEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "createdAt")
    val createdAt: Long,
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = createdAt,
    @ColumnInfo(name = "inputType")
    val inputType: BuildInputType,
    @ColumnInfo(name = "sourceLabel")
    val sourceLabel: String,
    @ColumnInfo(name = "sourceRef")
    val sourceRef: String,
    @ColumnInfo(name = "engine")
    val engine: BuildEngine,
    @ColumnInfo(name = "status")
    val status: BuildStatus,
    @ColumnInfo(name = "appName")
    val appName: String? = null,
    @ColumnInfo(name = "packageName")
    val packageName: String? = null,
    @ColumnInfo(name = "outputApkPath")
    val outputApkPath: String? = null,
    @ColumnInfo(name = "apkSizeBytes")
    val apkSizeBytes: Long? = null,
    @ColumnInfo(name = "durationMs")
    val durationMs: Long? = null,
    @ColumnInfo(name = "errorMessage")
    val errorMessage: String? = null,
)
