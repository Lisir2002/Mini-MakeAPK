package com.web2apk.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.web2apk.app.data.local.dao.BuildRecordDao
import com.web2apk.app.data.local.entity.BuildRecordEntity

/**
 * Web2APK 本地数据库（SQLite，经 SQLCipher 加密）。
 *
 * - `exportSchema = true`：schema JSON 输出到 `app/schemas/`，随版本演进可审计迁移。
 * - 实际打开动作由 [AppDatabaseFactory] 完成，本类只描述"有哪些表"。
 * - 版本演进时必须提供显式 Migration（当前 v1，无历史版本）。
 */
@Database(
    entities = [BuildRecordEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun buildRecordDao(): BuildRecordDao

    companion object {
        const val DATABASE_NAME = "web2apk.db"
    }
}
