package com.make.apk.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.icons.MmaIcons
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * 下拉选项——**自绘下拉**的数据契约。
 *
 * @param value 选项真实值（回传给 [MmaDropdownField.onSelected]，类型由调用方定）。
 * @param label 选项展示文案。
 * @param iconRes 前导图标资源（null = 纯文字项）。
 */
data class MmaOption<T>(
    val value: T,
    val label: String,
    val iconRes: Int? = null,
)

/**
 * 自绘下拉字段 + 分段式菜单（M3 Expressive 规范，联网调研 2026-10）。
 *
 * ## 为什么自绘
 *
 * 原生 `ExposedDropdownMenuBox` 的三宗罪：① 必须配 `readOnly OutlinedTextField`，
 * 输入框里塞不可编辑文本，语义与观感都别扭；② 菜单是整块卡片，项与项之间没有
 * "分段"关系；③ 选中态只换文字颜色，状态表达弱。本项目要求**全部控件自绘**，故重画。
 *
 * ## 自绘要点
 *
 * - **外圆内方**：一条 run 的两端保留大圆角、朝兄弟的一侧收小圆角（[ControlShapes]），
 *   项间留 2dp 间隙露出底色 —— 这就是"分段"感的来源；
 * - **选中项脱离 run**：四角全圆 + 强调容器色填充，用**形状 + 颜色**双重表达状态；
 * - **形状 morph**：字段展开时圆角 12 → 16，形状本身在提示"已打开"；
 * - **弹簧进场**：scale 0.8 → 1 + fade，用 spring 而非固定 easing（M3E 运动规范）；
 * - **全屏 scrim**：弹层内铺一层透明 scrim 吃掉所有外部点击 —— 因此点击字段本身
 *   不会"先关后开"（focusable Popup 的经典抖动问题），返回键由 Popup 自动接管。
 *
 * @param label 字段标签（如"缓存模式"）。
 * @param selectedLabel 当前选中项文案。
 * @param options 可选项。
 * @param onSelected 选中回调（携带 [MmaOption.value]）。
 */
@Composable
fun <T> MmaDropdownField(
    label: String,
    selectedLabel: String,
    options: List<MmaOption<T>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val control = LocalMmaDesign.current.control
    val density = LocalDensity.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    var expanded by remember { mutableStateOf(false) }
    var anchorPosition by remember { mutableStateOf(Offset.Zero) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }

    MmaDropdownAnchor(
        label = label,
        selectedLabel = selectedLabel,
        expanded = expanded,
        enabled = enabled,
        onExpand = { expanded = true },
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                anchorPosition = coordinates.positionInWindow()
                anchorSize = coordinates.size
            },
    )

    if (expanded) {
        // 菜单宽度 = 锚点宽度，夹紧到规范区间（112–280），避免长列表撑爆屏幕。
        val menuWidth = with(density) { anchorSize.width.toDp() }
            .coerceIn(control.menuMinWidth, control.menuMaxWidth)
        // 下方放不下就向上翻转，保证菜单永远完整可见。
        val estimatedHeight = control.menuItemHeight * options.size +
            control.menuGroupGap * (options.size - 1).coerceAtLeast(0)
        val anchorTopY = with(density) { anchorPosition.y.toDp() }
        val anchorBottomY = anchorTopY + with(density) { anchorSize.height.toDp() }
        val flipUp = anchorBottomY + estimatedHeight > screenHeight && anchorTopY - estimatedHeight > 0.dp

        Popup(
            popupPositionProvider = FullscreenOriginProvider,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // scrim：吃掉菜单之外的所有点击（含字段本身），点它即关闭。
                Box(modifier = Modifier.fillMaxSize().clickableNoRipple { expanded = false })
                MmaDropdownMenu(
                    options = options,
                    selectedLabel = selectedLabel,
                    menuWidth = menuWidth,
                    flipUp = flipUp,
                    onDismiss = { expanded = false },
                    onSelected = onSelected,
                    modifier = Modifier.offset {
                        val menuHeightPx = with(density) { estimatedHeight.roundToPx() }
                        IntOffset(
                            x = anchorPosition.x.roundToInt(),
                            y = if (flipUp) {
                                anchorPosition.y.roundToInt() - menuHeightPx
                            } else {
                                anchorPosition.y.roundToInt() + anchorSize.height
                            },
                        )
                    },
                )
            }
        }
    }
}

/**
 * 下拉字段本体（锚点）：标签 + 选中值 + 尾部箭头。
 *
 * 箭头旋转即"展开/收起"指示（复用 [MmaIcons.Collapse]，与顶栏折叠同一语义图标）。
 */
