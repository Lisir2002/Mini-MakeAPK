package com.make.apk.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.make.apk.data.model.ExportStatus

/**
 * 导出运行表（SQLite 持久化形态，§8.2）。
 *
 * 表职责：每次导出一条运行态记录，**从属于** [ProjectEntity]（N:1）。
 *
 * 与旧 `build_records` 的差异（§11.2）：
 * - **不含 `engine` 字段**——导出统一走本地注入，不存在引擎分支；
 * - 增 `projectId` 外键，项目被删则其运行记录一并清除（`ON DELETE CASCADE`）。
 *
 * 索引设计：
 * - `(projectId, createdAt)`：单项目的导出历史倒序分页扫描。
 * - `(status, createdAt)`：按状态筛选（"仅看失败"）时仍走索引有序扫描。
 */
@Entity(
    tableName = "export_runs",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["projectId", "createdAt"]),
        Index(value = ["status", "createdAt"]),
    ],
)
data class ExportRunEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "projectId")
    val projectId: Long,
    @ColumnInfo(name = "createdAt")
    val createdAt: Long,
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = createdAt,
    @ColumnInfo(name = "status")
    val status: ExportStatus,
    @ColumnInfo(name = "outputApkPath")
    val outputApkPath: String? = null,
    @ColumnInfo(name = "apkSizeBytes")
    val apkSizeBytes: Long? = null,
    @ColumnInfo(name = "durationMs")
    val durationMs: Long? = null,
    @ColumnInfo(name = "errorMessage")
    val errorMessage: String? = null,
)
