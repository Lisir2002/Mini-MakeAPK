package com.make.apk.data.model

import kotlinx.serialization.Serializable

/**
 * 可配置注入壳契约（阶段 6.1 · L2，详见 docs/design/shell-spec-design.md §1）。
 *
 * 与 L1 [WebRuntimeConfig] 的分工：
 * - L1 = **内容怎么跑**（入口 / webSettings / 主题色 / 图标 / 版本）；
 * - L2 = **壳长什么样**（顶栏 / 顶栏 tab / 底栏 tab / 侧边栏 四区开关与条目）。
 *
 * 落地约束（务必保持）：
 * - **纯 Kotlin，零 `android.*` 依赖** → 可 JVM 单测、可被导出壳工程直接消费；
 * - **只允许 kotlinx.serialization** → 主程序预览与未来导出壳用同一个序列化器读同一份 JSON；
 * - [specVersion] 必带 → 该对象会被写进 DB 与将来 APK 的 assets，必须能向后兼容解析；
 * - **新增字段一律带默认值** → 老数据反序列化不炸（配合 RuntimeJson 的 ignoreUnknownKeys）。
 *
 * **不变量**（设计文档 §8.2）：本对象的任何字段都不得被"运行时状态"污染
 * （选中态、抽屉开合、滚动位置一律不入 spec），
 * 否则预览与导出会因运行时残留而漂移。
 */
@Serializable
data class ShellSpec(
    /** 契约版本号（见 [ShellSpec.CURRENT_SPEC_VERSION]）。 */
    val specVersion: Int = CURRENT_SPEC_VERSION,
    /** 主题覆写（null = 跟随 L1 的 `themeColor`）。 */
    val theme: ShellTheme = ShellTheme(),
    /** 顶栏区（含标题、右侧动作按钮）。 */
    val topBar: ShellTopBar = ShellTopBar(),
    /** 顶栏 tab 区（既是导航栏，也是 pager 指示器）。 */
    val topTabs: ShellTopTabs = ShellTopTabs(),
    /** 底栏 tab 区。 */
    val bottomBar: ShellBottomBar = ShellBottomBar(),
    /** 侧边栏（抽屉）区（阶段 6.5 起由 [ShellDrawer] 渲染原生抽屉）。 */
    val drawer: ShellDrawer = ShellDrawer(),
) {
    companion object {
        /** 当前契约版本。新增字段时 +1，并在此登记升级说明。 */
        const val CURRENT_SPEC_VERSION: Int = 1

        /** 契约版本升级说明（写进文档与导出壳的兼容判断）。 */
        const val VERSION_NOTES: String =
            "v1: 四区（topBar/topTabs/bottomBar/drawer）+ ShellAction 仅 NavigateUrl 可用"

        /** 默认壳：四区全关，等价于"裸 WebView"（与阶段 5 现状一致，零回归）。 */
        val DEFAULT: ShellSpec = ShellSpec()
    }
}

/**
 * 壳主题覆写。
 *
 * **默认跟随 L1**：[useProjectThemeColor] = true 时，壳的强调色取 `WebRuntimeConfig.themeColor`
 * （应用主题色），保证"项目主题色"与"壳强调色"天然一致，不产生第二处真值。
 * 用户显式覆写时（[themeColorOverride] 非 null）才用壳自己的颜色。
 */
@Serializable
data class ShellTheme(
    /** 是否跟随项目主题色（L1 `themeColor`）。true 时 [themeColorOverride] 被忽略。 */
    val useProjectThemeColor: Boolean = true,
    /** 壳强调色覆写（ARGB）；null = 跟随项目主题色。 */
    val themeColorOverride: Int? = null,
    /** 顶栏是否沉浸（背景铺满状态栏区域）。 */
    val edgeToEdgeTopBar: Boolean = true,
    /** 底栏是否沉浸。 */
    val edgeToEdgeBottomBar: Boolean = true,
) {
    /** 解析最终强调色：null 表示"调用方用 L1 的 themeColor"。 */
    fun resolveAccent(projectThemeColor: Int): Int =
        if (useProjectThemeColor) projectThemeColor else (themeColorOverride ?: projectThemeColor)
}

