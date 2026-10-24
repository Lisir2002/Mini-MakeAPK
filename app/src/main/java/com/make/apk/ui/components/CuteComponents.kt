package com.make.apk.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.make.apk.ui.theme.Motion

// 珊瑚粉胶囊按钮（DESIGN_LANGUAGE §8）。shape=999dp，按压回弹由 Motion.SoftSpring 配合调用处动画。
@Composable
fun CuteButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// 柔和卡片：圆角 16dp + 表面色（M3 提供柔光 elevation）。禁止尖角硬阴影。
@Composable
fun CuteCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier,
        content = content,
    )
}

// 打包中可爱脉冲：替代生硬转圈。主色容器轻柔呼吸（spring 回弹）。
@Composable
fun BuildingPulse(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val scale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    Box(
        modifier = modifier
            .size(72.dp)
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text("📦", fontSize = 32.sp) // 占位 emoji，后续替换为矢量图标
    }
}
