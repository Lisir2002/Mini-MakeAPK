package com.make.apk.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
// 注意：`onReceivedSslError` 的 error 类型是 **android.net.http.SslError**（历史遗留位置），
// 不是 `android.webkit.SslError`——后者在 android.jar 中并不存在（编译期即可证伪）。
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.make.apk.data.model.ProgressPosition
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.WebRuntimeConfig
import android.util.Log

// WebView 渲染屏（本地注入架构）：消费 [WebRuntimeConfig]，统一渲染入口与 WebSettings。
// 不再区分"设备端 vs 云端"——全部走本地注入（设备端 WebView 封装 / 资产注入模板壳 + 本地重签），
// 与导出模板壳共用同一份源码（经 sourceSets 指向 app，零副本），故修本文件同时修复预览与导出。
//
// 内置浏览器健壮性（阶段 6.2 深度审计结论 + rc12 白屏专项加固）：
// - 混合内容（HTTPS 页内嵌 HTTP 资源）默认被系统静默拦截 → 表现为"加载一下就白屏"，
//   故 [applyRuntime] 强制 `MIXED_CONTENT_ALWAYS_ALLOW`；
// - 仅设 `WebViewClient` 无法接收进度/窗口事件，故补 `WebChromeClient` 驱动加载进度条；
// - 加载失败（主帧）原来完全静默，现通过 [onError] 上抛可见提示，便于定位白屏根因。
//
// ## rc12「标准浏览器能开、App 内白屏」专项（联网调研 + 实测结论落地）
//
// 实测 `https://platform.xiaomimimo.com/console/plan-manage`：桌面 / 移动 Chrome / WebView UA
// 三种形态**都能渲染**（未登录时前端 401 后跳转到小米账号登录页），说明服务端并未按 UA 拦截。
// 那么"App 内白屏"就只剩**客户端自身把加载链掐断**这一种可能，逐条堵死：
//
// 1. **多窗口被静默丢弃**（命中率最高）：`setSupportMultipleWindows` 默认 false，
//    而 `javaScriptCanOpenWindowsAutomatically=true` 会让含 `target=_blank` / `window.open`
//    的页面**直接不加载** → 停在空的 root div = 白屏。现开启并实现
//    `onCreateWindow`，把新窗口的导航**重定向回当前 WebView**；
// 2. **外部协议静默消费**：登录链一旦落到 `intent://` / 自定义 scheme，过去直接 return true
//    什么都不做 = 白屏。现 `intent://` 交由系统解析外跳，其余给出可见提示；
// 3. **UA 的 `; wv)` 标记**：系统 WebView 默认 UA 带该标记，大量站点据此降级或返回空页。
//    现做**归一化**（去掉标记，不伪造版本），与 Chrome 一致；自定义 UA 仍优先；
// 4. **SSL 错误默认 cancel**：`onReceivedSslError` 的 super 实现就是 `handler.cancel()` →
//    干净白屏且**无任何提示**。现按 `ignoreSslErrors` 决定放行/取消，两条路都留可见提示；
// 5. **Cookie**：显式 `setAcceptCookie(true)` + 第三方开关 + 落盘 flush，避免跨域登录回跳丢态；
// 6. **透明背景**：部分 ROM 上 `Color.TRANSPARENT` 的 WebView 直接不绘制内容。改为
//    **不透明**并跟随主题表面色（暗色主题依旧不闪白，因为底色本就是暗色）；
// 7. **空白可观测**：加载完成后做一次"空内容探测"，命中即上抛提示（脚本被拦截 / 需登录）。
//
// ## rc13「白屏可观测 + 自愈」专项（QA 真实靶子审计落地，靶子：现代 SPA + ES2020）
//
// 在 rc12 基础上补齐白屏的"看得见"与"爬得起来"四件事：
// 1. **JS 错误零可观测** → onConsoleMessage 采集 ERROR + 注入幂等错误采集器
//    （`__mmaLastError` / `__mmaHooked`），最后一条错误缓存到 view tag；
// 2. **根因三级判定**：空内容最终命中时依次判 内核语法能力（Chrome 80 门槛）→
//    JS 错误 → 兜底文案；探测两段式（2s 首探 + 3s 复探）防 SPA 慢渲染误报；
// 3. **坏缓存自愈**：主帧错误或空内容最终命中时，每轮至多一次切 `LOAD_NO_CACHE`
//    重载（防循环标记），重载前给用户可见说明；
// 4. **渲染进程真恢复**：onRenderProcessGone 通过 rebuildKey 驱动 Compose 重建
//    全新 WebView 实例并自动重载，不再是"阻止闪退后继续白屏"。
@Composable
fun WebViewScreen(
    config: WebRuntimeConfig,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    webViewFactory: (Context) -> View = { WebView(it) },
    /** 加载失败（主帧）时回调（如预览页用它弹 Snackbar）。导出壳默认不传。 */
    onError: ((String) -> Unit)? = null,
) {
    // 加载进度：由 WebChromeClient.onProgressChanged 驱动；仅"功能加载进度条"开启时渲染。
    val progress = remember { mutableStateOf(0) }
    // 渲染进程死亡后的重建代次：+1 使 key() 变化 → AndroidView 工厂重建**全新** WebView 实例
    // （渲染进程一旦崩溃，原 WebView 实例已被内核标记失效，复用只会继续白屏，必须整体重建）。
    val rebuildKey = remember { mutableStateOf(0) }
    val showProgress = config.webSettings.showLoadingProgress
    val progressPosition = config.webSettings.loadingProgressPosition
    // WebView 底色：**不透明**，取主题表面色（透明底在部分 ROM 上会导致内容不绘制 → 白屏）。
    val surfaceArgb = MaterialTheme.colorScheme.surface.toArgb()

    Box(modifier = modifier.fillMaxSize()) {
        key(rebuildKey.value) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = webViewFactory,
                update = { view ->
                    if (view !is WebView) return@AndroidView
                    // 导航拦截必须先于一切装配：未设 WebViewClient 时，页面内任何跳转（点击链接、
                    // 站点重定向）都会被交给系统处理——表现为"跳出应用、打开外部浏览器"，
                    // 壳的顶栏 / 底栏瞬间失效。预览与导出壳共用本文件，一处修复两边生效。
                    ensureClient(view, onError) { rebuildKey.value++ }
                    // 进度客户端（幂等，只装一次）：驱动加载进度条 + 接管新窗口 + 采集 JS console 错误。
                    setupChrome(view, onError) { progress.value = it }
                    // 暂停/恢复：由壳抽屉的 pauseContentWhileOpen 契约驱动，去重避免每次重组都调用。
                    applyPaused(view, paused)
                    // 仅在配置（入口/关键设置）变化时重新应用并加载，避免每次重组都 reload。
                    if (view.getTag(TAG_RUNTIME_KEY) == config) return@AndroidView
                    view.setTag(TAG_RUNTIME_KEY, config)
                    // SSL 开关随每次加载刷新（客户端是幂等单实例，只能从 tag 读当前配置）。
                    view.setTag(TAG_SSL_KEY, config.webSettings.ignoreSslErrors)
                    // 新一轮加载：自愈标记复位（每轮加载允许自愈一次）、清空去重地址、作废在途探测。
                    view.setTag(TAG_SELF_HEAL_KEY, false)
                    view.setTag(TAG_BLANK_HANDLED_KEY, null)
                    nextProbeGeneration(view)
                    // 新一次加载：进度归零，避免复用旧 WebView 时残留上一页满格进度。
                    progress.value = 0
                    applyRuntime(view, config, surfaceArgb)
                    // JS 错误采集器：加载前先注入一次（本次落在旧文档上、导航后即失效，
                    // 真正生效的挂载时机在 onPageStarted / onPageFinished，见 injectErrorCollector）。
                    injectErrorCollector(view)
                    // 虚拟文件集（HTML 多文件项目）：入口 HTML 经虚拟域名 baseURL 加载，
                    // 相对路径资源由客户端拦截供给——css/js 文件无需内联即可生效。
                    val files = (config.entry as? ProjectEntry.BundledHtml)
                        ?.files?.associate { it.name to it.content } ?: emptyMap()
                    view.setTag(TAG_FILES_KEY, files)
                    loadEntry(view, config.entry)
                },
                onRelease = { view ->
                    // 重组移除 / rebuildKey 重建时释放原生资源：WebView 不 destroy 会泄漏
                    // 进程级内核资源（content registry / Cookie 回调等），必须显式销毁。
                    if (view is WebView) runCatching { view.destroy() }
                },
            )
        }

        // 功能加载进度条：开关开启且尚未加载完成时显示，贴顶/贴底由设置决定。
        if (showProgress && progress.value < 100) {
            val indicator = @Composable {
                LinearProgressIndicator(
                    progress = { progress.value / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            if (progressPosition == ProgressPosition.BOTTOM) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                ) { indicator() }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                ) { indicator() }
            }
        }
    }
}

