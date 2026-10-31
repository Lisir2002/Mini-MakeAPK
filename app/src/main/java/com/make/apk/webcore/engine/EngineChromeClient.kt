package com.make.apk.webcore.engine

import android.os.Message
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Log

/**
 * 引擎进度/窗口客户端（W4 内核重写）：与旧单体 setupChrome（WebViewScreen.kt :221-307）
 * **逐行等价迁移**，tag 幂等守卫改为"引擎创建时一次性装配"（引擎是客户端唯一装配者）。
 *
 * 仅 [WebViewClient] 无法接收 onProgressChanged（进度条数据源）与窗口创建事件
 * （部分站点 window.open 依赖它，缺省会静默失败 → 白屏），故必须补 [WebChromeClient]。
 */
internal class EngineChromeClient(
    private val engine: WebViewEngine,
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        engine.emit(EngineEvent.ProgressChanged(newProgress.coerceIn(0, 100)))
    }

    /**
     * 页面标题回调（[com.make.apk.data.model.TitleSource.PAGE_TITLE] 来源）：
     * 浏览器标签 / 顶栏标题取自此。仅在标题非空时回传，
     * 避免把加载瞬间的空标题写进缓存。
     */
    override fun onReceivedTitle(view: WebView?, title: String?) {
        if (!title.isNullOrBlank()) engine.emit(EngineEvent.TitleReceived(title))
        super.onReceivedTitle(view, title)
    }

    /**
     * JS console 错误采集（rc13 白屏专项）：SPA 的脚本异常原本**零回调零日志**，
     * 白屏后毫无线索。这里做两件事：
     * 1. 记 `js_console_error` 日志（带来源与行号，Logcat 是导出壳唯一可用的诊断面）；
     * 2. 缓存"最后一条错误"到引擎闭包字段（旧 TAG_JS_ERROR_KEY 替代），
     *    供空内容探测做根因分级，并上行 [EngineEvent.ConsoleError]。
     * 限流去重：SPA 循环报错会瞬间刷屏，与上一条相同则只缓存不重复记日志/上行。
     */
    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        val message = consoleMessage
        if (message != null && message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
            val record = "${message.message()} @${message.sourceId()}:${message.lineNumber()}"
            if (engine.lastConsoleRecord != record) {
                engine.lastConsoleRecord = record
                Log.w(TAG_WEBVIEW_RUNTIME, "js_console_error: $record")
                engine.emit(EngineEvent.ConsoleError(record))
            }
        }
        return super.onConsoleMessage(consoleMessage)
    }

    /**
     * 新窗口（`window.open` / `target="_blank"`）：系统默认**忽略一切开窗请求**，
     * 页面因此停在空白。壳内只有一个可见 WebView，故创建一次性承载用的子 WebView，
     * 并把它的导航**转发回主 view**——既保住合规的 transport 协议（必须
     * [Message.sendToTarget]，否则子进程永不导航），又不新增可见容器。
     */
    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message,
    ): Boolean {
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        // W4 收口 F4：上一次开窗的临时子 WebView 就地销毁（transport 已消费完、
        // 不再被内核引用），兑现「destroy 唯一销毁点」契约——临时实例只在
        // 引擎闭包里存活一个开窗周期，绝不累积。
        engine.destroyPendingPopup()
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
        // 记到引擎闭包：主 view 销毁（engine.destroy）或下一次开窗时统一收口。
        engine.pendingPopup = child
        Log.i(TAG_WEBVIEW_RUNTIME, "popup_redirected")
        return true
    }

    override fun onCloseWindow(window: WebView) {
        // 子窗口只是一次性承载，无需销毁主 view；忽略即可（避免误杀当前页）。
        Log.i(TAG_WEBVIEW_RUNTIME, "popup_closed")
    }
}
