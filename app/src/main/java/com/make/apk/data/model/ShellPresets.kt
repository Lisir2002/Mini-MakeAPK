package com.make.apk.data.model

/**
 * 预置子模板（阶段 6.2 · 预览侧）。
 *
 * ## 定位
 *
 * 预置子模板是 **[ShellSpec] 的常量实例**——不是另一套数据模型，
 * 因此"预览用的壳"与"未来导出的壳"读的是同一种结构、同一个渲染器。
 * 后续"壳模板库"阶段只需扩充本文件 + 加选择 UI，**渲染层零改动**。
 *
 * ## 不落库
 *
 * 预置子模板是**出厂常量**，不写入 `projects.shell_spec_json`：
 * 落库的永远是"用户最终确认的那一份 spec"，预置只是"起手式"。
 *
 * ## 示例 URL 说明
 *
 * 三套模板中 tab / 底栏条目的目标 URL 均为 **占位示例地址**
 * （`https://example.com` 等 `example.com` 保留域），不代表真实站点；
 * 预览时**内容区默认加载项目自身的入口 URL**，仅当用户主动点击某个 tab 时才跳到示例地址。
 */
data class ShellPreset(
    /** 稳定标识（用于状态回传与选择 UI 的 key）。 */
    val id: String,
    /** 展示名（中文，用户可见）。 */
    val displayName: String,
    /** 一句话说明（用户可见）。 */
    val description: String,
    /** 壳契约本体。 */
    val spec: ShellSpec,
)

/**
 * 预置子模板清单（纯 Kotlin 常量，可 JVM 单测）。
 *
 * 三套覆盖三种典型形态：
 * - [CLASSIC] 经典款：顶栏（标题 + 返回）+ 底栏 3 tab；
 * - [MINIMAL] 极简款：仅顶栏（标题 + 返回），无底栏无 tab；
 * - [TABBED] 内容款：顶栏 + 顶栏 tab（3 项，带选中指示器）+ 底栏。
 */
object ShellPresets {

    /** 占位示例网址（保留域，仅作演示用）。 */
    const val EXAMPLE_URL: String = "https://example.com"

    /** 占位示例网址 2（保留域，仅作演示用）。 */
    const val EXAMPLE_URL_SECOND: String = "https://example.org"

    /** 占位示例网址 3（保留域，仅作演示用）。 */
    const val EXAMPLE_URL_THIRD: String = "https://example.net"

    /** 经典款：顶栏 + 底栏 3 tab。 */
    val CLASSIC: ShellPreset = ShellPreset(
        id = "classic",
        displayName = "经典款",
        description = "顶栏（标题 + 返回）+ 底栏 3 个 tab（首页 / 发现 / 我的）",
        spec = ShellSpec(
            topBar = ShellTopBar(
                enabled = true,
                showTitle = true,
                titleOverride = null,
                showBackButton = true,
                actions = listOf(
                    ShellMenuItem(
                        id = "itm_classic_refresh",
                        title = "刷新",
                        iconRef = ShellIconKeys.REFRESH,
                        action = ShellAction.NavigateUrl(EXAMPLE_URL),
                    ),
                ),
            ),
            topTabs = ShellTopTabs(enabled = false),
            bottomBar = ShellBottomBar(
                enabled = true,
                items = listOf(
                    ShellMenuItem(
                        id = "itm_classic_home",
                        title = "首页",
                        iconRef = ShellIconKeys.HOME,
                        action = ShellAction.NavigateUrl(EXAMPLE_URL),
                        selected = true,
                    ),
                    ShellMenuItem(
                        id = "itm_classic_discover",
                        title = "发现",
                        iconRef = ShellIconKeys.DISCOVER,
                        action = ShellAction.NavigateUrl(EXAMPLE_URL_SECOND),
                    ),
                    ShellMenuItem(
                        id = "itm_classic_profile",
                        title = "我的",
                        iconRef = ShellIconKeys.PROFILE,
                        action = ShellAction.NavigateUrl(EXAMPLE_URL_THIRD),
                    ),
                ),
                showLabels = true,
                maxItems = 5,
            ),
            drawer = ShellDrawer(enabled = false),
        ),
    )

    /** 极简款：仅顶栏（标题 + 返回），无底栏无 tab。 */
    val MINIMAL: ShellPreset = ShellPreset(
        id = "minimal",
        displayName = "极简款",
        description = "仅顶栏（标题 + 返回），无底栏、无顶栏 tab",
        spec = ShellSpec(
            topBar = ShellTopBar(
                enabled = true,
                showTitle = true,
                titleOverride = null,
                showBackButton = true,
                actions = emptyList(),
            ),
            topTabs = ShellTopTabs(enabled = false),
            bottomBar = ShellBottomBar(enabled = false),
            drawer = ShellDrawer(enabled = false),
        ),
    )