/** 应用暂停/恢复（抽屉展开时省资源；已处于目标状态时跳过）。 */
private fun applyPaused(view: WebView, paused: Boolean) {
    if (view.getTag(TAG_PAUSED_KEY) == paused) return
    view.setTag(TAG_PAUSED_KEY, paused)
    if (paused) view.onPause() else view.onResume()
}

/** 装配「应用内导航」客户端（幂等，只装一次）。 */
private fun ensureClient(
    view: WebView,
    onError: ((String) -> Unit)?,
    onRendererGone: (() -> Unit)?,
) {
    if (view.getTag(TAG_CLIENT_KEY) == true) return
    view.setTag(TAG_CLIENT_KEY, true)
    view.webViewClient = InAppWebViewClient(onError, onRendererGone)
}

/**
 * 装配「进度 / 窗口」客户端（幂等，只装一次）。
 *
 * 仅 [WebViewClient] 无法接收 `onProgressChanged`（进度条数据源）与窗口创建事件
 * （部分站点 `window.open` 依赖它，缺省会静默失败 → 白屏），故必须补 [WebChromeClient]。
 */
private fun setupChrome(view: WebView, onError: ((String) -> Unit)?, onProgress: (Int) -> Unit) {
    if (view.getTag(TAG_CHROME_KEY) == true) return
    view.setTag(TAG_CHROME_KEY, true)
    view.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            onProgress(newProgress.coerceIn(0, 100))
        }

        /**
         * JS console 错误采集（rc13 白屏专项）：SPA 的脚本异常原本**零回调零日志**，
         * 白屏后毫无线索。这里做两件事：
         * 1. 记 `js_console_error` 日志（带来源与行号，Logcat 是导出壳唯一可用的诊断面）；
         * 2. 缓存"最后一条错误"到 view tag，供空内容探测做根因分级（见 reportBlankRootCause）。
         * 限流去重：SPA 循环报错会瞬间刷屏，与上一条相同则只缓存不重复记日志。
         */
        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            val message = consoleMessage
            if (message != null && message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                val record = "${message.message()} @${message.sourceId()}:${message.lineNumber()}"
                if (view.getTag(TAG_JS_ERROR_KEY) != record) {
                    view.setTag(TAG_JS_ERROR_KEY, record)
                    Log.w(TAG_LOG, "js_console_error: $record")
                }
            }
            return super.onConsoleMessage(consoleMessage)
        }

        /**
         * 新窗口（`window.open` / `target="_blank"`）：系统默认**忽略一切开窗请求**，
         * 页面因此停在空白。壳内只有一个可见 WebView，故创建一次性承载用的子 WebView，
         * 并把它的导航**转发回主 view**——既保住合规的 transport 协议（必须
         * [android.os.Message.sendToTarget]，否则子进程永不导航），又不新增可见容器。
         */
        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: android.os.Message,
        ): Boolean {
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            val child = WebView(view.context)
            child.settings.javaScriptEnabled = view.settings.javaScriptEnabled
            child.settings.domStorageEnabled = view.settings.domStorageEnabled
            child.settings.javaScriptCanOpenWindowsAutomatically = true
            child.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    childView: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    view.loadUrl(request.url.toString())
                    return true
                }

                @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                override fun shouldOverrideUrlLoading(childView: WebView, url: String): Boolean {
                    view.loadUrl(url)
                    return true
                }
            }
            transport.webView = child
            resultMsg.sendToTarget()
            Log.i(TAG_LOG, "popup_redirected")
            return true
        }

        override fun onCloseWindow(window: WebView) {
            // 子窗口只是一次性承载，无需销毁主 view；忽略即可（避免误杀当前页）。
            Log.i(TAG_LOG, "popup_closed")
        }
    }
    // 兜底：未传 onError 的导出壳也要在 Logcat 留痕（onError 仅预览页传入）。
    if (onError == null) Log.i(TAG_LOG, "chrome_client_bound_without_error_sink")
}

