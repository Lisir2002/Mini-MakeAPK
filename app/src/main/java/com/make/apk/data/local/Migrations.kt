package com.make.apk.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.make.apk.data.json.RuntimeJson
import com.make.apk.data.model.ArchiveKind
import com.make.apk.data.model.ExportStatus
import com.make.apk.data.model.ProjectDefaults
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType

/**
 * v1 → v2：新增日志表 `log_entries`（全类别日志，随库加密）。
 *
 * 索引命名遵循 Room 约定：`index_<表名>_<列1>_<列2>`。
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
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
 * v2 → v3 迁移：拆表——新增 `projects`（稳定定义）+ `export_runs`（运行态），并搬运旧数据。
 *
 * 见设计文档 §8.2 / §8.3 / §8.5。
 */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ── 1. 建表（DDL 逐字对齐 app/schemas/.../3.json）──────────────
        // Room 迁移后会强校验 schema，与实体声明不一致会直接抛 IllegalStateException，
        // 故建表语句必须与 KSP 导出的 3.json **一字不差**。
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `projects` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `projectType` TEXT NOT NULL,
                `apkName` TEXT NOT NULL,
                `packageName` TEXT NOT NULL,
                `versionScheme` TEXT NOT NULL,
                `versionName` TEXT NOT NULL,
                `versionCode` INTEGER NOT NULL,
                `iconRef` TEXT,
                `themeColor` INTEGER NOT NULL,
                `entry_json` TEXT NOT NULL,
                `webSettings_json` TEXT NOT NULL,
                `isolationId` TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_projects_createdAt` ON `projects` (`createdAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_projects_packageName` ON `projects` (`packageName`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `export_runs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `projectId` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `outputApkPath` TEXT,
                `apkSizeBytes` INTEGER,
                `durationMs` INTEGER,
                `errorMessage` TEXT,
                FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_export_runs_projectId_createdAt` ON `export_runs` (`projectId`, `createdAt`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_export_runs_status_createdAt` ON `export_runs` (`status`, `createdAt`)",
        )

        // ── 2. 搬运旧数据 ──────────────────────────────────────────────
        migrateBuildRecords(db)
    }
}

/**
 * v3 → v4 迁移：删除 legacy `build_records` 表。
 *
 * 安全性：v2→v3 已把每一行复制为 `projects` + `export_runs`，
 * 此处仅 DROP 复制后的冗余副本，**零业务数据丢失**。
 * 用 `IF EXISTS` 保证新装设备（从未建过该表）幂等不报错。
 *
 * 注意：本迁移不改动 `projects`/`export_runs`/`log_entries` 的任何结构，
 * 故与 v4 实体声明及 `4.json` 逐字对齐（Room `onValidateSchema` 通过）。
 */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `build_records`")
    }
}

/**
 * v4 → v5 迁移：`projects` 新增 `shell_spec_json` 列（壳配置 L2，阶段 6.1）。
 *
 * 选择**可空 TEXT 列**而非 NOT NULL + DEFAULT：
 * - SQLite 的 `ADD COLUMN ... NOT NULL DEFAULT ''` 需要默认值字面量与 Room 导出 schema 的
 *   `defaultValue` 逐字一致，否则 `onValidateSchema` 抛 IllegalStateException（阶段 5 教训）；
 * - 可空列无默认值比对，且 `null` 语义清晰 = "未配置 → 兜底 ShellSpec.DEFAULT"。
 *
 * 幂等：SQLite **不支持** `ADD COLUMN IF NOT EXISTS`，故按项目既有惯例用 `execSQL`
 * （Room 只在版本号推进时执行一次，无需幂等）。
 *
 * 数据安全：本迁移**只加列、不动数据**，既有行 `shell_spec_json` 全为 NULL
 * → 领域层兜底为 `ShellSpec.DEFAULT`（四区全关 = 裸 WebView，与 v4 行为完全一致）。
 *
 * ## 为什么实体里 `shell_spec_json` 必须声明在字段列表**最末**
 *
 * - SQLite 的 `ALTER TABLE ... ADD COLUMN` **只能把新列追加到表末尾**；
 * - 若实体把它声明在中间，"全新安装"（Room 按实体声明序建表）与"老库升级"
 *   （追加到末尾）两条路径的**物理列序**会分叉；
 * - 真正会打红的是**本仓库自己的断言**（迁移产物与 `5.json` 的 DDL 逐字比对、
 *   以及"全新安装 vs 老库升级结构一致"断言）；
 * - **不是** `onValidateSchema`：Room 2.6.1 的 `TableInfo.equals` 比较
 *   `columns: Map<String, Column>`（按**列名**索引，**与列序无关**），
 *   故列序分叉不会触发 `onValidateSchema` 抛异常。
 */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `projects` ADD COLUMN `shell_spec_json` TEXT")
    }
}

