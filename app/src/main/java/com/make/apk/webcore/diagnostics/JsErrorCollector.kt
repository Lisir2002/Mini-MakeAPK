package com.make.apk.webcore.diagnostics

import android.webkit.WebView
import com.make.apk.webcore.engine.TAG_WEBVIEW_RUNTIME
import com.make.apk.webcore.engine.WebViewEngine

/**
 * JS 错误采集器（W4 内核重写，幂等）。
 *
 * 捕获 `window.onerror` 与 `unhandledrejection` 写入 `window.__mmaLastError`，
 * 与 onConsoleMessage 的原生侧缓存（[WebViewEngine.lastConsoleRecord]）互补：
 * 语法错误未必产生 console ERROR，未捕获异常未必走 console——两条路都留痕才有完整线索。
 *
 * 时机说明（与旧单体 injectErrorCollector :692-707 等价迁移）：WebView 没有"文档开始脚本"
 * API，且每个新文档的 window 是全新对象——加载前注入的那次落在旧文档上、导航后即失效。
 * 故在 onPageStarted（页面脚本执行前最靠前的时机）与 onPageFinished（兜底）随文档重挂，
 * `__mmaHooked` 标记保证同一文档内不会重复注入；本函数被多处幂等调用是设计使然而非冗余。
 *
 * 采集器脚本（[COLLECTOR_JS]）**原样随迁**（旧单体 :894-901，逐字符等值，勿改动）。
 */
object JsErrorCollector {

    /**
     * 注入脚本（约 0.4KB，幂等）：挂 `__mmaHooked` 防同页重复注入；捕获 onerror
     * （保留原 handler 链，不吞站点自身逻辑）与 unhandledrejection，消息截断 300 字符
     * 后写入 `__mmaLastError`，供空内容探测读取做根因分级。
     * 全程单引号，规避 Kotlin 字符串转义与 JSON 传参歧义。
     */
    const val COLLECTOR_JS: String =
        "(function(){if(window.__mmaHooked)return;window.__mmaHooked=1;" +
            "window.__mmaLastError='';var prev=window.onerror;" +
            "function save(m){if(!m)return;if(m.length>300)m=m.substring(0,300);" +
            "window.__mmaLastError=m;}" +
            "window.onerror=function(m){save(String(m));if(prev)prev.apply(this,arguments);};" +
            "window.addEventListener('unhandledrejection',function(e){var r=e&&e.reason;" +
            "save('Promise: '+(r&&r.message?r.message:String(r)));});})()"

    /** 注入错误采集器（幂等；JS 关闭时跳过，注入异常吞掉——诊断设施绝不阻断业务）。 */
    fun inject(view: WebView) {
        if (!view.settings.javaScriptEnabled) return
        runCatching { view.evaluateJavascript(COLLECTOR_JS, null) }
    }

    /** Logcat 留痕（导出壳唯一诊断面）：js_console_error，与旧单体等值。 */
    fun logConsoleError(record: String) {
        android.util.Log.w(TAG_WEBVIEW_RUNTIME, "js_console_error: $record")
    }
}
