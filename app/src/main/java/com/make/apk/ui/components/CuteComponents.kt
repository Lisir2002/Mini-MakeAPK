package com.make.apk.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.icons.MmaIcons
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.Motion

/**
 * 自绘主按钮（胶囊，DESIGN_LANGUAGE §8）。
 *
 * ## 为什么自绘
 *
 * 原实现是 `androidx.compose.material3.Button`：容器色、内边距、最小宽度、禁用态
 * 透明度全部由 Material 主题决定，且强制带水波纹。项目铁律要求**所有组件自绘**，
 * 故改为 Box + 令牌：形状走 `shapes.extraLarge`（胶囊）、按压 scale 0.96 后
 * SoftSpring 回弹（§6 微交互）、配色走 `colorScheme`。
 *
 * 签名保持不变 —— 全部调用点（新建 / 编辑 / 列表页）自动换成自绘皮肤。
 */
@Composable
fun CuteButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val control = LocalMmaDesign.current.control
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) Motion.PressScaleTarget else 1f,
        animationSpec = Motion.SoftSpring,
        label = "buttonPress",
    )

    Box(
        modifier = modifier
            .height(control.buttonHeight)
            .scale(scale)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(colorScheme.primary)
            .clickableNoRipple(onClick = onClick)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = typography.labelLarge,
            color = colorScheme.onPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 自绘卡片：圆角 `shapes.small`（--radius-md）+ 表面色 + 一级抬升阴影。禁止尖角硬阴影。
 *
 * 自绘原因同 [CuteButton]：`Card` 的容器色 / 抬升 / 涟漪来自 Material 主题。
 * 可选 [onClick] —— 有点击时整卡可点（无涟漪，按压反馈由调用方或按钮承担）。
 */
@Composable
fun CuteCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = Color.Unspecified,
    shape: Shape = MaterialTheme.shapes.small,
    content: @Composable ColumnScope.() -> Unit,
) {
    val elevation = LocalMmaDesign.current.elevation
    val colorScheme = MaterialTheme.colorScheme
    val resolvedColor = if (containerColor == Color.Unspecified) colorScheme.surface else containerColor

    Column(
        modifier = modifier
            .shadow(elevation = elevation.elevation1, shape = shape)
            .clip(shape)
            .background(resolvedColor)
            .then(
                if (onClick != null) {
                    Modifier.clickableNoRipple(onClick = onClick).semantics { role = Role.Button }
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}

/**
 * 打包中脉冲：替代生硬转圈。主色容器轻柔呼吸（[Motion.DurationBreath] 循环）。
 *
 * **图标为矢量**（[MmaIcons.InstallMobile]，语义"打包 → 安装到手机"）。
 * 此前此处用 emoji 字符占位，违反项目 P0 铁律「禁止 emoji 作功能图标」，已清除。
 */
@Composable
fun BuildingPulse(modifier: Modifier = Modifier) {
    val control = LocalMmaDesign.current.control
    val colorScheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "buildingPulse")
    val scale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = Motion.DurationBreath),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Box(
        modifier = modifier
            .size(control.statusIconSize)
            .scale(scale)
            .clip(MaterialTheme.shapes.large)
            .background(colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        MmaIcon(
            iconRes = MmaIcons.InstallMobile,
            contentDescription = "打包中",
            size = control.statusIconSize / 2,
            tint = colorScheme.onPrimaryContainer,
        )
    }
}
