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
import com.make.apk.ui.theme.Spacing

/**
 * 编辑页顶部身份卡（§4.2 / §10.4 D-T2）——**可就地编辑**。
 *
 * 可改：应用名 / 包名 / 版本段式 / 版本号 / 内部版本号 / 图标（占位）。
 * 只读锁死：`projectType` 与 `entry`（保存后不可改，见 VM 锁死 API，无任何修改出口）。
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
    entryHtml: String,
    entryUrlMessage: FieldMessage?,
    entryHtmlMessage: FieldMessage?,
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
    onEntryHtmlChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CuteCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            // 图标 + 名称/包名（并排）
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProjectIcon(
                    apkName = apkName,
                    themeColor = themeColor,
                    iconRef = iconRef,
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

            // 入口（URL/HTML 可编辑，§优化·第四点：放开原 §6.2 锁死）。
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
                    value = entryHtml,
                    onValueChange = onEntryHtmlChanged,
                    label = ENTRY_HTML_LABEL,
                    placeholder = ENTRY_HTML_PLACEHOLDER,
                    message = entryHtmlMessage,
                    singleLine = false,
                    minLines = ENTRY_HTML_MIN_LINES,
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

/** 入口（HTML）输入框标签。 */
private const val ENTRY_HTML_LABEL: String = "入口 HTML"

/** 入口（HTML）占位提示。 */
private const val ENTRY_HTML_PLACEHOLDER: String = "<html>…</html>"

/** 入口（HTML）最小行数（多行粘贴区）。 */
private const val ENTRY_HTML_MIN_LINES: Int = 6
