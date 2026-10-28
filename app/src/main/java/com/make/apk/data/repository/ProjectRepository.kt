package com.make.apk.data.repository

import com.make.apk.data.classifier.ArchiveClassifier
import com.make.apk.data.local.AppDatabase
import com.make.apk.data.local.entity.ProjectEntity
import com.make.apk.data.mapper.toDomain
import com.make.apk.data.mapper.toEntity
import com.make.apk.data.model.ArchiveKind
import com.make.apk.data.model.DuplicatePackageNameException
import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectDefaults
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.VersionScheme
import com.make.apk.data.model.WebSettings
import com.make.apk.logging.MmaLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * 项目仓储：数据层对上层的**唯一出口**（§8.4）。
 *
 * 约定（与统一仓储层保持一致）：
 * - 对外只暴露领域模型 [Project]，永不泄漏 Room 实体；
 * - 读：返回 `Flow`，表变更自动推送；
 * - 写：`suspend`，由调用方在 `Dispatchers.IO` 中调用；
 * - [clock] 可注入，便于单元测试确定性地断言时间字段。
 *
 * **包名唯一性强制拦截**（§2.6 / §6.1）：同名包名会导出互相覆盖的 APK，
 * 故在写入前查库拦截并抛 [DuplicatePackageNameException]。
 */
class ProjectRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val dao = database.projectDao()

    // ── 读 ────────────────────────────────────────────────────────────

    /**
     * 全部读路径统一加 [catch] 兜底：数据库任何故障都降级为安全空值，
     * **绝不把故障泄漏到 UI 层**（rc3/diag1 闪退教训）。
     */
    fun observeAll(): Flow<List<Project>> =
        dao.observeAll().map { list -> list.map(ProjectEntity::toDomain) }
            .catch { e ->
                MmaLogger.error("observe_projects_failed", e)
                emit(emptyList())
            }

    /**
     * 全部项目（按**创建时间**倒序——新项目排前面）。
     *
     * 与 [observeAll]（按更新时间倒序）并存：项目列表页要"新项目在前"，
     * 单独提供一条查询，避免 UI 层自行排序（数据一致性在仓储层收口）。
     */
    fun observeAllByCreatedAt(): Flow<List<Project>> =
        dao.observeAllByCreatedAt().map { list -> list.map(ProjectEntity::toDomain) }
            .catch { e ->
                MmaLogger.error("observe_projects_by_created_at_failed", e)
                emit(emptyList())
            }

    fun observeById(id: Long): Flow<Project?> =
        dao.observeById(id).map { it?.toDomain() }
            .catch { e ->
                MmaLogger.error("observe_project_failed", e)
                emit(null)
            }

    fun observeCount(): Flow<Int> =
        dao.observeCount()
            .catch { e ->
                MmaLogger.error("observe_project_count_failed", e)
                emit(0)
            }

    suspend fun getById(id: Long): Project? = dao.getById(id)?.toDomain()

    suspend fun getByPackageName(packageName: String): Project? =
        dao.getByPackageName(packageName)?.toDomain()

    // ── 写 ────────────────────────────────────────────────────────────

    /**
     * 新建项目：写入前做包名唯一性拦截。
     *
     * @throws DuplicatePackageNameException 包名已被占用（强制拦截，不允许保存）。
     */
    suspend fun create(project: Project): Long {
        ensurePackageNameAvailable(project.packageName)
        val now = clock()
        // 隔离 id 以项目 id 为准：先落库拿到 id，再回填 isolationId。
        val draft = project.copy(
            id = 0,
            createdAt = if (project.createdAt > 0) project.createdAt else now,
            updatedAt = now,
            isolationId = "",
        )
        val id = dao.insert(draft.toEntity())
        val isolated = draft.copy(id = id, isolationId = ProjectDefaults.isolationIdOf(id))
        dao.update(isolated.toEntity())
        return id
    }

    /**
     * 更新项目。
     *
     * 包名唯一性在此**重新拦截**：编辑页改包名后可能与他人冲突（§2.6 / §10.4 D-T2）。
     */
    suspend fun update(project: Project) {
        ensurePackageNameAvailable(project.packageName, excludeId = project.id)
        val now = clock()
        dao.update(project.copy(updatedAt = now).toEntity())
    }

    /** 删除项目：其导出运行记录由外键 `ON DELETE CASCADE` 一并清除。 */
    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    // ── 迁移对账（§8.5 D-M2）────────────────────────────────────────

    /**
     * 归档类型一次性对账：嗅探旧 `ARCHIVE` 记录的真实框架，纠正 VUE / PHP。
     *
     * v2 → v3 迁移时无法在 SQL 层判定归档是 Vue 还是 PHP（需读文件内容），
     * 故迁移阶段统一暂置为 VUE + `ArchiveKind.GENERIC`；升级后由本方法纠正。
     *
     * 语义保证（§8.5）：
     * - 嗅探出 VUE / PHP → 纠正 `projectType` 与 `entry.kind`；
     * - 文件已失联 / 判据不满足 → **维持原状**（不猜，信息不丢）。
     *
     * @return 被纠正的项目数（0 表示无需纠正或无可纠正项）。
     */
    suspend fun reconcileArchiveKinds(): Int {
        val candidates = dao.getArchiveProjects()
        if (candidates.isEmpty()) return 0

        var corrected = 0
        for (entity in candidates) {
            val entry = entity.toDomain().entry
            if (entry !is ProjectEntry.Archive) continue
            if (entry.kind != ArchiveKind.GENERIC) continue

            val verdict = ArchiveClassifier.classify(entry.assetRef)
            val newKind = verdict.kind
            if (newKind == ArchiveKind.GENERIC) continue // 判据不足，维持原状

            val newType = when (newKind) {
                ArchiveKind.VUE -> ProjectType.VUE
                ArchiveKind.PHP -> ProjectType.PHP
                ArchiveKind.GENERIC -> continue
            }
            val updated = entity.toDomain().copy(
                projectType = newType,
                entry = entry.copy(kind = newKind),
                updatedAt = clock(),
            )
            runCatching { dao.update(updated.toEntity()) }
                .onSuccess {
                    corrected++
                    MmaLogger.operation(
                        event = "archive_kind_reconciled",
                        message = "归档类型对账纠正",
                        fields = mapOf("project_id" to entity.id, "kind" to newKind.name),
                    )
                }
                .onFailure { e -> MmaLogger.error("archive_kind_reconcile_failed", e) }
        }
        return corrected
    }

    // ── 内部 ──────────────────────────────────────────────────────────

    /**
     * 包名唯一性检查。
     *
     * 排除自身（`excludeId`）——编辑时保留原包名不应被判为冲突。
     */
    private suspend fun ensurePackageNameAvailable(packageName: String, excludeId: Long = 0L) {
        if (dao.countByPackageName(packageName, excludeId) > 0) {
            throw DuplicatePackageNameException(packageName)
        }
    }

    companion object {
        /** 本地多实例数据隔离目录后缀（§2.6）。 */
        fun isolationIdOf(projectId: Long): String = ProjectDefaults.isolationIdOf(projectId)

        /** 默认主题色：设计语言主色蓝 `#2563EB`（转发 [ProjectDefaults]，勿在此另立魔数）。 */
        const val DEFAULT_THEME_COLOR: Int = ProjectDefaults.DEFAULT_THEME_COLOR

        /** 默认版本段式与版本号（§2.6 / §6.1）。 */
        val DEFAULT_VERSION_SCHEME: VersionScheme = ProjectDefaults.DEFAULT_VERSION_SCHEME

        /** 默认版本号：三段 `1.0.0` / 二段 `1.0`（按 scheme 派生）。 */
        fun defaultVersionName(scheme: VersionScheme): String =
            ProjectDefaults.defaultVersionName(scheme)

        /** 默认 Web 渲染配置（§2.6 全集开启）。 */
        val DEFAULT_WEB_SETTINGS: WebSettings = ProjectDefaults.DEFAULT_WEB_SETTINGS

        /**
         * 构造一个新项目的骨架（添加页初始值）。
         *
         * `apkName` / `packageName` / `entry` 由 UI 按用户输入填充；
         * 此处只负责"与项目类型无关"的默认值，避免各处硬编码散落。
         */
        fun newProject(
            projectType: ProjectType,
            apkName: String,
            packageName: String,
            entry: ProjectEntry,
            iconRef: String? = null,
            themeColor: Int = DEFAULT_THEME_COLOR,
            versionScheme: VersionScheme = DEFAULT_VERSION_SCHEME,
            versionName: String = defaultVersionName(versionScheme),
            versionCode: Int = ProjectDefaults.DEFAULT_VERSION_CODE,
            webSettings: WebSettings = DEFAULT_WEB_SETTINGS,
            shellSpec: ShellSpec = ShellSpec.DEFAULT,
            now: Long = System.currentTimeMillis(),
        ): Project = Project(
            id = 0,
            projectType = projectType,
            apkName = apkName,
            packageName = packageName,
            versionScheme = versionScheme,
            versionName = versionName,
            versionCode = versionCode,
            iconRef = iconRef,
            themeColor = themeColor,
            entry = entry,
            webSettings = webSettings,
            shellSpec = shellSpec,
            isolationId = "",
            createdAt = now,
            updatedAt = now,
        )
    }
}
