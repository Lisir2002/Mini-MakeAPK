package com.make.apk.webcore.engine

import android.content.Context
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.WebRuntimeConfig
import com.make.apk.webcore.config.WebSettingsApplier
import com.make.apk.webcore.navigation.VirtualFileServer

/**
 * WebView 引擎（W4 内核重写核心）：**WebView 生命周期唯一管理者**。
 *
 * ## 与旧单体的关系
 *
 * 旧 `WebViewScreen.kt`（974 行单体）把"组合 + 装配 + 生命周期 + 诊断"全部揉在一个
 * Composable 里，跨重组状态全靠 12 个 view tag（TAG_RUNTIME_KEY ~ TAG_PAGE_START_KEY）。
 * W4 推倒重写后：
 * - **12 个 view tag 全部退役**，改为本类闭包字段（见各字段注释中的旧 tag 对应）；
 *   新代码禁止 view tag 传参（W4 硬约束）；
 * - 生命周期（创建 / 装配 / 暂停 / 重建 / 销毁）收敛到本类唯一管理；
 * - 诊断事件经 [EngineEvent] 上行（diagnostics 只经引擎回调接口，反摸不到 chrome）。
 *
 * ## 生命周期契约
 *
 * - [acquireView]：惰性创建 + 装配 + 首次加载（渲染进程死亡后自动重建）；
 * - [applyConfig]：配置变化才重应用 + 重载（旧 TAG_RUNTIME_KEY 快照判等等价）；
 * - [setPaused]：抽屉暂停契约，幂等（旧 TAG_PAUSED_KEY 等价）；
 * - [recreateRequest]：渲染进程崩溃 → chrome 层 rebuildKey++ → 再 acquireView；
 * - [destroy]：唯一销毁点（WebView 不 destroy 会泄漏进程级内核资源）；
 * - view 换出（[releaseView]）仅解除挂载引用，**不销毁**——引擎池（TabEngineRegistry）
 *   按 tab 保活，销毁统一走 [destroy]。
 *
 * 零依赖约束：仅 android.webkit / android.view / data.model / webcore.config（+
 * navigation.VirtualFileServer 常量），编译期禁 ui / Room / ViewModel / 主题
 * （template 模块同套源码，违规即模板编译失败）。
 */