/**
 * 应用内导航客户端：网页类协议一律交回本 WebView 加载，**绝不逃逸到系统浏览器**。
 *
 * 未设置 WebViewClient 时，WebView 会把页面内跳转交给 ActivityManager 选择处理器——
 * 用户点一个链接就被甩进 Chrome / 系统浏览器，壳的顶栏、底栏、抽屉全部失效，
 * "预览 == 导出"也随之失去意义（导出的 APK 同样会逃逸）。
 *
 * 拦截策略（返回 true = 消费掉、不加载也不外跳）：
 * - 放行（返回 false，本 WebView 自行加载）：http/https/file/data/about/javascript/blob/content；
 *   其中 `file` 是 Archive 项目预览的入口形态（`file://{assetRef}/index.html`），不可拦。
 * - 消费：`tel` / `mailto` / `sms` / `intent` / `market` 等外部应用协议——`intent://`
 *   交由系统解析外跳（带 fallback_url 兜底），其余给出**可见提示**（rc12：过去是静默
 *   消费，登录链一旦落到自定义 scheme 就表现为白屏）。
 */
private class InAppWebViewClient(
    private val onError: ((String) -> Unit)?,
    /** 渲染进程死亡时回调（WebViewScreen 据此 ++rebuildKey 重建 WebView 实例，真恢复）。 */
    private val onRendererGone: (() -> Unit)?,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url?.toString() ?: return false
        if (isInAppScheme(request.url?.scheme)) return false
        return handleExternal(view, url)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        val uri = Uri.parse(url)
        if (isInAppScheme(uri.scheme)) return false
        return handleExternal(view, url)
    }

    /** 外部协议：`intent://` 解析后外跳；其余提示"应用内不支持该协议"（绝不静默）。 */
    private fun handleExternal(view: WebView, url: String): Boolean {
        if (url.startsWith("intent://", ignoreCase = true)) {
            val intent = runCatching {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    component = null
                }
            }.getOrNull()
            if (intent != null && runCatching { view.context.startActivity(intent) }.isSuccess) {
                Log.i(TAG_LOG, "external_intent_launched")
                return true
            }
            // 兜底：intent 里通常带 browser_fallback_url，回落到本 WebView 打开。
            val fallback = intent?.getStringExtra("browser_fallback_url")
            if (!fallback.isNullOrBlank()) {
                view.loadUrl(fallback)
                Log.i(TAG_LOG, "external_intent_fallback")
                return true
            }
        }
        val scheme = url.substringBefore(':').ifBlank { "未知协议" }
        reportError("应用内无法打开 $scheme 链接，已阻止跳转")
        Log.w(TAG_LOG, "external_scheme_blocked: $scheme")
        return true
    }

    /** 主帧开始加载：留痕重定向链（白屏排查的第一手证据）+ 重置探测代次与错误缓存。 */
    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
        Log.i(TAG_LOG, "page_started: $url")
        // 新一轮加载：作废在途空内容探测（旧页的延迟回调不得串扰新页）；
        // 顺带清空上一页的 JS 错误缓存，避免把旧页异常误诊到新页头上。
        nextProbeGeneration(view)
        view.setTag(TAG_JS_ERROR_KEY, null)
        // 每个新文档的 window 都是全新对象，错误采集器必须随文档重挂（幂等，
        // 由 __mmaHooked 防同页重复）；此处是页面脚本执行前最靠前的挂载时机。
        injectErrorCollector(view)
        super.onPageStarted(view, url, favicon)
    }

    /** 主帧加载完成：Cookie 落盘 + 补挂错误采集器 + 空内容探测（两段式，见 probeBlankContent）。 */
    override fun onPageFinished(view: WebView, url: String) {
        Log.i(TAG_LOG, "page_finished: $url")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            runCatching { CookieManager.getInstance().flush() }
        }
        // 兜底再挂一次采集器：onPageStarted 时机在个别 ROM 上晚于首帧脚本执行，
        // 幂等标记保证同一文档内不会重复注入。
        injectErrorCollector(view)
        probeBlankContent(view)
        super.onPageFinished(view, url)
    }

    /**
     * 空内容探测（两段式 + 代次防串扰，rc13 白屏专项升级）：
     *
     * SPA 首屏靠脚本渲染，脚本一旦跑不起来，页面就是"加载成功但一片空白"且
     * **没有任何错误回调**。原实现在完成后固定 2 秒探测一次即上报，SPA 慢渲染会误报；
     * 现改为两段式：2 秒首探，为空再等 3 秒复探一次，仍空才进入处理
     * （[handleBlankConfirmed]：先自愈、自愈过则做根因三级判定并上报）。
     *
     * 防串扰：回调闭包持有探测代次，若期间发生新加载（代次已变）直接作废，
     * 避免旧页的延迟探测结果落到新页上。
     */
    private fun probeBlankContent(view: WebView) {
        if (!view.settings.javaScriptEnabled) return
        val pageUrl = view.url ?: ""
        val generation = currentProbeGeneration(view)
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching {
                view.evaluateJavascript(BLANK_PROBE_JS) { result ->
                    // 期间已有新加载：本探测作废
                    if (currentProbeGeneration(view) != generation) return@evaluateJavascript
                    if (result?.trim('"') != "1") return@evaluateJavascript
                    // 首探为空：可能是 SPA 慢渲染，复探一次再定论
                    Handler(Looper.getMainLooper()).postDelayed({
                        runCatching {
                            view.evaluateJavascript(BLANK_PROBE_JS) { again ->
                                if (currentProbeGeneration(view) != generation) {
                                    return@evaluateJavascript
                                }
                                if (again?.trim('"') == "1") handleBlankConfirmed(view, pageUrl)
                            }
                        }
                    }, BLANK_REPROBE_DELAY_MS)
                }
            }
        }, BLANK_PROBE_DELAY_MS)
    }

    /**
     * 空内容最终确认（两级去重 + 自愈分流）：
     * - 同一页面地址只处理一次（onPageFinished 对同一页可能触发多次，如 hash 跳转）；
     * - 本轮加载尚未自愈过 → 先做坏缓存自愈（[maybeSelfHeal]），重载后新探测再判；
     * - 自愈过仍空 → 才认定真白屏，做根因三级判定并上报（[reportBlankRootCause]）。
     */
    private fun handleBlankConfirmed(view: WebView, pageUrl: String) {
        if (view.getTag(TAG_BLANK_HANDLED_KEY) == pageUrl) return
        view.setTag(TAG_BLANK_HANDLED_KEY, pageUrl)
        if (view.getTag(TAG_SELF_HEAL_KEY) != true) {
            maybeSelfHeal(view, "blank_content")
            return
        }
        reportBlankRootCause(view)
    }

    /**
     * 坏缓存自愈（rc13）：缓存条目写入中断 / 版本错位时，"标准浏览器能开、App 内白屏"
     * 且无任何错误回调——是坏缓存最典型的表现。此处以 `LOAD_NO_CACHE` 临时改写缓存模式
     * 并重载一次（每轮加载至多一次，[TAG_SELF_HEAL_KEY] 防止"失败→重载→失败"死循环）；
     * 重载前先给用户一条可见说明，避免"闪一下不知道发生了什么"。重载会触发新一轮
     * onPageStarted/Finished，探测代次随之刷新、重新走两段式探测。
     */
    private fun maybeSelfHeal(view: WebView, reason: String) {
        if (view.getTag(TAG_SELF_HEAL_KEY) == true) return
        view.setTag(TAG_SELF_HEAL_KEY, true)
        // 自愈即新一轮处理窗口：清空去重地址，重载后仍空才上报根因
        view.setTag(TAG_BLANK_HANDLED_KEY, null)
        nextProbeGeneration(view)
        Log.w(TAG_LOG, "self_heal_reload: $reason")
        reportError(MSG_SELF_HEAL_RELOAD)
        runCatching {
            // NO_CACHE 保留至下一次 applyRuntime 恢复用户配置；本轮会话内宁可多拉网络
            // 也不再读可疑缓存。
            view.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            view.reload()
        }.onFailure {
            Log.w(TAG_LOG, "self_heal_reload_failed: ${it.message}")
        }
    }

    /**
     * 根因三级判定（自愈后仍空才走到这里）：
     * 1. 语法能力探测失败 = 内核 < Chrome 80，可选链 / 空值合并这类现代 SPA 硬门槛
     *    直接挂 → 给出"去系统设置更新 WebView"的可行动指引（直接命中 Xiaomi MiMo
     *    这类现代 SPA 白屏场景）；
     * 2. 采集到 JS 错误 → 上报截断后的错误信息（80 字符）；
     * 3. 都没有 → 兜底文案（脚本被拦截 / 需登录，可尝试刷新）。
     */
    private fun reportBlankRootCause(view: WebView) {
        runCatching {
            view.evaluateJavascript(SYNTAX_PROBE_JS) { syntax ->
                when {
                    // 探测链路本身失败（回调 null）：优雅降级为兜底文案，不误诊
                    syntax == null -> {
                        Log.w(TAG_LOG, "blank_root_cause: probe_failed")
                        reportError(MSG_BLANK_FALLBACK)
                    }
                    syntax.trim('"') != "1" -> {
                        Log.w(TAG_LOG, "blank_root_cause: engine_too_old")
                        reportError(MSG_BLANK_OLD_ENGINE)
                    }
                    else -> readJsErrorThenReport(view)
                }
            }
        }.onFailure {
            Log.w(TAG_LOG, "blank_root_cause: evaluate_failed")
            reportError(MSG_BLANK_FALLBACK)
        }
    }

    /** 读取注入采集器缓存的最后一条 JS 错误（页面侧 `__mmaLastError`），据此上报第二/三级文案。 */
    private fun readJsErrorThenReport(view: WebView) {
        runCatching {
            view.evaluateJavascript(LAST_ERROR_PROBE_JS) { raw ->
                // 页面侧采集为空时，回落到原生 console 缓存（采集器注入失败也能兜住）
                val jsError = raw?.trim('"')
                    ?.takeIf { it.isNotBlank() && it != "null" && it != "no-hook" }
                    ?: view.getTag(TAG_JS_ERROR_KEY) as? String
                if (jsError.isNullOrBlank()) {
                    Log.w(TAG_LOG, "blank_root_cause: unknown")
                    reportError(MSG_BLANK_FALLBACK)
                } else {
                    Log.w(TAG_LOG, "blank_root_cause: js_error")
                    // 轻量反转义：evaluateJavascript 读回的错误文本中 JSON 转义（\n、\"）
                    // 以字面形式残留，不引入 JSON 解析器，仅还原最常见的两种再截断展示
                    val shown = jsError
                        .replace("\\n", " ")
                        .replace("\\\"", "\"")
                        .take(JS_ERROR_SNIPPET_MAX)
                    reportError(MSG_BLANK_JS_ERROR_PREFIX + shown)
                }
            }
        }.onFailure {
            Log.w(TAG_LOG, "blank_root_cause: read_js_error_failed")
            reportError(MSG_BLANK_FALLBACK)
        }
    }

    /**
     * 主帧加载错误（新版回调）：仅主帧上报，避免子资源（图片/CSS）失败刷屏。
     * 主帧错误（DNS / 连接失败等）往往伴随坏缓存读出的错位资源，故先尝试自愈
     * （每轮至多一次，见 [maybeSelfHeal]），错误本身仍照常上报。
     */
    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: android.webkit.WebResourceError,
    ) {
        if (request.isForMainFrame) {
            maybeSelfHeal(view, "main_frame_error: ${error.description}")
            reportError(error.description?.toString())
        }
        super.onReceivedError(view, request, error)
    }

    /** 主帧加载错误（旧版回调，低版本走此；旧回调本身只覆盖主帧，直接自愈 + 上报）。 */
    @Suppress("DEPRECATION")
    override fun onReceivedError(
        view: WebView,
        errorCode: Int,
        description: String?,
        failingUrl: String?,
    ) {
        maybeSelfHeal(view, "main_frame_error_legacy: $errorCode")
        reportError(description)
        super.onReceivedError(view, errorCode, description, failingUrl)
    }

    /** HTTP 层级错误（4xx/5xx）：仅主帧、且仅状态码 >= 400 上报。 */
    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        response: android.webkit.WebResourceResponse,
    ) {
        if (request.isForMainFrame && response.statusCode >= 400) {
            reportError("HTTP ${response.statusCode}")
        }
        super.onReceivedHttpError(view, request, response)
    }

    /**
     * SSL 证书错误：**绝不调用 super**（super 即 `handler.cancel()` = 静默白屏）。
     *
     * 是否放行由设置项 `ignoreSslErrors` 决定；两条路都留可见提示与日志，
     * 让用户知道"为什么打不开"（rc12：过去这类站点表现为干净白屏、零线索）。
     */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        val url = error.url ?: ""
        val ignore = view.getTag(TAG_SSL_KEY) == true
        Log.w(TAG_LOG, "ssl_error code=${error.primaryError} url=$url ignore=$ignore")
        if (ignore) {
            handler.proceed()
            reportError("已按设置忽略证书错误，连接未加密校验：$url")
        } else {
            reportError("证书校验失败，已阻止加载：$url")
            handler.cancel()
        }
    }

    /** 记日志 + 上抛可见提示（description 空时给兜底文案）。 */
    private fun reportError(description: String?) {
        val message = description?.takeIf { it.isNotBlank() } ?: "页面加载失败"
        Log.e("webview_load_error", message)
        onError?.invoke(message)
    }

    /**
     * 渲染进程崩溃兜底（rc13 真恢复）：系统默认行为是宿主 Activity 被直接杀掉（体验为
     * "应用闪退"），返回 true 仅阻止闪退、原 WebView 实例已失效（复用只会继续白屏）。
     * 现通过 [onRendererGone] 令 Compose 侧 `rebuildKey++`，由 `key()` 驱动 AndroidView
     * 工厂重建**全新** WebView 并自动重载入口，同时给用户一条"已自动重载"的可见说明。
     */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        Log.w(TAG_LOG, "render_process_gone")
        reportError(MSG_RENDERER_GONE)
        onRendererGone?.invoke()
        return true
    }

    /**
     * HTML 多文件项目的虚拟文件供给：入口以 [VIRTUAL_HOST] 为 baseURL 加载后，
     * 页面里的相对路径资源（`css/style.css` / `js/app.js` 等）会请求到虚拟域名，
     * 在此按文件名就地返回内容——附加文件无需内联、无需服务器即可生效。
     */
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val files = view.getTag(TAG_FILES_KEY) as? Map<*, *> ?: return null
        val host = request.url.host ?: return null
        if (!host.equals(VIRTUAL_HOST, ignoreCase = true)) return null
        val path = request.url.path?.trimStart('/') ?: return null
        if (path.isEmpty()) return null
        val name = runCatching { java.net.URLDecoder.decode(path, "UTF-8") }.getOrNull() ?: path
        val content = files[name] as? String ?: return null
        return WebResourceResponse(mimeOf(name), "utf-8", content.byteInputStream())
    }

    /** 是否由本 WebView 自行加载（留在应用内）；未知 / 外部协议返回 false 走拦截。 */
    private fun isInAppScheme(scheme: String?): Boolean = when (scheme?.lowercase()) {
        "http", "https", "file", "data", "about", "javascript", "blob", "content" -> true
        else -> false
    }
}

