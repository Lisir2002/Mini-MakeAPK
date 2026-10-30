package com.make.apk.ui.viewmodel

import com.make.apk.data.model.ProjectDefaults
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.WebRuntimeConfig

/**
 * 壳预览屏 UI 状态（阶段 6.2 · 预览侧）。
 *
 * ## 状态边界
 *
 * - **只放"当前是什么"**：加载中 / 项目不存在 / 是否可预览 / 当前预置子模板 / 当前选中项；
 * - **不放"运行时残留"**：[currentUrl] 是"内容区当前加载的地址"，
 *   它由用户点击 tab / 底栏条目驱动，**不写回** [ShellSpec]（契约不变量，见 `ShellSpec` KDoc）；
 * - 预置子模板**不落库**：落库的永远是用户最终确认的那一份 spec，预置只是"起手式"。
 *
 * ## 不可变
 *
 * [shellSpec] 与 [presets] 一起构成"预览用的壳"；
 * 切换子模板只改这两个字段，**不改** [runtimeConfig]——
 * 这正是"切换壳不重载 WebView"的实现基础（内容运行时与壳形态彻底解耦）。
 */
data class ShellPreviewUiState(
    /** 是否正在加载（首帧为 true）。 */
    val loading: Boolean = true,
    /** 项目不存在（id 无效或已删除）。 */
    val projectNotFound: Boolean = false,
    /** 项目名（用作壳顶栏标题回退值）。 */
    val apkName: String = "",
    /** 项目主题色（ARGB）——壳强调色的来源。 */
    val themeColor: Int = ProjectDefaults.DEFAULT_THEME_COLOR,
    /**
     * **项目自身入口（只读真值）**：由 [com.make.apk.ui.screens.ShellPreviewScreen]
     * 所属 VM 在加载项目时写入一次，此后**任何壳动作都不改它**。
     *
     * ## 为什么必须有它
     *
     * [runtimeConfig] 的 `entry` 是"内容区**当前**加载的目标"，会被 tab / 底栏动作
     * 就地改写；而"打开本地页面"要的是**从项目文件集里取一页**——若把改写后的
     * entry 当取页源，访问过的页面会因为"入口名被改写 + 该文件已从附加文件集剔除"
     * 而从项目里消失，表现为：
     * - A → B → 再回 A：取不到（A 已不在源里）；
     * - 访问子页后回入口页：入口页也丢了（首屏不可再达）；
     * - 顶栏 tab 的 pager 各页都从被污染的 baseConfig 解析 → 分页漂移。
     *
     * 故本地页面一律 **从本字段解析**、只把结果写进 [runtimeConfig]（展示态），
     * 与"壳运行态不回写项目数据"同一不变式。
     */
    val projectEntry: ProjectEntry? = null,
    /**
     * 内容区运行时配置（喂给 `WebViewScreen`）。
     *
     * 仅当项目**可预览**（URL / HTML 项目）时非 null；点击 tab / 底栏条目改变目标地址时，
     * 本字段的 `entry` 会被替换为新的目标（URL 或本地页面）→ WebView 重新加载。
     * 它是**展示态**，不作为"项目有什么文件"的事实来源（那是 [projectEntry]）。
     */
    val runtimeConfig: WebRuntimeConfig? = null,
    /** 是否可预览（当前仅 URL 项目可预览）。 */
    val previewable: Boolean = false,
    /** 不可预览时的可见提示（用户能看懂的原因）。 */
    val unsupportedMessage: String? = null,
    /**
     * 当前生效的壳契约**恒等于项目自己存的 spec**（预览 == 导出的同源不变式）。
     *
     * ## 为什么不再有"预置子模板"字段
     *
     * 原状态带 `presets` / `selectedPresetId`，预览页据此渲染一条"壳模板"切换条，
     * 且当项目未配置壳时把渲染 spec 偷偷替换成 `ShellPresets.CLASSIC`——
     * 这直接破坏了"预览 == 导出"：导出用的是项目 spec（未配置 = 四区全关的纯网页），
     * 预览却显示带顶底栏的经典款，用户据此调完的配置装到真机上完全不是一回事。
     *
     * 预览页的职责只有一个：**如实渲染项目将要导出的那台"设备"**。
     * 想换壳去进阶配置页（预置壳 / 自定义都在那里配），配完回来预览即见。
     */
    val shellSpec: ShellSpec = ShellSpec.DEFAULT,
    /** 当前选中的顶栏 tab 条目 id。 */
    val selectedTopTabId: String? = null,
    /** 当前选中的底栏条目 id。 */
    val selectedBottomItemId: String? = null,
    /** 当前选中的抽屉菜单项 id；null = 无高亮（抽屉是动作列表，不是分页导航）。 */
    val selectedDrawerItemId: String? = null,
    /** 抽屉是否展开（**运行态**，不回写 spec）。 */
    val drawerOpen: Boolean = false,
    /**
     * 内容区是否应暂停（= 抽屉展开 && 契约 [com.make.apk.data.model.ShellDrawer.pauseContentWhileOpen]）。
     *
     * 由 VM 计算后交给页面执行，spec 自身不被运行状态污染。
     */
    val contentPaused: Boolean = false,
    /** 内容区当前加载的地址；null = 项目自身入口地址。 */
    val currentUrl: String? = null,
    /** 当前内容是否为预置模板的**示例地址**（`example.com` 保留域）。 */
    val isExampleContent: Boolean = false,
)
