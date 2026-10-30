package com.make.apk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.make.apk.data.model.DuplicatePackageNameException
import com.make.apk.data.model.ExportRun
import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectDefaults
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectFile
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.ProgressPosition
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.VersionScheme
import com.make.apk.data.model.WebSettings
import com.make.apk.data.repository.ExportRunRepository
import com.make.apk.data.repository.ProjectRepository
import com.make.apk.logging.LogFields
import com.make.apk.logging.MmaLogger
import com.make.apk.ui.fs.normalizePath
import com.make.apk.ui.fs.resolveCopyName
import com.make.apk.ui.fs.renamePathPrefix
import com.make.apk.ui.fs.directoryPathsOf
import com.make.apk.ui.fs.RawFsEntry
import com.make.apk.ui.fs.PasteMode
import com.make.apk.ui.fs.PasteRequest
import java.util.Locale
import com.make.apk.ui.editor.EditField
import com.make.apk.ui.editor.FieldMessage
import com.make.apk.ui.editor.ProjectDirectory
import com.make.apk.ui.editor.ProjectValidator
import com.make.apk.ui.editor.RepositoryProjectDirectory
import com.make.apk.ui.editor.iconPlaceholderLabelOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 编辑项目 ViewModel（§2.6）。
 *
 * 锁死项（编译期杜绝误改）：`projectType` / `isolationId` / `createdAt` / `id`
 * **不提供任何修改 API**——想改类型只能新建项目（§2.6 的硬约束）。
 *
 * 入口（[ProjectEntry]）：原设计保存后锁死（§6.2），§优化·第四点已放开——
 * **URL / HTML 两类现在允许在编辑页修改**（[onEntryUrlChanged] / [onEntryHtmlChanged]），
 * 资产包（VUE/PHP）入口因存档拾取尚未实现，仍沿用库中原值。
 *
 * 外部推送策略：订阅仓储的项目流；库里值变化时，
 * 仅当用户**没有改过表单**（[ProjectEditUiState.dirty] 为 false）才覆盖用户输入，
 * 否则用户的编辑会被冲掉。
 */