/**
 * 壳条目（顶栏动作按钮 / 顶栏 tab / 底栏 tab / 抽屉菜单项 通用）。
 *
 * 字段设计要点：
 * - [id] **稳定标识**：跨编辑会话不变，用于选中态锚点、持久化 key、埋点、导出壳的复用 key。
 *   约定由 UI 生成短码（如 `itm_9f2a`），**不可用下标**（重排即漂移）。
 * - [iconRef] 是**逻辑图标键**（如 `refresh` / `add` / `home`），不是 Android 资源 id / 资源名。
 *   预览侧由 app 的 `ShellIconResolver` 解析为 drawable，导出侧由壳工程解析为内联资产 → 两侧同键同形。
 * - [selected] 是**默认选中标记**（仅 tab 类区域有意义）；运行期选中态由渲染器持有，不回写 spec。
 * - [visible] 为 false 时条目保留在配置里但不渲染（"先关掉，后恢复"，不丢用户配置）。
 */
@Serializable
data class ShellMenuItem(
    /** 稳定标识（UI 生成，形如 `itm_a1b2`）。 */
    val id: String,
    /** 显示标题。 */
    val title: String,
    /** 逻辑图标键；null = 无图标（纯文字条目）。 */
    val iconRef: String? = null,
    /** 动作（当前仅 URL 可用，模型已开放）。 */
    @Serializable(with = ShellActionSurrogate::class)
    val action: ShellAction = ShellAction.NavigateUrl(""),
    /** 可见性开关（false = 保留配置但不渲染）。 */
    val visible: Boolean = true,
    /** 默认选中（tab 类区域生效；渲染期以运行态为准）。 */
    val selected: Boolean = false,
    /** 是否为入口根（tab 类区域生效）：标记后该 tab 即「回入口」的目标页与栈根。 */
    val isEntry: Boolean = false,
)

/**
 * 顶栏区（含标题 + 右侧动作按钮）。
 *
 * [actions] 与标题、顶栏 tab **分开建模**：动作按钮是"触发一次动作"，
 * tab 是"导航到一页"，两者生命周期与语义都不同，混在一个 list 里会让选中态语义混乱。
 */
@Serializable
data class ShellTopBar(
    /** 区域开关。false = 完全不渲染顶栏。 */
    val enabled: Boolean = false,
    /** 是否显示标题。 */
    val showTitle: Boolean = true,
    /** 标题文案；null = 用项目名（L1 `apkName`）。 */
    val titleOverride: String? = null,
    /** 是否显示返回按钮（预览态用于关闭预览）。 */
    val showBackButton: Boolean = true,
    /** 右侧动作按钮（如「添加」「刷新」）。 */
    val actions: List<ShellMenuItem> = emptyList(),
)

/**
 * 顶栏 tab 区。
 *
 * 双重身份：**既是导航栏，也是 pager 指示器**。
 * - [swipeEnabled] = true → 顶栏 tab 兼作分页指示器，内容区可左右滑动切页（阶段 6.4 已实现）；
 * - [swipeEnabled] = false → 顶栏 tab 只做导航（点击切页，不响应滑动）。
 *
 * **选中态恒开**（需求硬要求）：[showSelectionIndicator] 仅控制"指示器样式"，
 * 不允许关闭"当前页高亮"这件事本身；高亮恒在。
 */
@Serializable
data class ShellTopTabs(
    /** 区域开关。false = 不渲染顶栏 tab。 */
    val enabled: Boolean = false,
    /** 条目（每个 tab 一页）。 */
    val items: List<ShellMenuItem> = emptyList(),
    /** 是否允许左右滑动切页（tab 兼作 pager 指示器）；关闭时 tab 只做导航（点击切页）。 */
    val swipeEnabled: Boolean = true,
    /** 是否显示选中指示器（指示器样式，不影响"当前页高亮"本身）。 */
    val showSelectionIndicator: Boolean = true,
    /** 选中态呈现方式。 */
    val selectionStyle: ShellSelectionStyle = ShellSelectionStyle.INDICATOR,
)

