package com.make.apk.ui.components

import androidx.compose.animation.core.EaseInOut
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.make.apk.ui.theme.Motion

// 珊瑚粉胶囊按钮（DESIGN_LANGUAGE §8）。shape=shapes.extraLarge（--radius-pill 胶囊档）；
// 按压 scale → Motion.PressScaleTarget(0.96)、释放 SoftSpring 回弹（§6 微交互）。
@Composable
fun CuteButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) Motion.PressScaleTarget else 1f,
        animationSpec = Motion.SoftSpring,
    )
    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier.scale(scale),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// 柔和卡片：圆角 shapes.small（--radius-md）+ 表面色（M3 提供柔光 elevation）。禁止尖角硬阴影。
// onClick 非空时走 M3 可点击 Card 重载（按压有水波；设置页条目卡用）。
@Composable
fun CuteCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    if (onClick != null) {
        Card(
            onClick = onClick,
            shape = shape,
            colors = colors,
            modifier = modifier,
            content = content,
        )
    } else {
        Card(
            shape = shape,
            colors = colors,
            modifier = modifier,
            content = content,
        )
    }
}

// 打包中可爱脉冲：替代生硬转圈。主色容器轻柔呼吸（shapes.large 圆角 + Motion.DurationBreath 循环）。
@Composable
fun BuildingPulse(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val scale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = Motion.DurationBreath, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    Box(
        modifier = modifier
            .size(72.dp)
            .scale(scale)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text("📦", style = MaterialTheme.typography.displayLarge) // 占位 emoji，后续替换为矢量图标
    }
}