    /** 内容款：顶栏 + 顶栏 tab（3 项，带选中指示器）+ 底栏。 */
    val TABBED: ShellPreset = ShellPreset(
        id = "tabbed",
        displayName = "内容款",
        description = "顶栏 + 顶栏 tab（3 项，带选中指示器）+ 底栏",
        spec = ShellSpec(
            topBar = ShellTopBar(
                enabled = true,
                showTitle = true,
                titleOverride = null,
                showBackButton = true,
                actions = listOf(
                    ShellMenuItem(
                        id = "itm_tabbed_refresh",
                        title = "刷新",
                        iconRef = ShellIconKeys.REFRESH,
                        action = ShellAction.NavigateUrl(EXAMPLE_URL),
                    ),
                ),
            ),
            topTabs = ShellTopTabs(
                enabled = true,
                items = listOf(
                    ShellMenuItem(
                        id = "itm_tabbed_home",
                        title = "首页",
                        iconRef = ShellIconKeys.HOME,
                        action = ShellAction.NavigateUrl(EXAMPLE_URL),
                        selected = true,
                    ),
                    ShellMenuItem(
                        id = "itm_tabbed_discover",
                        title = "发现",
                        iconRef = ShellIconKeys.DISCOVER,
                        action = ShellAction.NavigateUrl(EXAMPLE_URL_SECOND),
                    ),
                    ShellMenuItem(
                        id = "itm_tabbed_profile",
                        title = "我的",
                        iconRef = ShellIconKeys.PROFILE,
                        action = ShellAction.NavigateUrl(EXAMPLE_URL_THIRD),
                    ),
                ),
                // 默认只做导航（点击切换）：滑动切页需宿主显式打开，此处保持关闭。
                swipeEnabled = false,
                showSelectionIndicator = true,
                selectionStyle = ShellSelectionStyle.INDICATOR,
            ),
            bottomBar = ShellBottomBar(
                enabled = true,
                items = listOf(
                    ShellMenuItem(
                        id = "itm_tabbed_bottom_home",
                        title = "首页",
                        iconRef = ShellIconKeys.HOME,
                        action = ShellAction.NavigateUrl(EXAMPLE_URL),
                        selected = true,
                    ),
                    ShellMenuItem(
                        id = "itm_tabbed_bottom_discover",
                        title = "发现",
                        iconRef = ShellIconKeys.DISCOVER,
                        action = ShellAction.NavigateUrl(EXAMPLE_URL_SECOND),
                    ),
                    ShellMenuItem(
                        id = "itm_tabbed_bottom_profile",
                        title = "我的",
                        iconRef = ShellIconKeys.PROFILE,
                        action = ShellAction.NavigateUrl(EXAMPLE_URL_THIRD),
                    ),
                ),
                showLabels = true,
                maxItems = 5,
            ),
            drawer = ShellDrawer(enabled = false),
        ),
    )

    /** 全部预置子模板（展示顺序即选择 UI 顺序）。 */
    val ALL: List<ShellPreset> = listOf(CLASSIC, MINIMAL, TABBED)

    /** 按 id 取预置；未知 id（含 null）兜底为 [CLASSIC]。 */
    fun byId(id: String?): ShellPreset = ALL.firstOrNull { it.id == id } ?: CLASSIC

    /**
     * 找出与给定壳契约**逐字段完全相等**的预置；没有完全相等的预置时返回 `null`。
     *
     * ## 用途
     *
     * 预览侧用它把"当前渲染的壳"反查回"应当高亮的预置 id"——
     * 两端同源，故"高亮项 == 渲染项"这条不变式**由构造保证**，
     * 而不是靠两处各写各的、指望它们碰巧一致。
     *
     * 返回 `null` 表示"当前壳不属于任何预置"（项目自定义壳）→ 选择条不高亮任何预置。
     */
    fun matchingBySpec(spec: ShellSpec): ShellPreset? = ALL.firstOrNull { it.spec == spec }
}

/**
 * 壳图标**逻辑键**（不是 Android 资源 id）。
 *
 * 预览侧由 app 的 `ShellIconResolver` 解析为 drawable，
 * 导出侧由壳工程解析为内联资产 → 两侧同键同形（设计文档 §5.4 R1）。
 */
object ShellIconKeys {
    /** 刷新。 */
    const val REFRESH: String = "refresh"

    /** 新增。 */
    const val ADD: String = "add"

    /** 首页。 */
    const val HOME: String = "home"

    /** 发现。 */
    const val DISCOVER: String = "discover"

    /** 我的。 */
    const val PROFILE: String = "profile"
}
