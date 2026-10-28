package com.make.apk.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.make.apk.data.security.SqlCipherPassphrase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * 数据库构造工厂：**Room + SQLCipher 的接线点**。
 *
 * 关键点：SQLCipher 的 [SupportOpenHelperFactory] 直接实现了
 * `androidx.sqlite.db.SupportSQLiteOpenHelper.Factory`，因此可以直接交给
 * `Room.databaseBuilder(...).openHelperFactory(...)`，**无需任何手写桥接层**
 * （这正是它与 WCDB 等引擎的关键差异：WCDB 的官方适配层只支持旧版
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
     * 不一致会直接抛 `IllegalStateException`，所以迁移 SQL 必须与
     * `app/schemas/.../N.json`（KSP 导出）逐字对齐。
     *
     * 迁移实现见 [MIGRATIONS]（`Migrations.kt`）。
     */
    val MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
    )

    fun create(context: Context): AppDatabase {
        val appContext = context.applicationContext
        // SQLCipher 原生库显式加载（双保险，覆盖其它入口）：
        // 新版 sqlcipher-android 不再自动 loadLibs，必须在构造
        // SupportOpenHelperFactory 之前完成，否则 nativeOpen 抛 UnsatisfiedLinkError。
        SqlCipherInitializer.initOnce()
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
            // 阶段 5：显式迁移链 1→2→3→4 已由 Migration_3_4_Test 覆盖全升级路径
            // （1→4 / 2→4 / 3→4 均通过 Room onValidateSchema 强校验，数据零丢失），
            // 按 PROJECT_BRIEF §7 移除自愈式破坏性迁移回退。
            // 三层兜底仍保证"数据库任何故障都不杀进程"：
            // 1) 迁移链任一环节出错 → Room 抛异常（等同线上闪退等价复现，由测试门禁拦截）；
            // 2) 加密库打不开（口令失配/旧库损坏）→ Repository 读 Flow 的 catch 兜底降级空态；
            // 3) 口令获取失败 → SqlCipherPassphrase 降级内存态随机口令并删无法解锁的旧库。
            //
            // 【回退开关】若 QA 发现移除后某升级路径无兜底闪退且无解，在 .build() 前
            // 调用 Room 自带的「自愈式破坏性迁移回退」并标注原因（当前已按 §7 移除）。
            .build()
    }
}

/**
 * 连接级 PRAGMA 调优（每次数据库打开时生效）。
 *
 * - `synchronous = NORMAL`：WAL 模式下已保证崩溃一致性，无需 FULL 的每次 fsync。
 * - `temp_store = MEMORY`：排序/临时表走内存，减少磁盘 I/O。
 */
private val PerformancePragmas = object : RoomDatabase.Callback() {
    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("PRAGMA synchronous = NORMAL")
        db.execSQL("PRAGMA temp_store = MEMORY")
    }
}