/**
 * 注入 JS 错误采集器（幂等，rc13 白屏专项）：
 *
 * 捕获 `window.onerror` 与 `unhandledrejection` 写入 `window.__mmaLastError`，
 * 与 onConsoleMessage 的原生侧缓存互补（语法错误未必产生 console ERROR，
 * 未捕获异常未必走 console——两条路都留痕才有完整线索）。
 *
 * 时机说明：WebView 没有"文档开始脚本"API，且每个新文档的 window 是全新对象——
 * loadEntry 前注入的那次落在旧文档上、导航后即失效。故在 onPageStarted（页面脚本
 * 执行前最靠前的时机）与 onPageFinished（兜底）随文档重挂，`__mmaHooked` 标记
 * 保证同一文档内不会重复注入；本函数被多处幂等调用是设计使然而非冗余。
 */
private fun injectErrorCollector(view: WebView) {
    if (!view.settings.javaScriptEnabled) return
    runCatching { view.evaluateJavascript(JS_ERROR_COLLECTOR_JS, null) }
}

/** 读取当前空内容探测代次（无记录按 0）。 */
private fun currentProbeGeneration(view: WebView): Int =
    (view.getTag(TAG_PROBE_GEN_KEY) as? Int) ?: 0

/** 自增并返回探测代次：新一轮加载开始时调用，使所有在途延迟探测回调作废。 */
private fun nextProbeGeneration(view: WebView): Int {
    val generation = currentProbeGeneration(view) + 1
    view.setTag(TAG_PROBE_GEN_KEY, generation)
    return generation
}

