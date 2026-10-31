package com.make.apk.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.data.model.ShellSelectionStyle
import com.make.apk.data.model.ShellTopTabs

/**
 * 壳顶栏 tab 区（[ShellTopTabs] → 导航栏 / pager 指示器）。
 *
 * ## 约束
 *
 * - **无状态**：选中项由外部通过 [selectedItemId] 传入，交互出口化为 [onItemSelected]；
 * - **不读 app 主题**：颜色 / 字体 / 尺寸一律取自 [ShellRenderEnv]；
 * - **选中态恒开**：无论 [selectionStyle] 与 [showIndicator] 取何值，
 *   "当前页高亮"这件事本身**不允许**关闭——选中的 tab 一定是强调色文字
 *   （[ShellSelectionStyle.TEXT] 档再叠加字重强调）。
 *
 * ## 三种选中态样式（[ShellSelectionStyle]）
 *
 * 三档**两两互不相同**（用户在 6.3 编辑器里选「文字」与选「指示器」必须看到差别）：
 *
 * | 档位 | 选中态表现 | 底部指示条 | 药丸背景 |
 * |---|---|---|---|
 * | [ShellSelectionStyle.INDICATOR] | 强调色文字 + 底部强调色指示条（M3 TabRow 风格） | 有 | 无 |
 * | [ShellSelectionStyle.PILL] | 强调容器色药丸背景 + 容器色上的前景色文字 | 无 | 有 |
 * | [ShellSelectionStyle.TEXT] | 强调色文字 + 字重强调（**无任何背景**） | 无 | 无 |
 *
 * ## [showIndicator]（[ShellTopTabs.showSelectionIndicator]）的语义
 *
 * 该开关只控制**底部指示条**这一种指示形态（[ShellSelectionStyle.INDICATOR] 档专用），
 * **不关闭选中语义**：
 *
 * - [ShellSelectionStyle.INDICATOR] → 指示条在 `showIndicator = false` 时不绘制
 *   （但**占位保留**，整排高度不跳动）；
 * - [ShellSelectionStyle.PILL] / [ShellSelectionStyle.TEXT] → 本档没有底部指示条，开关无影响。
 *
 * 无论开关如何，"当前页"始终由**文字颜色**（[ShellSelectionStyle.TEXT] 档再叠加字重）表达。
 *
 * @param topTabs 顶栏 tab 契约（`enabled = false` 时本组件不应被渲染）。
 * @param env 渲染环境。
 * @param iconResolver 逻辑图标键解析器。
 * @param selectedItemId 当前选中条目 id；null 时回退到契约里 `selected = true` 的条目。
 * @param onItemSelected 选中回调（宿主据此切页 / 加载目标 URL）。
 */
