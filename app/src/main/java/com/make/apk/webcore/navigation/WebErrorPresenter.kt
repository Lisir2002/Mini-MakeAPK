package com.make.apk.webcore.navigation

import android.content.Context
import android.util.Log
import android.webkit.WebView
import com.make.apk.R
import com.make.apk.webcore.diagnostics.WebViewEventHook

/**
 * 主帧加载失败的表现层（rc17，参照 EinkBro `WebErrorPagePresenter` 适配）。
 *
 * 承担两件事，两者都不再由 [InAppWebViewClient] 直接处理：
 *
 * 1. **协议错误降级重试**：委托 [SslDowngradePolicy] 判定，命中则把 https 换成 http
 *    重载一次（守卫条件见该策略类）。
 * 2. **内置错误页**：其余主帧失败不再只是留白 + 一条 Snackbar，而是渲染一张
 *    带原因与「重试」按钮的静态页。用户至少知道"发生了什么、能做什么"。
 *
 * 错误页颜色由 [surfaceArgb] 实时提供（引擎当前底色），文字色按底色的相对亮度
 * 推导——webcore 层不允许依赖 Compose 主题，但也不能在暗色主题下弹出一张白底页。
 */
internal class WebErrorPresenter(
    private val context: Context,
    private val surfaceArgb: () -> Int,
    private val loadId: () -> String = { "" },
) {

    /**
     * 最近一次已降级的地址（去 scheme）。
     *
     * 生命周期跟随"当前文档"：`onPageStarted` 发现地址变了就清空，
     * 因此同一地址在同一次停留中至多降级一次，但隔一段时间再访问仍可再降级。
     */
    private var downgradedKey: String? = null

    /** 新文档开始加载：地址变了就释放一次性降级额度。 */
    fun onPageStarted(url: String?) {
        if (url.isNullOrBlank()) return
        if (SslDowngradePolicy.schemeless(url) != downgradedKey) {
            downgradedKey = null
        }
    }

    /**
     * 处理一次主帧错误。
     *
     * @return true 表示已接管（已降级重试或已渲染错误页），调用方不应再重复提示；
     *   false 表示按常规链继续（自愈 + 上报 + 可见提示）。
     */
    fun handleMainFrameError(
        view: WebView,
        failingUrl: String?,
        isForMainFrame: Boolean,
        description: String?,
        errorCode: Int,
    ): Boolean {
        val decision = SslDowngradePolicy.decide(
            failingUrl = failingUrl,
            isForMainFrame = isForMainFrame,
            description = description,
            errorCode = errorCode,
            lastDowngradedKey = downgradedKey,
        )
        return when (decision) {
            is SslDowngradePolicy.Decision.None -> {
                // 非主帧错误交给调用方的既有链路（子资源失败不应画错误页）
                if (!isForMainFrame) return false
                val url = failingUrl?.takeIf { it.isNotBlank() } ?: return false
                showErrorPage(view, url, description)
                true
            }

            is SslDowngradePolicy.Decision.RetryPlainHttp -> {
                downgradedKey = decision.key
                Log.w(TAG, "ssl_protocol_downgrade: ${decision.plainUrl}")
                // 降级是"自动恢复"事件：必须有痕迹，否则日后看到页面能开
                // 却不知道它其实是走明文加载的（安全性也需可审计）。
                WebViewEventHook.report(
                    "preview_ssl_downgrade",
                    mapOf(
                        "to_plain_http" to true,
                        // 对应 LogFields.PREVIEW_LOAD_ID（webcore 零依赖，只能写字面量）。
                        "preview_load_id" to loadId(),
                    ),
                )
                runCatching { view.loadUrl(decision.plainUrl) }
                true
            }
        }
    }

    /** 渲染内置错误页（失败时静默回落——宁可留白也不能因渲染错误页而崩溃）。 */
    private fun showErrorPage(view: WebView, failedUrl: String, rawReason: String?) {
        val reason = friendlyReason(rawReason)
        val html = runCatching {
            context.resources.openRawResource(R.raw.web_error_page)
                .bufferedReader()
                .use { it.readText() }
        }.getOrNull() ?: return
        val palette = ErrorPalette.of(surfaceArgb())
        val filled = html
            .replace("__BG__", palette.background)
            .replace("__FG__", palette.foreground)
            .replace("__MUTED__", palette.muted)
            .replace("__ACCENT__", palette.accent)
            .replace("__TITLE__", TITLE)
            .replace("__REASON__", escapeHtml(reason))
            // 重试链接目标必须是转义后的绝对地址：HTML 属性里的 & 不转义会被当成实体开头
            .replace("__RETRY_HREF__", escapeHtml(failedUrl))
            .replace("__URL__", escapeHtml(displayUrl(failedUrl)))
        runCatching {
            view.loadDataWithBaseURL(null, filled, "text/html", "utf-8", null)
        }.onFailure { Log.w(TAG, "show_error_page_failed", it) }
    }

    companion object {
        private const val TAG = "webcore_error_page"

        /** 错误页标题（单一文案，避免各处拼接出不同说法）。 */
        const val TITLE: String = "页面无法打开"

        /**
         * 系统错误串 → 用户能看懂的一句话（纯函数，JVM 可单测）。
         *
         * 映射依据：Chromium 网络栈的错误名，不随系统语言变化；未识别的一律落到兜底文案，
         * 绝不把 `net::ERR_XXX` 原样抛给用户。
         */
        fun friendlyReason(raw: String?): String {
            val text = raw.orEmpty()
            if (text.isBlank()) return "请检查网络连接后重试。"
            return when {
                text.contains("INTERNET_DISCONNECTED", true) -> "网络已断开，请检查 Wi-Fi 或移动数据。"
                text.contains("NAME_NOT_RESOLVED", true) -> "找不到这个网站，请检查地址是否正确。"
                text.contains("CONNECTION_REFUSED", true) -> "服务器拒绝连接。"
                text.contains("CONNECTION_TIMED_OUT", true) || text.contains("TIMED_OUT", true) ->
                    "连接超时，服务器响应太慢。"
                text.contains("CONNECTION_RESET", true) -> "连接被重置。"
                text.contains("CONNECTION_CLOSED", true) -> "连接被意外关闭。"
                text.contains("ADDRESS_UNREACHABLE", true) -> "服务器无法访问。"
                text.contains("SSL", true) || text.contains("CERT", true) ->
                    "网站的安全证书有问题，已阻止加载。"
                text.contains("ERR_BLOCKED_BY", true) -> "请求被系统安全策略拦截。"
                text.contains("ERR_TOO_MANY_REDIRECTS", true) -> "该网站重定向次数过多。"
                text.contains("ERR_UNKNOWN_URL_SCHEME", true) -> "无法识别的链接协议。"
                else -> "页面加载失败，可以尝试重试。"
            }
        }

        /** HTML 文本/属性转义（错误页由字符串拼接而生，必须防注入）。 */
        fun escapeHtml(raw: String): String = buildString(raw.length + 16) {
            raw.forEach { ch ->
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(ch)
                }
            }
        }

        /** 展示用地址：过长时截断中段，避免撑破版面。 */
        fun displayUrl(url: String): String {
            val clean = url.removePrefix("https://").removePrefix("http://")
            return if (clean.length <= 64) clean else clean.take(40) + "…" + clean.takeLast(16)
        }
    }
}

