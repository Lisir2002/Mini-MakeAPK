package com.make.apk.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.Spacing

/**
 * 自绘 Tab 行（编辑页「基本 / 进阶 / 高级配置」）。
 *
 * ## 为什么自绘
 *
 * 原生 `TabRow` 的指示器是**等宽横条 + 位移动画**，且默认吃掉整个 tab 宽度；
 * 本项目要求指示器只占标签宽度的 60%，且用**宽度生长**而非位移来表达切换——
 * 位移会让"选中了谁"在动画中途变得模糊，宽度生长则始终只亮一个。
 *
 * ## 自绘要点
 *
 * - **指示器 = 宽度动画**：`fillMaxWidth(fraction)` 从 0 → 0.6，配合圆角胶囊，
 *   切换时是"长出来"而不是"滑过来"；
 * - **文字即层级**：选中 `labelLarge` + onSurface，未选中同字阶 + onSurfaceVariant
 *   （不改字重，避免切换时文字宽度跳动导致的抖动）；
 * - **底部 hairline**：1dp 分隔线把 Tab 区与内容区切开，不靠阴影。
 *
 * @param tabs 标签文案（顺序即索引顺序）。
 * @param selectedIndex 当前选中索引。
 * @param onSelected 选中回调（携带索引）。
 */
@Composable
fun MmaTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val control = LocalMmaDesign.current.control
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                val indicatorFraction by animateFloatAsState(
                    targetValue = if (isSelected) INDICATOR_WIDTH_FRACTION else 0f,
                    label = "tabIndicator",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(control.menuItemHeight)
                        .clickableNoRipple { onSelected(index) }
                        .semantics {
                            role = Role.Tab
                            contentDescription = label
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = Spacing.space2),
                    ) {
                        Text(
                            text = label,
                            style = typography.labelLarge,
                            color = if (isSelected) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(indicatorFraction)
                                .height(control.tabIndicatorHeight)
                                .clip(ControlShapes.pill)
                                .background(colorScheme.primary),
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(control.dividerHeight)
                .background(colorScheme.outlineVariant),
        )
    }
}

/** 选中指示器占标签区宽度的比例（居中，不与相邻 tab 粘连）。 */
private const val INDICATOR_WIDTH_FRACTION: Float = 0.6f
