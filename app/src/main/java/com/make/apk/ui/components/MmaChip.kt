package com.make.apk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.Spacing

/**
 * 自绘药丸芯片（预置壳选择 / 筛选态）。
 *
 * ## 为什么自绘
 *
 * 原生 `FilterChip` 选中时只换容器色，形状恒定；且它自带 8dp 圆角与 Material 内边距，
 * 与我们的分段控件不同语言。
 *
 * ## 自绘要点：**形状 morph**
 *
 * M3 Expressive 的核心手法是"状态用形状表达，不只用颜色"：
 * - 未选中：**胶囊**（全圆角）+ 描边 —— 读起来是"可点的弱选项"；
 * - 选中：**圆角方块**（12dp）+ 强调容器色填充 —— 形态一变，余光可辨。
 *
 * 两者直接切换两个令牌形状（[ControlShapes]），不做补间：芯片是瞬时反馈控件，
 * 补间圆角在此处只会让"选中"显得迟疑。
 *
 * @param label 芯片文案。
 * @param selected 选中态。
 * @param onClick 点击回调。
 * @param leadingIcon 前导矢量图标（如建议采纳的勾）；null = 纯文字芯片。
 */
@Composable
fun MmaChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: Int? = null,
) {
    val control = LocalMmaDesign.current.control
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shape = if (selected) ControlShapes.itemSelected else ControlShapes.pill
    val labelColor = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .height(control.chipHeight)
            .clip(shape)
            .background(if (selected) colorScheme.primaryContainer else colorScheme.surface)
            .border(
                width = control.dividerHeight,
                color = if (selected) colorScheme.primaryContainer else colorScheme.outline,
                shape = shape,
            )
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = Spacing.space4)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        if (leadingIcon != null) {
            MmaIcon(
                iconRes = leadingIcon,
                contentDescription = null,
                size = control.menuItemIcon,
                tint = labelColor,
            )
        }
        Text(
            text = label,
            style = typography.labelLarge,
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
