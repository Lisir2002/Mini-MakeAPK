package com.make.apk.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.make.apk.data.local.dao.BuildRecordDao
import com.make.apk.data.local.dao.LogEntryDao
import com.make.apk.data.local.entity.BuildRecordEntity
import com.make.apk.data.local.entity.LogEntryEntity

/**
 * MMA 本地数据库（SQLite，经 SQLCipher 加密）。
 *
 * - `exportSchema = true`：schema JSON 输出到 `app/schemas/`，随版本演进可审计迁移。
 * - 实际打开动作由 [AppDatabaseFactory] 完成，本类只描述"有哪些表"。
 * - 版本演进**必须**提供显式 Migration（见 `AppDatabaseFactory.MIGRATIONS`），
 *   已不再使用 `fallbackToDestructiveMigration`。
 *
 * 版本历史：
 * - v1：`build_records`（构建历史）
 * - v2：新增 `log_entries`（全类别日志，随库一同加密）
 */
@Database(
    entities = [BuildRecordEntity::class, LogEntryEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun buildRecordDao(): BuildRecordDao

    abstract fun logEntryDao(): LogEntryDao

    companion object {
        const val DATABASE_NAME = "web2apk.db"
    }
}