/** 依据扩展名推断虚拟文件的 MIME（未知类型回落 text/plain）。 */
private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "html", "htm" -> "text/html"
    "css" -> "text/css"
    "js", "mjs" -> "application/javascript"
    "json" -> "application/json"
    "svg" -> "image/svg+xml"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "ico" -> "image/x-icon"
    "txt", "md" -> "text/plain"
    "xml" -> "application/xml"
    else -> "text/plain"
}

/**
 * 应用 WebSettings 与第三方 Cookie 开关（本地与导出共用同一套配置）。
 *
 * @param surfaceArgb WebView 底色（主题 surface）：**不透明**——透明底在部分 ROM 上
 *   会导致内容根本不绘制（白屏），同时暗色主题用暗底也不会出现首帧闪白。
 */
private fun applyRuntime(view: WebView, config: WebRuntimeConfig, surfaceArgb: Int) {
    val web = config.webSettings
    view.settings.javaScriptEnabled = web.javascriptEnabled
    view.settings.domStorageEnabled = web.domStorageEnabled
    view.settings.loadWithOverviewMode = true
    view.settings.useWideViewPort = true
    view.settings.cacheMode = web.cacheMode
    // 图片显式开启（个别 ROM 裁剪默认值），显示效果兜底。
    view.settings.loadsImagesAutomatically = true
    // Archive 项目走 file:// 入口加载，显式放开文件访问（Android R+ 默认关闭）。
    view.settings.allowFileAccess = true
    // 内容访问 / 数据库 / 网络加载：个别 ROM 默认值被裁剪，显式写死（缺一就可能白屏）。
    view.settings.allowContentAccess = true
    view.settings.databaseEnabled = true
    view.settings.blockNetworkLoads = false
    // 媒体自动播放（视频类站点缺此会停在黑屏）。
    view.settings.mediaPlaybackRequiresUserGesture = false
    // 多窗口：不开启时，含 target=_blank / window.open 的页面会被系统**静默丢弃** → 白屏。
    view.settings.setSupportMultipleWindows(true)
    view.setBackgroundColor(surfaceArgb)
    view.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
    // 混合内容：HTTPS 页内嵌 HTTP 资源（图片/脚本/iframe）默认被系统拦截 → 整页白屏。
    // 这是"很多网站加载一下就白屏"的根因，强制放行以兼容真实站点。
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        view.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    }
    // 允许 JS 通过 window.open 开新窗口：部分站点缺此会静默失败（按钮点了没反应/白屏）。
    view.settings.javaScriptCanOpenWindowsAutomatically = true
    // UA 归一化：系统 WebView 默认 UA 带 "; wv)" 标记，大量站点（OAuth / 支付 / 部分 SPA）
    // 会据此降级或直接返回空页。去掉该标记即与 Chrome 一致（不伪造版本，避免被识别为假 UA）；
    // 用户自定义 UA 时以其为准。
    view.settings.userAgentString = web.userAgent?.takeIf { it.isNotBlank() }
        ?: view.settings.userAgentString.replace("; wv)", ")")
    // 第三方 Cookie：Android 11+ 默认关闭，需显式开启以支持 OAuth / 支付等场景；
    // 同时显式打开全局 Cookie 接受（个别 ROM 默认关闭）→ 跨域登录回跳才不丢态。
    CookieManager.getInstance().setAcceptCookie(true)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, web.thirdPartyCookies)
    }
}

