package com.make.apk.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.make.apk.data.model.ProjectFile
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.ProgressPosition
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.VersionScheme
import com.make.apk.ui.components.MmaScreen
import com.make.apk.ui.components.MmaTextButton
import com.make.apk.ui.editor.EditField
import com.make.apk.ui.editor.EditIdentityCard
import com.make.apk.ui.editor.EditTabRow
import com.make.apk.ui.editor.FieldMessage
import com.make.apk.ui.editor.HighSettingsTab
import com.make.apk.ui.editor.LoadingProgressCard
import com.make.apk.ui.editor.ProjectCodeTab
import com.make.apk.ui.editor.ShellEditorTab
import com.make.apk.ui.editor.WebSettingsTab
import com.make.apk.ui.fs.PasteRequest
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.icons.MmaIcons
import com.make.apk.ui.theme.Spacing
import com.make.apk.ui.viewmodel.EditTab
import com.make.apk.ui.viewmodel.ProjectEditUiState

/**
 * 项目编辑页（`project/{id}`，§4.2 / §10）——**页面无状态**（状态进、事件出）。
 *
 * 布局：外层 `Column(fillMaxSize)`，上 2/5 身份卡（可收起）+ 下 3/5 三 Tab。
 * 作为流程子页，有返回箭头、**不传底栏**（用 [MmaScreen]）。
 *
 * 跨文件共享约定（对齐 [AddProjectScreen]）：
 * - **零内部业务状态**：只有 `rememberScrollState()` 与各 Tab 内的局部交互态（如自定义 UA 草稿、
 *   删除确认弹窗可见性），因此 Paparazzi 可直接喂任意 [ProjectEditUiState] 出图，无需 VM / 数据库；
 * - **零校验判断**：所有提示都从 `state.errors / warnings` 按 [EditField] 取，文案唯一来源是
 *   `ProjectValidator`，本页面不拼任何校验语句；
 * - 收起开关放在**顶栏**（与「保存」同处 actions），保证整卡收起后用户仍能一键展开——
 *   避免把开关塞进身份卡导致收起后无法再展开。
 *
 * @param state 完整编辑状态（VM 推送）。
 * @param onToggleCollapse 收起/展开切换（`!state.collapsed`）。
 * @param onPreview 顶栏「预览」入口（进入壳预览 `project/{id}/run`）；
 *   为 null 时**不显示**该按钮——既有截图基线直接渲染本函数且不传该回调，故基线画面零漂移。
 */
