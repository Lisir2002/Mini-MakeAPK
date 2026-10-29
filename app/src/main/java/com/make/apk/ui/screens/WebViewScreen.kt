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
    val showProgress = config.webSettings.showLoadingProgress
    val progressPosition = config.webSettings.loadingProgressPosition
    // WebView 底色：**不透明**，取主题表面色（透明底在部分 ROM 上会导致内容不绘制 → 白屏）。
    val surfaceArgb = MaterialTheme.colorScheme.surface.toArgb()

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = webViewFactory,
            update = { view ->
                if (view !is WebView) return@AndroidView
                // 导航拦截必须先于一切装配：未设 WebViewClient 时，页面内任何跳转（点击链接、
                // 站点重定向）都会被交给系统处理——表现为"跳出应用、打开外部浏览器"，
                // 壳的顶栏 / 底栏瞬间失效。预览与导出壳共用本文件，一处修复两边生效。
                ensureClient(view, onError)
                // 进度客户端（幂等，只装一次）：驱动加载进度条 + 接管新窗口。
                setupChrome(view, onError) { progress.value = it }
                // 暂停/恢复：由壳抽屉的 pauseContentWhileOpen 契约驱动，去重避免每次重组都调用。
                applyPaused(view, paused)
                // 仅在配置（入口/关键设置）变化时重新应用并加载，避免每次重组都 reload。
                if (view.getTag(TAG_RUNTIME_KEY) == config) return@AndroidView
                view.setTag(TAG_RUNTIME_KEY, config)
                // SSL 开关随每次加载刷新（客户端是幂等单实例，只能从 tag 读当前配置）。
                view.setTag(TAG_SSL_KEY, config.webSettings.ignoreSslErrors)
                // 新一次加载：进度归零，避免复用旧 WebView 时残留上一页满格进度。
                progress.value = 0
                applyRuntime(view, config, surfaceArgb)
                // 虚拟文件集（HTML 多文件项目）：入口 HTML 经虚拟域名 baseURL 加载，
                // 相对路径资源由客户端拦截供给——css/js 文件无需内联即可生效。
                val files = (config.entry as? ProjectEntry.BundledHtml)
                    ?.files?.associate { it.name to it.content } ?: emptyMap()
                view.setTag(TAG_FILES_KEY, files)
                loadEntry(view, config.entry)
            },
        )

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
private fun ensureClient(view: WebView, onError: ((String) -> Unit)?) {
    if (view.getTag(TAG_CLIENT_KEY) == true) return
    view.setTag(TAG_CLIENT_KEY, true)
    view.webViewClient = InAppWebViewClient(onError)
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
private class InAppWebViewClient(private val onError: ((String) -> Unit)?) : WebViewClient() {

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

    /** 主帧开始加载：留痕重定向链（白屏排查的第一手证据）。 */
    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
        Log.i(TAG_LOG, "page_started: $url")
        super.onPageStarted(view, url, favicon)
    }

    /** 主帧加载完成：Cookie 落盘 + 空内容探测（脚本被拦截 / 需登录时的唯一可见线索）。 */
    override fun onPageFinished(view: WebView, url: String) {
        Log.i(TAG_LOG, "page_finished: $url")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            runCatching { CookieManager.getInstance().flush() }
        }
        probeBlankContent(view)
        super.onPageFinished(view, url)
    }

    /**
     * 空内容探测：SPA 首屏靠脚本渲染，脚本一旦被拦截（内核过低 / CSP / 语法错误），
     * 页面就是"加载成功但一片空白"且**没有任何错误回调**。这里在完成后 2 秒探测一次：
     * body 既无文本又几乎没有子节点 → 判定为空内容并上抛提示。
     */
    private fun probeBlankContent(view: WebView) {
        if (!view.settings.javaScriptEnabled) return
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching {
                view.evaluateJavascript(BLANK_PROBE_JS) { result ->
                    val blank = result?.trim('"') == "1"
                    if (blank) reportError("页面已加载但内容为空，可能是脚本被拦截或需要登录")
                }
            }
        }, BLANK_PROBE_DELAY_MS)
    }

    /**
     * 主帧加载错误（新版回调）：仅主帧上报，避免子资源（图片/CSS）失败刷屏。
     * 原来完全静默 → 白屏无提示；现记日志并上抛可见提示。
     */
    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: android.webkit.WebResourceError,
    ) {
        if (request.isForMainFrame) reportError(error.description?.toString())
        super.onReceivedError(view, request, error)
    }

    /** 主帧加载错误（旧版回调，低版本走此）。 */
    @Suppress("DEPRECATION")
    override fun onReceivedError(
        view: WebView,
        errorCode: Int,
        description: String?,
        failingUrl: String?,
    ) {
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
     * 渲染进程崩溃兜底：系统默认行为是宿主 Activity 被直接杀掉（体验为"应用闪退"）；
     * 返回 true 表示已处理，改为给用户一条可见提示（白屏根因之一：渲染进程被系统回收）。
     */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        reportError("渲染进程异常退出，请重新进入页面")
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

/** 空内容探测脚本：body 既无文本又几乎没有子节点 → 返回 "1"。 */
private const val BLANK_PROBE_JS: String =
    "(function(){var b=document.body;if(!b)return '1';" +
        "var t=(b.innerText||'').trim();" +
        "var n=b.getElementsByTagName('*').length;" +
        "return (t.length===0&&n<=2)?'1':'0';})()"

/** 空内容探测延迟（SPA 首屏渲染留足时间，避免误报）。 */
private const val BLANK_PROBE_DELAY_MS: Long = 2_000L

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
