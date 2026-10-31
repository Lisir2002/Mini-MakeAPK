package com.make.apk.webcore.diagnostics

import android.util.Log
import android.webkit.WebView
import com.make.apk.webcore.engine.WebViewEngine

/**
 * 主帧加载完成后的内容后处理（rc17，参照 EinkBro `WebContentPostProcessor` 适配）。
 *
 * 两件事，都在 `onPageFinished` 之后异步执行，不阻塞加载：
 *
 * 1. **UA 自检**（[UA_PROBE_JS]）：把页面实际看到的 UA 指纹落盘。
 *    「某些链接永远白」的排查此前全靠推断——现在有了硬证据：页面读到的
 *    `navigator.userAgent` 里是否残留 `wv` / `Version/4.0`、
 *    `navigator.userAgentData.brands` 里是否还有 `Android WebView`。
 *    这组数据能直接判定站点视角下我们是不是"真 Chrome"。
 *
 * 2. **Declarative Shadow DOM 兜底**（[DSD_FIX_JS]）：现代站点大量使用
 *    `<template shadowrootmode="open">` 声明式影子 DOM。内核未开启 DSD 特性时
 *    这些模板不会被挂载，页面结构是空的 → 白屏，且**没有任何错误回调**。
 *    这里手工 attachShadow 一次，把未挂载的模板补上（幂等，已挂载的跳过）。
 */
internal object ContentPostProcessor {

    private const val TAG = "webcore_post_process"

    /**
     * UA 指纹探测：返回 `mobile#hasWv#hasVersion4#brands`。
     *
     * 用紧凑分隔串而不是 JSON——webcore 零依赖，不引入 JSON 库；
     * 解析在 Kotlin 侧一次 split 即可。
     */
    const val UA_PROBE_JS: String =
        "(function(){try{" +
            "var d=navigator.userAgentData;" +
            "var m=d?(d.mobile?'1':'0'):'?';" +
            "var b='';" +
            "if(d&&d.brands){for(var i=0;i<d.brands.length;i++){b+=(i?'|':'')+d.brands[i].brand;}}" +
            "var ua=navigator.userAgent||'';" +
            "var wv=/;\\s*wv\\b/.test(ua)?'1':'0';" +
            "var v4=ua.indexOf('Version/4.0')>=0?'1':'0';" +
            "return m+'#'+wv+'#'+v4+'#'+b;" +
            "}catch(e){return '';}})()"

    /**
     * DSD 兜底：把未挂载的 `template[shadowrootmode]` 手工挂到宿主上。
     *
     * 幂等：`__mmaDsdFixed` 防同页重复；已存在 `shadowRoot` 的宿主跳过（不覆盖）。
     * 全程 try/catch——这是兜底逻辑，绝不允许因为它本身出错而影响页面。
     */
    const val DSD_FIX_JS: String =
        "(function(){try{" +
            "if(window.__mmaDsdFixed)return;" +
            "window.__mmaDsdFixed=true;" +
            "var list=document.querySelectorAll('template[shadowrootmode]');" +
            "for(var i=0;i<list.length;i++){" +
            "var t=list[i],h=t.parentElement;" +
            "if(!h||h.shadowRoot)continue;" +
            "var mode=t.getAttribute('shadowrootmode')||'open';" +
            "var r=h.attachShadow({mode:mode});" +
            "r.appendChild(t.content);" +
            "}" +
            "}catch(e){}})()"

    /**
     * 执行后处理。
     *
     * @param view 已完成主帧加载的 WebView。
     * @param engine 引擎（用于取 load_id 与上报）。
     */
    fun postProcess(view: WebView, engine: WebViewEngine) {
        if (!view.settings.javaScriptEnabled) return
        reportUaFingerprint(view, engine)
        runCatching { view.evaluateJavascript(DSD_FIX_JS, null) }
            .onFailure { Log.w(TAG, "dsd_fix_failed", it) }
    }

    /** UA 自检：结果落盘（这是「站点到底看到什么」的唯一硬证据）。 */
    private fun reportUaFingerprint(view: WebView, engine: WebViewEngine) {
        runCatching {
            view.evaluateJavascript(UA_PROBE_JS) { raw ->
                val parsed = parseProbe(raw) ?: return@evaluateJavascript
                WebViewEventHook.report(
                    "preview_ua_probe",
                    mapOf(
                        // 1 = UA 里仍有 "; wv" 标记（站点可识别为内嵌浏览器）
                        "has_wv" to parsed.hasWv,
                        // 1 = UA 里仍有 "Version/4.0"（真 Chrome 没有，第二指纹）
                        "has_version4" to parsed.hasVersion4,
                        // navigator.userAgentData.mobile：true / false / 未知('?')
                        "ua_data_mobile" to parsed.mobile,
                        // brand 列表里是否还留着 "Android WebView"
                        "has_webview_brand" to parsed.hasWebViewBrand,
                        // 对应 LogFields.PREVIEW_LOAD_ID（webcore 零依赖，只能写字面量）。
                        "preview_load_id" to engine.loadTraceId,
                    ),
                )
            }
        }.onFailure { Log.w(TAG, "ua_probe_failed", it) }
    }

    /** 解析 [UA_PROBE_JS] 的返回值（`mobile#hasWv#hasVersion4#brands`，带 JSON 引号）。 */
    fun parseProbe(raw: String?): UaFingerprint? {
        val cleaned = raw?.trim()?.trim('"') ?: return null
        val parts = cleaned.split('#')
        if (parts.size < 4) return null
        val brands = parts[3]
        return UaFingerprint(
            mobile = parts[0],
            hasWv = parts[1] == "1",
            hasVersion4 = parts[2] == "1",
            hasWebViewBrand = brands.contains("WebView", ignoreCase = true),
        )
    }

    /** UA 指纹快照。 */
    data class UaFingerprint(
        /** `1` = 移动端，`0` = 桌面，`?` = 内核不支持 userAgentData。 */
        val mobile: String,
        /** UA 字符串里是否残留 `; wv`。 */
        val hasWv: Boolean,
        /** UA 字符串里是否残留 `Version/4.0`。 */
        val hasVersion4: Boolean,
        /** Client Hints brand 列表里是否残留 `Android WebView`。 */
        val hasWebViewBrand: Boolean,
    ) {
        /** 站点视角下是否已完全等同于真 Chrome。 */
        val looksLikeRealChrome: Boolean
            get() = !hasWv && !hasVersion4 && !hasWebViewBrand
    }
}
