package com.make.apk.ui.viewmodel

import com.make.apk.data.model.ProjectDefaults
import com.make.apk.data.model.ShellPreset
import com.make.apk.data.model.ShellPresets
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
     * 内容区运行时配置（喂给 `WebViewScreen`）。
     *
     * 仅当项目**可预览**（URL 项目）时非 null；点击 tab / 底栏条目改变目标地址时，
     * 本字段的 `entry` 会被替换为新的 URL → WebView 重新加载目标地址。
     */
    val runtimeConfig: WebRuntimeConfig? = null,
    /** 是否可预览（当前仅 URL 项目可预览）。 */
    val previewable: Boolean = false,
    /** 不可预览时的可见提示（用户能看懂的原因）。 */
    val unsupportedMessage: String? = null,
    /** 全部预置子模板（选择 UI 的数据源）。 */
    val presets: List<ShellPreset> = ShellPresets.ALL,
    /**
     * 当前选中的预置子模板 id；**null = 不高亮任何预置**（"自定义"态）。
     *
     * ## 不变式（高亮 == 渲染）
     *
     * 非 null 时，该 id **必须**由"实际渲染的壳"反查得到（[ShellPresets.matchingBySpec]），
     * 故 [ShellPresets.byId] 解析出的预置契约与 [shellSpec] **逐字段相等**——
     * 即"选择条高亮的模板"与"实际渲染的壳"永远同源，不可能出现
     * "高亮经典款、实际渲染极简款"这类状态不一致。
     *
     * null 表示"当前渲染的壳是项目自定义壳，与任何预置都不相等"，
     * 此时选择条不高亮任何预置（改显示"自定义"态），从根上消灭不一致。
     */
    val selectedPresetId: String? = ShellPresets.CLASSIC.id,
    /** 当前生效的壳契约（= 选中预置子模板的 spec，或项目自定义壳）。 */
    val shellSpec: ShellSpec = ShellPresets.CLASSIC.spec,
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