@Composable
private fun MmaDropdownAnchor(
    label: String,
    selectedLabel: String,
    expanded: Boolean,
    enabled: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val control = LocalMmaDesign.current.control
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shape = if (expanded) ControlShapes.fieldOpen else ControlShapes.fieldClosed

    Row(
        modifier = modifier
            .clip(shape)
            .background(colorScheme.surface)
            .border(
                width = control.dividerHeight,
                color = if (expanded) colorScheme.primary else colorScheme.outline,
                shape = shape,
            )
            .then(if (enabled) Modifier.clickableNoRipple(onClick = onExpand) else Modifier)
            .padding(horizontal = Spacing.space4, vertical = Spacing.space2)
            .semantics {
                role = Role.DropdownList
                contentDescription = "$label：$selectedLabel"
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
                text = selectedLabel,
                style = typography.bodyLarge,
                color = if (enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant,
            )
        }
        MmaIcon(
            iconRes = MmaIcons.Collapse,
            contentDescription = null,
            size = control.menuItemIcon,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f },
        )
    }
}

/**
 * 分段式菜单面板：单组卡片，项间 2dp 间隙（[ControlShapes] 负责外圆内方）。
 */
@Composable
private fun <T> MmaDropdownMenu(
    options: List<MmaOption<T>>,
    selectedLabel: String,
    menuWidth: Dp,
    flipUp: Boolean,
    onDismiss: () -> Unit,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val control = LocalMmaDesign.current.control
    val colorScheme = MaterialTheme.colorScheme
    val menuSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(
            initialScale = MENU_CLOSED_SCALE,
            transformOrigin = TransformOrigin(
                pivotFractionX = 0.5f,
                pivotFractionY = if (flipUp) 1f else 0f,
            ),
            animationSpec = menuSpring,
        ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
        exit = scaleOut(targetScale = MENU_CLOSED_SCALE) + fadeOut(),
    ) {
        Column(
            modifier = Modifier
                .width(menuWidth)
                .shadow(elevation = LocalMmaDesign.current.elevation.elevation1, shape = ControlShapes.group)
                .clip(ControlShapes.group)
                .background(colorScheme.surfaceContainerLow)
                .border(
                    width = control.dividerHeight,
                    color = colorScheme.outlineVariant,
                    shape = ControlShapes.group,
                )
                .padding(control.menuGroupGap),
            verticalArrangement = Arrangement.spacedBy(control.menuGroupGap),
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = option.label == selectedLabel
                MmaDropdownItem(
                    option = option,
                    isSelected = isSelected,
                    shape = when {
                        isSelected -> ControlShapes.itemSelected
                        options.size == 1 -> ControlShapes.itemSingle
                        index == 0 -> ControlShapes.itemFirst
                        index == options.lastIndex -> ControlShapes.itemLast
                        else -> ControlShapes.itemMiddle
                    },
                    onClick = {
                        onSelected(option.value)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/** 单个菜单项：前导图标 + 标签 + 选中勾（选中时脱离 run，四角全圆）。 */
@Composable
private fun <T> MmaDropdownItem(
    option: MmaOption<T>,
    isSelected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
) {
    val control = LocalMmaDesign.current.control
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    // M3E 规范：选中态用**第三强调色**（tertiaryContainer），避免与触发器（primary）抢视觉；
    // 而非原生下拉那样只换文字色或套 primaryContainer。
    val contentColor = if (isSelected) colorScheme.onTertiaryContainer else colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(control.menuItemHeight)
            .clip(shape)
            .background(if (isSelected) colorScheme.tertiaryContainer else Color.Transparent)
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = control.menuItemPaddingH)
            .semantics { role = Role.Button },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        if (option.iconRes != null) {
            MmaIcon(
                iconRes = option.iconRes,
                contentDescription = null,
                size = control.menuItemIcon,
                tint = if (isSelected) contentColor else colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = option.label,
            style = typography.bodyLarge,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            MmaIcon(
                iconRes = MmaIcons.CheckCircle,
                contentDescription = null,
                size = control.menuItemIcon,
                tint = contentColor,
            )
        }
    }
}

/**
 * 把 Popup 铺满整个窗口：菜单在其中用 offset 精确定位到锚点下方，
 * 剩余区域即 scrim（点击关闭）。
 */
private object FullscreenOriginProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

/** 菜单进场初始缩放（M3E `ClosedScaleTarget` = 0.8）。 */
private const val MENU_CLOSED_SCALE: Float = 0.8f
