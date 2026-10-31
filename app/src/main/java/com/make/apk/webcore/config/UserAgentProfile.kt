package com.make.apk.webcore.config

import android.content.Context
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * UA / Client Hints 归一化（rc17「某些链接永远白」专项）。
 *
 * ## 为什么必须做
 *
 * 系统 WebView 的 UA 带**两个**指纹，站点据此识别"这是内嵌浏览器"并返回空壳 / 降级页：
 *
 * ```
 * Mozilla/5.0 (Linux; Android 16; V2324A Build/BP2A.250705.008; wv) AppleWebKit/537.36
 *                                                    ↑①wv      (KHTML, like Gecko)
 * Version/4.0 Chrome/130.0.6723.58 Mobile Safari/537.36
 * ↑②真 Chrome 没有这个 token
 * ```
 *
 * 旧实现只 `replace("; wv)", ")")`，去掉①但留下②，站点仍可判定。本类改为
 * **整段前缀替换**（EinkBro `WebViewConfigApplier.updateUserAgentString` 的做法）
 * 并额外清除 `Version/4.0`，产出与真 Chrome 一致的串。
 *
 * ## 第二层：Client Hints
 *
 * 即使 UA 字符串改对，WebView 仍会发送 `Sec-CH-UA-Mobile: ?1` /
 * `Sec-CH-UA-Platform: "Android"`，且 JS 侧 `navigator.userAgentData.brands`
 * 含 `"Android WebView"`。服务端读 header、页面读 JS，两层都能识别出内嵌浏览器。
 * 故额外用 [WebSettingsCompat.setUserAgentMetadata] 把 brand 列表中的
 * `Android WebView` 改为 `Google Chrome`（EinkBro `updateUserAgentClientHints` 同款）。
 *
 * 依赖 `androidx.webkit`（纯 compat 层，无内核），预览与导出壳同源。
 */
internal object UserAgentProfile {

    /** 真 Chrome 在 Android 上的 UA 后缀（不含括号前缀）：注意**没有** `Version/4.0`。 */
    private const val CHROME_SUFFIX = "AppleWebKit/537.36 (KHTML, like Gecko) %s Mobile Safari/537.36"

    /** 桌面 UA 的平台段（用于 UA 字符串推断，桌面模式不伪造 Windows）。 */
    private const val DESKTOP_PREFIX = "Mozilla/5.0 (X11; Linux x86_64)"

    /** WebView 专属 token：真 Chrome 的 UA 不含 `Version/4.0`。 */
    private const val WEBVIEW_VERSION_TOKEN = "Version/4.0 "

    /** Client Hints 中会被识破的 brand（大小写不敏感匹配）。 */
    private const val ANDROID_WEBVIEW_BRAND = "WebView"

    /** 伪装后的 brand 名。 */
    private const val CHROME_BRAND = "Google Chrome"

    /**
     * 计算移动端 UA：取系统默认 UA 的 Chrome 版本号，替换括号前缀与 `Version/4.0`。
     *
     * 保留系统内核的真实 Chrome 版本（不写死、不伪造），只清除 WebView 标记——
     * 伪造版本号会让站点判定为"异常 UA"，反而更容易被拦截。
     */
    fun mobileUserAgent(context: Context): String {
        val system = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
            ?: return ""
        return mobileUserAgentOf(system, mobilePrefix())
    }

    /**
     * 纯函数部分（JVM 可单测，无 Android 依赖）：把系统 UA 归一化为移动 Chrome UA。
     *
     * @param raw 系统默认 UA（可能带 `; wv)` 与 `Version/4.0`）。
     * @param prefix 目标括号前缀，形如 `Mozilla/5.0 (Linux; Android 16; V2324A)`。
     */
    fun mobileUserAgentOf(raw: String, prefix: String): String {
        if (raw.isBlank()) return raw
        val base = replaceBracketPrefix(raw, prefix)
        return stripWebViewTokens(base)
    }

