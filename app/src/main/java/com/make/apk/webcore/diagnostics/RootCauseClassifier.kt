package com.make.apk.webcore.diagnostics

/**
 * 白屏根因分级（W4 内核重写）：空内容两段式探测最终命中后的三级判定结果。
 *
 * 事件名契约（W3 ⑬，必须保持）：`preview_blank_root_cause`，reason ∈
 * {PROBE_FAILED, ENGINE_TOO_OLD, JS_ERROR, UNKNOWN}；级别 ERROR。
 * 文案分级（W4 主控拍板 5）：每级给出对应中文提示，经
 * [com.make.apk.webcore.engine.EngineEvent.BlankConfirmed] 事件上抛 chrome 层。
 *
 * 三级判定语义（与旧单体 reportBlankRootCause :510-579 等价迁移）：
 * 1. 语法能力探测失败 = 内核 < Chrome 80，现代 SPA 硬门槛 → 可行动的更新指引；
 * 2. 采集到 JS 错误 → 截断后的错误信息（80 字符）；
 * 3. 探测链路失败 / 无明确根因 → 兜底文案。
 */
sealed interface BlankRootCause {

    /** 探测链路自身失败（evaluateJavascript 回调 null / 抛异常），不误诊为页面问题。 */
    data object ProbeFailed : BlankRootCause

    /** 内核过旧（< Chrome 80，可选链 / 空值合并等现代 SPA 硬门槛）。 */
    data object EngineTooOld : BlankRootCause

    /** JS 错误（detail 带截断后的错误文本）。 */
    data class JsError(val detail: String) : BlankRootCause

    /** 有错误但归类不出（脚本被拦截 / 需登录等）。 */
    data object Unknown : BlankRootCause

    /** W3 埋点契约的 reason 字段值。 */
    val eventReason: String
        get() = when (this) {
            ProbeFailed -> "PROBE_FAILED"
            EngineTooOld -> "ENGINE_TOO_OLD"
            is JsError -> "JS_ERROR"
            Unknown -> "UNKNOWN"
        }

    /** 用户可见中文文案（W4 拍板 5：onError 通知按根因分级）。 */
    val message: String
        get() = when (this) {
            ProbeFailed, is Unknown -> MSG_BLANK_FALLBACK
            EngineTooOld -> MSG_BLANK_OLD_ENGINE
            is JsError -> MSG_BLANK_JS_ERROR_PREFIX + detail.unescapeJsSnippet()
        }

    private fun String.unescapeJsSnippet(): String =
        replace("\\n", " ").replace("\\\"", "\"").take(JS_ERROR_SNIPPET_MAX)

    companion object {
        /** 兜底文案（探测链路失败 / 无明确根因时的第三级）。 */
        const val MSG_BLANK_FALLBACK: String =
            "页面已加载但内容为空，可能是脚本被拦截或需要登录，可尝试刷新页面"

        /** 内核过旧文案（第一级，给出可行动的更新指引）。 */
        const val MSG_BLANK_OLD_ENGINE: String =
            "系统 WebView 内核过旧，不支持本页脚本（需 Chrome 80+）。" +
                "请到系统设置更新『Android System WebView』或『Google WebView』"

        /** JS 错误文案前缀（第二级，后接截断后的错误信息）。 */
        const val MSG_BLANK_JS_ERROR_PREFIX: String = "页面脚本执行出错："

        /** 截断上限（与 [RootCauseClassifier.JS_ERROR_SNIPPET_MAX] 同值）。 */
        internal const val JS_ERROR_SNIPPET_MAX: Int = 80
    }
}

/**
 * 根因分类器（纯函数，JVM 可单测）：把探测原始结果映射为 [BlankRootCause]。
 *
 * 探针 JS 常量**原样随迁**（旧单体 :903-916，逐字符等值，勿改动——探测脚本自身
 * 不能使用新语法，否则旧内核上整体解析失败、探测无从谈起）。
 */
object RootCauseClassifier {

    /** 根因分级上报时截取的 JS 错误信息最大长度（Snackbar 文案克制的硬上限）。 */
    const val JS_ERROR_SNIPPET_MAX: Int = 80

    /**
     * 内核语法能力探测脚本：可选链 `?.` / 空值合并 `??` 是 Chrome 80 才支持的语法，
     * 现代 SPA 普遍依赖——用 `new Function` 包裹探测体，编译失败即判定内核过旧。
     */
    const val SYNTAX_PROBE_JS: String =
        "(function(){try{new Function(\"var a={};a?.b;a??1\")}catch(e){return '0'}return '1'})()"

    /**
     * 读取最后一条 JS 错误（含 `__mmaHooked` 安装状态）：有错误返回错误文本；
     * 无错误且采集器未挂上（注入失败）返回 "no-hook"，供原生侧降级判断。
     */
    const val LAST_ERROR_PROBE_JS: String =
        "(function(){var e=window.__mmaLastError||'';return e?e:(window.__mmaHooked?'':'no-hook');})()"

    /**
     * 第一级判定：语法探测结果 → 根因；null（语法通过）表示需要继续读 JS 错误。
     *
     * @param raw evaluateJavascript 回调原文（null = 探测链路失败）。
     */
    fun fromSyntaxProbe(raw: String?): BlankRootCause? = when {
        // 探测链路本身失败（回调 null）：优雅降级，不误诊
        raw == null -> BlankRootCause.ProbeFailed
        raw.trim('"') != "1" -> BlankRootCause.EngineTooOld
        else -> null
    }

    /**
     * 第二/三级判定：JS 错误读取结果 → 根因。
     *
     * @param rawJs 页面侧 `__mmaLastError` 读取原文（evaluateJavascript 回调）。
     * @param nativeFallback 原生 console 缓存（采集器注入失败时的兜底，旧 TAG_JS_ERROR_KEY 语义）。
     */
    fun fromJsError(rawJs: String?, nativeFallback: String?): BlankRootCause {
        val jsError = rawJs?.trim('"')
            ?.takeIf { it.isNotBlank() && it != "null" && it != "no-hook" }
            ?: nativeFallback?.takeIf { it.isNotBlank() }
        return if (jsError.isNullOrBlank()) {
            BlankRootCause.Unknown
        } else {
            BlankRootCause.JsError(jsError.take(JS_ERROR_SNIPPET_MAX))
        }
    }
}
