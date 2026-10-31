package com.make.apk.data.model

/**
 * URL 打开来源（W2 ④）：壳里每一个可能触发 URL 加载的角色。
 *
 * 与「打开方式」开关（[WebSettings.fitToApp]）共同决定目标（[UrlOpenTarget]），
 * 判定唯一事实源见 [resolveUrlOpenTarget]——预览与导出双侧共享本文件，判定同源。
 */
enum class UrlOpenOrigin {
    /** 项目入口（entry 首屏）。 */
    ENTRY,

    /** 顶栏 tab（导航骨架，兼 pager 指示器）。 */
    TOP_TAB,

    /** 底栏条目（导航骨架）。 */
    BOTTOM_TAB,

    /** 顶栏动作按钮。 */
    ACTION,

    /** 抽屉菜单项。 */
    DRAWER_ITEM,

    /** WebView 页内主帧 http/https 链接点击。 */
    IN_PAGE_LINK,
}

/**
 * URL 打开目标：应用内 WebView 承载，或交系统浏览器（Intent.ACTION_VIEW）。
 */
enum class UrlOpenTarget {
    /** 应用内嵌（WebView 加载，壳保持完整）。 */
    EMBEDDED,

    /** 外部浏览器（离开应用上下文）。 */
    EXTERNAL,
}

/**
 * 允许外链的来源集合——**壳骨架永不外开**。
 *
 * 触发语义一句话可解释：「壳的骨架留在应用内，内容跳转交给外部浏览器」：
 * - 入口（ENTRY）跳出即应用空白，壳不成立；
 * - 顶栏 tab / 底栏条目（TOP_TAB / BOTTOM_TAB）是导航骨架，切 tab 就跳出会让
 *   pager、选中态、回入口全部失效；
 * - OpenLocalPage 本地页面无 URL，类型隔离铁律，无"外开"可言（模型层直接不参与判定）；
 * - 只有顶栏动作按钮 / 抽屉菜单项 / 页内主帧链接这类"内容跳转"才可能外开。
 */
private val EXTERNAL_CAPABLE_ORIGINS: Set<UrlOpenOrigin> = setOf(
    UrlOpenOrigin.ACTION,
    UrlOpenOrigin.DRAWER_ITEM,
    UrlOpenOrigin.IN_PAGE_LINK,
)

/**
 * 解析 URL 打开目标（唯一事实源，预览与导出双侧调用同一函数）。
 *
 * 规则：`fitToApp = true`（默认）恒内嵌（与历史行为一致，零回归）；
 * `fitToApp = false` 时仅 [EXTERNAL_CAPABLE_ORIGINS] 内的来源外开，
 * 骨架来源（ENTRY / TOP_TAB / BOTTOM_TAB）仍内嵌。
 *
 * 纯 Kotlin、零 `android.*` 依赖：可 JVM 单测，可被导出壳工程直接消费。
 */
fun resolveUrlOpenTarget(settings: WebSettings, origin: UrlOpenOrigin): UrlOpenTarget =
    if (!settings.fitToApp && origin in EXTERNAL_CAPABLE_ORIGINS) {
        UrlOpenTarget.EXTERNAL
    } else {
        UrlOpenTarget.EMBEDDED
    }
