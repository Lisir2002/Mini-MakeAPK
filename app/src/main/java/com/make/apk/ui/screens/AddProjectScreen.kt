package com.make.apk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.make.apk.data.model.ProjectDefaults
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.VersionScheme
import com.make.apk.ui.components.MmaScreen
import com.make.apk.ui.editor.AddField
import com.make.apk.ui.editor.FieldMessage
import com.make.apk.ui.editor.FieldMessageText
import com.make.apk.ui.editor.ProjectIcon
import com.make.apk.ui.editor.ProjectSourceSection
import com.make.apk.ui.editor.ProjectTextField
import com.make.apk.ui.editor.ProjectTypeSelector
import com.make.apk.ui.editor.SectionCard
import com.make.apk.ui.editor.VersionSchemeChips
import com.make.apk.ui.theme.Spacing
import com.make.apk.ui.viewmodel.AddProjectUiState

/**
 * 新建项目页（`add`，§4.1 / §9）——**页面无状态**（状态进、事件出）。
 *
 * 布局：准星选择器 → 来源输入区 → 基本信息表单（整页可滚动）。
 *
 * 跨文件共享约定：
 * - **零内部业务状态**：只有 `rememberScrollState()`，因此 Paparazzi 可直接喂任意
 *   [AddProjectUiState] 出图，无需 ViewModel / 数据库 / 导航；
 * - **零校验判断**：所有提示都从 `state.errors / warnings` 按 [AddField] 取，
 *   文案唯一来源是 `ProjectValidator`，本页面不拼任何校验语句；
 * - 顶栏右侧「保存」是**唯一**保存入口（§4.1 只定义顶栏保存，不另加底部按钮）。
 *
 * 流程子页：有返回箭头、**不传 bottomBar**。
 */
@Composable
fun AddProjectScreen(
    state: AddProjectUiState,
    onBack: () -> Unit,
    onProjectTypeSelected: (ProjectType) -> Unit,
    onUrlChanged: (String) -> Unit,
    onHtmlChanged: (String) -> Unit,
    onApkNameChanged: (String) -> Unit,
    onPackageNameChanged: (String) -> Unit,
    onVersionSchemeChanged: (VersionScheme) -> Unit,
    onVersionNameChanged: (String) -> Unit,
    onVersionCodeChanged: (String) -> Unit,
    onPickIcon: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MmaScreen(
        title = "新建项目",
        onBack = onBack,
        modifier = modifier,
        actions = {
            TextButton(
                onClick = onSave,
                enabled = state.canSave,
            ) {
                Text("保存")
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
                    htmlInput = state.htmlInput,
                    urlMessage = messageOf(state, AddField.ENTRY_URL),
                    htmlMessage = messageOf(state, AddField.ENTRY_HTML),
                    // 占位类型（VUE/PHP）没有输入框，归档错误挂在占位卡底部——先有展示位，
                    // 后续接上 zip 选择器时错误才不会"无处可见"。
                    archiveMessage = messageOf(state, AddField.ENTRY_ARCHIVE),
                    onUrlChanged = onUrlChanged,
                    onHtmlChanged = onHtmlChanged,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── 基本信息（§4.1 第 2 项）──────────────────────────────
            SectionCard(title = "基本信息") {
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
                    TextButton(onClick = onPickIcon) {
                        Text("选择图标")
                    }
                }
                messageOf(state, AddField.ICON)?.let { message ->
                    FieldMessageText(message = message)
                }

                ProjectTextField(
                    value = state.apkName,
                    onValueChange = onApkNameChanged,
                    label = APK_NAME_LABEL,
                    placeholder = APK_NAME_PLACEHOLDER,
                    message = messageOf(state, AddField.APK_NAME),
                    modifier = Modifier.fillMaxWidth(),
                )

                ProjectTextField(
                    value = state.packageName,
                    onValueChange = onPackageNameChanged,
                    label = PACKAGE_NAME_LABEL,
                    placeholder = PACKAGE_NAME_PLACEHOLDER,
                    message = messageOf(state, AddField.PACKAGE_NAME),
                    modifier = Modifier.fillMaxWidth(),
                )

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
}

/**
 * 取字段提示：**错误优先于警告**（同一字段同时挂两条时，先让人看见阻断项）。
 */
private fun messageOf(state: AddProjectUiState, field: AddField): FieldMessage? =
    state.errors[field] ?: state.warnings[field]

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
