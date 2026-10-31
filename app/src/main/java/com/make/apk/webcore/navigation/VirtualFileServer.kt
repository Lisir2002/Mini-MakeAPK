package com.make.apk.webcore.navigation

import android.net.Uri
import android.webkit.WebResourceResponse
import java.net.URLDecoder

/**
 * HTML 多文件项目的虚拟文件服务（W4 内核重写）。
 *
 * 入口以 [BASE_URL]（虚拟域名 `mma.local`）为 baseURL 加载后，页面里的相对路径资源
 * （`css/style.css` / `js/app.js` 等）会请求到虚拟域名，由 WebViewClient 的
 * `shouldInterceptRequest` 就地返回内容——附加文件无需内联、无需服务器即可生效。
 *
 * 与旧单体（WebViewScreen.kt :663-735 的 shouldInterceptRequest + mimeOf + VIRTUAL_HOST 常量）
 * **逐行等价迁移**；解析逻辑（host 判定 / 路径解码 / MIME 推断）为纯函数，JVM 可单测，
 * 仅 [responseFor] 触到 android 类型（WebResourceResponse）。
 */
object VirtualFileServer {

    /** 虚拟域名（仅本地拦截消费，不发起真实网络请求）。 */
    const val HOST: String = "mma.local"

    /** 虚拟 baseURL（相对路径由此解析）。 */
    const val BASE_URL: String = "https://$HOST/"

    /** host 是否虚拟域（忽略大小写；null / 其它域返回 false）。 */
    fun isVirtualHost(host: String?): Boolean = host?.equals(HOST, ignoreCase = true) == true

    /**
     * 把请求路径还原为虚拟文件名：去首部 `/` 后做 URL 解码（文件名可能含中文 / 空格）。
     * 解码失败回落原始路径（绝不抛异常）；空路径返回 null（无可供给文件）。
     */
    fun resolveFileName(path: String?): String? {
        val trimmed = path?.trimStart('/') ?: return null
        if (trimmed.isEmpty()) return null
        return runCatching { URLDecoder.decode(trimmed, "UTF-8") }.getOrNull() ?: trimmed
    }

    /**
     * 依据扩展名推断虚拟文件的 MIME（未知类型回落 text/plain）。
     * 与旧单体 `mimeOf` 等值迁移，映射表零增删。
     */
    fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
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
     * 拦截入口：请求命中虚拟域且文件存在时构造响应，否则 null（交网络栈）。
     *
     * @param files 当前虚拟文件集（name → content，来自 ProjectEntry.BundledHtml）。
     */
    fun responseFor(files: Map<String, String>, url: Uri?): WebResourceResponse? {
        if (files.isEmpty()) return null
        val host = url?.host ?: return null
        if (!isVirtualHost(host)) return null
        val name = resolveFileName(url?.path) ?: return null
        val content = files[name] ?: return null
        return WebResourceResponse(mimeOf(name), "utf-8", content.byteInputStream())
    }
}
