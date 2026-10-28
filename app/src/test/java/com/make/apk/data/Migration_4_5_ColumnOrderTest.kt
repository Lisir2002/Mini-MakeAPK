package com.make.apk.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.make.apk.data.local.AppDatabase
import com.make.apk.data.local.AppDatabaseFactory
import com.make.apk.data.local.MIGRATION_4_5
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * v4 → v5 迁移的**独立列序核验**（QA 独立补测 · 阶段 6.1）。
 *
 * ## 为什么已有 [Migration_4_5_Test] 还要再补一组
 *
 * 已有测试用**归一化后的 DDL 文本**比对（`normalizeSql` 去空白后逐字比）来间接证明列序一致。
 * 本测试换一条**更直接、不依赖文本归一化规则**的独立路径：
 *
 * **用 `PRAGMA table_info` 读真实 SQLite 的列序**，与 KSP 导出的 `5.json` 的 `fields` 数组顺序
 * **逐项比对**（列名 + 顺序 + 类型 + NOT NULL + 默认值）。
 *
 * 之所以要"两条独立路径"：DDL 文本比对依赖"归一化后逐字相等"这一强假设
 * （一旦 Room 导出的 `createSql` 排版变了就误报/漏报）；
 * 而 `PRAGMA table_info` 是**数据库自己说的**列序，不受文本格式影响。
 *
 * ## 本测试同时独立验证工程师偏差 1 的推理
 *
 * 工程师称：`shellSpecJson` 必须声明在字段列表**最末**，否则
 * "KSP 导出列序与迁移产物列序不一致 → DDL 比对与 `onValidateSchema` 失败"。
 *
 * 独立结论（详见评审报告）：
 * - **"声明在末尾"这个决定本身是对的**——SQLite 的 `ALTER TABLE ... ADD COLUMN` 只能追加到表末尾，
 *   若实体把该列声明在中间，则"全新安装"（Room 按实体声明序建表）与"老库升级"（追加到末尾）
 *   两条路径的**物理列序**会分叉，既有的 fresh-vs-migrated 结构一致性断言即失败；
 * - 但"**`onValidateSchema` 会失败**"这一条**不准确**：Room 的 `TableInfo.equals`
 *   比较的是 `columns: Map<String, Column>`（按列名索引），**与列序无关**，
 *   所以列序分叉不会触发 `onValidateSchema` 抛异常。
 *   真正会失败的是"两条路径产物结构必须一致"这类**自建断言**（以及 DDL 逐字比对）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration_4_5_ColumnOrderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearDatabase() {
        context.getDatabasePath(TEST_DB_NAME).delete()
    }

    /**
     * 迁移产物（老库 v4 升级到 v5）的**真实列序**必须与 KSP 导出的 `5.json` 字段序完全一致。
     *
     * 断言逐项进行：列名、列序、类型、NOT NULL、默认值——任一不一致即失败。
     */
    @Test
    fun migratedTable_columnOrderMatchesExportedSchema() {
        createLegacyV4Database()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .addMigrations(MIGRATION_4_5)
            .build()
        db.openHelper.writableDatabase

        val expected = exportedFields(5, "projects")
        val actual = pragmaTableInfo(db.openHelper.writableDatabase, "projects")

        assertEquals(
            "迁移后 projects 表的列数必须与 5.json 一致",
            expected.size,
            actual.size,
        )
        expected.forEachIndexed { index, field ->
            val column = actual[index]
            assertEquals("第 ${index + 1} 列列名不一致", field["columnName"], column.name)
            assertEquals("列 ${column.name} 的类型不一致", field["affinity"], column.type)
            assertEquals(
                "列 ${column.name} 的 NOT NULL 不一致",
                if (field["notNull"] == true) 1 else 0,
                column.notNull,
            )
            assertEquals(
                "列 ${column.name} 的默认值不一致（v5 新列必须无默认值）",
                field["defaultValue"] ?: null,
                column.defaultValue,
            )
        }
        db.close()
    }

    /**
     * `shell_spec_json` 必须落在**最后一位**——这是"实体声明在字段列表最末"的直接证据。
     *
     * 若有人日后把该字段挪到中间，本断言即失败（迁移产物与新装库列序会分叉）。
     */
    @Test
    fun shellSpecColumn_isAppendedLast() {
        createLegacyV4Database()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .addMigrations(MIGRATION_4_5)
            .build()
        db.openHelper.writableDatabase

        val actual = pragmaTableInfo(db.openHelper.writableDatabase, "projects")
        assertEquals("shell_spec_json", actual.last().name)
        assertEquals(
            "shell_spec_json 必须是可空列（null = 未配置 → 领域层兜底 ShellSpec.DEFAULT）",
            0,
            actual.last().notNull,
        )
        assertEquals(
            "shell_spec_json 必须无 SQL DEFAULT（默认值字面量需与 Room 导出 schema 逐字匹配，否则校验抛异常）",
            null,
            actual.last().defaultValue,
        )
        db.close()
    }

    /**
     * "全新安装"与"老库升级"两条路径的**物理列序**必须完全一致。
     *
     * 这正是"字段必须声明在最末"的**根本原因**：
     * SQLite 的 `ADD COLUMN` 只能追加到末尾，实体声明若在中间，两条路径列序就会分叉。
     */
    @Test
    fun freshInstallAndMigrated_haveIdenticalColumnOrder() {
        createLegacyV4Database()
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .addMigrations(MIGRATION_4_5)
            .build()
        migrated.openHelper.writableDatabase

        val freshDbName = "migration-4-5-order-fresh.db"
        context.getDatabasePath(freshDbName).delete()
        val fresh = Room.databaseBuilder(context, AppDatabase::class.java, freshDbName)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .build()
        fresh.openHelper.writableDatabase

        assertEquals(
            "全新安装与老库升级的 projects 列序必须一致（否则实体字段声明位置与 SQLite 追加语义冲突）",
            pragmaTableInfo(fresh.openHelper.writableDatabase, "projects").map { it.name },
            pragmaTableInfo(migrated.openHelper.writableDatabase, "projects").map { it.name },
        )
        migrated.close()
        fresh.close()
    }

    // ── 脚手架与读取辅助 ────────────────────────────────────────────────

    /** 造一个 v4 老库（照抄 KSP 导出的 4.json 建表，无 shell_spec_json 列）。 */
    private fun createLegacyV4Database() {
        val callback = object : SupportSQLiteOpenHelper.Callback(4) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val schema = exportedSchema(4)
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    db.execSQL(
                        entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")),
                    )
                    val indices = entity.optJSONArray("indices") ?: continue
                    for (j in 0 until indices.length()) {
                        val index = indices.getJSONObject(j)
                        db.execSQL(
                            index.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")),
                        )
                    }
                }
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB_NAME)
                .callback(callback)
                .build(),
        )
        helper.writableDatabase
        helper.close()
    }

    private data class Column(
        val name: String,
        val type: String,
        val notNull: Int,
        val defaultValue: String?,
    )

    /** 用 `PRAGMA table_info` 读真实列序（数据库自己说的顺序，不受 DDL 文本排版影响）。 */
    private fun pragmaTableInfo(db: SupportSQLiteDatabase, table: String): List<Column> {
        val columns = mutableListOf<Column>()
        db.query("PRAGMA table_info(`$table`)").use { c ->
            while (c.moveToNext()) {
                columns += Column(
                    name = c.getString(1) ?: "",
                    type = c.getString(2) ?: "",
                    notNull = c.getInt(3),
                    defaultValue = if (c.isNull(4)) null else c.getString(4),
                )
            }
        }
        return columns
    }

    /** 读 KSP 导出的 schema JSON 中某张表的字段声明（保持导出顺序）。 */
    private fun exportedFields(version: Int, tableName: String): List<Map<String, Any?>> {
        val schema = exportedSchema(version)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            if (entity.getString("tableName") != tableName) continue
            val fields = entity.getJSONArray("fields")
            return (0 until fields.length()).map { index ->
                val field = fields.getJSONObject(index)
                mapOf(
                    "columnName" to field.getString("columnName"),
                    "affinity" to field.getString("affinity"),
                    "notNull" to field.getBoolean("notNull"),
                    "defaultValue" to if (field.isNull("defaultValue")) null else field.getString("defaultValue"),
                )
            }
        }
        error("导出的 $version.json 里找不到表 $tableName")
    }

    private fun exportedSchema(version: Int): JSONObject {
        val relative = "schemas/com.make.apk.data.local.AppDatabase/$version.json"
        var dir: File? = File("").absoluteFile
        repeat(6) {
            val candidate = File(dir, relative)
            if (candidate.exists()) return JSONObject(candidate.readText()).getJSONObject("database")
            dir = dir?.parentFile
        }
        error("找不到导出的 schema 文件：$relative")
    }

    companion object {
        private const val TEST_DB_NAME = "migration-4-5-order-test.db"
    }
}