/**
 * 把 `build_records` 的每一行搬运为「一个项目 + 一条导出运行」。
 *
 * ## 为何保留 `build_records`（不 DROP）
 *
 * 设计 §8.3 原写"搬运后 DROP build_records"，但**当时 UI 仍在直接读该表**
 * （旧构建中/结果/预览屏）。若在数据层就删表，
 * v3 一上线旧界面立即查表失败。故此处改为：
 * - **复制**而非搬走：`build_records` 数据原样保留（legacy 表），
 *   同时**复制**一份到 `projects` + `export_runs`；
 * - 待阶段 2/3 界面切到新模型后，由 v4 迁移删表。
 *
 * 这与实现总览"调用点迁移后退役旧构建历史数据层"的顺序一致，
 * 且**数据零丢失**。
 *
 * ## 为何用 Kotlin 搬运而非纯 SQL
 *
 * `entry_json` 需要按入口类型生成**合法的 JSON 字符串**：
 * - 纯 SQL 拼接必须手工转义引号 / 反斜杠 / 换行，极易出错；
 * - SQL 的 `json_extract()` 依赖 JSON1 扩展，minSdk 24 的 SQLite 不保证启用。
 *
 * 故此处用 [RuntimeJson]（真正的序列化器）生成 JSON，并按行插入——**结果与运行时写入的
 * 结构完全一致**，不存在"迁移产出的 JSON 与新写入的 JSON 格式不同"的隐患。
 */
