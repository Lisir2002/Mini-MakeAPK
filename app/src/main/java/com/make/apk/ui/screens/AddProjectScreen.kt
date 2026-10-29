package com.make.apk.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.make.apk.data.model.ProjectDefaults
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.VersionScheme
import com.make.apk.ui.components.MmaChip
import com.make.apk.ui.components.MmaDialog
import com.make.apk.ui.components.MmaScreen
import com.make.apk.ui.components.MmaTextButton
import com.make.apk.ui.editor.AddField
import com.make.apk.ui.editor.FieldMessage
import com.make.apk.ui.editor.FieldMessageText
import com.make.apk.ui.editor.ProjectIcon
import com.make.apk.ui.editor.ProjectSourceSection
import com.make.apk.ui.editor.ProjectTextField
import com.make.apk.ui.editor.ProjectTypeSelector
import com.make.apk.ui.editor.SectionCard
import com.make.apk.ui.editor.ThemeColorSwatches
import com.make.apk.ui.editor.VersionSchemeChips
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.icons.MmaIcons
import com.make.apk.ui.theme.Spacing
import com.make.apk.ui.viewmodel.AddProjectUiState

/**
 * 新建项目页（`add`，§4.1 / §9）——**表单无状态**（状态进、事件出）。
 *
 * 布局：准星选择器 → 来源输入区 → 基本信息表单（整页可滚动）。
 *
 * 跨文件共享约定：
 * - **零内部表单状态**：只有滚动状态与「放弃确认」对话框的纯 UI 状态，
 *   因此 Paparazzi 可直接喂任意 [AddProjectUiState] 出图，无需 ViewModel / 数据库 / 导航；
 * - **零校验判断**：所有提示都从 `state.errors / warnings` 按 [AddField] 取，
 *   文案唯一来源是 `ProjectValidator`，本页面不拼任何校验语句；
 * - 顶栏右侧「保存」是**唯一**保存入口（§4.1 只定义顶栏保存，不另加底部按钮），
 *   `saving` 时按钮内显示进度圈防重复提交。
 * - **放弃修改确认**：`dirty` 时（无论系统返回还是顶栏返回箭头）先弹确认，
 *   确认后才真正退出——表单填了一半被静默丢弃是不可接受的。
 *
 * 流程子页：有返回箭头、**不传 bottomBar**。
 */
