package com.make.apk

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import com.make.apk.data.model.entryTabItem
import com.make.apk.data.model.VersionScheme
import com.make.apk.data.model.WebRuntimeConfig
import com.make.apk.data.model.WebSettings
import com.make.apk.ui.screens.WebViewScreen
import com.make.apk.ui.shell.ShellHost
import com.make.apk.ui.shell.ShellRenderEnvFactory
import com.make.apk.ui.shell.visibleTopTabItems
import kotlinx.serialization.json.Json

/**
 * 子模板（插件式注入）落地验证入口（方向 1）。
 *
 * ## 它证明什么
 *
 * 本 Activity 与 app 的预览屏 [com.make.apk.ui.screens.ShellPreviewScreen] **消费同一份**
 * `ShellHost` 渲染器与同一套 `ShellSpec` 数据模型（源码经 sourceSets 指向 app，零副本）。
 * 区别仅在于：
 * - app 预览屏的壳来自「用户实时编辑的 ShellSpec」；
 * - 本模板的壳来自**编译期注入**的 `res/raw/shell_spec.json`（导出壳的最小化等价形态）。
 *
 * 两者喂给同一个 `ShellHost` → 渲染结果天然一致，从而实证
 * 「预览 == 导出 ⟺ 同一份 ShellSpec + 同一份 ShellHost」在真实 APK 产物上成立。
 *
 * ## 不变量
 *
 * 模板只作为壳的「消费方」：不修改壳源码、不污染 ShellSpec 运行时状态
 * （选中态/抽屉开合由本地 remember 持有，不回写 spec）。
 */
class TemplateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 模板自带 Compose Material3 默认主题（不依赖 app 的 MmaTheme）。
            MaterialTheme {
                TemplateApp()
            }
        }
    }
}

/**
 * 模板应用根：读注入的壳契约 → 构造示例运行时 → 交给唯一渲染入口 [ShellHost]。
 *
 * ## tab↔入口联动（集中式路由 + 每 tab 独立栈 + 回入口）
 *
 * - **TabStack**：每个顶栏 tab 持一条独立的 URL 后退栈（[tabStacks]，键 = tab id，栈顶 = 当前显示）。
 *   tab 内导航只压/弹自身栈；切换 tab 保留各自状态。
 * - **EntryRoot**：被标记为 `isEntry` 的 tab（或首个可见 tab）是其所属栈的入口根；
 *   点顶栏「回入口」（home 图标）或重选当前 tab，即复位到入口根。
 * - **RouterContract**：[ShellAction.NavigateUrl] 经 [routeToTab] 解析——命中某 tab 目标则切到该 tab，
 *   否则压入当前 tab 栈；[ShellAction.InternalPage] 复位到入口根；[ShellAction.Native.REFRESH] 重载当前页；
 *   [ShellAction.OpenLocalPage] 显式忽略（导出壳只注入壳契约，不含项目页面内容，见 handleAction 内注释）。
 * - 运行态（选中 tab / 栈）由本地持有，**不回写 spec**（壳「不变量」），故与 app 预览屏一致。
 */
