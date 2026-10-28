package com.make.apk.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.Spacing

/**
 * 自绘开关（轨道 + 滑块）。
 *
 * ## 为什么自绘
 *
 * 原生 `Switch` 的轨道 / 滑块几何写死在 Material 主题里，且选中态配色直接吃
 * `colorScheme.primary`；我们要的是"关态 = 中性描边容器、开态 = 强调色实心"，
 * 且滑块位移走弹簧而非匀速过渡。自绘后几何与配色全部走令牌。
 *
 * ## 自绘要点
 *
 * - **弹簧位移**：滑块用 `spring`（MediumBouncy / High），松手瞬间有轻微回弹，
 *   操作"手感"来自物理而非时长；
 * - **描边即关态**：关态轨道是描边容器而非灰块，和页面里其他中性容器同语言；
 * - **语义无障碍**：`role = Switch` + 播报"标签：开/关"。
 *
 * @param checked 是否开启。
 * @param onCheckedChange 切换回调；null = 只读展示。
 * @param contentDescription 无障碍播报文案（应为开关标签，读屏会补"开/关"）。
 */
@Composable
fun MmaSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val control = LocalMmaDesign.current.control
    val colorScheme = MaterialTheme.colorScheme

    val travel: Dp = control.switchTrackWidth - control.switchThumb - control.switchThumbInset * 2
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) travel else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "switchThumb",
    )

    Box(
        modifier = modifier
            .size(width = control.switchTrackWidth, height = control.switchTrackHeight)
            .clip(ControlShapes.pill)
            .background(if (checked) colorScheme.primary else colorScheme.surfaceVariant)
            .border(
                width = control.dividerHeight,
                color = if (checked) colorScheme.primary else colorScheme.outline,
                shape = ControlShapes.pill,
            )
            .then(
                if (onCheckedChange != null) {
                    Modifier.clickableNoRipple { onCheckedChange(!checked) }
                } else {
                    Modifier
                },
            )
            .semantics {
                role = Role.Switch
                if (contentDescription != null) {
                    this.contentDescription = "$contentDescription：${if (checked) "开" else "关"}"
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset + control.switchThumbInset)
                .size(control.switchThumb)
                .clip(ControlShapes.pill)
                .background(if (checked) colorScheme.onPrimary else colorScheme.outline),
        )
    }
}

/**
 * 开关行：左侧标签（+ 可选说明），右侧 [MmaSwitch]，整行语义为一个开关。
 *
 * 与单行开关（[MmaSwitch]）分离的原因：设置页里"标签 + 说明 + 开关"是高频组合，
 * 每处各写一遍 Row 会导致间距 / 字阶漂移。
 */
@Composable
fun MmaSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = typography.bodyLarge,
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
        MmaSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            contentDescription = label,
        )
    }
}