@Composable
fun ProjectEditScreen(
    state: ProjectEditUiState,
    onBack: () -> Unit,
    onApkNameChanged: (String) -> Unit,
    onPackageNameChanged: (String) -> Unit,
    onVersionSchemeChanged: (VersionScheme) -> Unit,
    onVersionNameChanged: (String) -> Unit,
    onVersionCodeChanged: (String) -> Unit,
    onEntryUrlChanged: (String) -> Unit,
    // 注：入口 HTML 的内容编辑已统一收敛到「项目代码 → 入口文件 → 编辑器页」
    // （`BundledHtml.withFile` 对入口文件名写回 `html`），本页不再承载 HTML 编辑区。
    onEntryFileChanged: (String) -> Unit,
    onThemeColorChanged: (Int) -> Unit,
    onPickIcon: (() -> Unit)? = null,
    onShellSpecChanged: (ShellSpec) -> Unit,
    onJavascriptEnabledChanged: (Boolean) -> Unit,
    onDomStorageEnabledChanged: (Boolean) -> Unit,
    onFitToAppChanged: (Boolean) -> Unit,
    onThirdPartyCookiesChanged: (Boolean) -> Unit,
    onCacheModeChanged: (Int) -> Unit,
    onUserAgentChanged: (String?) -> Unit,
    onIgnoreSslErrorsChanged: (Boolean) -> Unit,
    onShowLoadingProgressChanged: (Boolean) -> Unit = {},
    onLoadingProgressPositionChanged: (ProgressPosition) -> Unit = {},
    /** 打开文件（rc11：文件目录树 → 独立编辑器页；NavHost 接线导航）。 */
    onOpenFile: (String) -> Unit = {},
    /** 新增附加文件（文件目录树）。 */
    onFileAdded: (String) -> Unit = {},
    /** 删除附加文件（文件目录树）。 */
    onFileDeleted: (String) -> Unit = {},
    /** 新建文件夹（文件目录树；空文件夹约定，`name/` 空条目落库）。 */
    onFolderAdded: (String) -> Unit = {},
    /** 重命名 / 移动文件或目录（前缀替换，由 ViewModel 落库）。 */
    onFileRenamed: (String, String) -> Unit = { _, _ -> },
    /** 粘贴（复制 / 剪切落库）。 */
    onFilesPasted: (PasteRequest) -> Unit = {},
    onToggleCollapse: (Boolean) -> Unit,
    onTabSelected: (EditTab) -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onPreview: (() -> Unit)? = null,
) {
    MmaScreen(
        title = "项目编辑",
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (onPreview != null) {
                IconButton(onClick = onPreview) {
                    MmaIcon(
                        iconRes = MmaIcons.Play,
                        contentDescription = "预览注入壳",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            IconButton(onClick = { onToggleCollapse(!state.collapsed) }) {
                MmaIcon(
                    iconRes = MmaIcons.Collapse,
                    contentDescription = if (state.collapsed) "展开基本信息" else "收起基本信息",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.graphicsLayer {
                        rotationZ = if (state.collapsed) 0f else 180f
                    },
                )
            }
            MmaTextButton(
                label = "保存",
                onClick = onSave,
                enabled = state.canSave,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 顶部身份卡（可收起）：AnimatedVisibility 收起后高度归零，底部 weight(1f) 顶满。
            AnimatedVisibility(
                visible = !state.collapsed,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Box(modifier = Modifier.padding(horizontal = Spacing.space5, vertical = Spacing.space2)) {
                    EditIdentityCard(
                        apkName = state.apkName,
                        packageName = state.packageName,
                        versionScheme = state.versionScheme,
                        versionName = state.versionName,
                        versionCode = state.versionCode,
                        projectType = state.projectType,
                        entryRef = state.entryRef,
                        entryUrl = state.entryUrl,
                        entryFile = state.entryFile,
                        entryUrlMessage = messageOf(state, EditField.ENTRY_URL),
                        entryFileMessage = messageOf(state, EditField.ENTRY_FILE),
                        themeColor = state.themeColor,
                        iconRef = state.iconRef,
                        onPickIcon = onPickIcon,
                        apkNameMessage = messageOf(state, EditField.APK_NAME),
                        packageNameMessage = messageOf(state, EditField.PACKAGE_NAME),
                        versionNameMessage = messageOf(state, EditField.VERSION_NAME),
                        versionCodeMessage = messageOf(state, EditField.VERSION_CODE),
                        onApkNameChanged = onApkNameChanged,
                        onPackageNameChanged = onPackageNameChanged,
                        onVersionSchemeChanged = onVersionSchemeChanged,
                        onVersionNameChanged = onVersionNameChanged,
                        onVersionCodeChanged = onVersionCodeChanged,
                        onEntryUrlChanged = onEntryUrlChanged,
                        onEntryFileChanged = onEntryFileChanged,
                    )
                }
            }

            // 底部 Tab 区：TabRow 按项目类型过滤（HTML 多一个「项目代码」页），内容区可滚动。
            Column(modifier = Modifier.weight(1f)) {
                EditTabRow(
                    selectedTab = state.selectedTab,
                    onTabSelected = onTabSelected,
                    projectType = state.projectType,
                    modifier = Modifier.fillMaxWidth(),
                )
                when (state.selectedTab) {
                    // 每个 Tab 内容包一层滚动 Box（weight(1f) 撑满区域高度），
                    // 内层卡片只按内容高度排布（fillMaxWidth），避免出现"内容顶对齐、底部大片留白"。
                    EditTab.BASIC -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        WebSettingsTab(
                            webSettings = state.webSettings,
                            themeColor = state.themeColor,
                            onJavascriptEnabledChanged = onJavascriptEnabledChanged,
                            onDomStorageEnabledChanged = onDomStorageEnabledChanged,
                            onFitToAppChanged = onFitToAppChanged,
                            onThirdPartyCookiesChanged = onThirdPartyCookiesChanged,
                            onCacheModeChanged = onCacheModeChanged,
                            onUserAgentChanged = onUserAgentChanged,
                            onIgnoreSslErrorsChanged = onIgnoreSslErrorsChanged,
                            onThemeColorChanged = onThemeColorChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.space5, vertical = Spacing.space4),
                        )
                    }

                    // 「项目代码」：HTML 类专属（EditTabRow 已按类型过滤，此处防御兜底）。
                    // rc11：只显示文件目录树，点击文件进入独立编辑器页（onOpenFile 由 NavHost 接线）。
                    EditTab.CODE -> if (state.projectType == ProjectType.HTML) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            ProjectCodeTab(
                                entryFile = state.entryFile.trim().ifBlank { "index.html" },
                                // 入口 HTML 与附加文件一起构成目录树（入口带「入口」徽标）。
                                files = listOf(
                                    ProjectFile(
                                        name = state.entryFile.trim().ifBlank { "index.html" },
                                        content = state.entryHtml,
                                    ),
                                ) + state.projectFiles,
                                onOpenFile = onOpenFile,
                                onFileAdded = onFileAdded,
                                onFolderAdded = onFolderAdded,
                                onFileRenamed = onFileRenamed,
                                onFileDeleted = onFileDeleted,
                                onFilesPasted = onFilesPasted,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.space5, vertical = Spacing.space4),
                            )
                        }
                    }

                    EditTab.TEMPLATE -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ShellEditorTab(
                                shellSpec = state.shellSpec,
                                // 项目内页面文件（壳条目「本地页面」的候选；URL 项目为空集）。
                                localPageFileNames = state.localPageFileNames,
                                onShellSpecChanged = onShellSpecChanged,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.space5, vertical = Spacing.space4),
                            )
                            // 功能加载进度条：rc11 起挂在进阶配置（壳的可见部件，与壳同屏配置）。
                            LoadingProgressCard(
                                showLoadingProgress = state.webSettings.showLoadingProgress,
                                loadingProgressPosition = state.webSettings.loadingProgressPosition,
                                onShowLoadingProgressChanged = onShowLoadingProgressChanged,
                                onLoadingProgressPositionChanged = onLoadingProgressPositionChanged,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.space5, vertical = Spacing.space4),
                            )
                        }
                    }

                    EditTab.EXPORT -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        HighSettingsTab(
                            apkName = state.apkName,
                            engineLabel = state.engineLabel,
                            isolationId = state.isolationId,
                            onDelete = onDelete,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.space5, vertical = Spacing.space4),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 取字段提示：**错误优先于警告**（同一字段同时挂两条时，先让人看见阻断项）。
 */
private fun messageOf(state: ProjectEditUiState, field: EditField): FieldMessage? =
    state.errors[field] ?: state.warnings[field]
