package com.make.apk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.make.apk.data.model.ArchiveKind
import com.make.apk.data.model.DuplicatePackageNameException
import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectDefaults
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.VersionScheme
import com.make.apk.data.repository.ProjectRepository
import com.make.apk.logging.LogFields
import com.make.apk.logging.MmaLogger
import com.make.apk.ui.editor.AddField
import com.make.apk.ui.editor.ArchiveProbe
import com.make.apk.ui.editor.FieldMessage
import com.make.apk.ui.editor.FileArchiveProbe
import com.make.apk.ui.editor.ProjectDirectory
import com.make.apk.ui.editor.ProjectValidator
import com.make.apk.ui.editor.RepositoryProjectDirectory
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 添加项目 ViewModel（§2.6）。
 *
 * 职责：
 * - 持有添加页表单状态（[AddProjectUiState]），并对每次输入**同步**重算本地可判定规则；
 * - 包名占用是**异步**事实（要查库），用 300ms 防抖查询，避免每敲一个字符就查一次；
 * - 保存走 [ProjectDirectory]（仓储的窄接口），`isolationId` 由仓储回填，VM 不自己拼。
 *
 * 依赖注入：手工 DI（见 [AddProjectViewModelFactory]），本项目不引入 Hilt。
 */
class AddProjectViewModel(
    private val directory: ProjectDirectory,
    private val validator: ProjectValidator = ProjectValidator(),
    private val archiveProbe: ArchiveProbe = FileArchiveProbe(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddProjectUiState())
    val uiState: StateFlow<AddProjectUiState> = _uiState.asStateFlow()

    /** 一次性事件通道（缓冲、不重放）。 */
    private val eventChannel = Channel<AddProjectEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    /** 包名占用查询的防抖任务（新的输入会取消上一次未完成的查询）。 */
    private var packageNameCheckJob: Job? = null

    // ── 项目类型 ───────────────────────────────────────────────────────

    /**
     * 选择项目类型。
     *
     * 切换类型会**清空来源输入**（URL / HTML / 资产包互斥，旧值留着只会误导）。
     *
     * [AddProjectUiState.dirty] 的处理：只有"此前已有选中项"才算用户改动。
     * 准星选择器**首帧就会回传**默认项（URL），那不是用户操作——若在此无条件置 dirty，
     * 用户什么都没填时 dirty 就已经是 true，阶段 4/5 接上"放弃修改？"确认后会退化成
     * "空表单返回也弹确认"。
     */
    fun onProjectTypeSelected(type: ProjectType) {
        val state = _uiState.value
        _uiState.value = state.copy(
            projectType = type,
            urlInput = "",
            htmlInput = "",
            archivePath = null,
            dirty = state.dirty || state.projectType != null,
        )
        refreshSuggestions()
        revalidate()
    }

    // ── 入口输入 ───────────────────────────────────────────────────────

    /** 远程网址输入变化。 */
    fun onUrlChanged(url: String) {
        _uiState.value = _uiState.value.copy(urlInput = url, dirty = true)
        refreshSuggestions()
        revalidate()
    }

    /** 内置 HTML 输入变化。 */
    fun onHtmlChanged(html: String) {
        _uiState.value = _uiState.value.copy(htmlInput = html, dirty = true)
        refreshSuggestions()
        revalidate()
    }

    /** 选择了资产包（路径 + 框架类型）。 */
    fun onArchivePicked(path: String, kind: ArchiveKind) {
        _uiState.value = _uiState.value.copy(
            archivePath = path,
            archiveKind = kind,
            dirty = true,
        )
        refreshSuggestions()
        revalidate()
    }

    // ── 基本字段 ───────────────────────────────────────────────────────

    /** 应用名称输入变化（标记为手动编辑，不再自动跟随建议）。 */
    fun onApkNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(apkName = name, apkNameEdited = true, dirty = true)
        revalidate()
    }

    /** 包名输入变化（标记为手动编辑，并触发占用查询）。 */
    fun onPackageNameChanged(packageName: String) {
        _uiState.value = _uiState.value.copy(
            packageName = packageName,
            packageNameEdited = true,
            dirty = true,
        )
        revalidate()
        schedulePackageNameCheck(packageName)
    }

    /**
     * 切换版本号段式。
     *
     * 段式变了，版本号格式必然不匹配，故**重置为该段式的默认版本号**；
     * 内部版本号（versionCode）与段式无关，保持不变。
     */
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

    /** 图标引用变化（null 表示恢复默认占位）。 */
    fun onIconChanged(iconRef: String?) {
        _uiState.value = _uiState.value.copy(iconRef = iconRef, dirty = true)
        revalidate()
    }

    /** 主题色变化。 */
    fun onThemeColorChanged(themeColor: Int) {
        _uiState.value = _uiState.value.copy(themeColor = themeColor, dirty = true)
    }

    // ── 建议 ───────────────────────────────────────────────────────────

    /** 采用应用名建议（显式采用后恢复"跟随建议"状态）。 */
    fun applySuggestionApkName() {
        val suggestion = _uiState.value.apkNameSuggestion ?: return
        _uiState.value = _uiState.value.copy(apkName = suggestion, apkNameEdited = false, dirty = true)
        revalidate()
    }

    /** 采用包名建议（显式采用后恢复"跟随建议"状态）。 */
    fun applySuggestionPackageName() {
        val suggestion = _uiState.value.packageNameSuggestion ?: return
        _uiState.value = _uiState.value.copy(
            packageName = suggestion,
            packageNameEdited = false,
            dirty = true,
        )
        revalidate()
        schedulePackageNameCheck(suggestion)
    }

    // ── 保存 ───────────────────────────────────────────────────────────

    /**
     * 保存项目。
     *
     * 流程：再跑一次全量校验 → 通过则落库 → 成功派发 [AddProjectEvent.Saved]。
     * 包名被占用（[DuplicatePackageNameException]）→ 落到包名字段的错误上并派发
     * [AddProjectEvent.SaveFailed]；其他异常 → 记日志并同样派发
     * [AddProjectEvent.SaveFailed]（一次性事件是失败通知的**唯一**出口）。
     */
    fun save() {
        if (_uiState.value.saving) return

        // 保存前再跑一次全量校验：防抖中的包名占用检查可能尚未回来。
        revalidate()
        val state = _uiState.value
        if (!state.canSave) return

        val project = buildProject(state) ?: return

        _uiState.value = state.copy(saving = true)
        viewModelScope.launch {
            runCatching { directory.create(project) }
                .onSuccess { id ->
                    MmaLogger.operation(
                        event = "project_created",
                        message = "新建项目",
                        fields = mapOf(LogFields.SUCCESS to true, "project_id" to id),
                    )
                    _uiState.value = _uiState.value.copy(saving = false)
                    eventChannel.send(AddProjectEvent.Saved(id))
                }
                .onFailure { throwable ->
                    if (throwable is DuplicatePackageNameException) {
                        val message = validator.packageNameOccupied()
                        _uiState.value = _uiState.value.copy(
                            saving = false,
                            packageNameConflict = true,
                            errors = _uiState.value.errors + (AddField.PACKAGE_NAME to message),
                        )
                        MmaLogger.error("project_save_failed", throwable)
                        eventChannel.send(AddProjectEvent.SaveFailed(message.text))
                    } else {
                        val message = throwable.message ?: SAVE_FAILED_FALLBACK
                        _uiState.value = _uiState.value.copy(saving = false)
                        MmaLogger.error("project_save_failed", throwable)
                        eventChannel.send(AddProjectEvent.SaveFailed(message))
                    }
                }
        }
    }

    // ── 内部 ───────────────────────────────────────────────────────────

    /**
     * 构造待保存的项目对象；输入不完整时返回 null。
     *
     * `isolationId` 留空——由仓储在拿到 id 后回填（§2.6）。
     */
    private fun buildProject(state: AddProjectUiState): Project? {
        val type = state.projectType ?: return null
        val entry = buildEntry(state, type) ?: return null
        val versionCode = state.versionCode.trim().toIntOrNull() ?: return null
        return Project(
            id = 0,
            projectType = type,
            apkName = state.apkName.trim(),
            packageName = state.packageName.trim(),
            versionScheme = state.versionScheme,
            versionName = state.versionName.trim(),
            versionCode = versionCode,
            iconRef = state.iconRef,
            themeColor = state.themeColor,
            entry = entry,
            webSettings = ProjectDefaults.DEFAULT_WEB_SETTINGS,
            isolationId = "",
            createdAt = 0L,
            updatedAt = 0L,
        )
    }

    /** 按项目类型构造入口引用；缺必要输入时返回 null。 */
    private fun buildEntry(state: AddProjectUiState, type: ProjectType): ProjectEntry? = when (type) {
        ProjectType.URL -> state.urlInput.trim().takeIf { it.isNotBlank() }?.let(ProjectEntry::Url)
        ProjectType.HTML -> state.htmlInput.trim().takeIf { it.isNotBlank() }?.let(ProjectEntry::BundledHtml)
        ProjectType.VUE, ProjectType.PHP ->
            state.archivePath?.trim()?.takeIf { it.isNotBlank() }
                ?.let { path -> ProjectEntry.Archive(assetRef = path, kind = state.archiveKind) }
    }

    /** 重算自动建议；用户手动改过的字段不再被自动覆盖。 */
    private fun refreshSuggestions() {
        val state = _uiState.value
        val type = state.projectType
        val apkNameSuggestion = validator.suggestApkName(type, state.urlInput, state.htmlInput, state.archivePath)
        val packageNameSuggestion = validator.suggestPackageName(type, state.urlInput)
        val nextApkName = if (!state.apkNameEdited) apkNameSuggestion ?: state.apkName else state.apkName
        val nextPackageName =
            if (!state.packageNameEdited) packageNameSuggestion ?: state.packageName else state.packageName
        _uiState.value = _uiState.value.copy(
            apkNameSuggestion = apkNameSuggestion,
            packageNameSuggestion = packageNameSuggestion,
            apkName = nextApkName,
            packageName = nextPackageName,
            // 包名变了，旧的占用结论即失效
            packageNameConflict = if (nextPackageName == state.packageName) {
                state.packageNameConflict
            } else {
                false
            },
        )
        // 自动跟随的包名同样需要查占用（否则用户根本没碰过包名字段，也会带着冲突保存）
        if (nextPackageName != state.packageName) {
            schedulePackageNameCheck(nextPackageName)
        }
    }

    /** 同步重算本地可判定规则（包名占用除外，那是查库事实）。 */
    private fun revalidate() {
        val state = _uiState.value
        val errors = mutableMapOf<AddField, FieldMessage>()
        val warnings = mutableMapOf<AddField, FieldMessage>()

        validator.validateProjectType(state.projectType).error?.let {
            errors[AddField.PROJECT_TYPE] = it
        }

        val nameVerdict = validator.validateApkName(state.apkName)
        nameVerdict.error?.let { errors[AddField.APK_NAME] = it }
        nameVerdict.warning?.let { warnings[AddField.APK_NAME] = it }

        val packageVerdict = validator.validatePackageName(state.packageName)
        packageVerdict.error?.let { errors[AddField.PACKAGE_NAME] = it }
        packageVerdict.warning?.let { warnings[AddField.PACKAGE_NAME] = it }
        // 占用提示优先于格式提示（用户改包名时，占用才是当下最要紧的事）
        if (state.packageNameConflict) {
            errors[AddField.PACKAGE_NAME] = validator.packageNameOccupied()
        }

        val versionNameVerdict = validator.validateVersionName(state.versionName, state.versionScheme)
        versionNameVerdict.error?.let { errors[AddField.VERSION_NAME] = it }
        versionNameVerdict.warning?.let { warnings[AddField.VERSION_NAME] = it }

        val versionCodeVerdict = validator.validateVersionCode(state.versionCode)
        versionCodeVerdict.error?.let { errors[AddField.VERSION_CODE] = it }
        versionCodeVerdict.warning?.let { warnings[AddField.VERSION_CODE] = it }

        val iconVerdict = validator.validateIconRef(state.iconRef)
        iconVerdict.error?.let { errors[AddField.ICON] = it }
        iconVerdict.warning?.let { warnings[AddField.ICON] = it }

        when (state.projectType) {
            ProjectType.URL -> validator.validateEntryUrl(state.urlInput)
                .error?.let { errors[AddField.ENTRY_URL] = it }
            ProjectType.HTML -> validator.validateEntryHtml(state.htmlInput)
                .error?.let { errors[AddField.ENTRY_HTML] = it }
            ProjectType.VUE, ProjectType.PHP -> {
                val probeResult = state.archivePath
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { archiveProbe.probe(it) }
                validator.validateEntryArchive(state.archivePath, probeResult)
                    .error?.let { errors[AddField.ENTRY_ARCHIVE] = it }
            }
            null -> Unit
        }

        _uiState.value = _uiState.value.copy(errors = errors, warnings = warnings)
    }

    /**
     * 防抖查询包名占用。
     *
     * 只在包名**格式合法**时才查库——格式非法的包名不可能与库中记录相同，
     * 查了也是白查（还平白增加 IO）。
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
            val occupied = runCatching { directory.getByPackageName(candidate) != null }
                .onFailure { MmaLogger.error("package_name_check_failed", it) }
                .getOrDefault(false)
            _uiState.value = _uiState.value.copy(packageNameConflict = occupied)
            revalidate()
        }
    }

    companion object {
        /** 包名占用查询防抖时长（毫秒）。 */
        const val PACKAGE_NAME_CHECK_DEBOUNCE_MS: Long = 300L

        /** 异常未带文案时的兜底提示（人类可读，不直接把异常类名抛给用户）。 */
        private const val SAVE_FAILED_FALLBACK: String = "保存失败，请稍后再试"
    }
}

/**
 * 手工 DI 工厂（本项目未引入 Hilt，保持轻量）。
 *
 * 传入仓储即可：目录适配器、校验器、探测器都有默认实现。
 */
class AddProjectViewModelFactory(
    private val repository: ProjectRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        AddProjectViewModel(
            directory = RepositoryProjectDirectory(repository),
            validator = ProjectValidator(),
            archiveProbe = FileArchiveProbe(),
        ) as T
}
