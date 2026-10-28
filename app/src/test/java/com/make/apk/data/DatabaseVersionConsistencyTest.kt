package com.make.apk.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.make.apk.data.local.AppDatabase
import com.make.apk.data.local.AppDatabaseFactory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * DB 版本与迁移链**登记一致性**守门测试（阶段 5 QA 独立审查补测）。
 *
 * ## 为什么必须补
 *
 * 「迁移 SQL 与 `5.json` 对齐」只证明了**迁移产物**正确，
 * 但没证明**声明侧**与**产物侧**对得上。以下三类漂移都会让线上启动即崩，
 * 且**任何既有测试都发现不了**：
 *
 * 1. `AppDatabase.version` 与 `MIGRATIONS` 链末端不一致
 *    ——例如实体改到 v6 却忘了登记 `MIGRATION_5_6`：
 *    Room 打开 v5 老库时找不到 v5→v6 的迁移路径，直接抛
 *    `IllegalStateException("A migration from 5 to 6 was required but not found")`；
 * 2. `MIGRATIONS` 链**不连续**（如 1→2、3→4 缺 2→3）：
 *    老 v2 用户升级时同样找不到路径；
 * 3. KSP 导出的 `5.json` 的 `version` / `identityHash` 与实体声明脱节
 *    ——`5.json` 没重新生成或生成自旧的实体集。
 *
 * 这三类正是「移除 `fallbackToDestructiveMigration()` 之后**没有兜底**」的最大风险面：
 * 迁移链一旦断链，线上就是**开屏闪退**且**数据被清空**（没有 fallback 兜），
 * 故必须有自动化门禁，而不是靠人工记得同步三处。
 *
 * **版本号的权威来源**：Room 的 `@Database(version=…)` 是 BINARY 保留期，运行期反射不到，
 * 故本测试用**真实打开的库**读 `PRAGMA user_version`——那是 Room 实际写入、
 * 也是下次升级时用于选择迁移路径的值，与注解声明**不可能不一致**。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseVersionConsistencyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearDatabase() {
        context.getDatabasePath(DB_NAME).delete()
    }

    /**
     * Room 实际写入的 `user_version` 必须等于 KSP 导出的 `5.json` 的 `version`。
     *
     * 这是"实体声明 / 迁移链 / 导出 schema"三者对齐的**运行时权威校验**：
     * Room 建库时按实体声明写 `user_version`，按实体声明写 `room_master_table.identityHash`，
     * 因此"库里的版本 == 导出的 schema 版本"即等价于"声明与导出一致"。
     */
    @Test
    fun runtimeUserVersion_matchesExportedSchemaVersion() {
        val db = openFresh()

        val userVersion = db.openHelper.writableDatabase
            .query("PRAGMA user_version")
            .use { c ->
                c.moveToFirst()
                c.getInt(0)
            }

        val schemaVersion = exportedSchema(5).getInt("version")

        assertEquals(
            "库中 user_version 必须与导出的 5.json version 一致（否则 Room 会认为需要迁移）",
            schemaVersion,
            userVersion,
        )
        assertEquals(
            "当前应为 DB v5（阶段 6.1 目标态）",
            5,
            userVersion,
        )
        db.close()
    }

    /**
     * Room 迁移链必须**连续无缝**：从 v1 起，每一步 `startVersion` 恰好等于上一步的 `endVersion`。
     *
     * 断链（如缺 2→3）会让处于该版本的老用户**无迁移路径可用**，
     * Room 抛异常 → 启动闪退；移除 fallback 后无任何兜底。
     */
    @Test
    fun migrationChain_isContinuousFromV1ToCurrentVersion() {
        val sorted = AppDatabaseFactory.MIGRATIONS.sortedBy { it.startVersion }

        assertTrue("迁移链不得为空", sorted.isNotEmpty())

        var expectedStart = 1
        for (migration in sorted) {
            assertEquals(
                "迁移链断裂：期望 startVersion=$expectedStart，实际=${migration.startVersion}→${migration.endVersion}",
                expectedStart,
                migration.startVersion,
            )
            expectedStart = migration.endVersion
        }

        assertEquals(
            "迁移链末端必须覆盖到库的实际版本",
            currentUserVersion(),
            expectedStart,
        )
    }

    /**
     * 每一步迁移的 `endVersion` 必须严格大于 `startVersion`（不得有"原地迁移"）。
     *
     * 原地迁移会让 Room 的迁移路径搜索匹配失败。
     */
    @Test
    fun everyMigration_increasesVersion() {
        for (migration in AppDatabaseFactory.MIGRATIONS) {
            assertTrue(
                "迁移 ${migration.startVersion}→${migration.endVersion} 必须严格升版",
                migration.endVersion > migration.startVersion,
            )
        }
    }

    /**
     * KSP 导出的 `5.json` 必须与实体声明对齐：
     * - `database.version == 5`；
     * - 表集恰为 v4 的三张业务表（`log_entries` / `projects` / `export_runs`），
     *   **不含** legacy `build_records`；
     * - `identityHash` 非空（Room 用它做 schema 一致性校验的锚点）。
     */
    @Test
    fun exportedSchemaV5_matchesEntityDeclaration() {
        val schema = exportedSchema(5)

        assertEquals("5.json 的 version 必须等于 5", 5, schema.getInt("version"))

        val tableNames = schema.getJSONArray("entities")
            .let { entities ->
                (0 until entities.length()).map { entities.getJSONObject(it).getString("tableName") }
            }
            .sorted()

        assertEquals(
            "5.json 的表集应恰为 v5 的三张业务表（无 legacy build_records、无意外表）",
            listOf("export_runs", "log_entries", "projects"),
            tableNames,
        )

        assertTrue(
            "5.json 必须含 identityHash（Room schema 校验锚点）",
            schema.optString("identityHash").isNotBlank(),
        )
    }

    /**
     * `AppDatabase` **不得**再暴露 legacy DAO（退役彻底性的运行时校验）。
     *
     * 源码层删了 `BuildRecordEntity`/`buildRecordDao()`，但若有人后来又加回来，
     * 光靠 grep 抓不到"实体已删、DAO 方法残留"这类半吊子状态。
     */
    @Test
    fun appDatabase_exposesNoLegacyBuildRecordDao() {
        val methodNames = AppDatabase::class.java.declaredMethods.map { it.name }

        assertFalse(
            "AppDatabase 不得残留 buildRecordDao()（legacy 退役不彻底）",
            methodNames.any { it.contains("buildRecord", ignoreCase = true) },
        )
        assertTrue(
            "AppDatabase 应暴露 projectDao()",
            methodNames.any { it == "projectDao" },
        )
        assertTrue(
            "AppDatabase 应暴露 exportRunDao()",
            methodNames.any { it == "exportRunDao" },
        )
    }

    /**
     * 旧版本 schema 导出文件必须**全部保留**（迁移审计与历史回溯的依据）。
     *
     * v1/v2/v3/v4 是历史迁移的输入契约——删掉任一个，
     * `Migration_2_3_Test` / `Migration_3_4_Test` 造"老库"时无契约可比对。
     */
    @Test
    fun legacyExportedSchemas_areRetained() {
        for (version in listOf(1, 2, 3, 4)) {
            assertTrue(
                "schema $version.json 必须保留（历史迁移契约）",
                locateSchemaFile(version).exists(),
            )
        }
    }

    /**
     * 迁移链登记的每个版本都必须有对应的导出 schema 文件——
     * 「新增版本必须同步四处」铁律（`AppDatabase.version` + `entities` + `MIGRATIONS` + `N.json`）
     * 的自动化检查。
     */
    @Test
    fun everyMigratedVersion_hasExportedSchemaFile() {
        for (migration in AppDatabaseFactory.MIGRATIONS) {
            for (version in migration.startVersion..migration.endVersion) {
                assertTrue(
                    "版本 $version 缺少导出的 schema JSON",
                    locateSchemaFile(version).exists(),
                )
            }
        }
    }

    // ── 断言辅助 ────────────────────────────────────────────────────────

    /** 用真实 [AppDatabase] 打开一个全新库（触发 Room 建库 + `onValidateSchema`）。 */
    private fun openFresh(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .addMigrations(*AppDatabaseFactory.MIGRATIONS)
            .build()

    /** 读库中 Room 实际写入的版本号。 */
    private fun currentUserVersion(): Int {
        val db = openFresh()
        val version = db.openHelper.writableDatabase
            .query("PRAGMA user_version")
            .use { c ->
                c.moveToFirst()
                c.getInt(0)
            }
        db.close()
        return version
    }

    /** 读取 KSP 导出的 schema JSON（`app/schemas/.../N.json`）中的 `database` 节点。 */
    private fun exportedSchema(version: Int): JSONObject =
        JSONObject(locateSchemaFile(version).readText()).getJSONObject("database")

    private fun locateSchemaFile(version: Int): File {
        val relative = "schemas/com.make.apk.data.local.AppDatabase/$version.json"
        var dir: File? = File("").absoluteFile
        repeat(6) {
            val candidate = File(dir, relative)
            if (candidate.exists()) return candidate
            dir = dir?.parentFile
        }
        return File("schemas/com.make.apk.data.local.AppDatabase/$version.json")
    }

    companion object {
        private const val DB_NAME = "version-consistency-test.db"
    }
}
