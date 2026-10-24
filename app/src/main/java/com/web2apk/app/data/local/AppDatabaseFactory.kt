package com.web2apk.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.web2apk.app.data.security.SqlCipherPassphrase
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
            // v1 尚无历史版本；发布真实版本前必须替换为显式 Migration。
            .fallbackToDestructiveMigration()
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
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("PRAGMA synchronous = NORMAL")
        db.execSQL("PRAGMA temp_store = MEMORY")
    }
}
