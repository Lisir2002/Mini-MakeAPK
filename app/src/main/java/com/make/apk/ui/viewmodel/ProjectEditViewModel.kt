package com.make.apk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.make.apk.data.model.DuplicatePackageNameException
import com.make.apk.data.model.ExportRun
import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectDefaults
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.VersionScheme
import com.make.apk.data.model.WebSettings
import com.make.apk.data.repository.ExportRunRepository
import com.make.apk.data.repository.ProjectRepository
import com.make.apk.logging.LogFields
import com.make.apk.logging.MmaLogger
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

    /** 自定义 User-Agent（null 表示沿用系统默认）。 */
    fun onUserAgentChanged(userAgent: String?) =
        updateWebSettings { it.copy(userAgent = userAgent) }

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
