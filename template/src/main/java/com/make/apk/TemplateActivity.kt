package com.make.apk

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.make.apk.R
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.ShellAction
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.data.model.ShellNativeAction
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.ShellTopTabs
import com.make.apk.data.model.UrlOpenOrigin
import com.make.apk.data.model.UrlOpenTarget
import com.make.apk.data.model.VersionScheme
import com.make.apk.data.model.WebRuntimeConfig
import com.make.apk.data.model.WebSettings
import com.make.apk.data.model.entryTabItem
import com.make.apk.data.model.resolveUrlOpenTarget
import com.make.apk.ui.screens.openExternal
import com.make.apk.ui.shell.ShellHost
import com.make.apk.ui.shell.ShellRenderEnvFactory
import com.make.apk.ui.shell.visibleTopTabItems
import com.make.apk.webcore.chrome.WebViewSurface
import com.make.apk.webcore.config.WebViewStorageIsolation
import com.make.apk.webcore.engine.TabEngineRegistry
import com.make.apk.webcore.engine.WebViewEngine
import kotlinx.serialization.json.Json

/** 模板壳的存储隔离后缀（W3 ⑬ 定格占位，与 app 预览的 isolationId 语义对齐）。 */
private const val TEMPLATE_ISOLATION_ID: String = "webapp_template"

/**
 * 子模板（插件式注入）落地验证入口（方向 1）。
 *
 * ## 它证明什么
 *
 * 本 Activity 与 app 的预览屏 [com.make.apk.ui.screens.ShellPreviewScreen] **消费同一份**
 * `ShellHost` 渲染器、同一套 `ShellSpec` 数据模型、同一套 webcore 浏览器内核
 * （源码经 sourceSets 指向 app，零副本）。区别仅在于：
 * - app 预览屏的壳来自「用户实时编辑的 ShellSpec」；
 * - 本模板的壳来自**编译期注入**的 `res/raw/shell_spec.json`（导出壳的最小化等价形态）。
 *
 * 两者喂给同一个 `ShellHost` + 同一个 `WebViewSurface` → 渲染结果天然一致，从而实证
 * 「预览 == 导出 ⟺ 同一份 ShellSpec + 同一份 ShellHost + 同一份内核」在真实 APK 产物上成立。
 *
 * ## 不变量
 *
 * 模板只作为壳的「消费方」：不修改壳源码、不污染 ShellSpec 运行时状态
 * （选中态/抽屉开合由本地 remember 持有，不回写 spec）。
 *
 * ## W4：tabStacks URL 栈退役（主控拍板 4）
 *
 * 旧实现每 tab 维护一条 URL 后退栈（压/弹/复位全在 Kotlin 侧），WebView 只被动重载。
 * W4 起：页内导航交给 **WebView 原生 back/forward**（历史在引擎里），本类只负责
 * 「把导航指令发给对应 tab 的引擎」（[WebViewEngine.load] / [WebViewEngine.reload]）；
 * 页内历史随实例状态保存/恢复（[onSaveInstanceState] → [TabEngineRegistry.saveAll]）。
 */
class TemplateActivity : ComponentActivity() {

