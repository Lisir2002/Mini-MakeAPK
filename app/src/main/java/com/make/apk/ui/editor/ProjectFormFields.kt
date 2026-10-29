package com.make.apk.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import com.make.apk.data.model.ProjectDefaults
import com.make.apk.data.model.VersionScheme
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.components.MmaChip
import com.make.apk.ui.theme.LocalMmaDesign
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
 * 项目表单输入框（§2.6 的字段级提示挂载点）——**自绘**。
 *
 * ## 为什么自绘
 *
 * 原生 `OutlinedTextField` 的浮起标签动画、容器内高、支持文字排版全部由 Material
 * 决定，且标签与输入区重叠（未聚焦时占位在框内，聚焦时上浮），在密集表单里
 * 行高会随聚焦状态跳动。自绘版把**标签固定放在框外上方**：行高恒定，
 * 标签永远可读（填写长表单时不会"忘了这框是填什么的"）。
 *
 * 输入内核仍用 `BasicTextField` —— 光标 / 输入法 / 无障碍必须由系统承担，
 * 这是自绘化的**明确边界**（见 UI 审计文档 §7.3）：自绘的是"皮肤 + 排版"，
 * 不是重写一套输入法交互。
 *
 * 语义完全由 [message] 驱动：
 * - `ERROR` → 描边走 `colorScheme.error`，支持文字走 error（阻断保存）；
 * - `WARNING` → 描边保持常态，支持文字走 `onSurfaceVariant`（只提醒）；
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
    /** 中性提示（无校验消息时显示；如「留空默认 index.html」，不参与校验）。 */
    hint: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val control = LocalMmaDesign.current.control
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val isError = message?.severity == FieldMessage.Severity.ERROR

    Column(modifier = modifier) {
        Text(
            text = label,
            style = typography.labelMedium,
            color = if (isError) colorScheme.error else colorScheme.onSurfaceVariant,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(colorScheme.surface)
                .border(
                    width = control.dividerHeight,
                    color = when {
                        isError -> colorScheme.error
                        focused -> colorScheme.primary
                        else -> colorScheme.outline
                    },
                    shape = MaterialTheme.shapes.small,
                )
                .padding(horizontal = Spacing.space4, vertical = Spacing.space3),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            maxLines = if (singleLine) 1 else minLines,
            textStyle = typography.bodyLarge.copy(color = colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            interactionSource = interactionSource,
            decorationBox = { innerField ->
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        text = placeholder,
                        style = typography.bodyLarge,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
                innerField()
            },
        )
        if (message != null) {
            FieldMessageText(message = message)
        } else if (hint != null) {
            Text(
                text = hint,
                style = typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 版本段式切换（二段 `1.0` / 三段 `1.0.0`）——**自绘药丸**。
 *
 * 原用 `FilterChip`（原生，选中仅换色、形状恒定）；改用 [MmaChip] 后选中态
 * 会发生**形状 morph**（胶囊 → 圆角方块），与全站自绘控件同语言。
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
            MmaChip(
                label = versionSchemeLabelOf(candidate),
                selected = candidate == scheme,
                onClick = { onSchemeChanged(candidate) },
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
