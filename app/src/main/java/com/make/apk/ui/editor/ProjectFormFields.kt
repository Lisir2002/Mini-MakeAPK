package com.make.apk.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.make.apk.data.model.ProjectDefaults
import com.make.apk.data.model.VersionScheme
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.theme.Spacing

/**
 * 表单分区卡（§4.1 的「项目来源」「基本信息」两节）。
 *
 * 用 [CuteCard] 承载，内边距与分组间距一律走 `Spacing` / `shapes` 令牌。
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    CuteCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            SectionTitle(text = title)
            content()
        }
    }
}

/** 分区标题（分区卡内的一级标题）。 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * 项目表单输入框（§2.6 的字段级提示挂载点）。
 *
 * 语义完全由 [message] 驱动：
 * - `ERROR` → `isError = true`，支持文字走 `colorScheme.error`（M3 自动配色）；
 * - `WARNING` → `isError = false`，支持文字走 `onSurfaceVariant`（只提醒、不阻断保存）；
 * - `null` → 无支持文字。
 *
 * 文案**不自造**：[message] 由调用方从 `AddProjectUiState.errors / warnings` 取出，
 * 唯一来源始终是 [ProjectValidator]。
 */
@Composable
fun ProjectTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    message: FieldMessage? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        singleLine = singleLine,
        minLines = minLines,
        isError = message?.severity == FieldMessage.Severity.ERROR,
        label = { Text(label) },
        placeholder = placeholder?.let { hint ->
            { Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        supportingText = message?.let { fieldMessage ->
            { FieldMessageText(message = fieldMessage) }
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = keyboardType,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

/**
 * 版本段式切换（二段 `1.0` / 三段 `1.0.0`）。
 *
 * 用 `FilterChip` 而非 `SegmentedButton`：后者当前仍是实验性 API，
 * 一旦使用会把 `@OptIn(ExperimentalMaterial3Api)` 扩散到整个页面。
 *
 * 版本号示例取自 [ProjectDefaults.defaultVersionName]，避免在 UI 里硬编码 "1.0" / "1.0.0"。
 */
@Composable
fun VersionSchemeChips(
    scheme: VersionScheme,
    onSchemeChanged: (VersionScheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        VersionScheme.entries.forEach { candidate ->
            FilterChip(
                selected = candidate == scheme,
                onClick = { onSchemeChanged(candidate) },
                label = { Text(versionSchemeLabelOf(candidate)) },
                shape = MaterialTheme.shapes.extraLarge,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

/**
 * 字段级提示文字（**颜色只由 [FieldMessage.Severity] 决定**）。
 *
 * `ERROR` → `colorScheme.error`；`WARNING` → `onSurfaceVariant`。
 * 显式取色而非依赖所在槽位：同一个组件既挂在输入框的 `supportingText` 里，
 * 也可能单独出现在卡片下方（如准星的类型提示），两处必须同色。
 */
@Composable
fun FieldMessageText(
    message: FieldMessage,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message.text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = when (message.severity) {
            FieldMessage.Severity.ERROR -> MaterialTheme.colorScheme.error
            FieldMessage.Severity.WARNING -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

/** 段式选项文案（含该段式的版本号示例）。 */
private fun versionSchemeLabelOf(scheme: VersionScheme): String {
    val segmentName = when (scheme) {
        VersionScheme.TWO_SEGMENT -> "二段式"
        VersionScheme.THREE_SEGMENT -> "三段式"
    }
    return "$segmentName ${ProjectDefaults.defaultVersionName(scheme)}"
}
