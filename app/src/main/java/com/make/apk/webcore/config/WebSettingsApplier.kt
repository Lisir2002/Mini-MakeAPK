package com.make.apk.webcore.config

import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import com.make.apk.data.model.WebRuntimeConfig

/**
 * WebSettings 装配器（W4 内核重写）：把 [WebRuntimeConfig.webSettings] 应用到 WebView。
 *
 * 与旧单体 `WebViewScreen.applyRuntime`（:743-782）**逐行等价迁移**，拆出为无状态单例：
 * engine 层唯一的"设置 → 内核"写入口，预览与导出壳同套源码（template sourceSets），
 * 依赖仅 android.webkit + data.model（编译期禁 ui/Room/ViewModel/主题）。
 *
 * 历史遗留行为全部保留（rc12「标准浏览器能开、App 内白屏」专项结论）：
 * - 混合内容 `MIXED_CONTENT_ALWAYS_ALLOW`（W4 主控拍板 1：维持，不加序列化字段）；
 * - UA 归一化（去 `; wv)` 标记，不伪造版本）；
 * - 多窗口 / Cookie / 不透明底色 / 媒体自动播放等逐项显式写死。
 */
object WebSettingsApplier {

    /**
     * 应用 WebSettings 与第三方 Cookie 开关（本地与导出共用同一套配置）。
     *
     * @param surfaceArgb WebView 底色（主题 surface）：**不透明**——透明底在部分 ROM 上
     *   会导致内容根本不绘制（白屏），同时暗色主题用暗底也不会出现首帧闪白。
     */
    fun apply(view: WebView, config: WebRuntimeConfig, surfaceArgb: Int) {
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
        // （W4 主控拍板 1：维持 ALWAYS_ALLOW，不新增序列化字段。）
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
}
