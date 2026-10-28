package com.make.apk.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.make.apk.data.model.ShellBottomBar
import com.make.apk.data.model.ShellMenuItem

/**
 * 壳底栏区（[ShellBottomBar] → 原生底部导航栏等价物）。
 *
 * ## 约束
 *
 * - **无状态**：选中项由外部通过 [selectedItemId] 传入，交互出口化为 [onItemSelected]；
 * - **不读 app 主题**：颜色 / 字体 / 尺寸一律取自 [ShellRenderEnv]；
 * - **超限截断必告警**：条目数超过 [ShellBottomBar.maxItems] 时截断渲染，
 *   并通过 [onOverflow] 把"被丢弃了几条"上报给宿主——**不允许静默截断**。
 *
 * @param bottomBar 底栏契约（`enabled = false` 时本组件不应被渲染）。
 * @param env 渲染环境。
 * @param iconResolver 逻辑图标键解析器。
 * @param selectedItemId 当前选中条目 id；null 时回退到契约里 `selected = true` 的条目。
 * @param onItemSelected 选中回调（宿主据此切页 / 加载目标 URL）。
 * @param onOverflow 截断告警回调（参数 = 被丢弃的条目数）。
 */
@Composable
fun ShellBottomBarSection(
    bottomBar: ShellBottomBar,
    env: ShellRenderEnv,
    iconResolver: ShellIconResolver,
    selectedItemId: String?,
    onItemSelected: (ShellMenuItem) -> Unit,
    modifier: Modifier = Modifier,
    onOverflow: (Int) -> Unit = {},
) {
    val plan = ShellBottomBarPlanner.plan(bottomBar.items, bottomBar.maxItems)

    // 截断告警：只在"真的丢了东西"时上报一次（避免重组反复触发）。
    LaunchedEffect(plan.droppedCount) {
        if (plan.droppedCount > 0) onOverflow(plan.droppedCount)
    }

    if (plan.visibleItems.isEmpty()) return
    val effectiveSelectedId = resolveBottomSelectedId(plan.visibleItems, selectedItemId)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = env.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(env.bottomBarHeight)
                .navigationBarsPadding()
                .padding(horizontal = env.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            plan.visibleItems.forEach { item ->
                ShellBottomBarItem(
                    item = item,
                    selected = item.id == effectiveSelectedId,
                    showLabel = bottomBar.showLabels,
                    env = env,
                    iconResolver = iconResolver,
                    onClick = { onItemSelected(item) },
                )
            }
        }
    }
}

/**
 * 单个底栏条目：图标 + 文字标签（可选）。
 *
 * 选中态恒开：选中项图标与文字用强调色，未选中用次要色。
 */
@Composable
private fun RowScope.ShellBottomBarItem(
    item: ShellMenuItem,
    selected: Boolean,
    showLabel: Boolean,
    env: ShellRenderEnv,
    iconResolver: ShellIconResolver,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) env.accent else env.onSurfaceVariant
    val iconRes = iconResolver.resolve(item.iconRef)

    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = env.spaceXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = item.title,
                tint = contentColor,
                modifier = Modifier.size(SHELL_BOTTOM_ICON_SIZE),
            )
        }
        if (showLabel) {
            Spacer(modifier = Modifier.height(env.spaceXs))
            Text(
                text = item.title,
                style = env.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 解析生效的选中条目 id。
 *
 * 优先级：外部显式指定 > 契约里 `selected = true` 的条目 > 第一项（保证恒有高亮）。
 */
private fun resolveBottomSelectedId(items: List<ShellMenuItem>, selectedItemId: String?): String? {
    if (selectedItemId != null && items.any { it.id == selectedItemId }) return selectedItemId
    return items.firstOrNull { it.selected }?.id ?: items.firstOrNull()?.id
}

/** 壳底栏图标尺寸（与 app 侧 MmaIcon 默认一致：24dp）。 */
private val SHELL_BOTTOM_ICON_SIZE = 24.dp