private fun migrateBuildRecords(db: SupportSQLiteDatabase) {
    val rows = ArrayList<LegacyRow>()
    db.query("SELECT * FROM `build_records`").use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow("id")
        val createdAtIndex = cursor.getColumnIndexOrThrow("createdAt")
        val updatedAtIndex = cursor.getColumnIndexOrThrow("updatedAt")
        val inputTypeIndex = cursor.getColumnIndexOrThrow("inputType")
        val sourceLabelIndex = cursor.getColumnIndexOrThrow("sourceLabel")
        val sourceRefIndex = cursor.getColumnIndexOrThrow("sourceRef")
        val statusIndex = cursor.getColumnIndexOrThrow("status")
        val appNameIndex = cursor.getColumnIndexOrThrow("appName")
        val packageNameIndex = cursor.getColumnIndexOrThrow("packageName")
        val outputApkPathIndex = cursor.getColumnIndexOrThrow("outputApkPath")
        val apkSizeIndex = cursor.getColumnIndexOrThrow("apkSizeBytes")
        val durationIndex = cursor.getColumnIndexOrThrow("durationMs")
        val errorIndex = cursor.getColumnIndexOrThrow("errorMessage")

        while (cursor.moveToNext()) {
            rows += LegacyRow(
                id = cursor.getLong(idIndex),
                createdAt = cursor.getLong(createdAtIndex),
                updatedAt = cursor.getLong(updatedAtIndex),
                inputType = cursor.getString(inputTypeIndex),
                sourceLabel = cursor.getString(sourceLabelIndex),
                sourceRef = cursor.getString(sourceRefIndex),
                status = cursor.getString(statusIndex),
                appName = cursor.getString(appNameIndex),
                packageName = cursor.getString(packageNameIndex),
                outputApkPath = cursor.getString(outputApkPathIndex),
                apkSizeBytes = if (cursor.isNull(apkSizeIndex)) null else cursor.getLong(apkSizeIndex),
                durationMs = if (cursor.isNull(durationIndex)) null else cursor.getLong(durationIndex),
                errorMessage = cursor.getString(errorIndex),
            )
        }
    }

    for (row in rows) {
        val projectId = row.id
        val projectType = row.legacyProjectType()
        val entry = row.legacyEntry()
        val apkName = row.appName?.takeIf { it.isNotBlank() }
            ?: row.sourceLabel?.takeIf { it.isNotBlank() }
            ?: "未命名项目"
        // 包名为空时生成占位：必须非空（NOT NULL）且全局唯一（带 id 后缀）。
        val packageName = row.packageName?.takeIf { it.isNotBlank() }
            ?: "com.make.user.app${row.id}"

        db.execSQL(
            """
            INSERT INTO `projects` (
                `id`, `createdAt`, `updatedAt`, `projectType`, `apkName`, `packageName`,
                `versionScheme`, `versionName`, `versionCode`, `iconRef`, `themeColor`,
                `entry_json`, `webSettings_json`, `isolationId`
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                projectId,
                row.createdAt,
                row.updatedAt,
                projectType.name,
                apkName,
                packageName,
                ProjectDefaults.DEFAULT_VERSION_SCHEME.name,
                ProjectDefaults.defaultVersionName(ProjectDefaults.DEFAULT_VERSION_SCHEME),
                ProjectDefaults.DEFAULT_VERSION_CODE,
                null,
                ProjectDefaults.DEFAULT_THEME_COLOR,
                RuntimeJson.encodeEntry(entry),
                RuntimeJson.encodeSettings(ProjectDefaults.DEFAULT_WEB_SETTINGS),
                ProjectDefaults.isolationIdOf(projectId),
            ),
        )

        db.execSQL(
            """
            INSERT INTO `export_runs` (
                `projectId`, `createdAt`, `updatedAt`, `status`,
                `outputApkPath`, `apkSizeBytes`, `durationMs`, `errorMessage`
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                projectId,
                row.createdAt,
                row.updatedAt,
                row.legacyStatus().name,
                row.outputApkPath,
                row.apkSizeBytes,
                row.durationMs,
                row.errorMessage,
            ),
        )
    }
}

/** 迁移期间在内存中暂存的一行旧数据。 */
private data class LegacyRow(
    val id: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val inputType: String?,
    val sourceLabel: String?,
    val sourceRef: String?,
    val status: String?,
    val appName: String?,
    val packageName: String?,
    val outputApkPath: String?,
    val apkSizeBytes: Long?,
    val durationMs: Long?,
    val errorMessage: String?,
) {
    /**
     * 旧 `inputType` → 新 `projectType`（§8.3 搬运映射）。
     *
     * ARCHIVE → VUE（安全默认）；真实类型由升级后的 `ArchiveClassifier` 一次性嗅探纠正（§8.5 D-M2）。
     */
    fun legacyProjectType(): ProjectType = when (inputType) {
        "URL" -> ProjectType.URL
        "HTML" -> ProjectType.HTML
        else -> ProjectType.VUE
    }

    /**
     * 旧 `inputType` + `sourceRef` → 新 `entry`（§8.3）。
     *
     * ARCHIVE 分支保留**原始 `sourceRef`** 且 `kind = GENERIC`（未知框架标记），
     * 信息零丢失（§8.5 D-M2 第一阶段）。
     */
    fun legacyEntry(): ProjectEntry = when (inputType) {
        "URL" -> ProjectEntry.Url(sourceRef.orEmpty())
        "HTML" -> ProjectEntry.BundledHtml(sourceRef.orEmpty())
        else -> ProjectEntry.Archive(assetRef = sourceRef.orEmpty(), kind = ArchiveKind.GENERIC)
    }

    /** 旧 `status` → 新 `ExportStatus`（命名一致，直接映射；未知值兜底 QUEUED）。 */
    fun legacyStatus(): ExportStatus = when (status) {
        "QUEUED" -> ExportStatus.QUEUED
        "RUNNING" -> ExportStatus.RUNNING
        "SUCCESS" -> ExportStatus.SUCCESS
        "FAILED" -> ExportStatus.FAILED
        "CANCELLED" -> ExportStatus.CANCELLED
        else -> ExportStatus.QUEUED
    }
}
