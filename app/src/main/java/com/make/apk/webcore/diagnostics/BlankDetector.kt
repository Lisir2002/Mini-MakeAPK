package com.make.apk.webcore.diagnostics

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import com.make.apk.webcore.diagnostics.WebViewEventHook
import com.make.apk.webcore.engine.EngineEvent
import com.make.apk.webcore.engine.WebViewEngine

/**
 * 空内容探测（W4 内核重写，两段式 + 代次防串扰，rc13 白屏专项升级）。
 *
 * SPA 首屏靠脚本渲染，脚本一旦跑不起来，页面就是"加载成功但一片空白"且
 * **没有任何错误回调**。两段式：2 秒首探，为空再等 3 秒复探一次，仍空才进入处理
 * （[handleBlankConfirmed]：先自愈、自愈过则做根因三级判定并上报）。
 *
 * 防串扰：回调闭包持有探测代次（引擎闭包字段，旧 TAG_PROBE_GEN_KEY 替代），
 * 若期间发生新加载（代次已变）直接作废，避免旧页的延迟探测结果落到新页上。
 * 去重：同一页面地址只处理一次（旧 TAG_BLANK_HANDLED_KEY 替代）。
 *
 * W3 ⑬ 埋点契约保持：`preview_blank_confirmed`（has_url，WARN）、
 * `preview_blank_root_cause`（reason/detail，ERROR）。
 *
 * 探测 JS 常量**原样随迁**（旧单体 :876-886，逐字符等值，勿改动）。
 */
internal class BlankDetector(
    private val engine: WebViewEngine,
) {

    private val selfHeal = SelfHealController(engine)

    /** 空内容探测延迟（SPA 首屏渲染留足时间，避免误报）。 */
    private val blankProbeDelayMs: Long = 2_000L

    /** 空内容复探延迟（首探为空后再等 3 秒复探一次，SPA 慢渲染防误报，两段式第二段）。 */
    private val blankReprobeDelayMs: Long = 3_000L

    /** onPageFinished 入口（探测链起点）。 */
    fun onPageFinished(view: WebView, url: String) {
        probeBlankContent(view)
    }

    private fun probeBlankContent(view: WebView) {
        if (!view.settings.javaScriptEnabled) return
        val pageUrl = view.url ?: ""
        val generation = engine.probeGeneration
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching {
                view.evaluateJavascript(BLANK_PROBE_JS) { result ->
                    // 期间已有新加载：本探测作废
                    if (engine.probeGeneration != generation) return@evaluateJavascript
                    if (result?.trim('"') != "1") return@evaluateJavascript
                    // 首探为空：可能是 SPA 慢渲染，复探一次再定论
                    Handler(Looper.getMainLooper()).postDelayed({
                        runCatching {
                            view.evaluateJavascript(BLANK_PROBE_JS) { again ->
                                if (engine.probeGeneration != generation) {
                                    return@evaluateJavascript
                                }
                                if (again?.trim('"') == "1") handleBlankConfirmed(view, pageUrl)
                            }
                        }
                    }, blankReprobeDelayMs)
                }
            }
        }, blankProbeDelayMs)
    }

    /**
     * 空内容最终确认（两级去重 + 自愈分流）：
     * - 同一页面地址只处理一次（onPageFinished 对同一页可能触发多次，如 hash 跳转）；
     * - 本轮加载尚未自愈过 → 先做坏缓存自愈（[SelfHealController.maybeSelfHeal]），
     *   重载后新探测再判；
     * - 自愈过仍空 → 才认定真白屏，做根因三级判定并上报（[reportRootCause]）。
     */
    private fun handleBlankConfirmed(view: WebView, pageUrl: String) {
        if (engine.blankHandledUrl == pageUrl) return
        engine.blankHandledUrl = pageUrl
        // W3 ⑬ 埋点：两段式探测都为空 = 白屏最终确认（自愈与否由后续链路细分）。
        WebViewEventHook.report("preview_blank_confirmed", mapOf("has_url" to pageUrl.isNotBlank()))
        if (!engine.selfHealed) {
            selfHeal.maybeSelfHeal(view, "blank_content")
            return
        }
        reportRootCause(view)
    }

    /**
     * 根因三级判定（自愈后仍空才走到这里）：
     * 1. 语法能力探测失败 = 内核 < Chrome 80 → 可行动的更新指引；
     * 2. 采集到 JS 错误 → 上报截断后的错误信息（80 字符）；
     * 3. 都没有 → 兜底文案（脚本被拦截 / 需登录，可尝试刷新）。
     */
    private fun reportRootCause(view: WebView) {
        runCatching {
            view.evaluateJavascript(RootCauseClassifier.SYNTAX_PROBE_JS) { syntax ->
                val fromSyntax = RootCauseClassifier.fromSyntaxProbe(syntax)
                when {
                    fromSyntax != null -> emitCause(fromSyntax)
                    else -> readJsErrorThenReport(view)
                }
            }
        }.onFailure {
            WebViewEventHook.report(
                "preview_blank_root_cause",
                mapOf("reason" to "PROBE_FAILED", "detail" to "evaluate_failed"),
            )
            emitCause(BlankRootCause.ProbeFailed)
        }
    }

    /** 读取注入采集器缓存的最后一条 JS 错误（页面侧 `__mmaLastError`），据此上报第二/三级文案。 */
    private fun readJsErrorThenReport(view: WebView) {
        runCatching {
            view.evaluateJavascript(RootCauseClassifier.LAST_ERROR_PROBE_JS) { raw ->
                // 页面侧采集为空时，回落到原生 console 缓存（采集器注入失败也能兜住）
                val cause = RootCauseClassifier.fromJsError(raw, engine.lastConsoleRecord)
                emitCause(cause)
            }
        }.onFailure {
            WebViewEventHook.report(
                "preview_blank_root_cause",
                mapOf("reason" to "UNKNOWN", "detail" to "read_js_error_failed"),
            )
            emitCause(BlankRootCause.Unknown)
        }
    }

    /** 埋点（W3 契约字段：JS_ERROR 带 detail；其余 reason-only）+ 事件上行（W4 拍板 5）。 */
    private fun emitCause(cause: BlankRootCause) {
        val fields = buildMap {
            put("reason", cause.eventReason)
            (cause as? BlankRootCause.JsError)?.detail?.let { put("detail", it) }
        }
        WebViewEventHook.report("preview_blank_root_cause", fields)
        engine.emit(EngineEvent.BlankConfirmed(reason = cause.eventReason, message = cause.message))
    }

    companion object {
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
    }
}