@Composable
fun ShellTopTabsSection(
    topTabs: ShellTopTabs,
    env: ShellRenderEnv,
    iconResolver: ShellIconResolver,
    selectedItemId: String?,
    onItemSelected: (ShellMenuItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = visibleTopTabItems(topTabs)
    if (items.isEmpty()) return
    val effectiveSelectedId = resolveTopTabSelectedId(items, selectedItemId)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = env.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 固定最小高度占位：首屏即按最终布局占位，避免"被切一半"的闪烁
                // （行内 tab 用 weight(1f) 布局、无固定高度时，首帧测量瞬态会让整排塌缩再弹起）。
                // 48dp 为顶栏 tab 栏规范高度（组件尺寸语义，落在令牌白名单）。
                .heightIn(min = TOP_TABS_MIN_HEIGHT)
                .padding(horizontal = env.spaceSm),
            verticalAlignment = Alignment.Top,
        ) {
            items.forEach { item ->
                ShellTopTab(
                    item = item,
                    selected = item.id == effectiveSelectedId,
                    selectionStyle = topTabs.selectionStyle,
                    showIndicator = topTabs.showSelectionIndicator,
                    env = env,
                    iconResolver = iconResolver,
                    onClick = { onItemSelected(item) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * 单个顶栏 tab：图标（可选）+ 标题 + 该档的指示形态。
 *
 * **高度稳定**：同一档内所有 tab 结构一致，切换选中项时整排高度不跳动
 * （[ShellSelectionStyle.INDICATOR] 档的指示条**始终占位**，未选中时为透明）。
 */
@Composable
private fun ShellTopTab(
    item: ShellMenuItem,
    selected: Boolean,
    selectionStyle: ShellSelectionStyle,
    showIndicator: Boolean,
    env: ShellRenderEnv,
    iconResolver: ShellIconResolver,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconRes = iconResolver.resolve(item.iconRef)

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = env.spaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (selectionStyle) {
            // ── 指示条档：强调色文字 + 底部指示条，无背景 ──────────────────
            ShellSelectionStyle.INDICATOR -> {
                val contentColor = if (selected) env.accent else env.onSurfaceVariant
                ShellTopTabContent(
                    title = item.title,
                    titleColor = contentColor,
                    iconRes = iconRes,
                    iconTint = contentColor,
                    textStyle = env.typography.labelLarge,
                    env = env,
                )
                Spacer(modifier = Modifier.height(env.spaceXs))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(env.indicatorHeight)
                        .background(
                            color = if (selected && showIndicator) env.accent else Color.Transparent,
                        ),
                )
            }

            // ── 药丸档：胶囊背景 + 容器色上的前景色文字，无底部指示条 ────────
            ShellSelectionStyle.PILL -> {
                val contentColor = if (selected) env.onAccentContainer else env.onSurfaceVariant
                Box(
                    modifier = Modifier
                        .background(
                            color = if (selected) env.accentContainer else Color.Transparent,
                            shape = env.shapes.extraLarge,
                        )
                        .padding(horizontal = env.spaceSm, vertical = env.spaceXs),
                    contentAlignment = Alignment.Center,
                ) {
                    ShellTopTabContent(
                        title = item.title,
                        titleColor = contentColor,
                        iconRes = iconRes,
                        iconTint = contentColor,
                        textStyle = env.typography.labelLarge,
                        env = env,
                    )
                }
            }

            // ── 文字档：仅文字颜色 + 字重表达选中，无背景、无指示条 ──────────
            ShellSelectionStyle.TEXT -> {
                ShellTopTabContent(
                    title = item.title,
                    titleColor = if (selected) env.accent else env.onSurfaceVariant,
                    iconRes = iconRes,
                    // 仅**文字**变化，图标保持中性色
                    iconTint = env.onSurfaceVariant,
                    textStyle = if (selected) env.selectedLabelStyle else env.typography.labelLarge,
                    env = env,
                )
            }
        }
    }
}

/** 顶栏 tab 内容（图标 + 文字），三档共用，保证同档内尺寸一致。 */
@Composable
private fun ShellTopTabContent(
    title: String,
    titleColor: Color,
    iconRes: Int?,
    iconTint: Color,
    textStyle: TextStyle,
    env: ShellRenderEnv,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(SHELL_TAB_ICON_SIZE),
            )
            Spacer(modifier = Modifier.height(env.spaceXs))
        }
        Text(
            text = title,
            style = textStyle,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 顶栏 tab 的**可见**条目（`visible = false` 的条目保留在契约里但不渲染、也不成页）。
 *
 * 壳内只有这一份过滤实现：tab 区与滑动 pager（[ShellTopTabsPager]）都走它，
 * 故"隐藏条目不会渲染出来，也不会被滑到"恒成立（阶段 6.6 QA D7）。
 */
internal fun visibleTopTabItems(topTabs: ShellTopTabs): List<ShellMenuItem> =
    topTabs.items.filter { it.visible }

/**
 * 解析生效的选中条目 id。
 *
 * 优先级：外部显式指定 > 契约里 `selected = true` 的条目 > 第一项（保证恒有高亮）。
 *
 * tab 区与 pager 共用：pager 的下标由本函数的返回值在**可见条目列表**里反查，
 * 故隐藏条目不会让选中态错位。
 */
internal fun resolveTopTabSelectedId(items: List<ShellMenuItem>, selectedItemId: String?): String? {
    if (selectedItemId != null && items.any { it.id == selectedItemId }) return selectedItemId
    return items.firstOrNull { it.selected }?.id ?: items.firstOrNull()?.id
}

/** 壳 tab 图标尺寸（与 app 侧 MmaIcon 默认一致：24dp）。 */
private val SHELL_TAB_ICON_SIZE = 24.dp

/** 顶栏 tab 栏最小高度（组件尺寸语义，与 M3 TabRow 规范值同量级，落在令牌白名单）。 */
private val TOP_TABS_MIN_HEIGHT = 48.dp // token-gate: allow:顶栏tab栏规范高度（组件尺寸语义，非间距）