class WebViewEngine(
    context: Context,
    initialConfig: WebRuntimeConfig,
) {

    /** 引擎事件监听（chrome 层安装；同一引擎同一时刻至多一个监听者）。 */
    fun interface Listener {
        fun onEngineEvent(event: EngineEvent)
    }

    /** 事件出口（runCatching 包裹：监听者异常绝不反噬引擎）。 */
    var listener: Listener? = null

    /** 创建引擎时的宿主 context（组合作用域；与持有者同生命周期）。 */
    internal val context: Context = context

    /** 当前运行时配置（[applyConfig] 更新；导航 / SSL / 拟合开关等分支的读取源）。 */
    var config: WebRuntimeConfig = initialConfig
        private set

    // ── 闭包状态：12 个 view tag 的替代落点（W4 硬约束：禁止 view tag 传参）──

    /** 挂载中的 view（WebView 或测试桩；渲染进程死亡重建前保持不变）。 */
    private var root: View? = null

    /** 内核持有的 WebView（测试桩 / 非 WebView 宿主时为 null，全部装配跳过）。 */
    internal val web: WebView? get() = _web
    private var _web: WebView? = null

    /** 渲染进程是否已死亡（等 chrome 层重建；期间 acquireView 走重建路径）。 */
    internal var rendererDying: Boolean = false

    /** 引擎是否已销毁（销毁后 acquireView 静默返回空桩，绝不复活）。 */
    private var destroyed: Boolean = false

    /** 暂停状态（幂等去重；旧 TAG_PAUSED_KEY 替代）。 */
    private var pausedState: Boolean = false

    /** 最后一条 console 错误缓存（旧 TAG_JS_ERROR_KEY 替代；去重 + 根因分级兜底读取）。 */
    internal var lastConsoleRecord: String? = null

    /** 坏缓存自愈标记（旧 TAG_SELF_HEAL_KEY 替代；每轮加载至多自愈一次，防死循环）。 */
    internal var selfHealed: Boolean = false

    /** 空内容处理去重地址（旧 TAG_BLANK_HANDLED_KEY 替代；onPageFinished 多次触发不重复处理）。 */
    internal var blankHandledUrl: String? = null

    /** 空内容探测代次（旧 TAG_PROBE_GEN_KEY 替代；每次新加载 +1，作废在途延迟探测）。 */
    internal var probeGeneration: Int = 0

    /** 页面加载起点时间戳（旧 TAG_PAGE_START_KEY 替代；preview_loaded 埋点 duration 口径）。 */
    internal var pageStartedAt: Long = 0L

    /** 当前虚拟文件集（旧 TAG_FILES_KEY 替代；name → content，随每次加载刷新）。 */
    internal var virtualFiles: Map<String, String> = emptyMap()

    /** restoreState 先于 WebView 创建时暂存（引擎池恢复路径：onCreate → 组合创建引擎）。 */
    private var pendingRestore: Bundle? = null

    /** 最近一次记录的主题底色（首装 / 重建装配时应用；旧单体每次 update 捕获等价）。 */
    private var lastSurfaceArgb: Int = 0

    /**
     * onCreateWindow 的一次性子 WebView（W4 收口 F4）：transport 转发完成后它不再
     * 挂任何窗口，但实例仍占内核资源——记到引擎上，由 [destroy] 与下一次开窗统一
     * 收口（兑现「destroy 唯一销毁点」契约，防临时实例累积泄漏）。
     */
    internal var pendingPopup: WebView? = null

    /** 销毁并清空临时子 WebView（幂等，吞异常——诊断承载不阻断业务）。 */
    internal fun destroyPendingPopup() {
        pendingPopup?.let { runCatching { it.destroy() } }
        pendingPopup = null
    }

    // ── 生命周期 ──

    /**
     * 获取可挂载 view：惰性创建 + 装配 + 首次加载。
     *
     * @param viewFactory WebView 构造工厂（截图测试注入轻量 View 桩；默认真 WebView）。
     *   返回非 WebView 时仅视觉组合、不装配（与旧 update 的 `view !is WebView` 分支等价）。
     */
    fun acquireView(viewFactory: (Context) -> View = { WebView(it) }): View {
        if (destroyed) return View(context)
        if (root == null || rendererDying) buildView(viewFactory)
        return root ?: View(context)
    }

    /** view 从组合移除：仅解除挂载关系，WebView 资源保留（引擎池保活；销毁统一走 [destroy]）。 */
    fun releaseView() {
        // 保留 root / _web 引用与全部运行状态：页面重组移除（pager 翻页）后再次
        // acquireView 直接复用同一 WebView，页内导航历史不丢（W4 拍板 4：原生 back/forward）。
    }

    /**
     * 应用新配置：判等（旧 TAG_RUNTIME_KEY 快照判等，data class equals）失败才
     * 重应用设置并重载——避免每次重组都 reload。
     *
     * @param surfaceArgb WebView 底色（主题 surface）；每次记录，供重建 / 首装使用
     *   （与旧单体"主题切换不触发重载"的行为等价：仅记录，不单独触发）。
     */
    fun applyConfig(newConfig: WebRuntimeConfig, surfaceArgb: Int) {
        // 底色先记录：首装（buildView）发生在 update 之前，装配时必须取到主题表面色。
        lastSurfaceArgb = surfaceArgb
        val view = _web
        if (newConfig == config) {
            // 主题底色变化仅记录并就地应用，不触发重载（旧单体等价行为）。
            if (view != null && surfaceArgb != 0) {
                runCatching { view.setBackgroundColor(surfaceArgb) }
            }
            return
        }
        config = newConfig
        if (view == null) return
        // 新一轮加载：自愈标记复位、清空去重地址、作废在途探测（旧 update 等价序列）。
        selfHealed = false
        blankHandledUrl = null
        nextProbeGeneration()
        // 新一次加载：进度归零，避免复用旧 WebView 时残留上一页满格进度。
        emit(EngineEvent.ProgressChanged(0))
        WebSettingsApplier.apply(view, config, surfaceArgb)
        virtualFiles = extractVirtualFiles(config)
        JsErrorCollectorCompat.inject(view)
        loadEntry(view, config.entry)
    }

    /** 应用暂停/恢复（抽屉展开时省资源；已处于目标状态时跳过——旧 TAG_PAUSED_KEY 等价）。 */
    fun setPaused(paused: Boolean) {
        if (pausedState == paused) return
        pausedState = paused
        val view = _web ?: return
        if (paused) view.onPause() else view.onResume()
    }

    /** 导航到指定 URL（tabStacks URL 栈退役后的模板侧导航入口，W4 拍板 4）。 */
    fun load(url: String) {
        if (url.isBlank()) return
        _web?.loadUrl(url)
    }

    /** 重载当前页（REFRESH 动作；tabStacks"重放栈顶"的替代，W4 拍板 4）。 */
    fun reload() {
        _web?.reload()
    }

    /** 页内历史：WebView 原生 back/forward（W4 拍板 4；tabStacks 的完全替代）。 */
    fun canGoBack(): Boolean = _web?.canGoBack() == true

    /** 页内历史：后退一步。 */
    fun goBack() {
        _web?.goBack()
    }

    /** 页内历史：是否可前进。 */
    fun canGoForward(): Boolean = _web?.canGoForward() == true

    /** 页内历史：前进一步。 */
    fun goForward() {
        _web?.goForward()
    }

    /** 保存页内导航历史（onSaveInstanceState 接线；tabStacks 持久化的替代）。 */
    fun saveState(outState: Bundle) {
        val view = _web ?: return
        runCatching { view.saveState(outState) }
    }

    /** 恢复页内导航历史（WebView 未创建时暂存，acquireView 后灌回）。 */
    fun restoreState(state: Bundle) {
        val view = _web
        if (view == null) {
            pendingRestore = state
        } else {
            runCatching { view.restoreState(state) }
        }
    }

    /** 自愈专用：跳过缓存重载（NO_CACHE 保留至下一次 applyRuntime 恢复用户配置）。 */
    internal fun reloadNoCache(): Boolean {
        val view = _web ?: return false
        return runCatching {
            view.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            view.reload()
            true
        }.getOrDefault(false)
    }

    /**
     * 销毁引擎（唯一销毁点）：WebView 不 destroy 会泄漏进程级内核资源
     * （content registry / Cookie 回调等），必须显式销毁。
     */
    fun destroy() {
        if (destroyed) return
        destroyed = true
        listener = null
        destroyPendingPopup()
        _web?.let { runCatching { it.destroy() } }
        _web = null
        root = null
    }

    /** 渲染进程死亡：标记待重建 + 上行事件（chrome 层 rebuildKey++ 驱动重建，真恢复）。 */
    internal fun recreateRequest() {
        rendererDying = true
        emit(EngineEvent.RendererGone)
    }

    // ── 内部装配（navigation / diagnostics 协作层的挂载点）──

    /** 自增并返回探测代次：新一轮加载开始时调用，使所有在途延迟探测回调作废。 */
    internal fun nextProbeGeneration(): Int = ++probeGeneration

    /** 上行事件（监听者异常吞掉，绝不反噬引擎）。 */
    internal fun emit(event: EngineEvent) {
        val sink = listener ?: return
        runCatching { sink.onEngineEvent(event) }
    }

    /** 装配全新 WebView：销毁残骸 → 构造 → 双客户端 → 设置 → 注入 → 恢复态 → 加载。 */
    private fun buildView(viewFactory: (Context) -> View) {
        destroyPendingPopup()
        _web?.let { old -> runCatching { old.destroy() } }
        _web = null
        rendererDying = false
        val view = viewFactory(context)
        root = view
        if (view !is WebView) {
            // 测试桩 / 非 WebView 宿主：仅视觉组合，不装配（旧 update `view !is WebView` 等价）。
            emit(EngineEvent.ProgressChanged(0))
            return
        }
        _web = view
        // 导航拦截必须先于一切装配：未设 WebViewClient 时，页面内任何跳转都会被交给系统处理。
        view.webViewClient = com.make.apk.webcore.navigation.InAppWebViewClient(this)
        // 进度客户端：驱动加载进度条 + 接管新窗口 + 采集 JS console 错误 + 回传页面标题。
        view.webChromeClient = EngineChromeClient(this)
        WebSettingsApplier.apply(view, config, lastSurfaceArgb)
        virtualFiles = extractVirtualFiles(config)
        // JS 错误采集器：加载前先注入一次（本次落在旧文档上、导航后即失效，
        // 真正生效的挂载时机在 onPageStarted / onPageFinished——幂等注入是设计使然）。
        JsErrorCollectorCompat.inject(view)
        // 新建即视为新一轮加载：探测代次前移，作废旧页在途探测。
        nextProbeGeneration()
        emit(EngineEvent.ProgressChanged(0))
        // 引擎池恢复路径：实例状态（back/forward 历史）先灌回再加载。
        pendingRestore?.let { state ->
            runCatching { view.restoreState(state) }
            pendingRestore = null
        }
        loadEntry(view, config.entry)
    }

    /** 提取虚拟文件集（HTML 多文件项目）：供拦截器就地供给相对路径资源。 */
    private fun extractVirtualFiles(config: WebRuntimeConfig): Map<String, String> =
        (config.entry as? ProjectEntry.BundledHtml)
            ?.files?.associate { it.name to it.content } ?: emptyMap()

    /** 依据入口形态加载内容（URL 直连 / HTML 虚拟域 / 资产 file://）。 */
    internal fun loadEntry(view: WebView, entry: ProjectEntry) {
        when (entry) {
            is ProjectEntry.Url -> view.loadUrl(entry.url)
            is ProjectEntry.BundledHtml ->
                // 内联 HTML：无附加文件时 baseURL 传 null（单文件网页应用无需相对资源解析，
                // 内联 <style>/<script> 直接生效；避免伪造同域 base 导致相对链接跳到不存在主机）；
                // 有附加文件时改用虚拟域名 baseURL——相对路径资源由客户端拦截供给。
                if (virtualFiles.isNotEmpty()) {
                    view.loadDataWithBaseURL(
                        VirtualFileServer.BASE_URL, entry.html, "text/html", "utf-8", null,
                    )
                } else {
                    view.loadDataWithBaseURL(null, entry.html, "text/html", "utf-8", null)
                }
            is ProjectEntry.Archive ->
                // 本地注入：资产已解包到 assetRef 目录，直接以 file:// 加载解包后的入口。
                view.loadUrl("file://${entry.assetRef}/index.html")
        }
    }
}

/**
 * 采集器注入的引擎侧薄封装：避免 engine 包直接 import diagnostics 包类型
 * （依赖单向：diagnostics → engine；引擎只消费采集器脚本常量）。
 */
internal object JsErrorCollectorCompat {
    fun inject(view: WebView) {
        if (!view.settings.javaScriptEnabled) return
        runCatching {
            view.evaluateJavascript(
                com.make.apk.webcore.diagnostics.JsErrorCollector.COLLECTOR_JS, null,
            )
        }
    }
}
