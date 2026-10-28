package com.make.apk.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.make.apk.data.model.ProjectDefaults
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.ShellTopTabs

/**
 * 壳渲染入口（阶段 6.2 · L3，阶段 6.5 增抽屉区）——**整个壳只有一个组合根**。
 *
 * ## 组合顺序
 *
 * 顶栏 → 顶栏 tab → 内容区 → 底栏（自上而下），四区各自的 `enabled` 开关**逐一**决定
 * 对应 UI 是否出现；`ShellSpec.DEFAULT` 四区全关 → 渲染结果等价于"裸 WebView"（阶段 5 现状）。
 *
 * 抽屉（[com.make.apk.data.model.ShellDrawer]）是**唯一不占行的区**：它不排在自上而下的
 * 序列里，而是把整帧**包起来**（原生抽屉的等价形态），入口在顶栏左侧的汉堡按钮。
 * 该按钮的**实际**出现条件是 `drawer.enabled && topBar.enabled`（见下方实现说明）——
 * 顶栏关掉时没有承载汉堡的行，抽屉退化为 M3 边缘手势触达（与原生抽屉一致），
 * 故 KDoc 不承诺"抽屉开启 ⇒ 必有可见汉堡按钮"。
 *
 * ## 为什么是"唯一入口"
 *
 * 预览态与将来的导出壳都**只**通过本函数渲染壳，
 * 于是"预览 == 导出 ⟺ 同一份 ShellSpec + 同一份 ShellRenderer"这条一致性公式才有落点：
 * 壳的组合顺序、区域开关语义、选中态规则全都封在这一个函数里，
 * 不存在"预览走一套、导出走另一套"的分叉可能。
 *
 * ## 无状态约定
 *
 * - **不 new ViewModel**，不持有任何业务状态；
 * - 选中态与抽屉开合由外部通过 [selectedTopTabId] / [selectedBottomBarId] /
 *   [selectedDrawerItemId] / [drawerOpen] 传入（运行态不回写 spec）；
 * - 交互全部出口化为回调（[onBack] / [onShellAction] / [onTopTabSelected] /
 *   [onBottomBarSelected] / [onDrawerOpenChanged] / [onDrawerItemSelected]）。
 *
 * ## 滑动切页（阶段 6.6 收口）
 *
 * 顶栏 tab 兼作 pager 指示器（[ShellTopTabs.swipeEnabled]）时，内容区由本函数**内部**
 * 用 [ShellTopTabsPager] 包成 `HorizontalPager`，逐页调用 [pagedContent]：
 * 切页行为（滑动落定 → 选中、选中 → 滚动、离屏页常驻）**只在本包内存在一份**，
 * 预览屏与导出壳只要都从本入口渲染，就自动获得同一套滑动切页与内容暂停行为
 * （暂停由宿主按 `drawer.pauseContentWhileOpen` 契约注入到 [pagedContent] / [content] 里），
 * 不存在"预览走一套、导出走另一套"的分叉。
 *
 * [pagedContent] 为空（默认）时回落到单页 [content]，
 * 故 `ShellSpec.DEFAULT`（四区全关）与未开启滑动的壳**零回归**。
 *
 * ## 依赖边界
 *
 * 本文件及 `ui/shell/` 包内**不 import** `com.make.apk.ui.theme.*`：
 * 颜色 / 字体 / 形状 / 尺寸一律经 [ShellRenderEnv] 注入，
 * 而 [ShellRenderEnv] 的唯一派生点是 [ShellRenderEnvFactory]（壳包内唯一接触 `MaterialTheme` 的文件）。
 *
 * @param spec 壳契约。
 * @param modifier 修饰符。
 * @param projectThemeColor L1 的项目主题色（ARGB），用于派生默认强调色。
 * @param env 渲染环境；默认由 [ShellRenderEnvFactory.fromSpec] 从 [spec] + [projectThemeColor] 派生。
 * @param title 顶栏标题回退值（项目名）。
 * @param iconResolver 逻辑图标键解析器。
 * @param selectedTopTabId 当前选中的顶栏 tab id（null = 用契约里的默认选中）。
 * @param selectedBottomBarId 当前选中的底栏 tab id（null = 用契约里的默认选中）。
 * @param selectedDrawerItemId 当前选中的抽屉菜单项 id（null = 无高亮，抽屉是动作列表而非分页导航）。
 * @param drawerOpen 抽屉是否展开（受控）。
 * @param onBack 返回回调；null = 不显示返回按钮。
 * @param onShellAction 顶栏动作回调（宿主决定怎么执行，如 `NavigateUrl` 交给 WebView 加载）。
 * @param onTopTabSelected 顶栏 tab 选中回调。
 * @param onBottomBarSelected 底栏 tab 选中回调。
 * @param onDrawerOpenChanged 抽屉开合变化回调（手势 / 遮罩 / 菜单项点击后收起）。
 * @param onDrawerItemSelected 抽屉菜单项点击回调（宿主据此执行动作）。
 * @param onOverflow 底栏条目超限截断告警回调（参数 = 被丢弃的条目数）。
 * @param contentHeader 内容区**上方**的宿主工具位（如预览屏的"壳模板"选择条）。
 *   它不属于壳契约：不参与 `ShellSpec`，位置固定在顶栏 tab 与内容区之间、且**不随页滑动**
 *   （故不能塞进 [pagedContent] 里——那会让它变成某一页的一部分）。
 *   导出壳不传（默认 null），壳形态零污染。
 * @param pagedContent 分页内容区（参数 = 页下标 + 修饰符）；
 *   null（默认）时用 [content]。仅当"顶栏 tab 区开启 + 有可见条目 + [ShellTopTabs.swipeEnabled]
 *   + 本参数非空"四条件同时成立时才生效，由 [ShellTopTabsPager] 逐页调用。
 * @param content 内容区（由宿主放入 WebView 等）。
 */
