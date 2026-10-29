package com.make.apk.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.icons.MmaIcons
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * 应用内通知类别——决定容器配色与图标（全部取自 [MaterialTheme.colorScheme] 语义令牌，无硬编码色）。
 */
enum class MmaNoticeCategory {
    /** 成功（如保存完成）。 */
    Success,

    /** 警告（非阻断提示）。 */
    Warn,

    /** 错误（保存失败等阻断）。 */
    Error,

    /** 普通信息。 */
    Info,
}

/**
 * 单条通知数据（不可变值对象）。
 *
 * @param category 类别，决定配色与图标。
 * @param message 人类可读文案（禁止占位/模板文案）。
 */
@Stable
data class MmaNoticeData(
    val category: MmaNoticeCategory,
    val message: String,
)

/**
 * 通知宿主状态（类比 `SnackbarHostState`）：持有当前可见通知，暴露 [show] / [dismiss]。
 *
 * 与 Snackbar 的区别：通知**非队列**——新通知直接取代旧通知（同一位置只显示一条，
 * 符合「顶栏居中、轻量提示」的定位），且默认自动消失（见 [MmaNoticeHost]）。
 */
@Stable
class MmaNoticeHostState {
    var current: MmaNoticeData? by mutableStateOf(null)
        private set

    /** 显示一条通知（取代当前可见通知）。 */
    fun show(data: MmaNoticeData) {
        current = data
    }

    /** 立即隐藏当前通知（点击气泡或超时触发）。 */
    fun dismiss() {
        current = null
    }
}

/** 创建并记住一个 [MmaNoticeHostState]。 */
@Composable
fun rememberMmaNoticeHostState(): MmaNoticeHostState = remember { MmaNoticeHostState() }

/**
 * 通知宿主：在调用方指定位置渲染当前通知（通常 `align(TopCenter)` 置于顶栏正下方居中）。
 *
 * 行为：3 秒后自动消失；点击气泡立即消失（[clickableNoRipple]）。图标一律 SVG
 * （`res/drawable/ic_*.xml`），**禁用 emoji**。配色走语义令牌，按类别取容器/内容色。
 *
 * @param hostState 宿主状态（由 [rememberMmaNoticeHostState] 创建）。
 * @param modifier 定位修饰（调用方传 `align(TopCenter)` + 顶栏高度偏移）。
 */
@Composable
fun MmaNoticeHost(
    hostState: MmaNoticeHostState,
    modifier: Modifier = Modifier,
) {
    val data = hostState.current ?: return
    val colorScheme = MaterialTheme.colorScheme
    val design = LocalMmaDesign.current

    // 自动消失：data 变化（新通知取代旧通知）会重置计时。
    LaunchedEffect(data) {
        delay(MMA_NOTICE_AUTO_DISMISS_MS)
        hostState.dismiss()
    }

    val containerColor = when (data.category) {
        MmaNoticeCategory.Success -> colorScheme.primaryContainer
        MmaNoticeCategory.Warn -> colorScheme.tertiaryContainer
        MmaNoticeCategory.Error -> colorScheme.errorContainer
        MmaNoticeCategory.Info -> colorScheme.secondaryContainer
    }
    val contentColor = when (data.category) {
        MmaNoticeCategory.Success -> colorScheme.onPrimaryContainer
        MmaNoticeCategory.Warn -> colorScheme.onTertiaryContainer
        MmaNoticeCategory.Error -> colorScheme.onErrorContainer
        MmaNoticeCategory.Info -> colorScheme.onSecondaryContainer
    }
    val iconRes = when (data.category) {
        MmaNoticeCategory.Success -> MmaIcons.CheckCircle
        MmaNoticeCategory.Warn -> MmaIcons.Notifications
        MmaNoticeCategory.Error -> MmaIcons.SentimentDissatisfied
        MmaNoticeCategory.Info -> MmaIcons.Notifications
    }

    AnimatedVisibility(
        visible = true,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.96f),
        exit = fadeOut() + scaleOut(targetScale = 0.96f),
    ) {
        Row(
            modifier = Modifier
                .shadow(elevation = design.elevation.elevation2, shape = ControlShapes.group)
                .clip(ControlShapes.group)
                .background(containerColor)
                .clickableNoRipple { hostState.dismiss() }
                .semantics { contentDescription = data.message }
                .padding(horizontal = Spacing.space4, vertical = Spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
        ) {
            MmaIcon(
                iconRes = iconRes,
                contentDescription = null,
                size = 20.dp,
                tint = contentColor,
            )
            Text(
                text = data.message,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** 通知自动隐藏时长（毫秒）。 */
private const val MMA_NOTICE_AUTO_DISMISS_MS: Long = 3000L