/** 依据入口形态加载内容（本地注入：URL 直连 / HTML 内联 / 资产解包后 file://）。 */
private fun loadEntry(view: WebView, entry: ProjectEntry) {
    when (entry) {
        is ProjectEntry.Url ->
            view.loadUrl(entry.url)
        is ProjectEntry.BundledHtml ->
            // 内联 HTML：无附加文件时 baseURL 传 null（单文件网页应用无需相对资源解析，
            // 内联 <style>/<script> 直接生效；避免伪造同域 base 导致相对链接跳到不存在主机）；
            // 有附加文件时改用虚拟域名 baseURL——相对路径资源由客户端拦截供给（见 InAppWebViewClient）。
            if (view.getTag(TAG_FILES_KEY) is Map<*, *> && (view.getTag(TAG_FILES_KEY) as Map<*, *>).isNotEmpty()) {
                view.loadDataWithBaseURL(VIRTUAL_BASE_URL, entry.html, "text/html", "utf-8", null)
            } else {
                view.loadDataWithBaseURL(null, entry.html, "text/html", "utf-8", null)
            }
        is ProjectEntry.Archive -> {
            // 本地注入：资产已解包到 assetRef 目录，直接以 file:// 加载解包后的入口。
            // 解包 / 重签管线在导出阶段实现（见 §11 本地注入架构）；此处为预览态默认加载 index.html。
            view.loadUrl("file://${entry.assetRef}/index.html")
        }
    }
}

