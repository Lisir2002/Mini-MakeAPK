package com.make.apk.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.make.apk.data.model.ShellDrawer
import com.make.apk.data.model.ShellDrawerSource
import com.make.apk.data.model.ShellDrawerWidthMode
import com.make.apk.data.model.ShellMenuItem

/**
 * 壳侧边栏（抽屉）区（阶段 6.5 · [ShellDrawer] → 原生抽屉等价物）。
 *
 * ## 结构
 *
 * 抽屉与其余三区不同：**它不是自上而下的一行，而是包裹整个内容区的抽屉容器**。
 * 因此本组件把 [content] 包进 `ModalNavigationDrawer`，由顶栏的菜单按钮
 * （见 [ShellTopBarSection] 的 `onOpenDrawer`）驱动开合。
 *
 * ## 两种内容源（[ShellDrawerSource]）
 *
 * | 源 | 渲染 | 说明 |
 * |---|---|---|
 * | [ShellDrawerSource.NATIVE] | Compose `NavigationDrawerItem` 列表 | **本阶段主路径**，条目来自 [ShellDrawer.items] |
 * | [ShellDrawerSource.WEB] | **显式占位卡片** | Web 覆盖层需独立加载管线；契约与入口已留，渲染层**不静默失败**——把"未渲染"这件事画出来 |
 *
 * ## 约束
 *
 * - **无状态**：开合由外部 [open] 传入，交互出口化为 [onOpenChanged] / [onItemSelected]；
 * - **不读 app 主题**：颜色 / 字体 / 尺寸一律取自 [ShellRenderEnv]；
 * - [`pauseContentWhileOpen`][ShellDrawer.pauseContentWhileOpen] **不在本组件执行**——
 *   它是"宿主可选执行"的契约字段（是否暂停内容区 WebView 由宿主决定），
 *   渲染层只负责把开合状态如实回传。
 *
 * @param drawer 抽屉契约（`enabled = false` 时宿主不应渲染本组件）。
 * @param env 渲染环境。
 * @param iconResolver 逻辑图标键解析器。
 * @param open 抽屉是否展开（受控）。
 * @param onOpenChanged 开合变化回调（含手势展开 / 遮罩点击 / 条目点击后自动收起）。
 * @param selectedItemId 当前选中菜单项 id；null 时回退到契约里 `selected = true` 的条目。
 * @param onItemSelected 菜单项点击回调（**先回调，再收起抽屉**）。
 * @param headerTitle 抽屉头部标题（宿主传入；与顶栏标题同源，缺省回落文案"应用菜单"）。
 * @param content 被抽屉包裹的页面主体。
 */
@Composable
fun ShellDrawerSection(
    drawer: ShellDrawer,
    env: ShellRenderEnv,
    iconResolver: ShellIconResolver,
    open: Boolean,
    onOpenChanged: (Boolean) -> Unit,
    selectedItemId: String?,
    onItemSelected: (ShellMenuItem) -> Unit,
    headerTitle: String = "",
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val drawerState = rememberDrawerState(
        initialValue = if (open) androidx.compose.material3.DrawerValue.Open else androidx.compose.material3.DrawerValue.Closed,
    )

    // 外部状态 → 抽屉（受控同步）：幂等守卫避免"展开→回调→再展开"的回环抖动。
    LaunchedEffect(open) {
        if (open && !drawerState.isOpen) drawerState.open()
        if (!open && drawerState.isOpen) drawerState.close()
    }

    // 抽屉（手势 / 遮罩）→ 外部状态。
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen != open) onOpenChanged(drawerState.isOpen)
    }

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(resolveDrawerWidth(drawer = drawer, env = env)),
            ) {
                ShellDrawerHeader(title = headerTitle, env = env)
                HorizontalDivider(color = env.outline)
                when (drawer.contentSource) {
                    ShellDrawerSource.NATIVE -> ShellDrawerNativeMenu(
                        items = drawer.items.filter { it.visible },
                        env = env,
                        iconResolver = iconResolver,
                        selectedItemId = selectedItemId,
                        onItemSelected = { item ->
                            onItemSelected(item)
                            onOpenChanged(false)
                        },
                    )

                    ShellDrawerSource.WEB -> ShellDrawerWebPlaceholder(
                        url = drawer.webMenuUrl,
                        env = env,
                    )
                }
            }
        },
        content = content,
    )
}

/** 抽屉头部：项目名 + 说明（固定两行，切换菜单不跳动）。 */
@Composable
private fun ShellDrawerHeader(
    title: String?,
    env: ShellRenderEnv,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = env.spaceMd, vertical = env.spaceMd),
    ) {
        Text(
            text = title?.takeIf { it.isNotBlank() } ?: DRAWER_DEFAULT_TITLE,
            style = env.typography.titleMedium,
            color = env.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(env.spaceXs))
        Text(
            text = DRAWER_SUBTITLE,
            style = env.typography.bodySmall,
            color = env.onSurfaceVariant,
        )
    }
}

/**
 * 原生菜单列表。
 *
 * **空列表不静默**：给出"暂无菜单项"占位，让用户知道"这里以后会长出菜单"，
 * 而不是"抽屉点了没反应"。
 */
