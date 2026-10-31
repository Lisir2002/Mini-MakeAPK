package com.make.apk.webcore.navigation

import android.net.http.SslError
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Log
import com.make.apk.webcore.diagnostics.WebViewEventHook
import com.make.apk.webcore.config.HttpsPolicy
import com.make.apk.webcore.diagnostics.BlankDetector
import com.make.apk.webcore.diagnostics.ContentPostProcessor
import com.make.apk.webcore.diagnostics.SelfHealController
import com.make.apk.webcore.engine.EngineEvent
import com.make.apk.webcore.engine.TAG_WEBVIEW_RUNTIME
import com.make.apk.webcore.engine.WebViewEngine

/**
 * 应用内导航客户端（W4 内核重写）：全分支**委托**——判定交 [NavigationPolicy]，
 * 执行交 [ExternalSchemeDispatcher]，资源供给交 [VirtualFileServer]，
 * 白屏诊断交 [BlankDetector] / [SelfHealController]。本类自身零状态（状态全在引擎闭包）。
 *
 * 未设置 WebViewClient 时，WebView 会把页面内跳转交给 ActivityManager 选择处理器——
 * 用户点一个链接就被甩进 Chrome / 系统浏览器，壳的顶栏、底栏、抽屉全部失效，
 * "预览 == 导出"也随之失去意义（导出的 APK 同样会逃逸）。
 *
 * 与旧单体 InAppWebViewClient（WebViewScreen.kt :323-690）的分支等价映射：
 * - 新旧双签名 shouldOverrideUrlLoading → [intercept]（旧签名视同主帧，仅 minSdk 24 路径）；
 * - onPageStarted → 留痕重定向链 + 记加载起点 + 探测代次前移 + 清错误缓存 + 注入采集器
 *   + 明文 http 主帧埋点（W4 拍板 3 新增）；
 * - onPageFinished → Cookie flush + 兜底注入 + preview_loaded 埋点（W3 契约）+ 空内容探测；
 * - onReceivedError 双签名 / onReceivedHttpError → 自愈分流 + 可见提示；
 * - onReceivedSslError → **绝不调 super**（super 即 handler.cancel() = 静默白屏），
 *   按 ignoreSslErrors 决定 proceed/cancel，语义等价旧 TAG_SSL_KEY 分支；
 * - onRenderProcessGone → 引擎 recreateRequest（chrome 层 rebuildKey++，真恢复）；
 * - shouldInterceptRequest → 虚拟文件服务（HTML 多文件项目）。
 */
