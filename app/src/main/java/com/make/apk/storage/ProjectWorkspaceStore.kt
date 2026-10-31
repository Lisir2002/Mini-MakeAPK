package com.make.apk.storage

import android.content.Context
import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.logging.LogCategory
import com.make.apk.logging.LogFields
import com.make.apk.logging.LogLevel
import com.make.apk.logging.MmaEventLogger
import com.make.apk.logging.MmaLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 项目工作区（`MMAFile/Items/project-<id>/`）——**外部目录与内部数据库的双写对账层**。
 *
 * 用户需求（rc9）：创建项目时**内部数据库与外部目录同时创建**；外部目录被用户删除后，
 * 再次打开应用时自动从内部数据库拉取复制补全（自愈）。日志目录同理（见 FileSink + 启动回灌）。
 *
 * 设计要点：
 * - **数据库是唯一事实源**，工作区只是"人可查看"的外部镜像（manifest + 源码 + 图标副本）；
 * - 所有写入走 [com.make.apk.data.repository.ProjectRepository] 的钩子（create/update/delete），
 *   保证"写库必有镜像"，不存在两处写路径；
 * - [reconcileAll] 启动对账：目录缺失 → 从库重建并记 OPERATION 审计（谁的项目被自愈了）；
 * - 未授权存储时全部操作是**安全的空操作**（不报错、不降级功能，授权后启动对账自然补全）。
 *
 * 目录结构（⑥ 审计勘误后的口径，见各条内注）：
 * ```
 * MMAFile/Items/project-<id>/
 *   manifest.json          # 项目全量快照（entry / webSettings / shellSpec 全量内联——
 *                          #   HTML 源码**全文内联**进 entry，此前"无内联"认知与事实不符；
 *                          #   内联冗余换取「单文件可独立还原」，主控已拍板保持现状，
 *                          #   如需引用形态化另立 Phase）
 *   src/<入口文件>          # HTML：源码；URL：entry.url 文本；Archive：资产引用说明
 *   assets/icon.png        # 图标副本（iconRef 指向本地文件时；用户向镜像，非还原事实源）
 * ```
 *
 * 移植性口径：manifest 里的 `iconRef`（及 ShellSpec 的 `headerImageRef`）是**本机绝对路径**，
 * 跨设备/跨安装不可移植——「可独立还原」指领域配置（含 HTML 全文）；
 * 图标/头图在换设备还原时需重新选择（或经 Backup 链路打包二进制，Phase-2）。
 */