/** HTML 多文件项目的虚拟域名（仅本地拦截消费，不发起真实网络请求）。 */
private const val VIRTUAL_HOST: String = "mma.local"

/** HTML 多文件项目的虚拟 baseURL（相对路径由此解析）。 */
private const val VIRTUAL_BASE_URL: String = "https://mma.local/"

/** 日志标签（白屏排查：重定向链 / 拦截 / SSL / 空内容探测统一在此）。 */
private const val TAG_LOG: String = "webview_runtime"

/**
 * 空内容探测脚本：body 既无文本、且排除脚本类元素后几乎没有子节点 → 返回 "1"。
 * 计数用 querySelectorAll 排除 script/style/noscript——SPA 空壳页（如小米 MiMo）body 下
 * 只有 1 个 root div 加 1-2 个 <script>，旧口径把脚本计入导致两级脚本时 n=3 漏判；
 * 新口径下空壳页只剩 root div 及其极少容器（n<=2）。
 * 不排除 iframe 的权衡：iframe 壳页里 iframe 往往是唯一内容节点，排除会把正常壳页
 * 误判为空壳；loading 占位页通常也是纯 div，不排除 iframe 不影响占位页判定。
 */
private const val BLANK_PROBE_JS: String =
    "(function(){var b=document.body;if(!b)return '1';" +
        "var t=(b.innerText||'').trim();" +
        "var n=document.querySelectorAll('body *:not(script):not(style):not(noscript)').length;" +
        "return (t.length===0&&n<=2)?'1':'0';})()"

/** 空内容探测延迟（SPA 首屏渲染留足时间，避免误报）。 */
private const val BLANK_PROBE_DELAY_MS: Long = 2_000L

/** 空内容复探延迟（首探为空后再等 3 秒复探一次，SPA 慢渲染防误报，两段式第二段）。 */
private const val BLANK_REPROBE_DELAY_MS: Long = 3_000L

