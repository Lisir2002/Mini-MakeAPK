package com.make.apk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.icons.MmaIcons
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.Spacing

/**
 * 带说明的选择项（[MmaSelectField] 数据契约）。
 *
 * 与 [MmaOption] 的差异：**每项带一句说明**——缓存模式这类"名字不足以表达后果"的
 * 选项，用户不必猜「优先缓存」和「仅缓存」差在哪，选择面板里直接看得到。
 */
data class MmaSelectOption<T>(
    val value: T,
    val title: String,
    val description: String,
)

/**
 * 选择卡字段（rc11 重写缓存模式的选择方式）。
 *
 * ## 与下拉（[MmaDropdownField]) 的分工
 *
 * - 下拉：选项是**短词**（4 字以内）、含义自明 → 用弹出菜单，快；
 * - 选择卡：选项需要**解释**（每个模式影响什么）→ 点击字段打开**选择面板**，
 *   每项「标题 + 说明 + 选中态」整卡呈现，选完即关。
 *
 * ## 自绘要点（延续自绘控件语言）
 *
 * - 触发器与字段同形（外框 + 尾部箭头），但**多一行当前模式的说明**；
 * - 面板为居中对话框：选项卡两行排布，选中项 tertiaryContainer 整卡填充
 *   + CheckCircle 勾，未选中项仅 outline 描边；
 * - 点击选项即生效并关闭（少一步"确定"，符合选择而非编辑的语义）。
 */
@Composable
fun <T> MmaSelectField(
    label: String,
    options: List<MmaSelectOption<T>>,
    selectedValue: T?,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val selected = options.firstOrNull { it.value == selectedValue } ?: options.firstOrNull()
    var open by remember { mutableStateOf(false) }

    MmaSelectAnchor(
        label = label,
        selectedTitle = selected?.title ?: "",
        selectedDescription = selected?.description ?: "",
        enabled = enabled,
        onOpen = { open = true },
        modifier = modifier,
    )

    if (open) {
        MmaSelectPanel(
            title = label,
            options = options,
            selectedValue = selectedValue,
            onDismiss = { open = false },
            onSelected = {
                onSelected(it)
                open = false
            },
        )
    }
}

/** 触发器：字段形（外框 + 箭头），多一行当前模式说明。 */
@Composable
private fun MmaSelectAnchor(
    label: String,
    selectedTitle: String,
    selectedDescription: String,
    enabled: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val control = LocalMmaDesign.current.control
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Row(
        modifier = modifier
            .clip(ControlShapes.fieldClosed)
            .background(colorScheme.surface)
            .border(control.dividerHeight, colorScheme.outline, ControlShapes.fieldClosed)
            .then(if (enabled) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = Spacing.space4, vertical = Spacing.space2)
            .semantics {
                role = Role.DropdownList
                contentDescription = "$label：$selectedTitle"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
            )
            Text(
                text = selectedTitle,
                style = typography.bodyLarge,
                color = if (enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant,
            )
            if (selectedDescription.isNotBlank()) {
                Text(
                    text = selectedDescription,
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
        MmaIcon(
            iconRes = MmaIcons.Collapse,
            contentDescription = null,
            size = control.menuItemIcon,
            tint = colorScheme.onSurfaceVariant,
        )
    }
}

/** 选择面板（居中对话框）：每项「标题 + 说明 + 选中勾」，点选即生效。 */
@Composable
private fun <T> MmaSelectPanel(
    title: String,
    options: List<MmaSelectOption<T>>,
    selectedValue: T?,
    onDismiss: () -> Unit,
    onSelected: (T) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.space6)
                .clip(ControlShapes.group)
                .background(colorScheme.surfaceContainerLow)
                .border(1.dp, colorScheme.outlineVariant, ControlShapes.group)
                .padding(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space2),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
            )
            options.forEach { option ->
                val isSelected = option.value == selectedValue
                SelectOptionRow(
                    option = option,
                    isSelected = isSelected,
                    onClick = { onSelected(option.value) },
                )
            }
        }
    }
}

/** 单个选项卡：标题 + 说明 + 选中态（整卡填充 + 勾）。 */
@Composable
private fun <T> SelectOptionRow(
    option: MmaSelectOption<T>,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val control = LocalMmaDesign.current.control

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ControlShapes.itemSingle)
            .background(if (isSelected) colorScheme.tertiaryContainer else colorScheme.surface)
            .border(
                1.dp,
                if (isSelected) colorScheme.tertiaryContainer else colorScheme.outlineVariant,
                ControlShapes.itemSingle,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.space3, vertical = Spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) colorScheme.onTertiaryContainer else colorScheme.onSurface,
            )
            Text(
                text = option.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    colorScheme.onTertiaryContainer
                } else {
                    colorScheme.onSurfaceVariant
                },
            )
        }
        if (isSelected) {
            MmaIcon(
                iconRes = MmaIcons.CheckCircle,
                contentDescription = "已选中",
                size = control.menuItemIcon,
                tint = colorScheme.onTertiaryContainer,
            )
        }
    }
}