class ProjectWorkspaceStore(
    private val appContext: Context,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : com.make.apk.data.repository.ProjectRepository.ProjectWorkspaceHooks {

    /** 工作区清单（快照全部领域字段；序列化失败不算自愈失败——源码文件才是关键交付）。 */
    @Serializable
    data class Manifest(
        val projectId: Long,
        val projectType: String,
        val apkName: String,
        val packageName: String,
        val versionScheme: String,
        val versionName: String,
        val versionCode: Int,
        val iconRef: String? = null,
        val themeColor: Int,
        val entry: ProjectEntry,
        val webSettings: com.make.apk.data.model.WebSettings,
        val shellSpec: com.make.apk.data.model.ShellSpec,
        val isolationId: String,
        val createdAt: Long,
        val updatedAt: Long,
        val syncedAt: Long,
        /**
         * 目录结构版本（W3 ⑧）：可选字段，**读旧写新**——旧 manifest 无该键时反序列化
         * 缺省回落 1（v1 布局）；本轮起 [ProjectWorkspaceParams.STRUCTURE_VERSION] 恒写 2。
         */
        val structureVersion: Int = 1,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /** 项目工作区目录（**不保证存在**——存在性由调用方经 [isAuthorized] 判断）。 */
    fun workspaceDir(projectId: Long): File =
        File(MmaStorage.dir(MmaDir.ITEMS), "project-$projectId")

    /** 当前是否具备写外部 Items 的前提（未授权一律跳过，空操作不报错）。 */
    fun isAuthorized(): Boolean = runCatching {
        MmaStorage.isAuthorized(appContext)
    }.getOrDefault(false)

    /** 创建镜像（建库后调用；失败静默——外部镜像不阻断业务，启动对账会补）。 */
    override suspend fun onCreate(project: Project) {
        withContext(Dispatchers.IO) { writeWorkspace(project, reason = "workspace_created") }
    }

    /** 更新镜像（改库后调用；语义同 [onCreate]，整体重写保持简单一致）。 */
    override suspend fun onUpdate(project: Project) {
        withContext(Dispatchers.IO) { writeWorkspace(project, reason = "workspace_updated") }
    }

    /** 删除镜像（删库后调用；目录不存在是正常态，静默）。 */
    override fun onDelete(projectId: Long) {
        if (!isAuthorized()) return
        runCatching { workspaceDir(projectId).deleteRecursively() }
    }

    /**
     * 启动对账：库里有、外面没有的项目**逐个从库重建**（自愈）。
     *
     * @return 自愈的项目数（0 = 无缺口或未授权）。
     */
    override suspend fun reconcileAll(projects: List<Project>): Int = withContext(Dispatchers.IO) {
        if (!isAuthorized()) return@withContext 0
        var restored = 0
        projects.forEach { project ->
            val dir = workspaceDir(project.id)
            val complete = dir.isDirectory && File(dir, MANIFEST_FILE).isFile
            if (!complete) {
                val ok = writeWorkspace(project, reason = "workspace_restored")
                if (ok) restored++
            }
        }
        restored
    }

    // ── 内部 ──────────────────────────────────────────────────────────

    /** 写整个工作区；任一步失败即中止并记 ERROR（不写半个 manifest 冒充完整）。 */
    private fun writeWorkspace(project: Project, reason: String): Boolean {
        if (!isAuthorized()) return false
        return runCatching {
            val dir = workspaceDir(project.id)
            File(dir, SRC_DIR).apply { mkdirs() }
            File(dir, ASSETS_DIR).apply { mkdirs() }

            // 1) 源码镜像（按类型落人可读文件）
            writeSourceMirror(project, dir)

            // 2) 图标副本（iconRef 指向本地文件时才拷；URI/远端引用不碰）
            project.iconRef?.let { ref ->
                val source = File(ref)
                if (source.isFile) {
                    source.copyTo(File(dir, ICON_FILE), overwrite = true)
                }
            }

            // 3) manifest 最后写——它的存在即是"工作区完整"的标记
            val manifest = Manifest(
                projectId = project.id,
                projectType = project.projectType.name,
                apkName = project.apkName,
                packageName = project.packageName,
                versionScheme = project.versionScheme.name,
                versionName = project.versionName,
                versionCode = project.versionCode,
                iconRef = project.iconRef,
                themeColor = project.themeColor,
                entry = project.entry,
                webSettings = project.webSettings,
                shellSpec = project.shellSpec,
                isolationId = project.isolationId,
                createdAt = project.createdAt,
                updatedAt = project.updatedAt,
                syncedAt = clock(),
                // W3 ⑧：新写入恒为当前结构版本（读旧写新：旧 manifest 缺键回落 1）。
                structureVersion = ProjectWorkspaceParams.STRUCTURE_VERSION,
            )
            val manifestText = json.encodeToString(Manifest.serializer(), manifest)
            // manifest 体积告警（W3 ⑧，本轮生效）：HTML 全文内联可能撑大 manifest，
            // 超阈值记 WARN 提醒（不阻断——镜像写入照常完成）。
            if (manifestText.toByteArray(Charsets.UTF_8).size > ProjectWorkspaceParams.MANIFEST_WARN_BYTES) {
                MmaLogger.log(
                    LogLevel.WARN,
                    LogCategory.OPERATION,
                    "workspace_manifest_oversized",
                    message = "manifest 超过告警阈值（HTML 源码内联所致）",
                    fields = mapOf(
                        "project_id" to project.id,
                        LogFields.SIZE_BYTES to manifestText.toByteArray(Charsets.UTF_8).size,
                    ),
                )
            }
            File(dir, MANIFEST_FILE).writeText(manifestText, Charsets.UTF_8)
            true
        }.onSuccess {
            // W3 埋点对齐：workspace_created / workspace_updated / workspace_restored
            // 统一走 MmaEventLogger（事件名保持不变，审计口径单一事实源）。
            MmaEventLogger.operation(
                event = reason,
                message = "项目工作区镜像写入",
                fields = mapOf(
                    "project_id" to project.id,
                    LogFields.SUCCESS to true,
                    "structure_version" to ProjectWorkspaceParams.STRUCTURE_VERSION,
                ),
            )
        }.onFailure { e ->
            MmaLogger.error("workspace_write_failed", e, fields = mapOf("project_id" to project.id))
        }.getOrDefault(false)
    }

    /** 源码镜像：HTML 落源码、URL 落网址文本、Archive 落资产说明（VUE/PHP 资产包本体不复制——体积不可控）。 */
    private fun writeSourceMirror(project: Project, dir: File) {
        val src = File(dir, SRC_DIR)
        when (val entry = project.entry) {
            is ProjectEntry.BundledHtml -> File(src, entry.entryFile).writeText(entry.html, Charsets.UTF_8)
            is ProjectEntry.Url -> File(src, URL_MIRROR_FILE).writeText(entry.url, Charsets.UTF_8)
            is ProjectEntry.Archive -> File(src, ARCHIVE_MIRROR_FILE)
                .writeText("assetRef=${entry.assetRef}\nkind=${entry.kind.name}\n", Charsets.UTF_8)
        }
        // 类型防御性记录：未来新增类型漏写分支时，至少 manifest 里信息不丢。
        if (project.projectType == ProjectType.HTML && project.entry !is ProjectEntry.BundledHtml) {
            MmaLogger.error("workspace_type_entry_mismatch", message = "HTML 项目缺少 HTML 入口")
        }
    }

    companion object {
        private const val MANIFEST_FILE = "manifest.json"
        private const val SRC_DIR = "src"
        private const val ASSETS_DIR = "assets"
        private const val ICON_FILE = "$ASSETS_DIR/icon.png"
        private const val URL_MIRROR_FILE = "entry.url"
        private const val ARCHIVE_MIRROR_FILE = "asset.txt"
    }
}