/**
 * JS 错误采集器（注入脚本，约 0.4KB，幂等）：挂 `__mmaHooked` 防同页重复注入；
 * 捕获 onerror（保留原 handler 链，不吞站点自身逻辑）与 unhandledrejection，
 * 消息截断 300 字符后写入 `__mmaLastError`，供空内容探测读取做根因分级。
 * 全程单引号，规避 Kotlin 字符串转义与 JSON 传参歧义。
 */
private const val JS_ERROR_COLLECTOR_JS: String =
    "(function(){if(window.__mmaHooked)return;window.__mmaHooked=1;" +
        "window.__mmaLastError='';var prev=window.onerror;" +
        "function save(m){if(!m)return;if(m.length>300)m=m.substring(0,300);" +
        "window.__mmaLastError=m;}" +
        "window.onerror=function(m){save(String(m));if(prev)prev.apply(this,arguments);};" +
        "window.addEventListener('unhandledrejection',function(e){var r=e&&e.reason;" +
        "save('Promise: '+(r&&r.message?r.message:String(r)));});})()"

/**
 * 内核语法能力探测脚本：可选链 `?.` / 空值合并 `??` 是 Chrome 80 才支持的语法，
 * 现代 SPA 普遍依赖——用 `new Function` 包裹探测体（探测脚本自身不使用新语法，
 * 否则在旧内核上整个脚本解析失败、探测无从谈起），编译失败即判定内核过旧。
 */
private const val SYNTAX_PROBE_JS: String =
    "(function(){try{new Function(\"var a={};a?.b;a??1\")}catch(e){return '0'}return '1'})()"

/**
 * 读取最后一条 JS 错误（含 `__mmaHooked` 安装状态）：有错误返回错误文本；
 * 无错误且采集器未挂上（注入失败）返回 "no-hook"，供原生侧降级判断。
 */
private const val LAST_ERROR_PROBE_JS: String =
    "(function(){var e=window.__mmaLastError||'';return e?e:(window.__mmaHooked?'':'no-hook');})()"

/** 根因分级上报时截取的 JS 错误信息最大长度（ Snackbar 文案克制的硬上限）。 */
private const val JS_ERROR_SNIPPET_MAX: Int = 80

/** 最后一条 JS 错误缓存（onConsoleMessage 写入 / onPageStarted 清空，根因分级读取）。 */
private const val TAG_JS_ERROR_KEY = 0x7E110006

/** 坏缓存自愈标记（每轮加载至多自愈一次，防"失败→重载→失败"死循环）。 */
private const val TAG_SELF_HEAL_KEY = 0x7E110007

/** 空内容处理去重标记（记已处理的页面地址：onPageFinished 多次触发不重复处理 / 上报）。 */
private const val TAG_BLANK_HANDLED_KEY = 0x7E110008

/** 空内容探测代次（每次新加载 +1，作废在途延迟探测回调，防旧页结果串扰新页）。 */
private const val TAG_PROBE_GEN_KEY = 0x7E110009

/** 空内容兜底文案（探测链路失败 / 无明确根因时的第三级文案）。 */
private const val MSG_BLANK_FALLBACK: String =
    "页面已加载但内容为空，可能是脚本被拦截或需要登录，可尝试刷新页面"

/** 内核过旧根因文案（语法能力探测失败时的第一级，给出可行动的更新指引）。 */
private const val MSG_BLANK_OLD_ENGINE: String =
    "系统 WebView 内核过旧，不支持本页脚本（需 Chrome 80+）。" +
        "请到系统设置更新『Android System WebView』或『Google WebView』"

/** JS 错误根因文案前缀（第二级，后接截断后的错误信息）。 */
private const val MSG_BLANK_JS_ERROR_PREFIX: String = "页面脚本执行出错："

/** 自愈重载提示文案（跳过缓存重载前告知用户，避免"闪一下"无解释）。 */
private const val MSG_SELF_HEAL_RELOAD: String = "检测到页面异常，正在跳过缓存重新加载"

/** 渲染进程恢复提示文案（语义：已自动重载渲染进程，无需用户手动操作）。 */
private const val MSG_RENDERER_GONE: String = "渲染进程已自动重载，如页面仍异常请重新进入"

/** 用于去重判等，避免重组时重复 reload（与 Compose 内部 tag 机制隔离，使用独立整型 key）。 */
private const val TAG_RUNTIME_KEY = 0x7E110000

/** 用于去重判等，避免重组时重复 onPause / onResume。 */
private const val TAG_PAUSED_KEY = 0x7E110001

/** 用于去重判等，避免重复装配 WebViewClient（客户端无状态，装一次即可）。 */
private const val TAG_CLIENT_KEY = 0x7E110002

/** 用于去重判等，避免重复装配 WebChromeClient（进度客户端无状态，装一次即可）。 */
private const val TAG_CHROME_KEY = 0x7E110003

/** 当前虚拟文件集（name → content），随每次加载刷新，供拦截器读取。 */
private const val TAG_FILES_KEY = 0x7E110004

/** 当前是否忽略 SSL 证书错误（随每次加载刷新，供客户端读取）。 */
private const val TAG_SSL_KEY = 0x7E110005