    /** 页面级引擎池：按 tab id 保活引擎（切 tab 状态保留，历史随 Activity 状态保存）。 */
    private lateinit var engineRegistry: TabEngineRegistry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // W4 拍板 2：导出壳**真隔离**——setContent 之前（进程内尚无 WebView 创建）单点收口。
        WebViewStorageIsolation.ensureApplied(applicationContext, TEMPLATE_ISOLATION_ID)
        // W4 收口 F1：registry 持 Activity context（WebView 官方建议 activity context；
        // 生命周期由 onDestroy destroyAll 收口，不额外延长 Activity 生命周期）。
        engineRegistry = TabEngineRegistry(this)
        // 实例状态恢复：引擎在组合期才创建，先预置待灌回的页内历史。
        engineRegistry.restoreAll(savedInstanceState)
        setContent {
            // 模板自带 Compose Material3 默认主题（不依赖 app 的 MmaTheme）。
            MaterialTheme {
                TemplateApp(engineRegistry)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // tabStacks 退役后：页内导航历史（WebView back/forward 栈）随实例状态保存。
        engineRegistry.saveAll(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        // W4 收口 F2：**无条件**销毁引擎池——旋转重建时页内历史已随 onSaveInstanceState
        // 存档、新实例经 restoreAll 灌回；仅 isFinishing 判断会让旋转时旧 WebView
        // （内核资源 / Cookie 回调）泄漏。恢复不受影响：WebView 实例重建、导航栈恢复，
        // 这是 Android 对 WebView 的标准恢复姿势。
        if (::engineRegistry.isInitialized) engineRegistry.destroyAll()
    }
}

/**
 * 模板应用根：读注入的壳契约 → 构造示例运行时 → 交给唯一渲染入口 [ShellHost]。
 *
 * ## tab↔入口联动（集中式路由 + 引擎池，tabStacks 已退役）
 *
 * - **EnginePool**：每个顶栏 tab 持一个 [WebViewEngine]（键 = tab id，[TabEngineRegistry] 保活）；
 *   tab 内导航 = `engine.load(url)`（页内历史由 WebView 原生管理）；切换 tab 保留各自状态。
 * - **EntryRoot**：被标记为 `isEntry` 的 tab（或首个可见 tab）是入口根；
 *   点顶栏「回入口」（home 图标）或重选当前 tab，即 `engine.load(入口根)` 复位。
 * - **RouterContract**：[ShellAction.NavigateUrl] 经 [routeToTab] 解析——命中某 tab 目标则
 *   切到该 tab 并让该 tab 引擎导航；否则当前 tab 引擎导航；[ShellAction.InternalPage] 复位
 *   到入口根；[ShellAction.Native.REFRESH] = `engine.reload()`（栈顶重放的退役替代）；
 *   [ShellAction.OpenLocalPage] 解析注入 assets 的本地页面后导航（显式忽略≠丢失，见 handleAction）。
 * - 运行态（选中 tab / 抽屉开合）由本地持有，**不回写 spec**（壳「不变量」），故与 app 预览屏一致。
 */
@Composable
private fun TemplateApp(engineRegistry: TabEngineRegistry) {
    val spec: ShellSpec = rememberInjectedShellSpec()
    val themeColor: Int = 0xFF2196F3.toInt()

    // 示例运行时配置（基础模板）：与 app 预览共用同一份 WebRuntimeConfig 结构。
    val baseConfig = WebRuntimeConfig(
        projectType = ProjectType.URL,
        apkName = "模板示例",
        packageName = "com.make.apk.template",
        versionScheme = VersionScheme.THREE_SEGMENT,
        versionName = "0.0.0.1",
        versionCode = 1,
        themeColor = themeColor,
        entry = ProjectEntry.Url("https://example.com"),
        webSettings = WebSettings(),
        isolationId = TEMPLATE_ISOLATION_ID,
    )
    val baseEntryUrl = (baseConfig.entry as? ProjectEntry.Url)?.url ?: ""

    // 可见 tab（与 ShellHost 内部、pager 同一份顺序）。
    val visibleTabs = remember(spec) { visibleTopTabItems(spec.topTabs) }
    // 入口 tab：首个 isEntry 可见项，否则首个可见项（默认首项即入口）。
    val entryTab = remember(spec) { spec.topTabs.entryTabItem() }
    // 各顶栏 tab 条目的实时页面标题（网页 <title>），按条目 id 索引；
    // 与预览侧对称注入 ShellHost，保证「页面标题」来源 = PAGE_TITLE 预览 == 导出一致。
    val pageTitles = remember { mutableStateMapOf<String, String>() }

    // 本地页面读取器：把「项目内文件名」解析成 WebView 能直接加载的 assets URL。
    // 内容由注入脚本写进 assets/mma_pages/（见 scripts/gitops/inject_shell_spec.py）。
    val context = LocalContext.current
    val localPages = remember(context) { InjectedLocalPages(context) }

    // 运行态（与 app 预览对称，本地持有不回写 spec）：
    // 抽屉开合 / 抽屉与底栏的选中高亮（W2 ④ 补接：抽屉 / 底栏条目回调此前缺失，
    // 抽屉点不开、底栏点了没反应——现统一汇入 handleAction 集中路由）。
    var drawerOpen by remember { mutableStateOf(false) }
    var selectedDrawerItemId: String? by remember { mutableStateOf<String?>(null) }
    var selectedBottomItemId: String? by remember { mutableStateOf<String?>(null) }

    // 内容区暂停（QA Minor-F3）：与预览侧 VM 同一算式
    // contentPaused = drawerOpen && spec.drawer.pauseContentWhileOpen（ShellPreviewViewModel
    // onDrawerOpenChanged 同源）；pauseContentWhileOpen 为 false / 缺省（回落 true）时恒 false，
    // WebView 不被 onPause。两处 WebViewSurface（pager 逐页 + 单页 content）共用此值。
    val contentPaused = drawerOpen && spec.drawer.pauseContentWhileOpen

    var activeTabId: String? by remember {
        mutableStateOf(entryTab?.id ?: visibleTabs.firstOrNull()?.id)
    }

    // 某 tab 的入口根 URL（tabStacks 退役后：复位语义的唯一落点）。
    fun entryUrlOf(tab: ShellMenuItem?): String =
        tabEntryUrl(baseEntryUrl, tab, localPages)

    /** 取（或惰性创建）某 tab 的引擎；初始配置入口 = 该 tab 入口根。 */
    fun engineForTab(tabId: String?): WebViewEngine {
        val tab = visibleTabs.firstOrNull { it.id == tabId } ?: entryTab ?: visibleTabs.firstOrNull()
        val key = tab?.id ?: KEY_ENTRY_FALLBACK
        return engineRegistry.engineFor(
            key,
            baseConfig.copy(entry = ProjectEntry.Url(entryUrlOf(tab))),
        )
    }

    // ── 页内历史回退（W4 收口 F3，拍板 4：tabStacks 退役后由 WebView 原生 back/forward 承担）──
    // BackHandler 的 enabled 需要 Compose state；state 由「页面标题回调（每次文档加载
    // 完成必经）+ 本地导航动作 + 选中 tab 变化」共同刷新。页面栈为空时 enabled = false，
    // BackHandler 等同未注册 → 系统返回放行（绝不吃掉页面级返回）。
    var webCanGoBack by remember { mutableStateOf(false) }
    fun refreshWebCanGoBack() {
        webCanGoBack = engineRegistry.peek(activeTabId ?: entryTab?.id ?: "")?.canGoBack() == true
    }
    LaunchedEffect(activeTabId) { refreshWebCanGoBack() }
    BackHandler(enabled = webCanGoBack) {
        val engine = engineForTab(activeTabId)
        if (engine.canGoBack()) engine.goBack()
        refreshWebCanGoBack()
    }

    // 集中式路由 + ShellAction 联动（由 onShellAction / 抽屉 / 底栏回调入口派发）。
    // [origin] 来自触发入口（W2 ④）：与 app 预览侧同一判定函数 resolveUrlOpenTarget，
    // ACTION / DRAWER_ITEM 且 fitToApp=false 时外开，骨架来源恒内嵌（预览 == 导出）。
    fun handleAction(action: ShellAction, origin: UrlOpenOrigin) {
        when (action) {
            is ShellAction.NavigateUrl -> {
                // W2 ④：外开分支（与预览侧同源判定，同一执行边缘 openExternal）。
                if (resolveUrlOpenTarget(baseConfig.webSettings, origin) == UrlOpenTarget.EXTERNAL) {
                    if (!openExternal(context, action.url)) {
                        Log.w(TAG_TEMPLATE, "external_launch_failed: ${action.url}")
                    }
                    return
                }
                // 命中某 tab 目标 URL → 切到该 tab 并让该 tab 引擎导航；
                // 否则当前 tab 引擎导航（页内历史由 WebView 原生管理，tabStacks 已退役）。
                val match = routeToTab(visibleTabs, action.url)
                if (match != null) {
                    activeTabId = match
                    engineForTab(match).load(action.url)
                } else {
                    val id = activeTabId ?: entryTab?.id ?: return
                    engineForTab(id).load(action.url)
                }
                refreshWebCanGoBack()
            }
            is ShellAction.InternalPage -> {
                // 回入口：跳到入口 tab 并将其引擎复位到入口根（其他 tab 状态保留，互不干扰）。
                val id = entryTab?.id ?: activeTabId ?: return
                activeTabId = id
                engineForTab(id).load(entryUrlOf(visibleTabs.firstOrNull { it.id == id }))
                refreshWebCanGoBack()
            }
            is ShellAction.OpenLocalPage -> {
                // 本地页面：读注入到 assets 里的项目页面内容（相对路径原样保留）。
                // 硬约束：path 是**项目文件名**，绝不拼成网址；文件缺失时安全回落 + 留痕，
                // 绝不崩溃（注入脚本已在构建期校验过，这里是运行期兜底）。
                val localUrl = localPages.urlOf(action.path)
                if (localUrl != null) {
                    val id = activeTabId ?: entryTab?.id ?: return
                    engineForTab(id).load(localUrl)
                    refreshWebCanGoBack()
                } else {
                    Log.w(TAG_TEMPLATE, "local_page_missing: ${action.path}")
                }
            }
            is ShellAction.Native -> {
                // 仅 REFRESH 本期可执行：当前 tab 引擎原位重载
                //（tabStacks"重放栈顶"的退役替代，W4 拍板 4）；其余种类本期仅记录不执行。
                if (action.kind == ShellNativeAction.REFRESH) {
                    engineForTab(activeTabId).reload()
                }
            }
            is ShellAction.Unknown -> { /* 兜底：忽略未知动作，不崩 */ }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ShellHost(
            spec = spec,
            projectThemeColor = themeColor,
            env = ShellRenderEnvFactory.fromSpec(spec, themeColor),
            title = "模板示例",
            selectedTopTabId = activeTabId,
            // 实时页面标题（网页 <title>）按条目 id 注入，供「页面标题」来源 = PAGE_TITLE 使用。
            pageTitleForItem = { id -> pageTitles[id] },
            // 抽屉入口来自顶栏汉堡；返回键交给宿主（模板无上级栈，故 onBack = null）。
            onBack = null,
            onResetToEntry = {
                val id = entryTab?.id ?: activeTabId ?: return@ShellHost
                activeTabId = id
                engineForTab(id).load(entryUrlOf(visibleTabs.firstOrNull { it.id == id }))
                refreshWebCanGoBack()
            },
            onShellAction = { item -> handleAction(item.action, UrlOpenOrigin.ACTION) },
            onTopTabSelected = { item ->
                // 重选当前 tab → 引擎复位到入口根；切换 tab → 仅改选中态（各自引擎保留）。
                if (item.id == activeTabId) {
                    engineForTab(item.id).load(entryUrlOf(item))
                } else {
                    activeTabId = item.id
                }
                refreshWebCanGoBack()
            },
            // 底栏 / 抽屉回调补接（W2 ④）：底栏条目 = BOTTOM_TAB（骨架恒内嵌）；
            // 抽屉菜单项 = DRAWER_ITEM（fitToApp=false 时经同分支外开）。
            selectedBottomBarId = selectedBottomItemId,
            onBottomBarSelected = { item ->
                selectedBottomItemId = item.id
                handleAction(item.action, UrlOpenOrigin.BOTTOM_TAB)
            },
            drawerOpen = drawerOpen,
            onDrawerOpenChanged = { drawerOpen = it },
            selectedDrawerItemId = selectedDrawerItemId,
            onDrawerItemSelected = { item ->
                selectedDrawerItemId = item.id
                handleAction(item.action, UrlOpenOrigin.DRAWER_ITEM)
            },
            // 滑动切页开启时（spec.topTabs.swipeEnabled）逐页装引擎（按 tab id 保活）；
            // 未开启时 ShellHost 回落到单页 content（见 ShellHost 实现）。
            pagedContent = { page, pageModifier ->
                val tab = visibleTabs.getOrNull(page)
                if (tab != null) {
                    WebViewSurface(
                        engine = engineForTab(tab.id),
                        config = baseConfig.copy(entry = ProjectEntry.Url(entryUrlOf(tab))),
                        modifier = pageModifier,
                        // 该页对应顶栏 tab 条目 id：回传的网页标题归位到具体条目；
                        // 顺带刷新页内历史判定（F3：文档加载完成必经 onReceivedTitle）。
                        onTitle = { title ->
                            if (tab.id.isNotBlank()) pageTitles[tab.id] = title
                            refreshWebCanGoBack()
                        },
                        // 抽屉展开且契约开启时暂停该页（QA Minor-F3，与预览侧对齐）。
                        paused = contentPaused,
                        // W4 拍板 5：onError 通知按 BlankRootCause 分级中文文案
                        //（文案在内核侧分级成形；导出壳的诊断面是 Logcat，在此留痕）。
                        onError = { message -> Log.w(TAG_TEMPLATE, "shell_surface_error: $message") },
                    )
                }
            },
        ) { contentModifier ->
            WebViewSurface(
                engine = engineForTab(activeTabId),
                config = baseConfig.copy(entry = ProjectEntry.Url(entryUrlOf(
                    visibleTabs.firstOrNull { it.id == activeTabId },
                ))),
                modifier = contentModifier.fillMaxSize(),
                // 顺带刷新页内历史判定（F3）。
                onTitle = { refreshWebCanGoBack() },
                // 抽屉展开且契约开启时暂停内容区（QA Minor-F3，与预览侧对齐）。
                paused = contentPaused,
                onError = { message -> Log.w(TAG_TEMPLATE, "shell_surface_error: $message") },
            )
        }
    }
}

/** content 单页引擎池 key（无任何可见 tab 时的兜底）。 */
private const val KEY_ENTRY_FALLBACK: String = "entry_fallback"

/** 模板运行时日志标签（导出壳唯一可用的诊断面：Logcat）。 */
private const val TAG_TEMPLATE: String = "template_shell"

/**
 * 某 tab 的入口根 URL：取该 tab 动作的目标。
 *
 * - [ShellAction.NavigateUrl] → 该网址；
 * - [ShellAction.OpenLocalPage] → 注入的本地页面（assets URL）；
 * - 其余 / 目标为空 / 本地页面缺失 → 回落项目自身入口 [baseEntryUrl]。
 */
private fun tabEntryUrl(
    baseEntryUrl: String,
    tab: ShellMenuItem?,
    localPages: InjectedLocalPages,
): String {
    if (tab == null) return baseEntryUrl
    return when (val action = tab.action) {
        is ShellAction.NavigateUrl -> action.url.takeIf { it.isNotBlank() } ?: baseEntryUrl
        is ShellAction.OpenLocalPage -> localPages.urlOf(action.path) ?: baseEntryUrl
        else -> baseEntryUrl
    }
}

/** 集中式路由：把 URL 解析到其所属 tab（匹配某 tab 的 NavigateUrl 目标），未命中返回 null。 */
private fun routeToTab(visibleTabs: List<ShellMenuItem>, url: String): String? =
    visibleTabs.firstOrNull { (it.action as? ShellAction.NavigateUrl)?.url == url }?.id

/**
 * 读取编译期注入的壳契约 `res/raw/shell_spec.json`。
 *
 * R 来自同 namespace（com.make.apk）的 app res（模板已 srcDirs 共享），
 * 故 [R.raw.shell_spec] 可解析。解码用 `ignoreUnknownKeys = true`，
 * 与 app 端 RuntimeJson 的宽容策略一致（老壳读到新字段不炸）。
 */
@Composable
private fun rememberInjectedShellSpec(): ShellSpec {
    val context = LocalContext.current
    return remember {
        val jsonText = context.resources
            .openRawResource(R.raw.shell_spec)
            .bufferedReader()
            .use { it.readText() }
        Json { ignoreUnknownKeys = true }.decodeFromString<ShellSpec>(jsonText)
    }
}
