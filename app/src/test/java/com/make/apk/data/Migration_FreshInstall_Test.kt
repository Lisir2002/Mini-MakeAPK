package com.make.apk.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.make.apk.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「全新安装」路径补测（阶段 5 QA 独立审查）。
 *
 * 升级路径矩阵共四条：1→5 / 2→5 / 3→5 / **全新安装**。前三条已由
 * `Migration_2_3_Test`（openRoom 应用 1→2→3→4→5 全链）与 `Migration_3_4_Test`（3→4→5）覆盖，
 * 本测试补齐最后一条：无历史库时 Room 直接按 v5 实体建库。
 *
 * 断言：最终表集**恰好**为 v5 的三张表（export_runs / log_entries / projects，按名排序），
 * 即 legacy `build_records` 从未被创建、也不残留任何其他意外表。
 * Room 在 onCreate 后同样运行 `onValidateSchema`，schema 与实体不符会抛
 * `IllegalStateException`（等价线上闪退），故不抛异常本身即强校验通过。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration_FreshInstall_Test {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearDatabase() {
        context.getDatabasePath(DB_NAME).delete()
    }

    @Test
    fun freshInstall_v5Schema_hasExactlyThreeTablesAndNoLegacyBuildRecords() {
        // 不 addMigrations：全新安装无迁移可跑，Room 直接按 v5 实体建库。
        // 用框架 SQLite 而非 SQLCipher（native 库在 Robolectric JVM 不可用，且加密不影响 schema）。
        val db = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .build()
        db.openHelper.writableDatabase // 触发 onCreate → onValidateSchema

        val tables = mutableListOf<String>()
        db.openHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master " +
                "WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' " +
                // room_master_table 是 Room 自己的内部表（存 identityHash），不是业务表。
                // 它必然存在，不能算作"意外表"，故显式排除。
                "AND name != 'room_master_table' " +
                "ORDER BY name",
        ).use { c ->
            while (c.moveToNext()) tables += c.getString(0)
        }

        assertEquals(
            "全新安装 v5 库的表集应恰为三张业务表，无 legacy build_records、无意外表",
            listOf("export_runs", "log_entries", "projects"),
            tables,
        )
        db.close()
    }

    companion object {
        private const val DB_NAME = "migration-fresh-install-test.db"
    }
}