@Composable
private fun TemplateApp() {
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
        isolationId = "webapp_template",
    )
    val baseEntryUrl = (baseConfig.entry as? ProjectEntry.Url)?.url ?: ""

    // 可见 tab（与 ShellHost 内部、pager 同一份顺序）。
    val visibleTabs = remember(spec) { visibleTopTabItems(spec.topTabs) }
    // 入口 tab：首个 isEntry 可见项，否则首个可见项（默认首项即入口）。
    val entryTab = remember(spec) { spec.topTabs.entryTabItem() }

    // 本地页面读取器：把「项目内文件名」解析成 WebView 能直接加载的 assets URL。
    // 内容由注入脚本写进 assets/mma_pages/（见 scripts/gitops/inject_shell_spec.py）。
    val context = LocalContext.current
    val localPages = remember(context) { InjectedLocalPages(context) }

    // 每 tab 独立的 URL 后退栈（栈顶 = 当前显示）；首屏初始化为各自入口根。
    val tabStacks = remember {
        mutableStateMapOf<String, List<String>>().apply {
            visibleTabs.forEach { tab -> this[tab.id] = listOf(tabEntryUrl(baseEntryUrl, tab, localPages)) }
        }
    }
    var activeTabId: String? by remember {
        mutableStateOf(entryTab?.id ?: visibleTabs.firstOrNull()?.id)
    }

    // 当前 tab 的栈顶 URL；栈空则回落该 tab 的入口根。
    fun currentUrl(tabId: String?): String {
        val tab = visibleTabs.firstOrNull { it.id == tabId } ?: entryTab ?: visibleTabs.firstOrNull()
        return tabStacks[tabId]?.lastOrNull() ?: tabEntryUrl(baseEntryUrl, tab, localPages)
    }

    // 把 URL 压入目标 tab 栈（默认当前 tab），并激活该 tab。
    fun navigate(url: String, targetTabId: String? = null) {
        val tabId = targetTabId ?: activeTabId ?: entryTab?.id ?: return
        tabStacks[tabId] = (tabStacks[tabId] ?: emptyList()) + url
        activeTabId = tabId
    }

    // 复位某 tab 的栈到其入口根。
    fun resetTab(tabId: String?) {
        val tab = visibleTabs.firstOrNull { it.id == tabId } ?: return
        tabStacks[tab.id] = listOf(tabEntryUrl(baseEntryUrl, tab, localPages))
    }

    // 回入口：跳到入口 tab 并将其栈复位到入口根（其他 tab 栈保留，互不干扰）。
    fun resetToEntry() {
        val id = entryTab?.id ?: activeTabId ?: return
        activeTabId = id
        resetTab(id)
    }

    // 集中式路由 + ShellAction 联动（由 onShellAction 入口派发）。
    fun handleAction(action: ShellAction) {
        when (action) {
            is ShellAction.NavigateUrl -> {
                // 命中某 tab 目标 URL → 切到该 tab；否则压入当前 tab 栈。
                val match = routeToTab(visibleTabs, action.url)
                if (match != null) activeTabId = match else navigate(action.url)
            }
            is ShellAction.InternalPage -> resetToEntry()
            is ShellAction.OpenLocalPage -> {
                // 本地页面：读注入到 assets 里的项目页面内容（相对路径原样保留）。
                // 硬约束：path 是**项目文件名**，绝不拼成网址；文件缺失时安全回落 + 留痕，
                // 绝不崩溃（注入脚本已在构建期校验过，这里是运行期兜底）。
                val localUrl = localPages.urlOf(action.path)
                if (localUrl != null) {
                    navigate(localUrl)
                } else {
                    Log.w(TAG_TEMPLATE, "local_page_missing: ${action.path}")
                }
            }
            is ShellAction.Native -> {
                // 仅 REFRESH 本期可执行（重载当前 tab 栈顶）；其余种类本期仅记录不执行。
                if (action.kind == ShellNativeAction.REFRESH) {
                    val id = activeTabId ?: return
                    val stack = (tabStacks[id] ?: return).toMutableList()
                    val top = stack.removeLastOrNull() ?: return
                    stack.add(top) // 重放栈顶以触发 WebView 重载
                    tabStacks[id] = stack
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
            // 抽屉入口来自顶栏汉堡；返回键交给宿主（模板无上级栈，故 onBack = null）。
            onBack = null,
            onResetToEntry = { resetToEntry() },
            onShellAction = { item -> handleAction(item.action) },
            onTopTabSelected = { item ->
                // 重选当前 tab → 复位到入口根；切换 tab → 仅改选中态（各自栈保留）。
                if (item.id == activeTabId) resetTab(item.id) else activeTabId = item.id
            },
            // 滑动切页开启时（spec.topTabs.swipeEnabled）逐页装 WebView；
            // 未开启时 ShellHost 回落到单页 content（见 ShellHost 实现）。
            pagedContent = { page, pageModifier ->
                val tab = visibleTabs.getOrNull(page)
                if (tab != null) {
                    WebViewScreen(
                        config = baseConfig.copy(entry = ProjectEntry.Url(currentUrl(tab.id))),
                        modifier = pageModifier,
                    )
                }
            },
        ) { contentModifier ->
            WebViewScreen(
                config = baseConfig.copy(entry = ProjectEntry.Url(currentUrl(activeTabId))),
                modifier = contentModifier.fillMaxSize(),
            )
        }
    }
}

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
