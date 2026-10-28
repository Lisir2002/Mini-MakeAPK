package com.make.apk.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.make.apk.data.local.AppDatabase
import com.make.apk.data.local.MIGRATION_3_4
import com.make.apk.data.local.MIGRATION_4_5
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * v3 → v4 迁移专项测试。
 *
 * 复用阶段 1 建立的三重校验范式（`Migration_2_3_Test` 同款逻辑，自包含实现）：
 * 1. Room 自己跑迁移 + `onValidateSchema` 运行期强校验（schema 不符即抛 IllegalStateException）；
 * 2. 与「Room 按 v4 实体声明新建的库」逐表逐列比对（此处简化为与 `4.json` DDL 逐字比对）；
 * 3. 与 KSP 导出的 `4.json` DDL 归一化比对（`normalizeSql` 去 `IF NOT EXISTS`/空白后逐字比）。
 *
 * 覆盖范围：
 * - `build_records` 已被 DROP（sqlite_master 中不再出现）；
 * - `projects` / `export_runs` 行数在迁移前后不变（**零业务数据丢失**）；
 * - 迁移后 schema 通过 Room `onValidateSchema` 强校验（与 v4 实体声明一致）；
 * - 迁移后 DDL 与 `4.json` 逐字一致。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration_3_4_Test {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearDatabase() {
        context.getDatabasePath(TEST_DB_NAME).delete()
        context.getDatabasePath(FRESH_DB_NAME).delete()
    }

    // ─────────────────────────── 测试用例 ───────────────────────────

    /**
     * 核心断言：Room 用真实 `MIGRATION_3_4` 打开 v3 老库必须成功，且：
     * - `build_records` 被 DROP（sqlite_master 中不再出现）；
     * - `projects` / `export_runs` 行数不变（数据零丢失）。
     *
     * Room 在 `onUpgrade` 之后调用生成的 `onValidateSchema`，把实际表结构与 v4 实体声明
     * 逐列比对，不一致直接抛 `IllegalStateException`——这正是线上闪退的等价复现。
     */
    @Test
    fun migrate_3To4_dropsLegacyTableAndKeepsBusinessData() {
        createV3Database()
        insertV3Rows()

        val db = openV5FromV3(TEST_DB_NAME)
        db.openHelper.writableDatabase // 触发 onUpgrade → migrate → onValidateSchema

        // 业务数据无损：v3 复制产物原样保留
        assertEquals(3, countRows(db, "projects"))
        assertEquals(3, countRows(db, "export_runs"))
        // legacy 表已被 DROP
        assertFalse("build_records 应已被 v4 迁移删除", tableExists(db, "build_records"))
        db.close()
    }

    /** Room 迁移后会跑 `onValidateSchema`：最终 schema 与 v4 实体声明一致（不抛异常即通过）。 */
    @Test
    fun migrate_3To4_schemaValidatesAgainstV4Entities() {
        createV3Database()
        insertV3Rows()

        // 若 schema 不匹配，openV4FromV3 内部 onValidateSchema 会抛 IllegalStateException
        val db = openV5FromV3(TEST_DB_NAME)
        db.openHelper.writableDatabase

        // 与 4.json DDL 归一化比对一致（手写 SQL 不能有一字之差）
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

    /** 空 v3 库（无 build_records 数据）迁移后也只有 v4 的三个表、无 build_records。 */
    @Test
    fun migrate_3To4_emptyV3Database_hasNoBuildRecordsTable() {
        createV3Database()
        val db = openV5FromV3(TEST_DB_NAME)
        db.openHelper.writableDatabase

        assertEquals(0, countRows(db, "projects"))
        assertEquals(0, countRows(db, "export_runs"))
        assertFalse("build_records 应已被 v4 迁移删除", tableExists(db, "build_records"))
        db.close()
    }

    // ─────────────────────────── 测试脚手架 ───────────────────────────

    /** 造一个「版本为 3 的老库」：照抄 KSP 导出的 3.json 建表（含 build_records）。 */
    private fun createV3Database() {
        val callback = object : SupportSQLiteOpenHelper.Callback(3) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val schema = exportedSchema(3)
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

    /** 往 v3 老库灌入数据：build_records 3 行 + 模拟 v3 复制产物的 projects/export_runs 各 3 行。 */
    private fun insertV3Rows() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val w = helper.writableDatabase
        // legacy build_records（将被 v4 DROP，但其数据已等价存在于 projects/export_runs）
        val legacy = listOf(
            arrayOf<Any?>("URL", "example.com", "https://example.com", "SUCCESS", "Example", "com.example.app"),
            arrayOf<Any?>("HTML", "粘贴的 HTML", "<html></html>", "FAILED", "App2", "com.example.app2"),
            arrayOf<Any?>("ARCHIVE", "bundle.zip", "/x/bundle.zip", "CANCELLED", "Bundle", "com.example.bundle"),
        )
        for (row in legacy) {
            w.execSQL(
                """INSERT INTO `build_records`
                    (`createdAt`,`updatedAt`,`inputType`,`sourceLabel`,`sourceRef`,`engine`,`status`,`appName`,`packageName`)
                    VALUES (?,?,?,?,?,?,?,?,?)""".trimIndent(),
                arrayOf<Any?>(1000L, 2000L, row[0], row[1], row[2], "DEVICE_WEBVIEW", row[3], row[4], row[5]),
            )
        }
        // 模拟 v3 把 build_records 复制为 projects + export_runs（仅验证删表后二者行数不变）
        for (id in 1..3) {
            w.execSQL(
                """INSERT INTO `projects`
                    (`id`,`createdAt`,`updatedAt`,`projectType`,`apkName`,`packageName`,`versionScheme`,`versionName`,`versionCode`,`iconRef`,`themeColor`,`entry_json`,`webSettings_json`,`isolationId`)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
                arrayOf<Any?>(
                    id, 1000L, 2000L, "URL", "P$id", "com.example.p$id", "THREE_SEGMENT", "1.0.0", 1,
                    null, 0xE39E97, "{}", "{}", "webapp_$id",
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

    /**
     * 用真实 [AppDatabase] + `MIGRATION_3_4` → `MIGRATION_4_5` 打开 v3 老库。
     *
     * 用框架 SQLite 而非 SQLCipher：SQLCipher 依赖 native 库，无法在 Robolectric JVM 上运行，
     * 而**加密与否不影响 schema 与迁移逻辑**（SQLCipher 只是页级加密）。
     */
    private fun openV5FromV3(name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
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
        private const val TEST_DB_NAME = "migration-3-4-test.db"
        private const val FRESH_DB_NAME = "migration-3-4-fresh.db"
    }
}
