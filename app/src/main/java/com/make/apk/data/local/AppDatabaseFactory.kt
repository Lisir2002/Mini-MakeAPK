package com.make.apk.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.make.apk.data.security.SqlCipherPassphrase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * 数据库构造工厂：**Room + SQLCipher 的接线点**。
 *
 * 关键点：SQLCipher 的 [SupportOpenHelperFactory] 直接实现了
 * `androidx.sqlite.db.SupportSQLiteOpenHelper.Factory`，因此可以直接交给
 * `Room.databaseBuilder(...).openHelperFactory(...)`，**无需任何手写桥接层**
 * （这正是它与 WCDB 等引擎的关键差异：WCDB 的官方适配层只实现旧版
 * `android.arch.persistence.db` 接口，与 AndroidX Room 2.x 不兼容）。
 *
 * 性能与稳健性：
 * - `enableWriteAheadLogging = true`：开启 WAL，读不阻塞写，显著改善并发。
 * - [PerformancePragmas]：连接打开后下调 fsync 强度、临时表走内存。
 */
object AppDatabaseFactory {

    /**
     * 显式迁移链。**新增版本必须在此登记**，并让 [AppDatabase] 的 `version` 同步 +1。
     *
     * Room 会在打开时校验迁移后的 schema 与实体声明是否完全一致；
     * 不一致会直接抛 `IllegalStateException`，所以这里的 SQL 必须与
     * `app/schemas/.../N.json`（KSP 导出）逐字对齐。
     */
    val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

    fun create(context: Context): AppDatabase {
        val appContext = context.applicationContext
        val passphrase = SqlCipherPassphrase.obtain(appContext)

        // 参数：(口令, 数据库打开钩子, 是否启用 WAL)
        val openHelperFactory = SupportOpenHelperFactory(passphrase, null, true)

        return Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME,
        )
            .openHelperFactory(openHelperFactory)
            .addCallback(PerformancePragmas)
            .addMigrations(*MIGRATIONS)
            // 自愈（2026-10-26 定位 rc3/diag1 闪退根因后加固）：schema 版本/迁移不匹配时
            // 删库重建，避免 Room 直接抛 IllegalStateException 闪退。加密库打不开（口令失配/
            // 旧库损坏）时本项不覆盖 openHelper 打开阶段的异常，由 Repository 读 Flow 的
            // catch 兜底降级为空态——两层共同保证"数据库任何故障都不杀进程"。
            .fallbackToDestructiveMigration()
            .build()
    }
}

/**
 * v1 → v2：新增日志表 `log_entries`（全类别日志，随库加密）。
 *
 * 索引命名遵循 Room 约定：`index_<表名>_<列1>_<列2>`。
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `log_entries` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `timestampMs` INTEGER NOT NULL,
                `level` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `event` TEXT NOT NULL,
                `message` TEXT,
                `fieldsJson` TEXT,
                `stackTrace` TEXT,
                `traceId` TEXT NOT NULL,
                `sessionId` TEXT NOT NULL,
                `sequence` INTEGER NOT NULL,
                `threadName` TEXT NOT NULL,
                `durationMs` INTEGER,
                `success` INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_log_entries_timestampMs` ON `log_entries` (`timestampMs`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_log_entries_category_timestampMs` ON `log_entries` (`category`, `timestampMs`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_log_entries_event` ON `log_entries` (`event`)")
    }
}

/**
 * 连接级 PRAGMA 调优（每次数据库打开时生效）。
 *
 * - `synchronous = NORMAL`：WAL 模式下已保证崩溃一致性，无需 FULL 的每次 fsync。
 * - `temp_store = MEMORY`：排序/临时表走内存，减少磁盘 I/O。
 */
private val PerformancePragmas = object : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("PRAGMA synchronous = NORMAL")
        db.execSQL("PRAGMA temp_store = MEMORY")
    }
}
