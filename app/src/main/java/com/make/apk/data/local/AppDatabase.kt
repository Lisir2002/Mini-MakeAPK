package com.make.apk.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.make.apk.data.local.dao.ExportRunDao
import com.make.apk.data.local.dao.LogEntryDao
import com.make.apk.data.local.dao.ProjectDao
import com.make.apk.data.local.entity.ExportRunEntity
import com.make.apk.data.local.entity.LogEntryEntity
import com.make.apk.data.local.entity.ProjectEntity

/**
 * MMA 本地数据库（SQLite，经 SQLCipher 加密）。
 *
 * - `exportSchema = true`：schema JSON 输出到 `app/schemas/`，随版本演进可审计迁移。
 * - 实际打开动作由 [AppDatabaseFactory] 完成，本类只描述"有哪些表"。
 * - 版本演进**必须**提供显式 Migration（见 `AppDatabaseFactory.MIGRATIONS`），
 *   已不再使用自愈式破坏性迁移回退。
 *
 * 版本历史：
 * - v1：`build_records`（旧构建历史）
 * - v2：新增 `log_entries`（全类别日志，随库一同加密）
 * - v3：新增 `projects`（稳定定义）+ `export_runs`（运行态，见 §8.2）；
 *   旧 `build_records` 数据在此阶段复制到 `projects` + `export_runs`。
 * - v4：移除旧 `build_records` 表（数据已于 v3 复制到 projects + export_runs，**零业务数据丢失**）。
 * - v5：`projects` 新增可空列 `shell_spec_json`（壳配置 L2；NULL = 未配置 → 兜底默认壳）。
 */
@Database(
    entities = [
        LogEntryEntity::class,
        ProjectEntity::class,
        ExportRunEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun logEntryDao(): LogEntryDao

    /** 项目（稳定定义）。 */
    abstract fun projectDao(): ProjectDao

    /** 导出运行（运行态）。 */
    abstract fun exportRunDao(): ExportRunDao

    companion object {
        const val DATABASE_NAME = "web2apk.db"
    }
}
