package com.make.apk.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.Spacing

/**
 * 自绘分段单选组（M3 Expressive button group 规范，联网调研 2026-10）。
 *
 * ## 为什么替代原生 RadioButton 横排
 *
 * 原写法是若干个 `RadioButton + Text` 横向罗列：① 选项一多就溢出屏幕（抽屉宽度
 * 有 4 档）；② 圆圈的"选中"只有一个小点，弱到要凑近看；③ 整行只有一个圆圈可点，
 * 触控目标远小于 48dp。分段组把"一组互斥选项"画成一个连续容器，
 * **选中即整片填充**，状态在余光里就能读到。
 *
 * ## 自绘要点
 *
 * - **分段间隙 2dp**：每片之间露出容器底色，这是"分段"而非"一个长条"的关键；
 * - **形状 morph + 弹簧**：选中片填充强调容器色，圆点缩放用 spring；
 * - **等分宽度**：各片 `weight(1f)`，文案超出省略，绝不撑破容器。
 *
 * @param label 组标题（渲染在容器上方；null = 不渲染）。
 * @param options 互斥选项。
 * @param selectedValue 当前选中值（按 [MmaOption.value] 判等）。
 * @param onSelected 选中回调。
 */
@Composable
fun <T> MmaSegmentedChoice(
    options: List<MmaOption<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val control = LocalMmaDesign.current.control
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space2)) {
        if (label != null) {
            Text(
                text = label,
                style = typography.bodyLarge,
                color = colorScheme.onSurface,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ControlShapes.segmented)
                .background(colorScheme.surfaceVariant)
                .padding(control.menuGroupGap),
            horizontalArrangement = Arrangement.spacedBy(control.menuGroupGap),
        ) {
            options.forEach { option ->
                val isSelected = option.value == selectedValue
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(control.iconButtonSize)
                        .clip(ControlShapes.segmentPiece)
                        .background(if (isSelected) colorScheme.primaryContainer else Color.Transparent)
                        .clickableNoRipple { onSelected(option.value) }
                        .padding(horizontal = Spacing.space2)
                        .semantics {
                            role = Role.RadioButton
                            contentDescription = option.label
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.label,
                        style = typography.labelLarge,
                        color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 自绘单选圆点：外圈描边 + 内点（选中时弹簧放大显现）。
 *
 * 原生 `RadioButton` 的选中动画与配色无法接入我们的形状 / 运动令牌，故自绘。
 */
@Composable
fun MmaRadioDot(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val control = LocalMmaDesign.current.control
    val colorScheme = MaterialTheme.colorScheme
    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "radioDot",
    )

    Box(
        modifier = modifier
            .size(control.radioSize)
            .clip(ControlShapes.pill)
            .border(
                width = control.radioStroke,
                color = if (selected) colorScheme.primary else colorScheme.outline,
                shape = ControlShapes.pill,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(control.radioSize / 2)
                .graphicsLayer {
                    scaleX = dotScale
                    scaleY = dotScale
                }
                .clip(ControlShapes.pill)
                .background(colorScheme.primary),
        )
    }
}

/**
 * 单选行：自绘圆点 + 标签，整行可点（触控区 = 整行，远大于 48dp 下限）。
 *
 * 适用于**纵向列表型**单选（每一项独占一行）；横向分组请优先用 [MmaSegmentedChoice]。
 */
@Composable
fun MmaRadioRow(
    selected: Boolean,
    label: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick = onSelect)
            .padding(vertical = Spacing.space2)
            .semantics {
                role = Role.RadioButton
                contentDescription = label
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space3),
    ) {
        MmaRadioDot(selected = selected)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = typography.bodyMedium,
                color = colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
