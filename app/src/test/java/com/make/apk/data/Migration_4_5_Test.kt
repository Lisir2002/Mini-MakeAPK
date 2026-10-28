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
import com.make.apk.data.model.ShellSpec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * v4 → v5 迁移专项测试（阶段 6.1：壳配置列 `shell_spec_json` 落库）。
 *
 * ## 为什么必须独立成测
 *
 * v4→v5 是**加列**迁移（`ALTER TABLE ... ADD COLUMN`），不是重建表迁移。
 * 加列迁移有两类独有风险：
 *
 * 1. **列序错位**：SQLite 只能把新列追加到表末尾，
 *    若实体里 `shell_spec_json` 声明在中间，"全新安装"（Room 按实体声明序建表）
 *    与"老库升级"（`ALTER TABLE` 追加到末尾）两条路径的**物理列序**会分叉
 *    → 本测试的 DDL 逐字比对断言与"新装 vs 升级结构一致"断言即失败；
 * 2. **数据丢失 / 默认值错位**：加列时既有行的该列必须是 NULL，
 *    且**不能**带 SQL DEFAULT（默认值字面量必须与 Room 导出的 schema 逐字一致，
 *    否则 `onValidateSchema` 抛异常——阶段 5 踩过的坑）。
 *
 * > 注：列序分叉**不会**触发 `onValidateSchema` 失败——Room 2.6.1 的 `TableInfo.equals`
 * > 比较的是 `columns: Map<String, Column>`（按**列名**索引，**与列序无关**）。
 *
 * ## 三重校验范式（沿用阶段 1/5）
 *
 * 1. Room 真实执行迁移 + `onValidateSchema` 运行期强校验；
 * 2. 与 KSP 导出的 `5.json` DDL 归一化比对（`normalizeSql` 去 `IF NOT EXISTS` 与空白后逐字比）；
 * 3. 既有业务数据零丢失（`projects` / `export_runs` 行数与内容不变）。
 *
 * 覆盖三条路径：**1→5**（老用户全链升级）、**4→5**（上一版用户升级）、**全新安装**。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration_4_5_Test {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearDatabase() {
        context.getDatabasePath(TEST_DB_NAME).delete()
        context.getDatabasePath(FRESH_DB_NAME).delete()
    }

    // ─────────────────────────── 测试用例 ───────────────────────────

    /**
     * 路径一：**4 → 5**（上一版本用户升级）。
     *
     * 断言：
     * - Room 迁移成功（不抛 `IllegalStateException` = `onValidateSchema` 通过）；
     * - `projects` 表新增 `shell_spec_json` 列，且**可空、无默认值**；
     * - 既有行该列为 NULL（→ 领域层兜底 `ShellSpec.DEFAULT`，四区全关 = 裸 WebView）；
     * - 既有业务数据零丢失（行数不变）。
     */
    @Test
    fun migrate_4To5_addsNullableShellSpecColumnWithoutDataLoss() {
        createV4Database()
        insertV4Rows()

        val db = openV5FromV4(TEST_DB_NAME)
        db.openHelper.writableDatabase // 触发 onUpgrade → migrate → onValidateSchema

        // 业务数据零丢失
        assertEquals(3, countRows(db, "projects"))
        assertEquals(3, countRows(db, "export_runs"))

        // 新列存在、可空、无默认值
        val column = columnInfo(db, "projects", "shell_spec_json")
        assertTrue("projects 表应存在 shell_spec_json 列", column != null)
        assertEquals("shell_spec_json 应为可空列", 0, column!!.notNull)
        assertNull("shell_spec_json 不应有 SQL 默认值", column.defaultValue)

        // 既有行该列为 NULL → 领域层兜底为默认壳
        db.openHelper.writableDatabase.query(
            "SELECT `shell_spec_json` FROM `projects` ORDER BY `id`",
        ).use { c ->
            var count = 0
            while (c.moveToNext()) {
                assertTrue("既有行的 shell_spec_json 应为 NULL", c.isNull(0))
                count++
            }
            assertEquals(3, count)
        }
        db.close()
    }

    /**
     * 路径二：**1 → 5**（最老用户全链升级：1→2→3→4→5）。
     *
     * Room 会按注册的迁移链依次应用，故本用例实际断言的是**全链**可用。
     */
    @Test
    fun migrate_1To5_fullChainSucceedsWithoutDataLoss() {
        createV1Database()
        insertV1Row()

        val db = openRoomWithFullChain(TEST_DB_NAME)
        db.openHelper.writableDatabase

        assertEquals(1, countRows(db, "projects"))
        assertEquals(1, countRows(db, "export_runs"))
        assertEquals(0, countRows(db, "log_entries"))
        // v1 → v2 的产物：log_entries 表必须被建出来（否则 Room schema 校验会失败）
        assertTrue("log_entries 表必须存在", tableExists(db, "log_entries"))
        db.close()
    }

    /**
     * 路径三：**全新安装**（无历史库，Room 直接按 v5 实体建库）。
     *
     * `shell_spec_json` 列必须存在且可空无默认值——这保证"新装"与"升级"
     * 两条路径产出**完全一致**的 schema（否则老用户升级后与新装用户行为分叉）。
     */
    @Test
    fun freshInstall_v5_hasNullableShellSpecColumn() {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, FRESH_DB_NAME)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .build()
        db.openHelper.writableDatabase

        val column = columnInfo(db, "projects", "shell_spec_json")
        assertTrue("全新安装也应有 shell_spec_json 列", column != null)
        assertEquals(0, column!!.notNull)
        assertNull(column.defaultValue)
        db.close()
    }

    /**
     * DDL 逐字比对：迁移产物与 KSP 导出的 `5.json` 必须**一字不差**。
     *
     * 这是"加列迁移列序正确"的直接证据——
     * 若 `shell_spec_json` 在实体里声明在中间，"全新安装"导出的 `CREATE TABLE` 列序
     * 与 `ALTER TABLE` 追加后的实际列序不同，本断言即失败。
     */
    @Test
    fun migrate_4To5_ddlMatchesV5SchemaJson() {
        createV4Database()
        insertV4Rows()

        val db = openV5FromV4(TEST_DB_NAME)
        db.openHelper.writableDatabase

        val schema = exportedSchema(5)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val tableName = entity.getString("tableName")
            val expectedCreate = entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
            assertEquals(
                "表 $tableName 的 CREATE 语句与 5.json 不一致",
                normalizeSql(expectedCreate),
                normalizeSql(actualCreateSql(db, tableName)),
            )

            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                val index = indices.getJSONObject(j)
                val indexName = index.getString("name")
                val expectedIndex = index.getString("createSql").replace("\${TABLE_NAME}", tableName)
                assertEquals(
                    "索引 $indexName 的 CREATE 语句与 5.json 不一致",
                    normalizeSql(expectedIndex),
                    normalizeSql(actualCreateSql(db, indexName)),
                )
            }
        }
        db.close()
    }

    /**
     * 迁移后新旧两条路径（4→5 升级 / 全新安装）的 `projects` 表结构**完全一致**。
     *
     * 不一致意味着"老用户升级"与"新装用户"看到不同的表结构，
     * 是最难排查的一类分叉，故显式比对。
     */
    @Test
    fun migrate_4To5_schemaEqualsFreshInstall() {
        createV4Database()
        insertV4Rows()
        val migrated = openV5FromV4(TEST_DB_NAME)
        migrated.openHelper.writableDatabase

        val fresh = Room.databaseBuilder(context, AppDatabase::class.java, FRESH_DB_NAME)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .build()
        fresh.openHelper.writableDatabase

        assertEquals(
            "迁移后的 projects 表结构必须与全新安装一致",
            describeTable(fresh, "projects"),
            describeTable(migrated, "projects"),
        )
        migrated.close()
        fresh.close()
    }

    /**
     * 迁移后既有项目的壳契约在领域层兜底为 [ShellSpec.DEFAULT]（四区全关）。
     *
     * 这是"老用户升级后行为零变化"的语义保证：
     * 升级前是裸 WebView，升级后仍是裸 WebView（除非用户显式配置了壳）。
     */
    @Test
    fun migrate_4To5_existingProjectsFallBackToDefaultShellSpec() {
        createV4Database()
        insertV4Rows()

        val db = openV5FromV4(TEST_DB_NAME)
        db.openHelper.writableDatabase

        val rows = mutableListOf<String?>()
        db.openHelper.writableDatabase.query("SELECT `shell_spec_json` FROM `projects`").use { c ->
            while (c.moveToNext()) {
                rows += if (c.isNull(0)) null else c.getString(0)
            }
        }
        assertEquals(3, rows.size)
        assertTrue("既有行不应凭空出现壳配置", rows.all { it == null })

        // 领域层兜底：null → ShellSpec.DEFAULT（四区全关）
        val fallback = ShellSpec.DEFAULT
        assertEquals(false, fallback.topBar.enabled)
        assertEquals(false, fallback.topTabs.enabled)
        assertEquals(false, fallback.bottomBar.enabled)
        assertEquals(false, fallback.drawer.enabled)
        db.close()
    }

    // ─────────────────────────── 测试脚手架 ───────────────────────────

    /** 造一个「版本为 4 的老库」：照抄 KSP 导出的 4.json 建表（无 shell_spec_json 列）。 */
    private fun createV4Database() {
        createLegacyDatabase(version = 4)
    }

    /** 造一个「版本为 1 的老库」：照抄 KSP 导出的 1.json 建表（只有 build_records）。 */
    private fun createV1Database() {
        createLegacyDatabase(version = 1)
    }

    /** 通用：照抄 KSP 导出的 [version].json 建表。 */
    private fun createLegacyDatabase(version: Int) {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val schema = exportedSchema(version)
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val tableName = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
                    val indices = entity.optJSONArray("indices") ?: continue
                    for (j in 0 until indices.length()) {
                        val index = indices.getJSONObject(j)
                        db.execSQL(index.getString("createSql").replace("\${TABLE_NAME}", tableName))
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
        helper.writableDatabase // 触发 onCreate
        helper.close()
    }

    /** 往 v4 老库灌入 3 条业务数据（projects + export_runs 各 3 行）。 */
    private fun insertV4Rows() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val w = helper.writableDatabase
        for (id in 1..3) {
            w.execSQL(
                """INSERT INTO `projects`
                    (`id`,`createdAt`,`updatedAt`,`projectType`,`apkName`,`packageName`,`versionScheme`,`versionName`,`versionCode`,`iconRef`,`themeColor`,`entry_json`,`webSettings_json`,`isolationId`)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
                arrayOf<Any?>(
                    id, 1000L, 2000L, "URL", "P$id", "com.example.p$id", "THREE_SEGMENT", "1.0.0", 1,
                    null, 0xE39E97, "{}", "{}", "webapp_$id", // legacy-color: 模拟历史珊瑚粉数据行（迁移兼容断言，非当前默认）
                ),
            )
            w.execSQL(
                """INSERT INTO `export_runs`
                    (`projectId`,`createdAt`,`updatedAt`,`status`,`outputApkPath`,`apkSizeBytes`,`durationMs`,`errorMessage`)
                    VALUES (?,?,?,?,?,?,?,?)""".trimIndent(),
                arrayOf<Any?>(id, 1000L, 2000L, "SUCCESS", null, null, null, null),
            )
        }
        helper.close()
    }

    /** 往 v1 老库灌入 1 行 legacy `build_records`。 */
    private fun insertV1Row() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase.execSQL(
            """
                INSERT INTO `build_records` (
                    `createdAt`,`updatedAt`,`inputType`,`sourceLabel`,`sourceRef`,
                    `engine`,`status`,`appName`,`packageName`
                ) VALUES (?,?,?,?,?,?,?,?,?)
                """.trimIndent(),
            arrayOf<Any?>(
                1L, 2L, "URL", "legacy", "https://legacy.example", "DEVICE_WEBVIEW", "SUCCESS",
                "Legacy", "com.example.legacy",
            ),
        )
        helper.close()
    }

    /**
     * 用真实 [AppDatabase] + **仅** `MIGRATION_4_5` 打开 v4 老库。
     *
     * 用框架 SQLite 而非 SQLCipher：SQLCipher 依赖 native 库，无法在 Robolectric JVM 上运行，
     * 而**加密与否不影响 schema 与迁移逻辑**（SQLCipher 只是页级加密）。
     */
    private fun openV5FromV4(name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .addMigrations(MIGRATION_4_5)
            .build()

    /** 用完整迁移链（1→2→3→4→5）打开数据库。 */
    private fun openRoomWithFullChain(name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .addMigrations(*AppDatabaseFactory.MIGRATIONS)
            .build()

    // ─────────────────────────── 断言辅助 ───────────────────────────

    private fun countRows(db: AppDatabase, table: String): Int =
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM `$table`").use { c ->
            if (!c.moveToFirst()) return@use 0
            c.getInt(0)
        }

    private fun tableExists(db: AppDatabase, tableName: String): Boolean =
        db.openHelper.writableDatabase
            .query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?", arrayOf<Any>(tableName))
            .use { c -> c.moveToFirst() && c.getInt(0) > 0 }

    /** 一列的元信息（类型 / NOT NULL / 默认值 / 主键）。 */
    private data class ColumnInfo(
        val type: String,
        val notNull: Int,
        val defaultValue: String?,
        val primaryKey: Int,
    )

    private fun columnInfo(db: AppDatabase, table: String, column: String): ColumnInfo? {
        db.openHelper.writableDatabase.query("PRAGMA table_info(`$table`)").use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == column) {
                    return ColumnInfo(
                        type = c.getString(2) ?: "",
                        notNull = c.getInt(3),
                        defaultValue = if (c.isNull(4)) null else c.getString(4),
                        primaryKey = c.getInt(5),
                    )
                }
            }
        }
        return null
    }

    /** 表结构描述（列名 / 类型 / NOT NULL / 默认值 / 主键）。 */
    private fun describeTable(db: AppDatabase, table: String): String {
        val sb = StringBuilder()
        db.openHelper.writableDatabase.query("PRAGMA table_info(`$table`)").use { c ->
            while (c.moveToNext()) {
                sb.append(
                    "COL ${c.getString(1)} ${c.getString(2)} notNull=${c.getInt(3)} " +
                        "dflt=${c.getString(4)} pk=${c.getInt(5)}\n",
                )
            }
        }
        return sb.toString()
    }

    private fun actualCreateSql(db: AppDatabase, name: String): String {
        var sql = ""
        db.openHelper.writableDatabase.query(
            "SELECT `sql` FROM `sqlite_master` WHERE `name` = ?",
            arrayOf<Any>(name),
        ).use { c ->
            if (c.moveToFirst()) sql = c.getString(0) ?: ""
        }
        return sql
    }

    /**
     * 归一化 SQL：去掉 `IF NOT EXISTS` 与全部空白。
     *
     * 手写迁移 SQL 用多行缩进排版，而 Room 导出的 `createSql` 是单行——二者结构相同、
     * 排版不同。去掉所有空白后即可做**逐字**比对。
     */
    private fun normalizeSql(sql: String): String =
        sql.replace("IF NOT EXISTS", "")
            .replace(Regex("\\s+"), "")
            .trim()

    /** 读取 KSP 导出的 schema JSON（`app/schemas/.../N.json`）中的 `database` 节点。 */
    private fun exportedSchema(version: Int): JSONObject {
        val file = locateSchemaFile(version)
        return JSONObject(file.readText()).getJSONObject("database")
    }

    private fun locateSchemaFile(version: Int): File {
        val relative = "schemas/com.make.apk.data.local.AppDatabase/$version.json"
        var dir: File? = File("").absoluteFile
        repeat(6) {
            val candidate = File(dir, relative)
            if (candidate.exists()) return candidate
            dir = dir?.parentFile
        }
        error("找不到导出的 schema 文件：$relative（工作目录 ${File("").absolutePath}）")
    }

    companion object {
        private const val TEST_DB_NAME = "migration-4-5-test.db"
        private const val FRESH_DB_NAME = "migration-4-5-fresh.db"
    }
}
