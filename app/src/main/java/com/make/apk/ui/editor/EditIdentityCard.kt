package com.make.apk.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.VersionScheme
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.components.MmaTextButton
import com.make.apk.ui.theme.Spacing

/**
 * 编辑页顶部身份卡（§4.2 / §10.4 D-T2）——**可就地编辑**。
 *
 * 可改：应用名 / 包名 / 版本段式 / 版本号 / 内部版本号 / 图标（占位）；
 * 入口按类型隔离可改（URL → 网址；HTML → 入口文件）。
 * 只读锁死：`projectType` 与存档入口（VUE/PHP）。
 *
 * 校验零判断：错误 / 警告文案由调用方按 [EditField] 从状态取好传入（`messageOf`），
 * 本组件只按 [FieldMessage.Severity] 选色，不自造任何校验语句。
 *
 * 收起开关不在本卡内——它放在顶栏（见 [com.make.apk.ui.screens.ProjectEditScreen]），
 * 保证整卡收起后用户仍能一键展开。
 *
 * @param apkNameMessage 等四个 message 为「错误优先于警告」后的提示，null 表示无。
 */
@Composable
fun EditIdentityCard(
    apkName: String,
    packageName: String,
    versionScheme: VersionScheme,
    versionName: String,
    versionCode: String,
    projectType: ProjectType?,
    entryRef: String,
    entryUrl: String,
    entryFile: String,
    entryUrlMessage: FieldMessage?,
    entryFileMessage: FieldMessage?,
    themeColor: Int,
    iconRef: String?,
    apkNameMessage: FieldMessage?,
    packageNameMessage: FieldMessage?,
    versionNameMessage: FieldMessage?,
    versionCodeMessage: FieldMessage?,
    onApkNameChanged: (String) -> Unit,
    onPackageNameChanged: (String) -> Unit,
    onVersionSchemeChanged: (VersionScheme) -> Unit,
    onVersionNameChanged: (String) -> Unit,
    onVersionCodeChanged: (String) -> Unit,
    onEntryUrlChanged: (String) -> Unit,
    onEntryFileChanged: (String) -> Unit,
    /** 选择/更换图标（用户上传自己的图片）；null = 不可点击。 */
    onPickIcon: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    CuteCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            // 图标 + 名称/包名（并排）；图标可点击上传用户自己的图片（非形状预览）
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProjectIcon(
                    apkName = apkName,
                    themeColor = themeColor,
                    iconRef = iconRef,
                    onClick = onPickIcon,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space2),
                ) {
                    ProjectTextField(
                        value = apkName,
                        onValueChange = onApkNameChanged,
                        label = APK_NAME_LABEL,
                        placeholder = APK_NAME_PLACEHOLDER,
                        message = apkNameMessage,
                    )
                    ProjectTextField(
                        value = packageName,
                        onValueChange = onPackageNameChanged,
                        label = PACKAGE_NAME_LABEL,
                        placeholder = PACKAGE_NAME_PLACEHOLDER,
                        message = packageNameMessage,
                    )
                }
            }

            // 应用图标：点击图标或下方按钮上传用户自己的图片（替换默认占位）。
            if (onPickIcon != null) {
                MmaTextButton(
                    label = if (iconRef == null) ICON_PICK_LABEL else ICON_CHANGE_LABEL,
                    onClick = onPickIcon,
                )
            }

            // 只读：项目类型（锁死）；资产包入口（VUE/PHP）因存档拾取尚未实现，亦只读。
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.space3),
            ) {
                LockedField(
                    label = TYPE_LABEL,
                    value = projectType?.let { displayLabelOf(it) } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                if (projectType == ProjectType.VUE || projectType == ProjectType.PHP) {
                    LockedField(
                        label = ENTRY_LABEL,
                        value = entryRef.ifBlank { "—" },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 入口（按类型隔离：URL 改网址；HTML 改入口文件——代码在「项目代码」页维护）。
            when (projectType) {
                ProjectType.URL -> ProjectTextField(
                    value = entryUrl,
                    onValueChange = onEntryUrlChanged,
                    label = ENTRY_URL_LABEL,
                    placeholder = ENTRY_URL_PLACEHOLDER,
                    message = entryUrlMessage,
                    keyboardType = KeyboardType.Uri,
                    modifier = Modifier.fillMaxWidth(),
                )
                ProjectType.HTML -> ProjectTextField(
                    value = entryFile,
                    onValueChange = onEntryFileChanged,
                    label = ENTRY_FILE_LABEL,
                    placeholder = ENTRY_FILE_PLACEHOLDER,
                    message = entryFileMessage,
                    hint = ENTRY_FILE_HINT,
                    modifier = Modifier.fillMaxWidth(),
                )
                ProjectType.VUE, ProjectType.PHP, null -> Unit
            }

            // 版本
            VersionSchemeChips(
                scheme = versionScheme,
                onSchemeChanged = onVersionSchemeChanged,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.space3),
            ) {
                ProjectTextField(
                    value = versionName,
                    onValueChange = onVersionNameChanged,
                    label = VERSION_NAME_LABEL,
                    placeholder = VERSION_NAME_PLACEHOLDER,
                    message = versionNameMessage,
                    modifier = Modifier.weight(1f),
                )
                ProjectTextField(
                    value = versionCode,
                    onValueChange = onVersionCodeChanged,
                    label = VERSION_CODE_LABEL,
                    placeholder = VERSION_CODE_PLACEHOLDER,
                    message = versionCodeMessage,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 只读字段展示（如锁死的类型 / 入口）：标签 + 值，值单行省略避免撑破布局。
 */
@Composable
private fun LockedField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.space1),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val APK_NAME_LABEL: String = "应用名称"
private const val APK_NAME_PLACEHOLDER: String = "给你的 App 起个名字"
private const val PACKAGE_NAME_LABEL: String = "包名"
private const val PACKAGE_NAME_PLACEHOLDER: String = "com.example.app"
private const val VERSION_NAME_LABEL: String = "版本号"
private const val VERSION_NAME_PLACEHOLDER: String = "1.0.0"
private const val VERSION_CODE_LABEL: String = "内部版本号"
private const val VERSION_CODE_PLACEHOLDER: String = "1"
private const val TYPE_LABEL: String = "项目类型"
private const val ENTRY_LABEL: String = "入口"

/** 入口（URL）输入框标签。 */
private const val ENTRY_URL_LABEL: String = "入口网址"

/** 入口（URL）占位提示。 */
private const val ENTRY_URL_PLACEHOLDER: String = "https://example.com"

/** 入口文件输入框标签（HTML 类专属）。 */
private const val ENTRY_FILE_LABEL: String = "入口文件"

/** 入口文件输入框占位提示。 */
private const val ENTRY_FILE_PLACEHOLDER: String = "index.html"

/** 入口文件输入框中性提示（留空默认 index.html）。 */
private const val ENTRY_FILE_HINT: String = "网页应用首屏加载的文件，留空默认 index.html"

/** 未设置图标时「选择图标」按钮文案。 */
private const val ICON_PICK_LABEL: String = "选择图标"

/** 已设置图标时「更换图标」按钮文案。 */
private const val ICON_CHANGE_LABEL: String = "更换图标"
