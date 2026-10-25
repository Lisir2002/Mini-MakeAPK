package com.make.apk.ui.permission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.make.apk.ui.components.CuteButton
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.theme.Spacing

/**
 * 权限申请二次确认弹窗（应用内 UI，**不是**系统权限弹窗——后者不可定制）。
 *
 * 位置：`requestPermissions()` / 设置页 Intent **之前**。三种形态由 [stage] 驱动：
 * - [PermissionDialogStage.FirstTime]：首次说明，讲清用途再请用户放行
 * - [PermissionDialogStage.Rationale]：拒绝后解释，争取「再给一次机会」
 * - [PermissionDialogStage.GoToSettings]：永久拒绝后的设置引导
 *
 * 视觉规格（DESIGN_LANGUAGE §8 权限弹窗）：容器 `surface` + `shapes.large`（24dp 大圆角），
 * 64dp 圆形图标底 `primaryContainer` + 32dp 图标 `onPrimaryContainer`，
 * 主操作 [CuteButton]（胶囊）+ 次操作 TextButton；进入动效由接入方用
 * `Motion.DurationEnter` 控制（弹窗组件本身保持纯展示，便于截图回归）。
 *
 * @param spec 该权限的文案规格（标题/图标/三态说明）。
 * @param stage 当前形态，决定展示哪条说明与主按钮语义。
 * @param onConfirm 主按钮：FirstTime/Rationale → 发起系统请求；GoToSettings → 打开设置页。
 * @param onDismiss 次按钮（「以后再说」）/ 点击弹窗外。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MmaPermissionDialog(
    spec: MmaPermissionSpec,
    stage: PermissionDialogStage,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        // 平台默认宽度会限制我们的圆角容器；关掉后由弹窗自身 padding 控制
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        MmaPermissionDialogContent(
            spec = spec,
            stage = stage,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

/**
 * 弹窗内容（无 Dialog 外壳的纯内容 composable）。
 *
 * 与 [MmaPermissionDialog] 同源：截图测试在 Dialog window 渲染不可用的环境下
 * （Paparazzi 无真实 window）直接测本函数，像素仅差一层 scrim 背景。
 */
@Composable
internal fun MmaPermissionDialogContent(
    spec: MmaPermissionSpec,
    stage: PermissionDialogStage,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message = when (stage) {
        PermissionDialogStage.FirstTime -> spec.firstTimeMessage
        PermissionDialogStage.Rationale -> spec.rationaleMessage
        PermissionDialogStage.GoToSettings -> spec.settingsMessage
    }
    val confirmText = if (stage == PermissionDialogStage.GoToSettings) {
        spec.settingsConfirmText
    } else {
        spec.confirmText
    }

    Surface(
        modifier = Modifier.padding(horizontal = Spacing.space5),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.space6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp) // 图标底规范尺寸（PermissionSpec 视觉规格，已登记门禁允许集）
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                MmaIcon(
                    iconRes = spec.iconRes,
                    contentDescription = null, // 标题已表意，装饰性图标免重复朗读
                    size = 32.dp, // 圆底内图标规范尺寸（已登记门禁允许集）
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Text(
                text = spec.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            CuteButton(
                text = confirmText,
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
            )

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = spec.dismissText, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