/**
 * 错误页配色（纯计算，无主题依赖）。
 *
 * 只取底色一个输入：文字色按相对亮度二选一。原因见 [WebErrorPresenter] 注释——
 * webcore 不能 import Compose 主题，但暗色主题下弹白底页同样不可接受。
 */
internal data class ErrorPalette(
    val background: String,
    val foreground: String,
    val muted: String,
    val accent: String,
) {
    companion object {
        /** 亮色：中性近黑文字 + 品牌蓝（与 design-tokens 的 --primary 同值）。 */
        private const val FG_LIGHT = "#1A1A1A"
        private const val MUTED_LIGHT = "#6B7280"
        private const val ACCENT_LIGHT = "#2563EB"

        /** 暗色：提亮文字与品牌蓝，保证在深底上可读。 */
        private const val FG_DARK = "#F2F2F2"
        private const val MUTED_DARK = "#9CA3AF"
        private const val ACCENT_DARK = "#60A5FA"

        /**
         * 由底色推导整套配色。
         *
         * @param surfaceArgb 引擎当前底色（不透明 ARGB）。
         */
        fun of(surfaceArgb: Int): ErrorPalette {
            val dark = isDark(surfaceArgb)
            return ErrorPalette(
                background = hex(surfaceArgb),
                foreground = if (dark) FG_DARK else FG_LIGHT,
                muted = if (dark) MUTED_DARK else MUTED_LIGHT,
                accent = if (dark) ACCENT_DARK else ACCENT_LIGHT,
            )
        }

        /** 相对亮度判定（Rec.601 加权，足够区分深浅底，不必做 sRGB 线性化）。 */
        fun isDark(argb: Int): Boolean {
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            return luminance < 0.5
        }

        /** ARGB → `#RRGGBB`（丢弃 alpha：CSS 底色必须不透明）。 */
        fun hex(argb: Int): String {
            val rgb = argb and 0xFFFFFF
            return "#%06X".format(rgb)
        }
    }
}
