package com.make.apk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ShellAction
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.data.model.ShellPresets
import com.make.apk.data.model.ShellSpec
import com.make.apk.logging.MmaLogger
import com.make.apk.ui.editor.ProjectDirectory
import com.make.apk.ui.editor.RepositoryProjectDirectory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 壳预览 ViewModel（阶段 6.2 · 预览侧）。
 *
 * ## 职责边界
 *
 * - **加载项目**：从 [ProjectDirectory] 观察项目（读 URL 入口 + 主题色 + 项目名）；
 * - **切换预置子模板**：只改 [ShellPreviewUiState.shellSpec]，**不动** [ShellPreviewUiState.runtimeConfig]
 *   ——这正是"切换壳不重载 WebView"的实现基础（内容运行时与壳形态彻底解耦）；
 * - **执行壳动作**：顶栏动作 / 顶栏 tab / 底栏条目的 [ShellAction.NavigateUrl]
 *   落到"内容区加载目标 URL"。
 *
 * ## 无障碍与可解释性
 *
 * 预置模板的 tab / 底栏目标地址是**示例地址**（`example.com` 保留域），
 * 点击后内容区会跳到示例页——此时状态里 [ShellPreviewUiState.isExampleContent] 置 true，
 * 由页面显式标注"示例地址"，不让用户误以为是自己的站点。
 *
 * @param projectId 预览的项目 id。
 * @param directory 项目数据出口（窄接口，便于 JVM 单测）。
 */