@Composable
private fun ShellDrawerNativeMenu(
    items: List<ShellMenuItem>,
    env: ShellRenderEnv,
    iconResolver: ShellIconResolver,
    selectedItemId: String?,
    onItemSelected: (ShellMenuItem) -> Unit,
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = env.spaceMd),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = DRAWER_EMPTY_TEXT,
                style = env.typography.bodyMedium,
                color = env.onSurfaceVariant,
            )
        }
        return
    }

    val effectiveSelectedId = resolveDrawerSelectedId(items, selectedItemId)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = env.spaceSm),
        verticalArrangement = Arrangement.spacedBy(env.spaceXs),
    ) {
        items.forEach { item ->
            val iconRes = iconResolver.resolve(item.iconRef)
            NavigationDrawerItem(
                label = {
                    Text(
                        text = item.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                selected = item.id == effectiveSelectedId,
                onClick = { onItemSelected(item) },
                icon = if (iconRes == null) {
                    null
                } else {
                    {
                        Icon(
                            painter = painterResource(id = iconRes),
                            // 描述交给同一行的 label（内容同为 item.title）：
                            // 图标再挂一条同名描述会让 TalkBack 把条目名播两遍。
                            contentDescription = null,
                        )
                    }
                },
                shape = env.shapes.extraLarge,
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = env.accentContainer,
                    unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    selectedIconColor = env.onAccentContainer,
                    unselectedIconColor = env.onSurfaceVariant,
                    selectedTextColor = env.onAccentContainer,
                    unselectedTextColor = env.onSurfaceVariant,
                ),
                modifier = Modifier.padding(horizontal = env.spaceSm),
            )
        }
    }
}

/**
 * Web 覆盖层菜单的**显式占位**。
 *
 * 契约里 [ShellDrawer.webMenuUrl] 已登记，但渲染 Web 覆盖层需要独立的加载管线
 * 与主程序 WebView 之外的实例管理——本阶段不实现。
 * 这里把"未实现"画成一张可见的卡片（含目标地址），
 * 而不是留白：留白会被误判为"配置没生效"。
 */
@Composable
private fun ShellDrawerWebPlaceholder(
    url: String?,
    env: ShellRenderEnv,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(all = env.spaceMd),
        verticalArrangement = Arrangement.spacedBy(env.spaceXs),
    ) {
        Text(
            text = DRAWER_WEB_TITLE,
            style = env.typography.titleSmall,
            color = env.onSurface,
        )
        Text(
            text = url?.takeIf { it.isNotBlank() } ?: DRAWER_WEB_NO_URL,
            style = env.typography.bodySmall,
            color = env.onSurfaceVariant,
        )
        Text(
            text = DRAWER_WEB_NOTICE,
            style = env.typography.bodySmall,
            color = env.onSurfaceVariant,
        )
    }
}

/**
 * 解析生效的选中菜单项 id。
 *
 * 优先级：外部显式指定 > 契约里 `selected = true` 的条目 > **null**（抽屉允许无高亮项——
 * 抽屉是"动作列表"而非"分页导航"，没有"当前页"这一说）。
 */
private fun resolveDrawerSelectedId(items: List<ShellMenuItem>, selectedItemId: String?): String? {
    if (selectedItemId != null && items.any { it.id == selectedItemId }) return selectedItemId
    return items.firstOrNull { it.selected }?.id
}

/**
 * 解析抽屉宽度（dp）；[ShellDrawerWidthMode.CUSTOM] 档的非法值（≤0）回落到中档，
 * 越界值钳制到 [ShellDrawerWidths.minDp] / [ShellDrawerWidths.maxDp] 之间。
 *
 * 具体档位数值取自 [ShellRenderEnv.drawerWidths]（宿主注入），壳内不写死尺寸。
 */
internal fun resolveDrawerWidth(drawer: ShellDrawer, env: ShellRenderEnv): Dp {
    val width = drawer.width
    val widths = env.drawerWidths
    return when (width.mode) {
        ShellDrawerWidthMode.SMALL -> widths.small
        ShellDrawerWidthMode.MEDIUM -> widths.medium
        ShellDrawerWidthMode.LARGE -> widths.large
        ShellDrawerWidthMode.CUSTOM ->
            width.customDp
                .takeIf { it > 0 }
                ?.coerceIn(widths.minDp, widths.maxDp)
                ?.dp
                ?: widths.medium
    }
}

/** 抽屉头部标题缺省文案。 */
private const val DRAWER_DEFAULT_TITLE: String = "应用菜单"

/** 抽屉头部副标题。 */
private const val DRAWER_SUBTITLE: String = "原生抽屉 · 与导出壳同源"

/** 抽屉无菜单项时的占位文案。 */
private const val DRAWER_EMPTY_TEXT: String = "暂无菜单项"

/** Web 覆盖层占位卡片的标题。 */
private const val DRAWER_WEB_TITLE: String = "Web 覆盖层菜单"

/** Web 覆盖层未配置地址时的文案。 */
private const val DRAWER_WEB_NO_URL: String = "未配置覆盖层地址"

/** Web 覆盖层本阶段不渲染的显式说明。 */
private const val DRAWER_WEB_NOTICE: String = "本阶段不渲染 Web 覆盖层，仅登记目标地址"
