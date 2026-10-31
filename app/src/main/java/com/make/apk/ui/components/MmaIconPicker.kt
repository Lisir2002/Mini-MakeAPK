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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import com.make.apk.ui.shell.MmaShellIconResolver
import com.make.apk.ui.shell.ShellIconResolver
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * 图标选择器的单条候选项。
 *
 * @param key 逻辑图标键（`[ShellIconResolver]` 协议）；null = 无图标（纯文字条目）。
 * @param label 展示文案（同时作为无障碍描述与测试标签来源）。
 */
data class IconPickerEntry(
    val key: String?,
    val label: String,
)

/**
 * 图标选择器的一个分类（与 [MmaShellIconResolver] 的逻辑键对齐，便于预览侧与导出侧同键同形）。
 *
 * @param title 分类标题（如「通用」「编辑」「文件」「媒体」）。
 * @param items 该分类下的候选图标。
 */
data class IconPickerCategory(
    val title: String,
    val items: List<IconPickerEntry>,
)

/**
 * 自绘图标选择器（覆盖弹层，非 M3 `ExposedDropdownMenuBox`——项目已明确拒绝原生下拉）。
 *
 * ## 交互形态（W1 任务⑭）
 *
 * - **触发区**：图标在弹窗内**居中预览**（外壳卡片 + 当前图标 + 底部箭头），点击展开；
 * - **覆盖弹层**：锚定在触发区正下方弹出，内部为**分类图标网格**，可纵向滚动；
 * - **收起方式三选一**：① 点击弹层内任意图标即选中并收起；② 点击弹层**底部箭头**收起；
 *   ③ 点击弹层之外的 scrim（外部点击）收起；
 * - **预览 == 导出**：图标一律走逻辑键 → [ShellIconResolver] 解析，不落具体 res id。
 *
 * ## 为什么自绘
 *
 * 原生 `ExposedDropdownMenuBox` 必须配 `readOnly OutlinedTextField`，文本型下拉承载
 * "图标网格"语义错位，且本项目全部控件自绘（见 [MmaDropdownField] 设计说明）。本组件复用
 * 同一套"全屏 Popup + scrim + 固定定位"手法，保证交互一致性。
 *
 * @param label 字段标签（如"图标"）。
 * @param selectedKey 当前选中逻辑键；null = 无图标。
 * @param catalog 分类图标目录（调用方提供，通常对齐 [MmaShellIconResolver]）。
 * @param onSelected 选中回调（携带逻辑键；null 表示清除图标）。
 * @param enabled 是否可交互。
 * @param resolver 逻辑键解析器（默认 [MmaShellIconResolver]）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MmaIconPicker(
    label: String,
    selectedKey: String?,
    catalog: List<IconPickerCategory>,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    resolver: ShellIconResolver = MmaShellIconResolver,
) {
    val control = LocalMmaDesign.current.control
    val elevation = LocalMmaDesign.current.elevation
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    var expanded by remember { mutableStateOf(false) }
    var anchorPosition by remember { mutableStateOf(Offset.Zero) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }

    // 当前选中项的展示文案（用于触发区副标题与无障碍描述）。
    val selectedLabel = catalog.firstNotNullOfOrNull { category ->
        category.items.firstOrNull { it.key == selectedKey }?.label
    } ?: "无图标"
    val selectedIconRes = resolver.resolve(selectedKey)

    // 弹层宽度：夹紧到规范区间，避免窄屏溢出。
    val pickerWidth = control.menuMaxWidth.coerceAtMost(screenWidth - Spacing.space6)
    val maxPanelHeight: Dp = screenHeight * 0.5f

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space1),
    ) {
        Text(
            text = label,
            style = typography.labelMedium,
            color = colorScheme.onSurfaceVariant,
        )
        // 触发区：图标居中预览 + 底部箭头；点击展开覆盖弹层。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ControlShapes.fieldClosed)
                .background(colorScheme.surfaceVariant)
                .border(
                    width = control.dividerHeight,
                    color = if (expanded) colorScheme.primary else colorScheme.outline,
                    shape = ControlShapes.fieldClosed,
                )
                .then(if (enabled) Modifier.clickableNoRipple { expanded = true } else Modifier)
                .padding(vertical = Spacing.space3)
                .onGloballyPositioned { coordinates ->
                    anchorPosition = coordinates.positionInWindow()
                    anchorSize = coordinates.size
                }
                .testTag("icon_picker_trigger")
                .semantics {
                    role = Role.DropdownList
                    contentDescription = "$label：$selectedLabel"
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.space1),
            ) {
                Box(
                    modifier = Modifier
                        .size(control.badgeIconSize)
                        .clip(ControlShapes.group)
                        .background(colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selectedIconRes != null) {
                        MmaIcon(
                            iconRes = selectedIconRes,
                            contentDescription = null,
                            size = control.iconButtonSize,
                            tint = colorScheme.onSurface,
                        )
                    } else {
                        Text(
                            text = "无图标",
                            style = typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
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
    }

    if (expanded) {
        val maxPanelHeightPx = with(density) { maxPanelHeight.roundToPx() }
        val anchorTopY = with(density) { anchorPosition.y.toDp() }
        val anchorBottomY = anchorTopY + with(density) { anchorSize.height.toDp() }
        val fitsBelow = anchorBottomY + maxPanelHeight <= screenHeight
        val fitsAbove = anchorTopY - maxPanelHeight >= 0.dp
        // 下方放不下就向上翻转，保证弹层永远完整可见。
        val flipUp = !fitsBelow && fitsAbove

        val pickerWidthPx = with(density) { pickerWidth.roundToPx() }
        val screenWidthPx = with(density) { screenWidth.roundToPx() }
        val panelTopPx = if (flipUp) {
            anchorPosition.y.roundToInt() - maxPanelHeightPx
        } else {
            anchorPosition.y.roundToInt() + anchorSize.height
        }
        val rawLeftPx = anchorPosition.x + (anchorSize.width - pickerWidthPx) / 2f
        val panelLeftPx = rawLeftPx.roundToInt().coerceIn(0, (screenWidthPx - pickerWidthPx).coerceAtLeast(0))

        Popup(
            popupPositionProvider = IconPickerFullscreenOriginProvider,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // scrim：吃掉弹层之外的所有点击（含触发区本身），点它即收起。
                Box(modifier = Modifier.fillMaxSize().clickableNoRipple { expanded = false })
                AnimatedVisibility(
                    visible = true,
                    modifier = Modifier.offset { IntOffset(panelLeftPx, panelTopPx) },
                    enter = scaleIn(
                        initialScale = MENU_CLOSED_SCALE,
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                            pivotFractionX = 0.5f,
                            pivotFractionY = if (flipUp) 1f else 0f,
                        ),
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
                    exit = scaleOut(targetScale = MENU_CLOSED_SCALE) + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier
                            .width(pickerWidth)
                            .heightIn(max = maxPanelHeight)
                            .shadow(elevation = elevation.elevation1, shape = ControlShapes.group)
                            .clip(ControlShapes.group)
                            .background(colorScheme.surfaceContainerLow)
                            .border(
                                width = control.dividerHeight,
                                color = colorScheme.outlineVariant,
                                shape = ControlShapes.group,
                            )
                            .verticalScroll(rememberScrollState())
                            .padding(Spacing.space3)
                            .testTag("icon_picker_panel"),
                        verticalArrangement = Arrangement.spacedBy(Spacing.space3),
                    ) {
                        catalog.forEach { category ->
                            Column(
                                verticalArrangement = Arrangement.spacedBy(Spacing.space2),
                            ) {
                                Text(
                                    text = category.title,
                                    style = typography.labelMedium,
                                    color = colorScheme.onSurfaceVariant,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.space2),
                                ) {
                                    category.items.forEach { entry ->
                                        val isSelected = entry.key == selectedKey
                                        val cellRes = resolver.resolve(entry.key)
                                        Box(
                                            modifier = Modifier
                                                .size(control.iconButtonSize)
                                                .clip(ControlShapes.itemSingle)
                                                .background(
                                                    if (isSelected) colorScheme.primaryContainer else Color.Transparent,
                                                )
                                                .clickableNoRipple {
                                                    onSelected(entry.key)
                                                    expanded = false
                                                }
                                                .testTag(
                                                    "icon_option_${entry.key ?: "none"}",
                                                )
                                                .semantics {
                                                    role = Role.Button
                                                    contentDescription = entry.label
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (cellRes != null) {
                                                MmaIcon(
                                                    iconRes = cellRes,
                                                    contentDescription = null,
                                                    size = control.menuItemIcon,
                                                    tint = if (isSelected) {
                                                        colorScheme.onPrimaryContainer
                                                    } else {
                                                        colorScheme.onSurfaceVariant
                                                    },
                                                )
                                            } else {
                                                Text(
                                                    text = "无",
                                                    style = typography.labelMedium,
                                                    color = if (isSelected) {
                                                        colorScheme.onPrimaryContainer
                                                    } else {
                                                        colorScheme.onSurfaceVariant
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // 底部箭头：点击收起弹层。
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickableNoRipple { expanded = false }
                                .testTag("icon_picker_collapse")
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "收起图标选择器"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            MmaIcon(
                                iconRes = MmaIcons.Collapse,
                                contentDescription = null,
                                size = control.menuItemIcon,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.graphicsLayer { rotationZ = 180f },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 把 Popup 铺满整个窗口：弹层在其中用 offset 精确定位到锚点下方，
 * 剩余区域即 scrim（点击收起）。与 [MmaDropdownField] 共用同一手法。
 */
private object IconPickerFullscreenOriginProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

/** 弹层进场初始缩放（M3E `ClosedScaleTarget` = 0.8）。 */
private const val MENU_CLOSED_SCALE: Float = 0.8f