@Composable
fun ShellHost(
    spec: ShellSpec,
    modifier: Modifier = Modifier,
    projectThemeColor: Int = ProjectDefaults.DEFAULT_THEME_COLOR,
    env: ShellRenderEnv = ShellRenderEnvFactory.fromSpec(spec, projectThemeColor),
    title: String = "",
    iconResolver: ShellIconResolver = rememberShellIconResolver(),
    selectedTopTabId: String? = null,
    selectedBottomBarId: String? = null,
    selectedDrawerItemId: String? = null,
    drawerOpen: Boolean = false,
    onBack: (() -> Unit)? = null,
    onShellAction: (ShellMenuItem) -> Unit = {},
    onTopTabSelected: (ShellMenuItem) -> Unit = {},
    onBottomBarSelected: (ShellMenuItem) -> Unit = {},
    onDrawerOpenChanged: (Boolean) -> Unit = {},
    onDrawerItemSelected: (ShellMenuItem) -> Unit = {},
    onOverflow: (Int) -> Unit = {},
    contentHeader: (@Composable () -> Unit)? = null,
    pagedContent: (@Composable (Int, Modifier) -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    // 汉堡入口需要顶栏来承载：抽屉区开启**且**顶栏区开启时才注入（null = 不渲染）。
    // 顶栏关掉时抽屉仍可用——退回 M3 的边缘手势（ModalNavigationDrawer 默认开启），
    // 与原生抽屉行为一致，不做"凭空造一个按钮"的假承诺。
    val openDrawer: (() -> Unit)? = if (spec.drawer.enabled && spec.topBar.enabled) {
        { onDrawerOpenChanged(true) }
    } else {
        null
    }

    // 可见 tab 条目：tab 区与 pager 共用同一份顺序（隐藏条目既不成 tab 也不成页）。
    val visibleTopTabs = visibleTopTabItems(spec.topTabs)
    val effectiveTopTabId = resolveTopTabSelectedId(visibleTopTabs, selectedTopTabId)
    // 选中态下标基于**可见条目列表**反查：隐藏条目不会让选中态错位。
    val selectedTopTabIndex = visibleTopTabs.indexOfFirst { it.id == effectiveTopTabId }
    // 滑动切页的四个前提条件（缺一即回落到单页内容区，保证 DEFAULT 壳零回归）。
    val activePagedContent = pagedContent?.takeIf {
        spec.topTabs.enabled && spec.topTabs.swipeEnabled && visibleTopTabs.isNotEmpty()
    }

    val frame: @Composable (Modifier) -> Unit = { frameModifier ->
        Column(modifier = frameModifier.fillMaxSize()) {
            if (spec.topBar.enabled) {
                ShellTopBarSection(
                    topBar = spec.topBar,
                    env = env,
                    title = title,
                    iconResolver = iconResolver,
                    onBack = onBack,
                    onShellAction = onShellAction,
                    onOpenDrawer = openDrawer,
                )
            }
            if (spec.topTabs.enabled) {
                ShellTopTabsSection(
                    topTabs = spec.topTabs,
                    env = env,
                    iconResolver = iconResolver,
                    selectedItemId = selectedTopTabId,
                    onItemSelected = onTopTabSelected,
                )
            }
            // 宿主工具位：固定夹在顶栏 tab 与内容区之间，两条内容分支都渲染它
            // （否则"开启滑动"就变成"预览工具消失"）。
            contentHeader?.invoke()
            if (activePagedContent != null) {
                ShellTopTabsPager(
                    pageCount = visibleTopTabs.size,
                    selectedIndex = selectedTopTabIndex.coerceAtLeast(0),
                    onPageSettled = { page ->
                        val item = visibleTopTabs.getOrNull(page)
                        // 幂等守卫：落点页已是选中页则不再回写选中态（杜绝回环抖动）。
                        if (item != null && item.id != effectiveTopTabId) onTopTabSelected(item)
                    },
                    modifier = Modifier.weight(1f),
                ) { page -> activePagedContent(page, Modifier) }
            } else {
                content(Modifier.weight(1f))
            }
            if (spec.bottomBar.enabled) {
                ShellBottomBarSection(
                    bottomBar = spec.bottomBar,
                    env = env,
                    iconResolver = iconResolver,
                    selectedItemId = selectedBottomBarId,
                    onItemSelected = onBottomBarSelected,
                    onOverflow = onOverflow,
                )
            }
        }
    }

    if (spec.drawer.enabled) {
        ShellDrawerSection(
            drawer = spec.drawer,
            env = env,
            iconResolver = iconResolver,
            open = drawerOpen,
            onOpenChanged = onDrawerOpenChanged,
            selectedItemId = selectedDrawerItemId,
            onItemSelected = onDrawerItemSelected,
            headerTitle = spec.topBar.titleOverride ?: title,
            modifier = modifier,
            content = { frame(Modifier) },
        )
    } else {
        frame(modifier)
    }
}