/** 顶栏 tab 的选中态呈现方式。 */
@Serializable
enum class ShellSelectionStyle {
    /** 指示器条（M3 TabRow 风格）。 */
    INDICATOR,

    /** 药丸背景（胶囊高亮）。 */
    PILL,

    /** 仅文字颜色变化。 */
    TEXT,
}

/**
 * 底栏 tab 区。
 *
 * 与顶栏 tab 的差异：**底栏 tab 恒为导航**（无滑动切页语义）；
 * 条目数上限 [maxItems]（M3 NavigationBar 在窄屏下超过 5 项会挤压，故默认上限 5）。
 */
@Serializable
data class ShellBottomBar(
    /** 区域开关。false = 不渲染底栏。 */
    val enabled: Boolean = false,
    /** 条目。 */
    val items: List<ShellMenuItem> = emptyList(),
    /** 是否显示文字标签。 */
    val showLabels: Boolean = true,
    /** 条目数量上限（超出时渲染层截断并告警）。 */
    val maxItems: Int = 5,
)

/**
 * 侧边栏（抽屉）区。
 *
 * 主理人裁决：抽屉**本体是原生 Drawer**，内部菜单列表可以是 Web/HTML 渲染。
 * 阶段 6.5 已实现抽屉本体：原生抽屉 + [ShellDrawer.items] 的菜单列表；
 * Web 覆盖层（[contentSource] = WEB）仍是**契约已登记、渲染层未实现**的形态。
 */
@Serializable
data class ShellDrawer(
    /** 区域开关。false = 不渲染抽屉入口。 */
    val enabled: Boolean = false,
    /** 菜单条目（原生渲染路径使用）。 */
    val items: List<ShellMenuItem> = emptyList(),
    /** 抽屉内容来源。 */
    val contentSource: ShellDrawerSource = ShellDrawerSource.NATIVE,
    /** Web 覆盖层地址（`contentSource = WEB` 时生效；渲染层仅登记并在占位卡片上展示）。 */
    val webMenuUrl: String? = null,
    /** 抽屉宽度策略。 */
    val width: ShellDrawerWidth = ShellDrawerWidth(),
    /** 打开抽屉时是否暂停内容区 WebView（省资源；Web 覆盖层菜单打开时应暂停）。 */
    val pauseContentWhileOpen: Boolean = true,
)

/** 抽屉内容来源。 */
@Serializable
enum class ShellDrawerSource {
    /** 原生 Compose 列表（唯一已实现路径）。 */
    NATIVE,

    /** Web 覆盖层渲染菜单（契约已登记，渲染层未实现：画一张显式占位卡片）。 */
    WEB,
}

/** 抽屉宽度策略（dp 上限用尺寸令牌，此处只表达"取哪一档"）。 */
@Serializable
data class ShellDrawerWidth(
    /** 宽度档位。 */
    val mode: ShellDrawerWidthMode = ShellDrawerWidthMode.MEDIUM,
    /** 自定义宽度（dp）；mode = CUSTOM 时生效。 */
    val customDp: Int = 0,
)

/** 抽屉宽度档位。 */
@Serializable
enum class ShellDrawerWidthMode {
    /** 窄（约 240dp 量级）。 */
    SMALL,

    /** 中（约 300dp 量级）。 */
    MEDIUM,

    /** 宽（约 360dp 量级）。 */
    LARGE,

    /** 自定义（[ShellDrawerWidth.customDp]）。 */
    CUSTOM,
}

/**
 * 入口 tab（栈根）：首个 `isEntry = true` 的可见项；否则首个可见项；无可见项则 null。
 *
 * 「回入口」按钮 / [ShellAction.InternalPage] 都指向此处；无显式标记时回落首项，
 * 与"默认首项即入口"的直觉一致（向后兼容：老数据全为 false，仍取首项）。
 */
fun ShellTopTabs.entryTabItem(): ShellMenuItem? {
    val visible = items.filter { it.visible }
    return visible.firstOrNull { it.isEntry } ?: visible.firstOrNull()
}