internal class InAppWebViewClient(
    private val engine: WebViewEngine,
) : WebViewClient() {

    private val blankDetector = BlankDetector(engine)
    private val selfHeal = SelfHealController(engine)

    /**
     * 主帧失败的表现层（rc17 拆分）：SSL 协议错误降级重试 + 内置错误页。
     *
     * 从本类中拆出，是因为它持有"一次性降级额度"这类跨回调状态，而本类的契约是
     * "零状态、只做分发"。底色从引擎实时取（暗色主题下错误页不能是白底）。
     */
    private val errorPresenter = WebErrorPresenter(
        context = engine.context,
        surfaceArgb = { engine.lastSurfaceArgb },
        loadId = { engine.loadTraceId },
    )

    // ── 导航拦截 ──

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url?.toString() ?: return false
        return intercept(view, url, request.isForMainFrame)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        // 旧签名回调（minSdk 24 以下才走，仅为编译保留）视同主帧。
        return intercept(view, url, isMainFrame = true)
    }

    /** 判定（NavigationPolicy）+ 执行（ExternalSchemeDispatcher）分离的单点拦截。 */
    private fun intercept(view: WebView, url: String, isMainFrame: Boolean): Boolean {
        val fitToApp = engine.config.webSettings.fitToApp
        return when (val decision = NavigationPolicy.decide(url, isMainFrame, fitToApp)) {
            is NavDecision.InPlace -> false
            is NavDecision.External -> {
                if (ExternalSchemeDispatcher.dispatchExternal(view, decision.url)) {
                    Log.i(TAG_WEBVIEW_RUNTIME, "in_page_link_external: $url")
                    // 成功移交外部：留痕（URL 由 app 侧 Sanitizer 裁剪成 scheme://host/***）。
                    WebViewEventHook.report(
                        "webview_link_external",
                        mapOf(
                            "url" to decision.url,
                            // 对应 LogFields.PREVIEW_LOAD_ID（webcore 零依赖，只能写字面量）。
                            "preview_load_id" to engine.loadTraceId,
                        ),
                    )
                } else {
                    reportError(view, "无法在外部打开该链接")
                }
                true
            }
            is NavDecision.Intent -> {
                if (ExternalSchemeDispatcher.dispatchIntent(view, decision.url)) {
                    true
                } else {
                    blockWithNotice(view, decision.url)
                    true
                }
            }
            is NavDecision.BlockWithNotice -> {
                blockWithNotice(view, decision.url)
                true
            }
        }
    }

    /** 拦截提示（rc12：绝不静默——登录链一旦落到自定义 scheme 就表现为白屏）。 */
    private fun blockWithNotice(view: WebView, url: String) {
        val scheme = url.substringBefore(':').ifBlank { "未知协议" }
        reportError(view, "应用内无法打开 $scheme 链接，已阻止跳转")
        Log.w(TAG_WEBVIEW_RUNTIME, "external_scheme_blocked: $scheme")
        // 拦截即白屏高发点（intent:// 登录链 / 支付 scheme）：落盘留证，只记 scheme 不记 URL。
        WebViewEventHook.report(
            "webview_external_scheme_blocked",
            mapOf(
                "scheme" to url.substringBefore(':'),
                // 对应 LogFields.PREVIEW_LOAD_ID（webcore 零依赖，只能写字面量）。
                "preview_load_id" to engine.loadTraceId,
            ),
        )
    }

    // ── 生命周期埋点与诊断链 ──

    /** 主帧开始加载：留痕重定向链（白屏排查的第一手证据）+ 重置探测代次与错误缓存。 */
    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
        Log.i(TAG_WEBVIEW_RUNTIME, "page_started: $url")
        // 记录加载起点（preview_loaded 埋点的 duration 口径，W3 ⑬）。
        engine.pageStartedAt = System.currentTimeMillis()
        // 首帧时刻归零：本轮若从未收到 onPageCommitVisible，就是"提交了但没画出来"。
        engine.firstPaintAt = 0L
        // 换页时释放一次性降级额度（同一地址间隔访问仍可再降级，同次停留至多一次）。
        errorPresenter.onPageStarted(url)
        // 新一轮加载：作废在途空内容探测（旧页的延迟回调不得串扰新页）；
        // 顺带清空上一页的 JS 错误缓存，避免把旧页异常误诊到新页头上。
        engine.nextProbeGeneration()
        engine.lastConsoleRecord = null
        // 新一轮加载：换发关联 id，把本次加载产生的 page / error / blank 事件串成因果链。
        engine.loadTraceId = java.util.UUID.randomUUID().toString().replace("-", "").take(8)
        // 重定向链归零（302 链在旧实现里表现为"中间一段无事件"，无法归因）。
        engine.redirectHops = 0
        engine.navigationStartUrl = url
        engine.lastVisitedUrl = ""
        // 加载起点埋点（诊断盲区补齐）：此前只打 logcat，重启即丢——「没开始加载」与
        // 「开始但没完成」无法区分。必须放在 loadTraceId 赋值**之后**（否则 load_id 是旧值/空）。
        WebViewEventHook.report(
            "preview_load_started",
            mapOf(
                "url" to url,
                // 对应 LogFields.PREVIEW_LOAD_ID（webcore 零依赖，只能写字面量）。
                "preview_load_id" to engine.loadTraceId,
            ),
        )
        // 明文 http 主帧放行（W4 拍板 3）+ plain_http_loaded 埋点（渐进收紧的数据依据）。
        if (HttpsPolicy.isPlainTextHttp(url)) {
            WebViewEventHook.report(
                HttpsPolicy.EVENT_PLAIN_HTTP_LOADED,
                mapOf(
                    "has_url" to url.isNotBlank(),
                    // 对应 LogFields.PREVIEW_LOAD_ID（webcore 零依赖，只能写字面量）。
                    "preview_load_id" to engine.loadTraceId,
                ),
            )
        }
        // 每个新文档的 window 都是全新对象，错误采集器必须随文档重挂（幂等，
        // 由 __mmaHooked 防同页重复）；此处是页面脚本执行前最靠前的挂载时机。
        com.make.apk.webcore.diagnostics.JsErrorCollector.inject(view)
        super.onPageStarted(view, url, favicon)
    }

    /**
     * **主帧导航提交**（含 302 重定向跳）：本方法是全 API 级别唯一能观测到重定向链的回调。
     *
     * 为什么必须接：重定向是「白屏时长」的最大来源之一（www → mbd 这类 302 首访要付
     * DNS + TLS + 服务端渲染的完整开销）。旧实现完全不接，于是那一整段空窗在日志里
     * 表现为「A 开始 → 长时间无事件 → B 完成」，既无 URL 也无跳数，只能靠相邻事件的
     * 时间差去猜，无法与「渲染失败导致的白屏」区分。
     *
     * 实现说明：`WebViewClient` **没有** `onReceivedHttpRedirectFor`——该回调在
     * android-34 与 androidx.webkit 的 `WebViewClientCompat` 中均不存在（已用 javap 核对）。
     * `doUpdateVisitedHistory` 是每次主帧导航提交都会触发的既有回调，据此以
     * 「提交地址 != 起始地址」判定重定向跳，不伪造状态码（拿不到就记 -1）。
     */
    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        val step = RedirectTracker.evaluate(
            prevUrl = engine.lastVisitedUrl,
            url = url,
            startUrl = engine.navigationStartUrl,
            hops = engine.redirectHops,
        )
        if (step != null) {
            engine.lastVisitedUrl = url
            engine.redirectHops = step.hops
            WebViewEventHook.report(
                "preview_redirect",
                mapOf(
                    "hop" to step.hops,
                    // 跨站跳转要重付 DNS + TLS，是白屏时长的主要来源；同站内跳转则不然。
                    // 不能记 URL：脱敏器会把 url 键裁成 scheme://host/***，
                    // from / to 会变成同一串，事件既无用又像 bug。
                    "host_changed" to step.hostChanged,
                    "path_changed" to step.pathChanged,
                    "is_reload" to isReload,
                    // 对应 LogFields.PREVIEW_LOAD_ID（webcore 零依赖，只能写字面量）。
                    "preview_load_id" to engine.loadTraceId,
                ),
            )
            Log.i(TAG_WEBVIEW_RUNTIME, "redirect hop${step.hops}: host_changed=${step.hostChanged} -> $url")
        }
        super.doUpdateVisitedHistory(view, url, isReload)
    }

    /**
     * **首帧提交**（API 23+，minSdk 24 故无需版本分支）。
     *
     * 官方语义：WebView 从此不再绘制上一页遗留内容，下一次绘制将显示新页面。
     * 这是"页面真的画出来了"的唯一可信信号——`onPageFinished` 只代表文档加载完，
     * 文档明确写着"不保证下一帧反映 DOM 状态"，所以过去 `success` 恒为 true 是假指标。
     *
     * 记录时刻供 `preview_loaded` 判定：本次加载若从未进入这里，说明
     * commit 了却没有绘制（尺寸 0 / 被遮挡 / 合成器异常），这类白屏此前完全不可见。
     */
    override fun onPageCommitVisible(view: WebView, url: String) {
        engine.firstPaintAt = System.currentTimeMillis()
        val firstPaintMs = (engine.firstPaintAt - engine.pageStartedAt).coerceAtLeast(0L)
        Log.i(TAG_WEBVIEW_RUNTIME, "page_commit_visible: ${firstPaintMs}ms $url")
        WebViewEventHook.report(
            "preview_first_paint",
            mapOf(
                "first_paint_ms" to firstPaintMs,
                // 对应 LogFields.PREVIEW_LOAD_ID（webcore 零依赖，只能写字面量）。
                "preview_load_id" to engine.loadTraceId,
            ),
        )
        super.onPageCommitVisible(view, url)
    }

    /** 主帧加载完成：Cookie 落盘 + 补挂错误采集器 + preview_loaded 埋点 + 空内容探测。 */
    override fun onPageFinished(view: WebView, url: String) {
        Log.i(TAG_WEBVIEW_RUNTIME, "page_finished: $url")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            runCatching { CookieManager.getInstance().flush() }
        }
        // 自愈重载的文档已落地：恢复用户 cacheMode（NO_CACHE 必须一次性生效，
        // 否则后续每个页面都绕开缓存，从"偶尔慢"恶化成"越用越慢"）。
        engine.restoreCacheMode(view)
        // 兜底再挂一次采集器：onPageStarted 时机在个别 ROM 上晚于首帧脚本执行，
        // 幂等标记保证同一文档内不会重复注入。
        com.make.apk.webcore.diagnostics.JsErrorCollector.inject(view)
        // preview_loaded 埋点（W3 ⑬ 契约保持）：duration = onPageStarted → onPageFinished。
        val durationMs = (System.currentTimeMillis() - engine.pageStartedAt).coerceAtLeast(0L)
        // success 口径修正（rc17）：此前**硬编码 true**，只能证明"文档加载完"，
        // 完全不能证明"画出来了"（官方文档：onPageFinished 不保证下一帧反映 DOM）。
        // 现要求本次加载确实经历过首帧提交——没经历就是 commit 了却未绘制
        // （尺寸 0 / 被遮挡 / 合成器异常），必须记 false，否则日志永远在骗人。
        val firstPaintMs = if (engine.firstPaintAt > engine.pageStartedAt) {
            engine.firstPaintAt - engine.pageStartedAt
        } else {
            -1L
        }
        WebViewEventHook.report(
            "preview_loaded",
            mapOf(
                "duration_ms" to durationMs,
                "success" to (firstPaintMs >= 0),
                // 首帧耗时；-1 = 本次加载从未收到 onPageCommitVisible
                "first_paint_ms" to firstPaintMs,
                // 最终落地地址：302 链之后 view 实际停留的 URL。此前只有落点没有起点，
                // 无法回答"这个 loaded 对应哪个 started"。
                "final_url" to (view.url ?: url),
                "redirect_hops" to engine.redirectHops,
                // 因果链锚点：同一次加载的错误 / 白屏事件都带同一个 preview_load_id。
                // 对应 LogFields.PREVIEW_LOAD_ID（webcore 零依赖，只能写字面量）。
                "preview_load_id" to engine.loadTraceId,
            ),
        )
        engine.emit(EngineEvent.PageLoaded(durationMs))
        // 加载后处理（rc17）：UA 指纹自检 + Declarative Shadow DOM 兜底。
        // 异步执行、不阻塞 onPageFinished 返回。
        ContentPostProcessor.postProcess(view, engine)
        blankDetector.onPageFinished(view, url)
        super.onPageFinished(view, url)
    }

    // ── 加载错误 ──

    /**
     * 主帧加载错误（新版回调）：仅主帧上报，避免子资源（图片/CSS）失败刷屏。
     * 主帧错误（DNS / 连接失败等）往往伴随坏缓存读出的错位资源，故先尝试自愈
     * （每轮至多一次），错误本身仍照常上报。
     */
    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) {
            // 主帧失败 = 白屏第一现场：落盘留证（logcat 重启即丢）。
            WebViewEventHook.report(
                "webview_main_frame_error",
                mapOf(
                    "url" to (request.url?.toString() ?: ""),
                    "error_code" to error.errorCode,
                    // 对应 LogFields.PREVIEW_LOAD_ID（webcore 零依赖，只能写字面量）。
                    "preview_load_id" to engine.loadTraceId,
                ),
            )
            // 先交表现层（rc17）：SSL 协议错误 → 自动降级 http 重试一次；
            // 其余主帧失败 → 渲染内置错误页。已接管时不再自动自愈——
            // 错误页带「重试」按钮，用户可控；自动 reload 反而会盖掉失败原因。
            val handled = errorPresenter.handleMainFrameError(
                view = view,
                failingUrl = request.url?.toString(),
                isForMainFrame = true,
                description = error.description?.toString(),
                errorCode = error.errorCode,
            )
            if (!handled) {
                // 主帧错误（DNS / 连接失败等）往往伴随坏缓存读出的错位资源，
                // 故先尝试自愈（每轮至多一次），错误本身仍照常上报。
                selfHeal.maybeSelfHeal(view, "main_frame_error: ${error.description}")
                reportError(view, error.description?.toString())
            }
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
        WebViewEventHook.report(
            "webview_main_frame_error",
            mapOf(
                "url" to (failingUrl ?: ""),
                "error_code" to errorCode,
                // 对应 LogFields.PREVIEW_LOAD_ID（webcore 零依赖，只能写字面量）。
                "preview_load_id" to engine.loadTraceId,
            ),
        )
        // 与新版回调同契约：先交表现层（降级 / 错误页），未接管才走自愈 + 提示。
        val handled = errorPresenter.handleMainFrameError(
            view = view,
            failingUrl = failingUrl,
            isForMainFrame = true,
            description = description,
            errorCode = errorCode,
        )
        if (!handled) {
            selfHeal.maybeSelfHeal(view, "main_frame_error_legacy: $errorCode")
            reportError(view, description)
        }
        super.onReceivedError(view, errorCode, description, failingUrl)
    }

    /** HTTP 层级错误（4xx/5xx）：仅主帧、且仅状态码 >= 400 上报。 */
    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        response: WebResourceResponse,
    ) {
        if (request.isForMainFrame && response.statusCode >= 400) {
            // 4xx/5xx 主帧是"看起来加载成功、实际错误页/空白"的典型来源，必须落盘。
            WebViewEventHook.report(
                "webview_http_error",
                mapOf(
                    "url" to (request.url?.toString() ?: ""),
                    "status_code" to response.statusCode,
                    // 对应 LogFields.PREVIEW_LOAD_ID（webcore 零依赖，只能写字面量）。
                    "preview_load_id" to engine.loadTraceId,
                ),
            )
            reportError(view, "HTTP ${response.statusCode}")
        }
        super.onReceivedHttpError(view, request, response)
    }

    /**
     * SSL 证书错误：**绝不调用 super**（super 即 `handler.cancel()` = 静默白屏）。
     *
     * 是否放行由设置项 `ignoreSslErrors` 决定（旧 TAG_SSL_KEY 语义等价——引擎每次装配
     * / applyConfig 都从 config 现读，不再需要 tag 中转）；两条路都留可见提示与日志，
     * 让用户知道"为什么打不开"（rc12：过去这类站点表现为干净白屏、零线索）。
     */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        val url = error.url ?: ""
        val ignore = engine.config.webSettings.ignoreSslErrors
        Log.w(TAG_WEBVIEW_RUNTIME, "ssl_error code=${error.primaryError} url=$url ignore=$ignore")
        // SSL 拦截过去只打 logcat：用户侧表现为"干净白屏 / 打不开"，事后零证据。
        // url 交由 app 侧 Sanitizer 裁剪（scheme://host/***），此处不做任何自拼。
        WebViewEventHook.report(
            "webview_ssl_error",
            mapOf(
                "url" to url,
                "error_code" to error.primaryError,
                "ignore" to ignore,
                // 对应 LogFields.PREVIEW_LOAD_ID（webcore 零依赖，只能写字面量）。
                "preview_load_id" to engine.loadTraceId,
            ),
        )
        val message = if (ignore) {
            handler.proceed()
            "已按设置忽略证书错误，连接未加密校验：$url"
        } else {
            handler.cancel()
            "证书校验失败，已阻止加载：$url"
        }
        // 单一提示通道：仅经 SslOutcome 事件上行（chrome 层消费一次，绝不重复 onError）。
        Log.e("webview_load_error", message)
        engine.emit(EngineEvent.SslOutcome(url = url, ignored = ignore, message = message))
    }

    /**
     * 渲染进程崩溃兜底（rc13 真恢复）：系统默认行为是宿主 Activity 被直接杀掉（体验为
     * "应用闪退"），返回 true 仅阻止闪退、原 WebView 实例已失效（复用只会继续白屏）。
     * 现经引擎 [WebViewEngine.recreateRequest] 上行 [EngineEvent.RendererGone]，
     * chrome 层 rebuildKey++ 驱动 AndroidView 工厂重建**全新** WebView 并自动重载入口，
     * 同时给用户一条"已自动重载"的可见说明。
     */
    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
        Log.w(TAG_WEBVIEW_RUNTIME, "render_process_gone")
        // 渲染进程死亡是"整页被回收"级别的事件：落盘留证，否则只能等用户复述"闪了一下"。
        WebViewEventHook.report(
            "webview_renderer_gone",
            mapOf(
                "url" to (view.url ?: ""),
                // 对应 LogFields.PREVIEW_LOAD_ID（webcore 零依赖，只能写字面量）。
                "preview_load_id" to engine.loadTraceId,
            ),
        )
        // 单一提示通道：可见说明由 chrome 层在 RendererGone 事件处理时给出（绝不重复 onError）。
        engine.recreateRequest()
        return true
    }

    // ── 资源供给 ──

    /**
     * HTML 多文件项目的虚拟文件供给：入口以 `https://mma.local/` 为 baseURL 加载后，
     * 页面里的相对路径资源（`css/style.css` / `js/app.js` 等）会请求到虚拟域名，
     * 在此按文件名就地返回内容——附加文件无需内联、无需服务器即可生效。
     */
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? = VirtualFileServer.responseFor(engine.virtualFiles, request.url)

    // ── 内部 ──

    /** 记日志 + 经引擎上行可见提示（description 空时给兜底文案）。 */
    private fun reportError(view: WebView, description: String?) {
        val message = description?.takeIf { it.isNotBlank() } ?: "页面加载失败"
        Log.e("webview_load_error", message)
        engine.emit(EngineEvent.MainFrameError(message))
    }
}