    /**
     * 纯函数：把 UA 的括号前缀整体替换掉。
     *
     * 为什么整段替换而不是删 `; wv)`：`; wv` 的写法在不同 ROM / 内核版本上有
     * `; wv)`、`; wv ` 等多种变体，逐种替换总有漏网；而括号段是 UA 的固定结构，
     * 整体替换一次到位。
     */
    fun replaceBracketPrefix(raw: String, prefix: String): String {
        val close = raw.indexOf(')')
        if (close <= 0) return raw
        val open = raw.indexOf('(')
        if (open < 0 || open > close) return raw
        // prefix 自带 "Mozilla/5.0 (" 开头，因此整段 [0..close] 一并替换：
        // 若像早期实现那样保留 raw.substring(0, open)，会拼出
        // "Mozilla/5.0 Mozilla/5.0 (Linux; ...)" 的双前缀（单测已覆盖此回归）。
        return prefix + raw.substring(close + 1)
    }

    /**
     * 纯函数：清除残留的 WebView 标记（`Version/4.0` 与各种 `wv` 变体）。
     *
     * 幂等：对已归一化的 UA 再调一次结果不变（单测覆盖）。
     */
    fun stripWebViewTokens(raw: String): String {
        var ua = raw.replace(WEBVIEW_VERSION_TOKEN, "")
        // 兜底：非常规 ROM 可能残留 " wv)" / "; wv)" 变体
        ua = ua.replace("; wv)", ")").replace(" wv)", ")").replace("; wv ", " ")
        // 连续空格收敛（替换后可能产生 "Gecko)  Chrome/" 这样的双空格）
        return ua.replace(Regex(" {2,}"), " ")
    }

    /**
     * 应用 Client Hints 修补：把 `Android WebView` brand 换成 `Google Chrome`。
     *
     * @param isMobile 与最终生效的 UA 字符串保持一致——UA 说桌面而 hints 说移动，
     *   站点会判定为不一致 UA 而更容易拦。调用方按 UA 里是否含 `Mobile` 传入。
     *
     * 老内核（不支持 [WebViewFeature.USER_AGENT_METADATA]）直接跳过——不报错、
     * 不降级 UA 字符串（UA 归一化已独立生效）。
     */
    fun applyClientHints(view: WebView, isMobile: Boolean) {
        if (!runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA) }
                .getOrDefault(false)
        ) {
            return
        }
        runCatching {
            val current = WebSettingsCompat.getUserAgentMetadata(view.settings)
            val brands = current.brandVersionList.map { brandVersion ->
                if (brandVersion.brand.contains(ANDROID_WEBVIEW_BRAND, ignoreCase = true)) {
                    UserAgentMetadata.BrandVersion.Builder(brandVersion)
                        .setBrand(CHROME_BRAND)
                        .build()
                } else {
                    brandVersion
                }
            }
            val patched = UserAgentMetadata.Builder(current)
                .setBrandVersionList(brands)
                .setMobile(isMobile)
                .setPlatform("Android")
                .build()
            WebSettingsCompat.setUserAgentMetadata(view.settings, patched)
        }
    }

    /** 当前设备的移动 UA 前缀（与系统 UA 的括号段同构，仅去掉 Build 号与 `; wv`）。 */
    private fun mobilePrefix(): String {
        val release = Build.VERSION.RELEASE.orEmpty().ifBlank { "10" }
        val model = Build.MODEL.orEmpty().ifBlank { "K" }
        return "Mozilla/5.0 (Linux; Android $release; $model)"
    }

    /** 桌面 UA 前缀（保留暴露给单测断言使用）。 */
    internal fun desktopPrefix(): String = DESKTOP_PREFIX

    /** 后缀模板（保留暴露给单测断言使用）。 */
    internal fun chromeSuffix(chromeVersion: String): String = CHROME_SUFFIX.format(chromeVersion)
}