class ProjectEditViewModel(
    private val projectId: Long,
    private val directory: ProjectDirectory,
    private val validator: ProjectValidator = ProjectValidator(),
    private val exportRuns: Flow<List<ExportRun>>? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectEditUiState())
    val uiState: StateFlow<ProjectEditUiState> = _uiState.asStateFlow()

    /** 一次性事件通道（缓冲、不重放）。 */
    private val eventChannel = Channel<ProjectEditEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    /** 包名占用查询的防抖任务。 */
    private var packageNameCheckJob: Job? = null

    /** 库中的项目实体（保存时作为基底，只改可变字段）。 */
    private var loaded: Project? = null

    init {
        observeProject()
        observeExportRuns()
    }

    // ── 加载 ───────────────────────────────────────────────────────────

    /** 订阅项目流：首个非 null 值用于初始化表单，之后按"外部推送策略"处理。 */
    private fun observeProject() {
        viewModelScope.launch {
            directory.observeById(projectId).collect { project ->
                if (project == null) {
                    // 持续为 null → 项目不存在
                    if (_uiState.value.loading) {
                        _uiState.value = _uiState.value.copy(loading = false, projectNotFound = true)
                    }
                    return@collect
                }
                loaded = project
                applyLoaded(project)
            }
        }
    }

    /** 订阅导出运行记录（只读展示）。 */
    private fun observeExportRuns() {
        val source = exportRuns ?: return
        viewModelScope.launch {
            source.collect { runs: List<ExportRun> ->
                _uiState.value = _uiState.value.copy(exportRuns = runs)
            }
        }
    }

    /**
     * 把库里的值写进表单。
     *
     * 用户改过表单（dirty）时**不覆盖**可编辑字段，只刷新只读展示字段。
     */
    private fun applyLoaded(project: Project) {
        val current = _uiState.value
        if (current.dirty) {
            _uiState.value = current.copy(
                loading = false,
                projectNotFound = false,
                projectType = project.projectType,
                entryRef = project.entry.rawRef,
                isolationId = project.isolationId,
                engineLabel = engineLabelOf(project.projectType),
                shellSpec = project.shellSpec,
            )
            return
        }
        _uiState.value = current.copy(
            loading = false,
            projectNotFound = false,
            projectType = project.projectType,
            entryRef = project.entry.rawRef,
            apkName = project.apkName,
            packageName = project.packageName,
            versionScheme = project.versionScheme,
            versionName = project.versionName,
            versionCode = project.versionCode.toString(),
            iconRef = project.iconRef,
            iconPlaceholderLabel = iconPlaceholderLabelOf(project.apkName),
            webSettings = project.webSettings,
            themeColor = project.themeColor,
            isolationId = project.isolationId,
            engineLabel = engineLabelOf(project.projectType),
            // 入口按类型回填到可编辑字段（URL → 网址；HTML → 入口文件 + 源码）。
            entryUrl = (project.entry as? ProjectEntry.Url)?.url ?: "",
            entryFile = (project.entry as? ProjectEntry.BundledHtml)?.entryFile
                ?: ProjectEntry.BundledHtml.DEFAULT_ENTRY_FILE,
            entryHtml = (project.entry as? ProjectEntry.BundledHtml)?.html ?: "",
            projectFiles = (project.entry as? ProjectEntry.BundledHtml)?.files ?: emptyList(),
            // 壳契约只读展示：读到即用，不参与 dirty 语义（本轮无壳配置写路径）。
            shellSpec = project.shellSpec,
            dirty = false,
        )
        revalidate()
    }

    // ── 基本字段 ───────────────────────────────────────────────────────

    /** 应用名称输入变化。 */
    fun onApkNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(
            apkName = name,
            iconPlaceholderLabel = iconPlaceholderLabelOf(name),
            dirty = true,
        )
        revalidate()
    }

    /** 包名输入变化（触发占用查询，排除自身）。 */
    fun onPackageNameChanged(packageName: String) {
        _uiState.value = _uiState.value.copy(packageName = packageName, dirty = true)
        revalidate()
        schedulePackageNameCheck(packageName)
    }

    /** 切换版本号段式：版本号重置为该段式默认值，内部版本号不变。 */
    fun onVersionSchemeChanged(scheme: VersionScheme) {
        _uiState.value = _uiState.value.copy(
            versionScheme = scheme,
            versionName = ProjectDefaults.defaultVersionName(scheme),
            dirty = true,
        )
        revalidate()
    }

    /** 展示版本号输入变化。 */
    fun onVersionNameChanged(versionName: String) {
        _uiState.value = _uiState.value.copy(versionName = versionName, dirty = true)
        revalidate()
    }

    /** 内部版本号输入变化。 */
    fun onVersionCodeChanged(versionCode: String) {
        _uiState.value = _uiState.value.copy(versionCode = versionCode, dirty = true)
        revalidate()
    }

    /** 图标引用变化。 */
    fun onIconChanged(iconRef: String?) {
        _uiState.value = _uiState.value.copy(iconRef = iconRef, dirty = true)
        revalidate()
    }

    /**
     * 入口（URL 类）变化（§优化·第四点：放开原 §6.2 锁死）。
     *
     * 仅当项目类型为 [ProjectType.URL] 时有意义；其他类型不会调用此方法。
     */
    fun onEntryUrlChanged(url: String) {
        _uiState.value = _uiState.value.copy(entryUrl = url, dirty = true)
        revalidate()
    }

    /**
     * 入口（HTML 类）源码变化（「项目代码」页编辑）。
     *
     * 留空不阻断保存——保存时兜底内置默认模板。
     */
    fun onEntryHtmlChanged(html: String) {
        _uiState.value = _uiState.value.copy(entryHtml = html, dirty = true)
        revalidate()
    }

    /**
     * 入口文件名变化（HTML 类专属，类型隔离）。
     *
     * 留空 = 默认 index.html（首屏加载文件，保存时兜底）。
     */
    fun onEntryFileChanged(name: String) {
        _uiState.value = _uiState.value.copy(entryFile = name, dirty = true)
        revalidate()
    }

    /** 新增附加文件（rc11：文件目录树；重名 / 与入口同名 / 与目录同名静默忽略——UI 已前置校验）。 */
    fun onProjectFileAdded(name: String) {
        val trimmed = normalizePath(name)
        val current = _uiState.value
        if (trimmed.isEmpty()) return
        if (current.entryFile.equals(trimmed, ignoreCase = true)) return
        if (current.projectFiles.any { it.name.removeSuffix("/").equals(trimmed, ignoreCase = true) }) return
        _uiState.value = current.copy(
            projectFiles = current.projectFiles + ProjectFile(trimmed, ""),
            dirty = true,
        )
        MmaLogger.operation(
            event = "project_file_added",
            message = "新建文件",
            fields = mapOf(LogFields.SUCCESS to true, "path" to trimmed),
        )
    }

    /**
     * 新建文件夹（空文件夹约定：落库为 `name/` 空目录条目）。
     *
     * 路径归一化后追加尾 "/"；重名（精确 / 与现有文件同名 / 与入口同名）静默忽略。
     * 父目录由树构建层按路径前缀隐式推导，无需在此创建。
     */
    fun onProjectFolderAdded(name: String) {
        val trimmed = normalizePath(name)
        val current = _uiState.value
        if (trimmed.isEmpty()) return
        if (current.entryFile.equals(trimmed, ignoreCase = true)) return
        if (current.projectFiles.any { it.name.removeSuffix("/").equals(trimmed, ignoreCase = true) }) return
        val folderName = "$trimmed/"
        _uiState.value = current.copy(
            projectFiles = current.projectFiles + ProjectFile(folderName, ""),
            dirty = true,
        )
        MmaLogger.operation(
            event = "project_folder_added",
            message = "新建文件夹",
            fields = mapOf(LogFields.SUCCESS to true, "path" to trimmed),
        )
    }

    /**
     * 重命名 / 移动（前缀替换；目标重名不静默覆盖）。
     *
     * - 旧名即入口文件：仅更新 [ProjectEditUiState.entryFile]（入口内容以 [ProjectEntry.BundledHtml.html] 为单一事实源）；
     * - 否则对 [ProjectEditUiState.projectFiles] 中命中 `oldName` 或以其为前缀的条目做前缀替换
     *   （含空目录条目 `oldName/`），实现文件与目录（及其子树）的统一重命名 / 移动。
     */
    fun onProjectFileRenamed(oldName: String, newName: String) {
        val old = normalizePath(oldName)
        val newN = normalizePath(newName)
        if (old.isEmpty() || newN.isEmpty() || old == newN) return
        val current = _uiState.value
        if (old.equals(current.entryFile, ignoreCase = true)) {
            if (current.entryFile.equals(newN, ignoreCase = true)) return
            if (current.projectFiles.any { it.name.removeSuffix("/").equals(newN, ignoreCase = true) }) return
            _uiState.value = current.copy(entryFile = newN, dirty = true)
            MmaLogger.operation(
                event = "project_file_renamed",
                message = "重命名入口文件",
                fields = mapOf(LogFields.SUCCESS to true, "from" to old, "to" to newN),
            )
            return
        }
        val targetExists = current.projectFiles.any { it.name.removeSuffix("/").equals(newN, ignoreCase = true) } ||
            current.entryFile.equals(newN, ignoreCase = true) ||
            // 隐式目录（如 css/style.css 推导出的 css）同名也应判冲突，否则重命名目录到
            // 已存在目录会导致其子文件被前缀替换到目标目录造成重复条目。
            directoryPathsOf(current.projectFiles.map { RawFsEntry(it.name, 0L) })
                .any { it.equals(newN, ignoreCase = true) }
        if (targetExists) {
            MmaLogger.operation(
                event = "project_file_renamed",
                message = "重命名被拒：目标重名",
                fields = mapOf(LogFields.SUCCESS to false, "from" to old, "to" to newN),
            )
            return
        }
        val renamed = current.projectFiles.mapNotNull { f ->
            val renamedName = renamePathPrefix(f.name, old, newN)
            if (renamedName == f.name) f else f.copy(name = renamedName)
        }
        _uiState.value = current.copy(projectFiles = renamed, dirty = true)
        MmaLogger.operation(
            event = "project_file_renamed",
            message = "重命名文件",
            fields = mapOf(LogFields.SUCCESS to true, "from" to old, "to" to newN),
        )
    }

    /**
     * 删除附加文件 / 目录（目录递归：删除该前缀下全部条目 + 目录条目自身）。
     *
     * 入口文件不可删（防御性拦截，组件层也已禁用）；无匹配时不做无谓标脏。
     */
    fun onProjectFileDeleted(name: String) {
        val norm = normalizePath(name)
        if (norm.isEmpty()) return
        val current = _uiState.value
        if (current.entryFile.equals(norm, ignoreCase = true)) return
        val filtered = current.projectFiles.filterNot { f ->
            val n = f.name.removeSuffix("/")
            n == norm || n.startsWith("$norm/")
        }
        if (filtered.size == current.projectFiles.size) return
        _uiState.value = current.copy(projectFiles = filtered, dirty = true)
        MmaLogger.operation(
            event = "project_file_deleted",
            message = "删除文件",
            fields = mapOf(LogFields.SUCCESS to true, "path" to norm),
        )
    }

    /**
     * 粘贴（复制 / 剪切）：把 [PasteRequest.sources] 落到 [PasteRequest.targetDir]。
     *
     * - **复制**：对每个源，递归复制其下全部条目（含空目录条目），路径做前缀替换；
     *   目标同名时自动改名（[resolveCopyName]，如 `name 副本` / `name(2)`）；
     * - **剪切**：复制完成后从原位置删除源（移动语义）；
     * - 全部走现有"更新 UiState → 落库"同一路径（[save] 时写回 Room 的 projects JSON）。
     */
    fun onProjectFilesPasted(request: PasteRequest) {
        val current = _uiState.value
        val all = current.projectFiles
        val taken = mutableSetOf<String>().apply {
            addAll(all.map { it.name.removeSuffix("/") })
            // 隐式目录（由文件路径前缀推导，如 css/style.css → css）一并纳入，
            // 否则同目录级复制 / 剪切到已存在目录会漏判冲突、原路径重复加入。
            addAll(directoryPathsOf(all.map { RawFsEntry(it.name, 0L) }))
            add(current.entryFile)
        }
        val additions = mutableListOf<ProjectFile>()
        val toRemove = mutableSetOf<String>()
        val targetDir = normalizePath(request.targetDir)
        for (src in request.sources) {
            val s = normalizePath(src)
            if (s.isEmpty()) continue
            val isDir = all.any { it.name == "$s/" } ||
                all.any { it.name.removeSuffix("/").startsWith("$s/") }
            val base = s.substringAfterLast('/')
            var dest = if (targetDir.isEmpty()) base else "$targetDir/$base"
            dest = resolveCopyName(dest, taken)
            taken.add(dest)
            if (isDir) {
                // 显式空目录条目一并复制
                all.firstOrNull { it.name.removeSuffix("/") == s && it.name.endsWith("/") }
                    ?.let { additions += ProjectFile("$dest/", "") }
                // 复制目录下全部条目（前缀替换保留子树结构）
                for (f in all) {
                    val fn = f.name.removeSuffix("/")
                    if (fn.startsWith("$s/")) {
                        additions += ProjectFile(name = renamePathPrefix(f.name, s, dest), content = f.content)
                    }
                }
            } else {
                val srcFile = all.firstOrNull { it.name.removeSuffix("/") == s }
                additions += ProjectFile(name = dest, content = srcFile?.content ?: "")
            }
            if (request.mode == PasteMode.CUT) toRemove.add(s)
        }
        if (additions.isEmpty() && toRemove.isEmpty()) return
        // 剪切：删除需连带整棵子树——对每个待删源 s，移除 fn == s 或 fn.startsWith("s/") 的条目；
        // 单文件 s 无子项，仅精确匹配自身，行为保持不变。
        val remaining = current.projectFiles.filterNot { entry ->
            val fn = entry.name.removeSuffix("/")
            toRemove.any { s -> fn == s || fn.startsWith("$s/") }
        }
        _uiState.value = current.copy(projectFiles = remaining + additions, dirty = true)
        MmaLogger.operation(
            event = "project_files_pasted",
            message = "粘贴文件",
            fields = mapOf(
                LogFields.SUCCESS to true,
                LogFields.COUNT to additions.size,
                "mode" to request.mode.name.lowercase(Locale.US),
            ),
        )
    }

    /** 主题色变化（**不影响** webSettings）。 */
    fun onThemeColorChanged(themeColor: Int) {
        _uiState.value = _uiState.value.copy(themeColor = themeColor, dirty = true)
    }

    /**
     * 壳契约（L2）变化（阶段 6.3）。
     *
     * 任意区域改动都通过 [com.make.apk.ui.editor.ShellEditorTab] 上抛**新的整份** [ShellSpec]，
     * 这里只做"替换 + 标脏"，不关心内部差异——保持壳是不可变值对象的单一事实源。
     */
    fun onShellSpecChanged(spec: ShellSpec) {
        _uiState.value = _uiState.value.copy(shellSpec = spec, dirty = true)
    }

    // ── Web 渲染配置（六字段）──────────────────────────────────────────

    /** 是否启用 JavaScript。 */
    fun onJavascriptEnabledChanged(enabled: Boolean) =
        updateWebSettings { it.copy(javascriptEnabled = enabled) }

    /** 是否启用 DOM Storage。 */
    fun onDomStorageEnabledChanged(enabled: Boolean) =
        updateWebSettings { it.copy(domStorageEnabled = enabled) }

    /** 是否内嵌打开（false 走 Custom Tabs 外链）。 */
    fun onFitToAppChanged(enabled: Boolean) =
        updateWebSettings { it.copy(fitToApp = enabled) }

    /** 是否接受第三方 Cookie。 */
    fun onThirdPartyCookiesChanged(enabled: Boolean) =
        updateWebSettings { it.copy(thirdPartyCookies = enabled) }

    /** 缓存模式（见 [WebSettings] 伴生常量）。 */
    fun onCacheModeChanged(cacheMode: Int) =
        updateWebSettings { it.copy(cacheMode = cacheMode) }

    /** 自定义 User-Agent（null 表示沿用系统默认 UA，仅做去 `; wv)` 归一化）。 */
    fun onUserAgentChanged(userAgent: String?) =
        updateWebSettings { it.copy(userAgent = userAgent) }

    /** 忽略 SSL 证书错误（默认关：放行意味着不再校验证书，风险由用户承担）。 */
    fun onIgnoreSslErrorsChanged(enabled: Boolean) =
        updateWebSettings { it.copy(ignoreSslErrors = enabled) }

    /** 功能加载进度条开关。 */
    fun onShowLoadingProgressChanged(enabled: Boolean) =
        updateWebSettings { it.copy(showLoadingProgress = enabled) }

    /** 功能加载进度条位置（顶部 / 底部）。 */
    fun onLoadingProgressPositionChanged(position: ProgressPosition) =
        updateWebSettings { it.copy(loadingProgressPosition = position) }

    // ── 页面态（不落库）────────────────────────────────────────────────

    /** 折叠状态变化。 */
    fun onCollapsedChanged(collapsed: Boolean) {
        _uiState.value = _uiState.value.copy(collapsed = collapsed)
    }

    /** 页签切换。 */
    fun onTabSelected(tab: EditTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    // ── 保存 ───────────────────────────────────────────────────────────

    /**
     * 保存修改。
     *
     * 只改可变字段（应用名 / 包名 / 版本 / 图标 / 主题色 / Web 配置），
     * 锁死字段沿用库中原值（§2.6）。
     */
    fun save() {
        if (_uiState.value.saving) return

        revalidate()
        val state = _uiState.value
        if (!state.canSave) return

        val base = loaded ?: return
        val versionCode = state.versionCode.trim().toIntOrNull() ?: return
        // 入口重建（§优化·第四点；HTML 自 rc9 起为「入口文件 + 源码」形态）：
        // URL/HTML 用编辑页最新值（HTML 代码空 → 兜底默认模板；文件空 → 兜底 index.html）；
        // 资产包（VUE/PHP）沿用库中原值。
        val entry = when (state.projectType) {
            ProjectType.URL -> ProjectEntry.Url(state.entryUrl.trim())
            ProjectType.HTML -> ProjectEntry.BundledHtml(
                html = state.entryHtml.trim().ifBlank { ProjectEntry.BundledHtml.DEFAULT_TEMPLATE },
                entryFile = state.entryFile.trim().ifBlank { ProjectEntry.BundledHtml.DEFAULT_ENTRY_FILE },
                files = state.projectFiles,
            )
            ProjectType.VUE, ProjectType.PHP, null -> base.entry
        }
        val updated = base.copy(
            apkName = state.apkName.trim(),
            packageName = state.packageName.trim(),
            versionScheme = state.versionScheme,
            versionName = state.versionName.trim(),
            versionCode = versionCode,
            iconRef = state.iconRef,
            themeColor = state.themeColor,
            entry = entry,
            webSettings = state.webSettings,
            shellSpec = state.shellSpec,
        )

        _uiState.value = state.copy(saving = true)
        viewModelScope.launch {
            runCatching { directory.update(updated) }
                .onSuccess {
                    MmaLogger.operation(
                        event = "project_updated",
                        message = "更新项目",
                        fields = mapOf(LogFields.SUCCESS to true, "project_id" to projectId),
                    )
                    _uiState.value = _uiState.value.copy(saving = false, dirty = false)
                    eventChannel.send(ProjectEditEvent.Saved(projectId))
                }
                .onFailure { throwable ->
                    if (throwable is DuplicatePackageNameException) {
                        val message = validator.packageNameOccupied()
                        _uiState.value = _uiState.value.copy(
                            saving = false,
                            packageNameConflict = true,
                            errors = _uiState.value.errors + (EditField.PACKAGE_NAME to message),
                        )
                        MmaLogger.error("project_update_failed", throwable)
                        eventChannel.send(ProjectEditEvent.SaveFailed(message.text))
                    } else {
                        val message = throwable.message ?: "保存失败，请稍后再试"
                        _uiState.value = _uiState.value.copy(saving = false)
                    MmaLogger.error("project_update_failed", throwable)
                    eventChannel.send(ProjectEditEvent.SaveFailed(message))
                }
            }
        }
    }

    /** 删除项目（连同其导出记录，由外键级联）。 */
    fun delete() {
        viewModelScope.launch {
            runCatching { directory.delete(projectId) }
                .onSuccess {
                    MmaLogger.operation(
                        event = "project_deleted",
                        message = "删除项目",
                        fields = mapOf(LogFields.SUCCESS to true, "project_id" to projectId),
                    )
                    eventChannel.send(ProjectEditEvent.Deleted(projectId))
                }
                .onFailure { throwable ->
                    MmaLogger.error("project_delete_failed", throwable)
                    eventChannel.send(ProjectEditEvent.SaveFailed(throwable.message ?: "删除失败，请稍后再试"))
                }
        }
    }

    // ── 内部 ───────────────────────────────────────────────────────────

    /** 更新 Web 渲染配置（单项修改，不影响其他字段）。 */
    private fun updateWebSettings(transform: (WebSettings) -> WebSettings) {
        _uiState.value = _uiState.value.copy(
            webSettings = transform(_uiState.value.webSettings),
            dirty = true,
        )
    }

    /** 同步重算本地可判定规则。 */
    private fun revalidate() {
        val state = _uiState.value
        val errors = mutableMapOf<EditField, FieldMessage>()
        val warnings = mutableMapOf<EditField, FieldMessage>()

        val nameVerdict = validator.validateApkName(state.apkName)
        nameVerdict.error?.let { errors[EditField.APK_NAME] = it }
        nameVerdict.warning?.let { warnings[EditField.APK_NAME] = it }

        val packageVerdict = validator.validatePackageName(state.packageName)
        packageVerdict.error?.let { errors[EditField.PACKAGE_NAME] = it }
        packageVerdict.warning?.let { warnings[EditField.PACKAGE_NAME] = it }
        if (state.packageNameConflict) {
            errors[EditField.PACKAGE_NAME] = validator.packageNameOccupied()
        }

        val versionNameVerdict = validator.validateVersionName(state.versionName, state.versionScheme)
        versionNameVerdict.error?.let { errors[EditField.VERSION_NAME] = it }
        versionNameVerdict.warning?.let { warnings[EditField.VERSION_NAME] = it }

        val versionCodeVerdict = validator.validateVersionCode(state.versionCode)
        versionCodeVerdict.error?.let { errors[EditField.VERSION_CODE] = it }
        versionCodeVerdict.warning?.let { warnings[EditField.VERSION_CODE] = it }

        val iconVerdict = validator.validateIconRef(state.iconRef)
        iconVerdict.error?.let { errors[EditField.ICON] = it }
        iconVerdict.warning?.let { warnings[EditField.ICON] = it }

        // 入口（按类型隔离校验，错误挂到对应字段）：
        // URL → 网址；HTML → 入口文件名（源码可空，保存兜底默认模板，不再阻断）。
        when (state.projectType) {
            ProjectType.URL -> validator.validateEntryUrl(state.entryUrl)
                .error?.let { errors[EditField.ENTRY_URL] = it }
            ProjectType.HTML -> validator.validateEntryFile(state.entryFile)
                .error?.let { errors[EditField.ENTRY_FILE] = it }
            ProjectType.VUE, ProjectType.PHP, null -> Unit
        }

        // 壳条目引用的本地页面体检（M3）：条目指向的 .html 被删后预览 / 导出会"点了没反应"，
        // 用户很难自查，故在保存前给出一条**可见警告**（不阻断保存——删文件可能是有意为之）。
        // 挂在 ENTRY_FILE 字段上：它是"项目文件"类提示的唯一既有展示位。
        validator.validateShellLocalPages(state.shellSpec, state.localPageFileNames)
            .warning?.let { warnings[EditField.ENTRY_FILE] = it }

        _uiState.value = _uiState.value.copy(errors = errors, warnings = warnings)
    }

    /**
     * 防抖查询包名占用，并**排除自身**（保留原包名不应被判为冲突，§2.6）。
     */
    private fun schedulePackageNameCheck(packageName: String) {
        packageNameCheckJob?.cancel()
        val candidate = packageName.trim()
        if (candidate.isEmpty() || validator.validatePackageName(candidate).hasError) {
            _uiState.value = _uiState.value.copy(packageNameConflict = false)
            revalidate()
            return
        }
        packageNameCheckJob = viewModelScope.launch {
            delay(PACKAGE_NAME_CHECK_DEBOUNCE_MS)
            val occupied = runCatching {
                val hit = directory.getByPackageName(candidate)
                hit != null && hit.id != projectId
            }
                .onFailure { MmaLogger.error("package_name_check_failed", it) }
                .getOrDefault(false)
            _uiState.value = _uiState.value.copy(packageNameConflict = occupied)
            revalidate()
        }
    }

    /** 渲染引擎说明文案（只读展示）。 */
    private fun engineLabelOf(type: ProjectType): String = when (type) {
        ProjectType.URL -> "设备端即时封装（WebView）"
        ProjectType.HTML -> "资产注入 + 本地重签"
        ProjectType.VUE -> "资产注入 + 本地重签"
        ProjectType.PHP -> "资产注入 + 本地重签"
    }

    companion object {
        /** 包名占用查询防抖时长（毫秒）。 */
        const val PACKAGE_NAME_CHECK_DEBOUNCE_MS: Long = 300L
    }
}

/**
 * 手工 DI 工厂（本项目未引入 Hilt，保持轻量）。
 */
class ProjectEditViewModelFactory(
    private val projectId: Long,
    private val repository: ProjectRepository,
    private val exportRunRepository: ExportRunRepository? = null,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        ProjectEditViewModel(
            projectId = projectId,
            directory = RepositoryProjectDirectory(repository),
            validator = ProjectValidator(),
            exportRuns = exportRunRepository?.observeByProject(projectId),
        ) as T
}