class ShellPreviewViewModel(
    private val projectId: Long,
    private val directory: ProjectDirectory,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShellPreviewUiState())
    val uiState: StateFlow<ShellPreviewUiState> = _uiState.asStateFlow()

    /** 一次性事件通道（缓冲、不重放）。 */
    private val eventChannel = Channel<ShellPreviewEvent>(Channel.BUFFERED)
    val events: Flow<ShellPreviewEvent> = eventChannel.receiveAsFlow()

    init {
        observeProject()
    }

    // ── 加载 ───────────────────────────────────────────────────────────

    /** 订阅项目流：取入口地址、主题色与项目名。 */
    private fun observeProject() {
        viewModelScope.launch {
            directory.observeById(projectId).collect { project ->
                if (project == null) {
                    if (_uiState.value.loading) {
                        _uiState.value = _uiState.value.copy(
                            loading = false,
                            projectNotFound = true,
                            previewable = false,
                            unsupportedMessage = null,
                        )
                    }
                    return@collect
                }
                applyProject(project)
            }
        }
    }

    /** 把项目落进状态：URL 项目可预览，其余类型给出可见提示。 */
    private fun applyProject(project: Project) {
        val entry = project.entry
        if (entry !is ProjectEntry.Url) {
            _uiState.value = _uiState.value.copy(
                loading = false,
                projectNotFound = false,
                previewable = false,
                apkName = project.apkName,
                themeColor = project.themeColor,
                runtimeConfig = null,
                unsupportedMessage = UNSUPPORTED_ENTRY_MESSAGE,
            )
            return
        }
        val base = ShellPresets.CLASSIC
        // 壳形态：项目已配置过壳就用项目那份（"预览 == 导出"的前提）；
        // 未配置（默认壳 = 四区全关）则回落到默认预置子模板「经典款」。
        val renderedSpec = if (project.shellSpec != ShellSpec.DEFAULT) project.shellSpec else base.spec
        // **同源不变式**：高亮的预置 id 由"实际渲染的壳"反查得到（不是另写一份），
        // 因此"高亮项 == 渲染项"恒成立——不允许出现"高亮经典款、渲染极简款"这类不一致。
        // 项目配置的是**自定义壳**（与任何预置都不逐字段相等）→ 不高亮任何预置（null），
        // 选择条据此显示"自定义"态。
        val highlightedPresetId = ShellPresets.matchingBySpec(renderedSpec)?.id
        _uiState.value = resetShellRuntime(
            _uiState.value.copy(
                loading = false,
                projectNotFound = false,
                previewable = true,
                apkName = project.apkName,
                themeColor = project.themeColor,
                runtimeConfig = project.toRuntimeConfig(),
                unsupportedMessage = null,
                currentUrl = entry.url,
                isExampleContent = isExampleUrl(entry.url),
                selectedPresetId = highlightedPresetId,
                shellSpec = renderedSpec,
            ),
        )
    }

    /**
     * 复位壳的**抽屉运行态**（[selectPreset] 与 [applyProject] 共用，杜绝两处各写各的）。
     *
     * 必须复位的原因：项目流是长流（Room 行一变就重新发射），新推送的 spec 可能
     * 已关掉抽屉区或删掉了条目；此时渲染层不再渲染抽屉 UI，用户**没有任何手段**再关抽屉
     * → 若不复位，[ShellPreviewUiState.contentPaused] 恒为 true，
     * 内容区 WebView 会永久停在 `onPause()`（表现为"内容永远不刷新、不响应"）。
     * 选中项 id 一并清空：条目被删后它是悬空引用。
     */
    private fun resetShellRuntime(state: ShellPreviewUiState): ShellPreviewUiState =
        state.copy(
            selectedDrawerItemId = null,
            drawerOpen = false,
            contentPaused = false,
        )

    // ── 预置子模板 ─────────────────────────────────────────────────────

    /**
     * 切换预置子模板。
     *
     * 只改壳契约，**不改** [ShellPreviewUiState.runtimeConfig] → WebView 不重载。
     * 选中态复位到"契约自带的默认选中"，避免上一个模板的条目 id 串味；
     * 抽屉运行态经 [resetShellRuntime] 一并复位（与 [applyProject] 共用同一份复位逻辑）。
     */
    fun selectPreset(presetId: String) {
        val preset = ShellPresets.byId(presetId)
        _uiState.value = resetShellRuntime(
            _uiState.value.copy(
                selectedPresetId = preset.id,
                shellSpec = preset.spec,
                selectedTopTabId = null,
                selectedBottomItemId = null,
            ),
        )
    }

    // ── 壳交互 ─────────────────────────────────────────────────────────

    /** 顶栏 tab 选中：更新选中态，并按动作加载目标地址。 */
    fun onTopTabSelected(item: ShellMenuItem) {
        _uiState.value = _uiState.value.copy(selectedTopTabId = item.id)
        applyAction(item.action)
    }

    /** 底栏条目选中：更新选中态，并按动作加载目标地址。 */
    fun onBottomItemSelected(item: ShellMenuItem) {
        _uiState.value = _uiState.value.copy(selectedBottomItemId = item.id)
        applyAction(item.action)
    }

    /** 顶栏动作按钮点击（刷新 / 分享 / …）。 */
    fun onShellAction(item: ShellMenuItem) {
        applyAction(item.action)
    }

    /** 抽屉菜单项点击：更新高亮项，并按动作加载目标地址（收起由渲染层统一回调 [onDrawerOpenChanged]）。 */
    fun onDrawerItemSelected(item: ShellMenuItem) {
        _uiState.value = _uiState.value.copy(selectedDrawerItemId = item.id)
        applyAction(item.action)
    }

    /**
     * 抽屉开合变化（手势展开 / 遮罩点击 / 菜单项点击后收起）。
     *
     * 顺带按契约 [com.make.apk.data.model.ShellDrawer.pauseContentWhileOpen] 计算
     * [ShellPreviewUiState.contentPaused]，由页面据此暂停/恢复内容区 WebView。
     */
    fun onDrawerOpenChanged(open: Boolean) {
        val pause = open && _uiState.value.shellSpec.drawer.pauseContentWhileOpen
        _uiState.value = _uiState.value.copy(drawerOpen = open, contentPaused = pause)
    }

    /**
     * 执行壳动作。
     *
     * 当前仅 [ShellAction.NavigateUrl] 可执行；其余类型（原生动作 / 内置页 / 未知）
     * 一律记日志并忽略——**不崩溃、不静默**：未知动作在预览里表现为"点了没反应"，
     * 属于契约已登记但尚未实现的类型。
     */
    private fun applyAction(action: ShellAction) {
        when (action) {
            is ShellAction.NavigateUrl -> navigateTo(action.url)
            is ShellAction.Native -> {
                MmaLogger.operation(
                    event = "shell_native_action_ignored",
                    message = "原生动作本阶段不执行：${action.kind}",
                )
            }
            is ShellAction.InternalPage -> {
                MmaLogger.operation(
                    event = "shell_internal_page_ignored",
                    message = "内置原生页本阶段不执行：${action.pageId}",
                )
            }
            ShellAction.Unknown -> {
                MmaLogger.operation(
                    event = "shell_unknown_action_ignored",
                    message = "未知壳动作，本阶段不执行",
                )
            }
        }
    }

    /** 内容区加载目标地址（空地址忽略）。 */
    private fun navigateTo(url: String) {
        val target = url.trim()
        if (target.isEmpty()) return
        val config = _uiState.value.runtimeConfig ?: return
        _uiState.value = _uiState.value.copy(
            runtimeConfig = config.copy(entry = ProjectEntry.Url(target)),
            currentUrl = target,
            isExampleContent = isExampleUrl(target),
        )
    }

    companion object {
        /**
         * 非 URL 项目的可见提示。
         *
         * 预览当前只支持 URL 项目：`BundledHtml` / `Archive` 的内容区需要真实的
         * 资产解包管线（阶段 5 导出阶段实现），主程序侧无法等价呈现。
         */
        const val UNSUPPORTED_ENTRY_MESSAGE: String = "当前仅支持 URL 项目预览"

        /** 示例地址判定（`example.com` / `example.org` / `example.net` 保留域）。 */
        fun isExampleUrl(url: String): Boolean {
            val normalized = url.trim().lowercase()
            return normalized.startsWith("https://example.com") ||
                normalized.startsWith("https://example.org") ||
                normalized.startsWith("https://example.net")
        }
    }
}

/**
 * 壳预览一次性事件（不重放）。
 */
sealed interface ShellPreviewEvent {

    /** 需要给用户一条可见提示（如示例地址提醒）。 */
    data class Message(val text: String) : ShellPreviewEvent
}

/**
 * 手工 DI 工厂（本项目未引入 Hilt，保持轻量）。
 */
class ShellPreviewViewModelFactory(
    private val projectId: Long,
    private val repository: com.make.apk.data.repository.ProjectRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ShellPreviewViewModel(
            projectId = projectId,
            directory = RepositoryProjectDirectory(repository),
        ) as T
}
