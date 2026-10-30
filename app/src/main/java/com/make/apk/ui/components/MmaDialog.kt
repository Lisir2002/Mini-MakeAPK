package com.make.apk.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.Spacing

/**
 * 自绘弹窗（M3 Expressive dialog 规范，联网调研 2026-10）。
 *
 * ## 为什么自绘
 *
 * 原生 `AlertDialog` / `BasicAlertDialog` 自带 Material 容器色、内边距与按钮排版，
 * 且按钮强制走 `TextButton`（涟漪 + Material 形状）；一旦我们的圆角 / 间距 / 按钮
 * 风格与之不同，就会出现"弹窗和页面不是一套皮肤"的割裂感。
 *
 * ## 自绘要点
 *
 * - **28dp 容器圆角**：比页面卡片（12/16）大一档，用形状本身表达"这是浮起来的东西"；
 * - **弹簧进场**：scale 0.9 → 1 + fade，锚点在卡片中心；
 * - **遮罩可点关闭**：scrim 独立一层，点击即 dismiss（与菜单同源策略）；
 * - **危险操作一色到底**：[destructive] 为真时确认按钮走 error 色，
 *   不靠图标或文案单独强调——颜色即语义。
 *
 * @param onDismissRequest 关闭请求（点遮罩 / 返回键 / 点取消）。
 * @param title 弹窗标题。
 * @param content 内容区（表单 / 说明文字）。
 * @param confirmLabel 确认按钮文案；null = 不渲染确认按钮（纯信息弹窗）。
 * @param onConfirm 确认回调。
 * @param confirmEnabled 确认按钮可用态（表单校验不通过时置 false，比"点了才报错"友好）。
 * @param dismissLabel 取消按钮文案；null = 不渲染取消按钮（内容自带按钮时用它）。
 * @param destructive 危险操作（删除类）：确认按钮走 error 色。
 * @param centered 居中版式：**说明型**弹窗（如权限申请）用——标题、内容、图标居中；
 *   默认 false（左对齐，适合表单型弹窗，标签与输入框左对齐更好读）。
 */
@Composable
fun MmaDialog(
    onDismissRequest: () -> Unit,
    title: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissLabel: String? = DIALOG_DISMISS_DEFAULT,
    destructive: Boolean = false,
    centered: Boolean = false,
) {
    val control = LocalMmaDesign.current.control
    val elevation = LocalMmaDesign.current.elevation
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    // 视口高度：内容区限高的基准（矮屏 / 横屏同样成立）。
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // 遮罩：独立一层，点击关闭（不吃掉卡片上的点击，因为卡片在其上方）。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                    .clickableNoRipple(onClick = onDismissRequest),
            )
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { visible = true }
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(
                    initialScale = DIALOG_CLOSED_SCALE,
                    transformOrigin = TransformOrigin(0.5f, 0.5f),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                ) + fadeIn(),
                exit = scaleOut(targetScale = DIALOG_CLOSED_SCALE) + fadeOut(),
            ) {
                Column(
                    modifier = modifier
                        .widthIn(max = control.dialogMaxWidth)
                        .clip(ControlShapes.dialog)
                        .background(colorScheme.surface)
                        .padding(Spacing.space5),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space4),
                    horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
                ) {
                    Text(
                        text = title,
                        style = typography.titleLarge,
                        color = colorScheme.onSurface,
                        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                    )
                    // 内容区可滚动 + 限高（视口 90%）：表单型弹窗变高时（如条目弹窗新增
                    // 分段控件）矮屏 / 横屏 / 大字号下也不会溢出屏幕——溢出会让确认按钮
                    // 被挤到屏幕外，表现为"填完了却点不到确定"。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = screenHeight * DIALOG_MAX_HEIGHT_RATIO)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        content()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dismissLabel != null) {
                            MmaTextButton(
                                label = dismissLabel,
                                onClick = onDismissRequest,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                        if (confirmLabel != null && onConfirm != null) {
                            MmaTextButton(
                                label = confirmLabel,
                                onClick = onConfirm,
                                color = if (destructive) colorScheme.error else colorScheme.primary,
                                enabled = confirmEnabled,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 自绘文字按钮：纯文字 + 语义色，无 Material 涟漪。
 *
 * 取代原生 `TextButton`（自带 Material 内边距与涟漪）：既用于弹窗按钮区，
 * 也用于列表行内的次级操作（如项目卡「删除」）。
 *
 * 禁用态降透明度而非改灰——保留色相，让"为什么不能点"不至于读成"另一种按钮"。
 */
@Composable
fun MmaTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    enabled: Boolean = true,
    content: @Composable (Color) -> Unit = { resolved ->
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = resolved,
        )
    },
) {
    val alpha = LocalMmaDesign.current.alpha
    val colorScheme = MaterialTheme.colorScheme
    val contentColor = (if (color == Color.Unspecified) colorScheme.primary else color)
        .copy(alpha = if (enabled) alpha.a7 else alpha.a4)

    Box(
        modifier = modifier
            .then(if (enabled) Modifier.clickableNoRipple(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.space4, vertical = Spacing.space2)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        content(contentColor)
    }
}

/** 遮罩不透明度（M3 scrim 规范 32%）。 */
private const val SCRIM_ALPHA: Float = 0.32f

/** 内容区限高占比（视口的 90%：标题与按钮区始终留在屏内）。 */
private const val DIALOG_MAX_HEIGHT_RATIO: Float = 0.9f

/** 弹窗进场初始缩放。 */
private const val DIALOG_CLOSED_SCALE: Float = 0.9f

/** 默认取消文案。 */
private const val DIALOG_DISMISS_DEFAULT: String = "取消"
