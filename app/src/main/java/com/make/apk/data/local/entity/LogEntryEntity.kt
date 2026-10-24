package com.make.apk.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 日志条目表（落**加密** SQLite，随数据库一同受 SQLCipher 保护）。
 *
 * 索引设计：
 * - `timestampMs`：所有查询与清理都按时间范围，必备。
 * - `category + timestampMs`：按类别筛选（如只看崩溃）时仍走有序索引。
 * - `event`：按稳定事件名聚合排查（`build_failed` 出现了几次）。
 *
 * 全量化：[durationMs] 与 [success] 从结构化字段里**提升为独立列**，
 * 让"构建耗时分布""失败率"这类量化分析能直接 SQL 聚合，而不必解析 JSON。
 */
@Entity(
    tableName = "log_entries",
    indices = [
        Index(value = ["timestampMs"]),
        Index(value = ["category", "timestampMs"]),
        Index(value = ["event"]),
    ],
)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "timestampMs")
    val timestampMs: Long,
    @ColumnInfo(name = "level")
    val level: String,
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "event")
    val event: String,
    @ColumnInfo(name = "message")
    val message: String? = null,
    /** 结构化字段的 JSON（已脱敏）。 */
    @ColumnInfo(name = "fieldsJson")
    val fieldsJson: String? = null,
    /** 异常栈（已脱敏、已截断）。 */
    @ColumnInfo(name = "stackTrace")
    val stackTrace: String? = null,
    @ColumnInfo(name = "traceId")
    val traceId: String,
    @ColumnInfo(name = "sessionId")
    val sessionId: String,
    @ColumnInfo(name = "sequence")
    val sequence: Long,
    @ColumnInfo(name = "threadName")
    val threadName: String,
    /** 量化维度：耗时，来自字段 `duration_ms`。 */
    @ColumnInfo(name = "durationMs")
    val durationMs: Long? = null,
    /** 量化维度：成败，来自字段 `success`。 */
    @ColumnInfo(name = "success")
    val success: Boolean? = null,
)
