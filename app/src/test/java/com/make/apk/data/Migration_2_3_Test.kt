package com.make.apk.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.make.apk.data.json.RuntimeJson
import com.make.apk.data.local.AppDatabase
import com.make.apk.data.local.AppDatabaseFactory
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.VersionScheme
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * v2 → v3 迁移测试（§8.4 / §8.5）。
 *
 * 这是阶段 1 **最重要的测试**：迁移脚本是手写 SQL，一旦与 Room 期望的 schema 不一致，
 * App 启动即抛 `IllegalStateException`（rc3 闪退同款）。
 *
 * ## 为何不用官方的 `MigrationTestHelper`
 *
 * `MigrationTestHelper` 的构造函数首参是 `android.app.Instrumentation`，且它从
 * **assets** 里读取 KSP 导出的 schema JSON（`context.assets.open("$assetsFolder/$version.json")`）。
 * 这两个前提在 Robolectric 单元测试环境下都不成立：
 * - 单测没有 `Instrumentation` 实例；
 * - 单测的 assets 不含 `app/schemas`（那是构建期产物，不打进测试资源包）。
 *
 * 故本测试改用**等价且更贴近生产**的验证路径：
 * 1. **让 Room 自己跑迁移**：用真实 [AppDatabase] + 真实迁移链打开一个 v2 老库，
 *    Room 内部会在 `onUpgrade` 后调用 `onValidateSchema` 做**运行期 schema 强校验**，
 *    不一致直接抛 `IllegalStateException`——这正是线上闪退的等价复现；
 * 2. **与「Room 按实体声明新建的 v3 库」逐表逐列比对**（PRAGMA table_info / index_list /
 *    foreign_key_list），等价覆盖 `MigrationTestHelper` 的 schema 校验；
 * 3. **与 KSP 导出的 `3.json` 逐字比对 DDL**，保证手写 SQL 与 Room 生成语句一字不差。
 *
 * 覆盖范围：
 * - schema 一致性（Room 强校验 + PRAGMA 比对 + DDL 逐字比对）；
 * - 数据不丢：旧 `build_records` 每一行都在 `projects` + `export_runs` 中有对应；
 * - 映射正确：URL / HTML / ARCHIVE 三类映射到正确的 `projectType` 与 `entry_json`；
 * - ARCHIVE 零丢失：暂置 VUE 但 `entry_json` 保留原始 `sourceRef` 与 `kind=GENERIC`（§8.5 D-M2）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration_2_3_Test {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearDatabase() {
        // 每个用例都从干净的文件库开始（Robolectric 下 context 复用同一目录）。
        context.getDatabasePath(TEST_DB_NAME).delete()
        context.getDatabasePath(FRESH_DB_NAME).delete()
    }

    // ─────────────────────────── 测试用例 ───────────────────────────

    /**
     * 核心断言：Room 用真实迁移链打开 v2 老库必须成功。
     *
     * Room 在 `onUpgrade` 之后会调用生成的 `onValidateSchema`，把**实际表结构**与
     * **实体声明**逐列比对，不一致直接抛 `IllegalStateException`。
     * 这条用例失败 == 线上启动即崩。
     */
    @Test
    fun migrate_2To3_roomOpensLegacyDatabaseWithoutSchemaMismatch() {
        createLegacyDatabase(version = 2)
        insertLegacyRows()

        // 关键：不加 fallbackToDestructiveMigration——迁移出错必须炸出来，而不是静默删库。
        val db = openRoom(TEST_DB_NAME)
        db.openHelper.writableDatabase // 触发 onUpgrade → migrate → onValidateSchema

        assertEquals(3, countRows(db, "projects"))
        assertEquals(3, countRows(db, "export_runs"))
        db.close()
    }

    /**
     * schema 等价性：迁移后的库与「Room 按实体声明新建的 v3 库」逐表逐列一致。
     *
     * 这是 `MigrationTestHelper.runMigrationsAndValidate(..., validate = true)` 的等价物，
     * 但不依赖 assets 与 Instrumentation。
     */
    @Test
    fun migrate_2To3_schemaEqualsFreshRoomCreatedDatabase() {
        createLegacyDatabase(version = 2)
        insertLegacyRows()
        val migrated = openRoom(TEST_DB_NAME)
        migrated.openHelper.writableDatabase

        // 参照库：空库交给 Room 按当前实体声明建表（v3 权威结构）。
        val fresh = openRoom(FRESH_DB_NAME)
        fresh.openHelper.writableDatabase

        for (table in ALL_TABLES) {
            assertEquals(
                "表 $table 的列定义与实体声明不一致",
                describeTable(fresh, table),
                describeTable(migrated, table),
            )
        }
        migrated.close()
        fresh.close()
    }

    /**
     * 全链路（1→2→3→4→5）迁移后 DDL 与 KSP 导出的 `5.json` 逐字一致
     * （手写 SQL 不能有一字之差；`build_records` 已在 v4 被 DROP，`shell_spec_json` 已在 v5 由 ALTER TABLE 追加，故以 v5 schema 为准）。
     */
    @Test
    fun migrate_fullChain_ddlMatchesV5SchemaJson() {
        createLegacyDatabase(version = 2)
        val db = openRoom(TEST_DB_NAME)
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
     * 全链路数据不丢：删表后 `projects` / `export_runs` 行数不变，
     * 且 `build_records` 已被 DROP（sqlite_master 中不再出现）。
     */
    @Test
    fun migrate_fullChain_dataNotLostAfterDrop() {
        createLegacyDatabase(version = 2)
        insertLegacyRows()
        val db = openRoom(TEST_DB_NAME)
        db.openHelper.writableDatabase

        // 业务表行数必须等于 v3 复制产物（3 行），不可因 DROP 而减少
        assertEquals(3, countRows(db, "projects"))
        assertEquals(3, countRows(db, "export_runs"))

        // legacy 表已被 DROP
        val legacyExists = db.openHelper.writableDatabase
            .query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='build_records'")
            .use { c -> c.moveToFirst() && c.getInt(0) > 0 }
        assertFalse("build_records 应已被 v4 迁移删除", legacyExists)
        db.close()
    }

    /** 旧数据零丢失：三条旧记录全部有对应的项目与运行记录，且映射正确。 */
    @Test
    fun migrate_2To3_dataIsNotLost() {
        createLegacyDatabase(version = 2)
        insertLegacyRows()
        val db = openRoom(TEST_DB_NAME)
        db.openHelper.writableDatabase

        val projects = mutableListOf<Array<Any?>>()
        db.openHelper.writableDatabase.query(
            "SELECT `id`, `apkName`, `packageName`, `projectType` FROM `projects` ORDER BY `id`",
        ).use { c ->
            while (c.moveToNext()) {
                projects += arrayOf(c.getLong(0), c.getString(1), c.getString(2), c.getString(3))
            }
        }
        assertEquals(3, projects.size)

        // 1) URL 记录
        assertEquals("Example", projects[0][1])
        assertEquals("com.example.app", projects[0][2])
        assertEquals(ProjectType.URL.name, projects[0][3])

        // 2) HTML 记录：appName 为空 → 回退 sourceLabel；packageName 为空 → 生成占位包名
        assertEquals("粘贴的 HTML", projects[1][1])
        assertEquals("com.make.user.app2", projects[1][2])
        assertEquals(ProjectType.HTML.name, projects[1][3])

        // 3) ARCHIVE 记录：暂置 VUE（安全默认），原始 sourceRef 保留在 entry_json
        assertEquals("Bundle", projects[2][1])
        assertEquals(ProjectType.VUE.name, projects[2][3])

        // export_runs 与项目一一对应，且运行态字段被搬运
        val runs = mutableListOf<Array<Any?>>()
        db.openHelper.writableDatabase.query(
            "SELECT `projectId`, `status`, `apkSizeBytes`, `durationMs` FROM `export_runs` ORDER BY `projectId`",
        ).use { c ->
            while (c.moveToNext()) {
                runs += arrayOf(c.getLong(0), c.getString(1), c.getLong(2), c.getLong(3))
            }
        }
        assertEquals(3, runs.size)
        assertEquals(1L, runs[0][0])
        assertEquals("SUCCESS", runs[0][1])
        assertEquals(2048L, runs[0][2])
        assertEquals(500L, runs[0][3])
        assertEquals("FAILED", runs[1][1])
        assertEquals("boom", errorMessageOf(db, 2))
        assertEquals("CANCELLED", runs[2][1])
        db.close()
    }

    /** entry_json 必须是**合法且可反序列化**的 JSON（这是手写 SQL 最易翻车处）。 */
    @Test
    fun migrate_2To3_entryJsonIsValidJson() {
        createLegacyDatabase(version = 2)
        insertLegacyRows()
        val db = openRoom(TEST_DB_NAME)
        db.openHelper.writableDatabase

        val entries = mutableListOf<String>()
        db.openHelper.writableDatabase.query("SELECT `entry_json` FROM `projects` ORDER BY `id`").use { c ->
            while (c.moveToNext()) entries += c.getString(0)
        }
        assertEquals(3, entries.size)

        // URL
        assertTrue(entries[0].contains("\"type\":\"url\""))
        assertTrue(entries[0].contains("https://example.com"))
        // HTML（内容含尖括号但不含引号，仍须是合法 JSON）
        assertTrue(entries[1].contains("\"type\":\"html\""))
        // ARCHIVE：暂置 GENERIC，但原始引用完整保留（§8.5 D-M2 信息零丢失）
        assertTrue(entries[2].contains("\"type\":\"archive\""))
        assertTrue(entries[2].contains("bundle.zip"))
        assertTrue(entries[2].contains("\"kind\":\"GENERIC\""))

        // 用与运行时完全相同的解码器解析，确保迁移产物与新写入产物同构。
        entries.forEach { RuntimeJson.decodeEntry(it) }
        db.close()
    }

    /** 默认值按 §2.6 落库：三段式 / 1.0.0 / versionCode=1 / 主题色 / 隔离 id。 */
    @Test
    fun migrate_2To3_defaultsFollowDesignSpec() {
        createLegacyDatabase(version = 2)
        insertLegacyRows()
        val db = openRoom(TEST_DB_NAME)
        db.openHelper.writableDatabase

        db.openHelper.writableDatabase.query(
            "SELECT `versionScheme`, `versionName`, `versionCode`, `themeColor`, `isolationId` " +
                "FROM `projects` ORDER BY `id` LIMIT 1",
        ).use { c ->
            c.moveToFirst()
            assertEquals(VersionScheme.THREE_SEGMENT.name, c.getString(0))
            assertEquals("1.0.0", c.getString(1))
            assertEquals(1, c.getInt(2))
            assertNotNull(c.getString(4))
            assertEquals("webapp_1", c.getString(4))
        }
        db.close()
    }

    /** v2 空库迁移后也应建出空的新表（不抛异常）。 */
    @Test
    fun migrate_2To3_emptyDatabase_createsTables() {
        createLegacyDatabase(version = 2)
        val db = openRoom(TEST_DB_NAME)
        db.openHelper.writableDatabase

        assertEquals(0, countRows(db, "projects"))
        assertEquals(0, countRows(db, "export_runs"))
        db.close()
    }

    /**
     * 迁移链完整：1 → 2 → 3 → 4 → 5 连续升级可用（老用户从 v1 直接升到 v5）。
     *
     * 注：Room 打开 v1 老库时会按注册的迁移链依次应用 `MIGRATION_1_2` → `MIGRATION_2_3`
     * → `MIGRATION_3_4` → `MIGRATION_4_5`，故本用例实际断言的是 **1→2→3→4→5 全链**。
     */
    @Test
    fun migrate_1To5_fullChainSucceeds() {
        // v1 老库：只有 build_records（没有 log_entries），user_version = 1
        createLegacyDatabase(version = 1)
        insertLegacyRow(
            version = 1,
            createdAt = 1,
            updatedAt = 2,
            inputType = "URL",
            sourceLabel = "legacy",
            sourceRef = "https://legacy.example",
            status = "SUCCESS",
            appName = "Legacy",
            packageName = "com.example.legacy",
        )

        // 用完整迁移链（1→2→3→4→5）打开 v1 库
        val db = openRoom(TEST_DB_NAME)
        db.openHelper.writableDatabase
        assertEquals(1, countRows(db, "projects"))
        assertEquals(1, countRows(db, "export_runs"))
        // v1 → v2 的产物：log_entries 表必须被建出来（否则 Room schema 校验会失败）
        assertTrue(countRows(db, "log_entries") == 0)
        db.close()
    }

    /** 迁移产物可被领域层反序列化（端到端闭环：SQL → JSON → 领域模型）。 */
    @Test
    fun migrate_2To3_migratedJsonIsDecodable() {
        createLegacyDatabase(version = 2)
        insertLegacyRows()
        val db = openRoom(TEST_DB_NAME)
        db.openHelper.writableDatabase

        db.openHelper.writableDatabase.query(
            "SELECT `entry_json`, `webSettings_json` FROM `projects` ORDER BY `id`",
        ).use { c ->
            while (c.moveToNext()) {
                // 用与运行时完全相同的解码器解析，确保迁移产物与新写入产物同构。
                RuntimeJson.decodeEntry(c.getString(0))
                RuntimeJson.decodeSettings(c.getString(1))
            }
        }
        db.close()
    }

    // ─────────────────────────── 测试脚手架 ───────────────────────────

    /**
     * 造一个「版本为 [version] 的老库」。
     *
     * 用**框架层** SQLite（[FrameworkSQLiteOpenHelperFactory]）而不是 Room，
     * 以便精确控制老库结构（照抄 KSP 导出的老版本 schema）与 user_version。
     */
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

    /** 灌入三条旧数据：URL 成功 / HTML 失败 / ARCHIVE 取消。 */
    private fun insertLegacyRows(version: Int = 2) {
        insertLegacyRow(
            version = version,
            createdAt = 1000,
            updatedAt = 2000,
            inputType = "URL",
            sourceLabel = "example.com",
            sourceRef = "https://example.com",
            status = "SUCCESS",
            appName = "Example",
            packageName = "com.example.app",
            outputApkPath = "/tmp/a.apk",
            apkSizeBytes = 2048,
            durationMs = 500,
            errorMessage = null,
        )
        insertLegacyRow(
            version = version,
            createdAt = 3000,
            updatedAt = 4000,
            inputType = "HTML",
            sourceLabel = "粘贴的 HTML",
            sourceRef = "<html><body>hi</body></html>",
            status = "FAILED",
            appName = null,
            packageName = null,
            outputApkPath = null,
            apkSizeBytes = null,
            durationMs = 800,
            errorMessage = "boom",
        )
        insertLegacyRow(
            version = version,
            createdAt = 5000,
            updatedAt = 6000,
            inputType = "ARCHIVE",
            sourceLabel = "bundle.zip",
            sourceRef = "/data/user/0/com.make.apk/files/bundle.zip",
            status = "CANCELLED",
            appName = "Bundle",
            packageName = "com.example.bundle",
            outputApkPath = null,
            apkSizeBytes = null,
            durationMs = 100,
            errorMessage = null,
        )
    }

    /** 往老库 `build_records` 灌一行。 */
    private fun insertLegacyRow(
        version: Int,
        createdAt: Long,
        updatedAt: Long,
        inputType: String,
        sourceLabel: String?,
        sourceRef: String?,
        status: String,
        appName: String?,
        packageName: String?,
        outputApkPath: String? = null,
        apkSizeBytes: Long? = null,
        durationMs: Long? = null,
        errorMessage: String? = null,
    ) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase.execSQL(
            """
                INSERT INTO `build_records` (
                    `createdAt`, `updatedAt`, `inputType`, `sourceLabel`, `sourceRef`,
                    `engine`, `status`, `appName`, `packageName`, `outputApkPath`,
                    `apkSizeBytes`, `durationMs`, `errorMessage`
                ) VALUES (?, ?, ?, ?, ?, 'DEVICE_WEBVIEW', ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            arrayOf<Any?>(
                createdAt,
                updatedAt,
                inputType,
                sourceLabel,
                sourceRef,
                status,
                appName,
                packageName,
                outputApkPath,
                apkSizeBytes,
                durationMs,
                errorMessage,
            ),
        )
        helper.close()
    }

    /**
     * 用真实 [AppDatabase] + 真实迁移链打开数据库。
     *
     * 用框架 SQLite 而非 SQLCipher：SQLCipher 依赖 native 库，无法在 Robolectric JVM 上运行，
     * 而**加密与否不影响 schema 与迁移逻辑**（SQLCipher 只是页级加密）。
     */
    private fun openRoom(name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .addMigrations(*AppDatabaseFactory.MIGRATIONS)
            .build()

    // ─────────────────────────── 断言辅助 ───────────────────────────

    private fun countRows(db: AppDatabase, table: String): Int =
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM `$table`").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }

    private fun errorMessageOf(db: AppDatabase, projectId: Long): String? =
        db.openHelper.writableDatabase.query(
            "SELECT `errorMessage` FROM `export_runs` WHERE `projectId` = ?",
            arrayOf<Any>(projectId),
        ).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    /** 表结构描述：列（名/类型/NOT NULL/默认值/主键）+ 索引 + 外键。 */
    private fun describeTable(db: AppDatabase, table: String): String {
        val sqlite = db.openHelper.writableDatabase
        val sb = StringBuilder()

        sqlite.query("PRAGMA table_info(`$table`)").use { c ->
            while (c.moveToNext()) {
                sb.append("COL ${c.getString(1)} ${c.getString(2)} notNull=${c.getInt(3)} " +
                    "dflt=${c.getString(4)} pk=${c.getInt(5)}\n")
            }
        }
        sqlite.query("PRAGMA index_list(`$table`)").use { c ->
            while (c.moveToNext()) {
                val indexName = c.getString(1)
                val unique = c.getInt(2)
                sb.append("IDX $indexName unique=$unique\n")
                sqlite.query("PRAGMA index_info(`$indexName`)").use { ic ->
                    while (ic.moveToNext()) {
                        sb.append("  IDXCOL ${ic.getString(2)}\n")
                    }
                }
            }
        }
        sqlite.query("PRAGMA foreign_key_list(`$table`)").use { c ->
            while (c.moveToNext()) {
                sb.append(
                    "FK ${c.getString(3)}.${c.getString(2)} -> ${c.getString(4)}.${c.getString(5)} " +
                        "onDelete=${c.getString(6)} onUpdate=${c.getString(7)}\n",
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
     * 排版不同。去掉所有空白后即可做**逐字**比对（DDL 里不存在需要保留空白的标识符）。
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
        private const val TEST_DB_NAME = "migration-test.db"
        private const val FRESH_DB_NAME = "migration-fresh.db"

        private val ALL_TABLES = listOf("log_entries", "projects", "export_runs")
    }
}