@Composable
fun AddProjectScreen(
    state: AddProjectUiState,
    onBack: () -> Unit,
    onProjectTypeSelected: (ProjectType) -> Unit,
    onUrlChanged: (String) -> Unit,
    onEntryFileChanged: (String) -> Unit,
    onApkNameChanged: (String) -> Unit,
    onPackageNameChanged: (String) -> Unit,
    onVersionSchemeChanged: (VersionScheme) -> Unit,
    onVersionNameChanged: (String) -> Unit,
    onVersionCodeChanged: (String) -> Unit,
    onApplySuggestionApkName: () -> Unit,
    onApplySuggestionPackageName: () -> Unit,
    onThemeColorChanged: (Int) -> Unit,
    onPickIcon: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    /** 预览入口（保存成功后提供，进入壳预览 `project/{id}/run`）；null 时不显示。 */
    onPreview: ((Long) -> Unit)? = null,
) {
    // 「放弃修改？」确认框：纯 UI 状态（不进 UiState——它不属于表单数据）。
    var showDiscardDialog by remember { mutableStateOf(false) }

    // 确认退出动作：对话框与系统返回/顶栏返回共用。
    val requestBack: () -> Unit = { if (state.dirty) showDiscardDialog = true else onBack() }

    // 系统返回手势/按键同样走确认（dirty 才拦截）。
    BackHandler(enabled = state.dirty) { showDiscardDialog = true }

    MmaScreen(
        title = "新建项目",
        onBack = requestBack,
        modifier = modifier,
        actions = {
            // 保存成功后留驻编辑页：给出一键预览入口（§优化：保存不再强制退出页面）。
            if (state.savedProjectId != null && onPreview != null) {
                IconButton(onClick = { onPreview(state.savedProjectId) }) {
                    MmaIcon(
                        iconRes = MmaIcons.Play,
                        contentDescription = "预览注入壳",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            // 自绘文字按钮：原生 TextButton 的内边距与涟漪与自绘顶栏不同皮肤。
            MmaTextButton(
                label = "保存",
                onClick = onSave,
                enabled = state.canSave,
            ) { contentColor ->
                if (state.saving) {
                    // 保存中：进度圈替代文字（同一槽位，避免双击重复提交）
                    CircularProgressIndicator(
                        modifier = Modifier.size(SAVE_PROGRESS_SIZE),
                        strokeWidth = SAVE_PROGRESS_STROKE,
                        color = contentColor,
                    )
                } else {
                    Text("保存", color = contentColor)
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.space5),
            verticalArrangement = Arrangement.spacedBy(Spacing.space5),
        ) {
            Spacer(modifier = Modifier.height(Spacing.space2))

            // ── 项目类型（准星选择器，保存后锁死）──────────────────────
            ProjectTypeSelector(
                selected = state.projectType,
                onSelected = onProjectTypeSelected,
                modifier = Modifier.fillMaxWidth(),
            )
            // 类型提示：真实页面首帧即回传 URL，故恒为空；保留展示以覆盖"未确认"态。
            messageOf(state, AddField.PROJECT_TYPE)?.let { message ->
                FieldMessageText(message = message)
            }

            // ── 来源（形态由类型决定，§9.3）───────────────────────────
            SectionCard(title = "项目来源") {
                ProjectSourceSection(
                    projectType = state.projectType,
                    urlInput = state.urlInput,
                    entryFileInput = state.entryFileInput,
                    urlMessage = messageOf(state, AddField.ENTRY_URL),
                    entryFileMessage = messageOf(state, AddField.ENTRY_FILE),
                    // 占位类型（VUE/PHP）没有输入框，归档错误挂在占位卡底部——先有展示位，
                    // 后续接上 zip 选择器时错误才不会"无处可见"。
                    archiveMessage = messageOf(state, AddField.ENTRY_ARCHIVE),
                    onUrlChanged = onUrlChanged,
                    onEntryFileChanged = onEntryFileChanged,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── 基本信息（§4.1 第 2 项）──────────────────────────────
            SectionCard(title = "基本信息") {
                // 身份区：图标 + 图标说明（点击可换）+ 主题色板
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProjectIcon(
                        apkName = state.apkName,
                        themeColor = state.themeColor,
                        iconRef = state.iconRef,
                        onClick = onPickIcon,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space1)) {
                        Text(
                            text = "应用图标",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (state.iconRef == null) ICON_CUSTOMIZE_HINT else ICON_CHANGE_HINT,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                MmaTextButton(
                    label = ICON_PICK_LABEL,
                    onClick = onPickIcon,
                )
                messageOf(state, AddField.ICON)?.let { message ->
                    FieldMessageText(message = message)
                }

                // 主题色：占位图标底色 + 导出壳的品牌色，新建时即可确定
                Text(
                    text = "主题色",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ThemeColorSwatches(
                    selected = state.themeColor,
                    onSelected = onThemeColorChanged,
                    modifier = Modifier.fillMaxWidth(),
                )

                ProjectTextField(
                    value = state.apkName,
                    onValueChange = onApkNameChanged,
                    label = APK_NAME_LABEL,
                    placeholder = APK_NAME_PLACEHOLDER,
                    message = messageOf(state, AddField.APK_NAME),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.apkNameSuggestion?.let { suggestion ->
                    SuggestionChipRow(
                        label = APK_NAME_LABEL,
                        suggestion = suggestion,
                        onApply = onApplySuggestionApkName,
                    )
                }

                ProjectTextField(
                    value = state.packageName,
                    onValueChange = onPackageNameChanged,
                    label = PACKAGE_NAME_LABEL,
                    placeholder = PACKAGE_NAME_PLACEHOLDER,
                    message = messageOf(state, AddField.PACKAGE_NAME),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.packageNameSuggestion?.let { suggestion ->
                    SuggestionChipRow(
                        label = PACKAGE_NAME_LABEL,
                        suggestion = suggestion,
                        onApply = onApplySuggestionPackageName,
                    )
                }

                VersionSchemeChips(
                    scheme = state.versionScheme,
                    onSchemeChanged = onVersionSchemeChanged,
                    modifier = Modifier.fillMaxWidth(),
                )

                ProjectTextField(
                    value = state.versionName,
                    onValueChange = onVersionNameChanged,
                    label = VERSION_NAME_LABEL,
                    placeholder = ProjectDefaults.defaultVersionName(state.versionScheme),
                    message = messageOf(state, AddField.VERSION_NAME),
                    modifier = Modifier.fillMaxWidth(),
                )

                ProjectTextField(
                    value = state.versionCode,
                    onValueChange = onVersionCodeChanged,
                    label = VERSION_CODE_LABEL,
                    placeholder = ProjectDefaults.DEFAULT_VERSION_CODE.toString(),
                    message = messageOf(state, AddField.VERSION_CODE),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(Spacing.space6))
        }
    }

    if (showDiscardDialog) {
        DiscardChangesDialog(
            onDismiss = { showDiscardDialog = false },
            onConfirm = {
                showDiscardDialog = false
                onBack()
            },
        )
    }
}

/**
 * 字段建议采纳行：**自动建议已算出但用户未采用时**，给一键采纳入口。
 *
 * 数据链路由 ViewModel 维护（`applySuggestionApkName` / `applySuggestionPackageName`），
 * 本组件只负责展示与转发——建议与当前值相同时不展示（没有可采纳的差异）。
 */
@Composable
private fun SuggestionChipRow(
    label: String,
    suggestion: String,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestion.isBlank()) return
    MmaChip(
        label = "采用${label}建议：$suggestion",
        selected = false,
        onClick = onApply,
        leadingIcon = MmaIcons.CheckCircle,
        modifier = modifier,
    )
}

/**
 * 「放弃修改？」确认对话框（返回意图唯一确认出口，文案不与校验器耦合）。
 *
 * 走自绘 [MmaDialog]：原生 `AlertDialog` 的标题 / 正文 / 按钮排版与全站弹窗不同皮肤，
 * 且按钮强制为 `TextButton`（Material 涟漪 + 内边距）。
 */
@Composable
private fun DiscardChangesDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    MmaDialog(
        onDismissRequest = onDismiss,
        title = DISCARD_TITLE,
        confirmLabel = DISCARD_CONFIRM,
        onConfirm = onConfirm,
        destructive = true,
        content = {
            Text(
                text = DISCARD_BODY,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/**
 * 取字段提示：**错误优先于警告**（同一字段同时挂两条时，先让人看见阻断项）。
 */
private fun messageOf(state: AddProjectUiState, field: AddField): FieldMessage? =
    state.errors[field] ?: state.warnings[field]

/** 图标选择按钮文案。 */
private const val ICON_PICK_LABEL: String = "选择图标"

/** 放弃确认弹窗标题。 */
private const val DISCARD_TITLE: String = "放弃新建项目？"

/** 放弃确认主操作文案（危险操作，按钮走 error 色）。 */
private const val DISCARD_CONFIRM: String = "放弃"

/** 放弃确认正文。 */
private const val DISCARD_BODY: String = "已填写的内容不会被保存。"

/** 应用名称输入框标签。 */
private const val APK_NAME_LABEL: String = "应用名称"

/** 应用名称输入框占位提示。 */
private const val APK_NAME_PLACEHOLDER: String = "给你的 App 起个名字"

/** 包名输入框标签。 */
private const val PACKAGE_NAME_LABEL: String = "包名"

/** 包名输入框占位提示（示例取自校验器接受的合法格式）。 */
private const val PACKAGE_NAME_PLACEHOLDER: String = "com.example.app"

/** 展示版本号输入框标签。 */
private const val VERSION_NAME_LABEL: String = "版本号"

/** 内部版本号输入框标签。 */
private const val VERSION_CODE_LABEL: String = "内部版本号"

/** 未设置图标时的引导文案。 */
private const val ICON_CUSTOMIZE_HINT: String = "点击图标或下方按钮选择"

/** 已设置图标时的更换文案。 */
private const val ICON_CHANGE_HINT: String = "点击更换"

/** 保存中进度圈尺寸（按钮内行内尺寸，经门禁白名单 16dp）。 */
private val SAVE_PROGRESS_SIZE = 16.dp

/** 保存中进度圈描边宽。 */
private val SAVE_PROGRESS_STROKE = 2.dp
