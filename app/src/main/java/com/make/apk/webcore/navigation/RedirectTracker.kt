package com.make.apk.webcore.navigation

import java.util.Locale

/**
 * 重定向链判定（rc17 新增）：**纯函数**，不持有状态、不依赖 WebView，便于单测。
 *
 * 背景：`WebViewClient` 没有重定向回调（`onReceivedHttpRedirectFor` 在 android-34 与
 * `androidx.webkit` 的 `WebViewClientCompat` 中均不存在，已用 javap 核对）。唯一全 API 可用的
 * 观测点是 `doUpdateVisitedHistory`——每次主帧导航提交都会触发，含 302 跳。
 *
 * ## 两个必须避开的坑
 *
 * **1. 不能直接比较 URL 字符串。** 内核回调的地址与起始地址常常只是**写法不同**：
 * 补了末尾斜杠（`https://a.com` → `https://a.com/`）、域名大小写、默认端口（`:443`）、
 * 追加了空锚点（`#`）。字符串判等会把这些全部误报成重定向，跳数虚高，
 * 于是「等网络」被误判成「站点在反复跳转」。故比较前先走 [normalize]。
 *
 * **2. 不能把 URL 写进日志字段。** 脱敏器对键名含 `url|uri|link|href` 的字段一律裁成
 * 「协议 + 域名 + 掩码」三截（脱敏红线，见 `Sanitizer.URL_KEY`）。若记录
 * `from_url` / `to_url`，二者会被裁成**同一个字符串**，
 * 事件读起来像「X 重定向到 X」，既无用又误导。
 * 故本类只输出**判定结论**（第几跳 / 域名是否变 / 路径是否变），不输出地址。
 * 拿不到状态码，同样不伪造。
 *
 * 注：Kotlin 的块注释**可嵌套**，注释里再出现「斜杠 + 星号」会让闭合失配，故上处以文字描述掩码。
 */
internal object RedirectTracker {

    /**
     * 判定结果：一次**实质**的地址跳变。
     *
     * @property hops 计入本次后的跳数。
     * @property hostChanged 域名是否变化（跨站跳转 = 要重付 DNS/TLS，白屏时长的主要来源）。
     * @property pathChanged 路径或查询是否变化（同站内跳转）。
     * @property sameDoc 是否**同文档**地址变化（rc19 新增）。SPA 路由（pushState /
     *   replaceState）与 bfcache 前进后退只改地址不换文档——它们不产生网络请求、
     *   不重付 DNS/TLS，与白屏时长无关，**不计入跳数**，否则 rc18 实机日志已实证
     *   （account.xiaomi.com 的登录 SPA 两次路由被误报成 hop1/hop2）。
     */
    data class Step(
        val hops: Int,
        val hostChanged: Boolean,
        val pathChanged: Boolean,
        val sameDoc: Boolean = false,
    )

    /**
     * 归一化后的可比结构：只保留比较需要的域名与路径，不含敏感查询值以外的身份信息，
     * 且**不落盘**——仅供 [evaluate] 内部比较。
     */
    internal data class Normalized(
        val host: String,
        val path: String,
    )

    /**
     * 判定一次主帧导航提交。
     *
     * @param prevUrl 上一次提交的地址（首次为空）。
     * @param url 本次提交的地址。
     * @param startUrl 本次加载的起始地址（`onPageStarted` 写入）。
     * @param hops 当前跳数。
     * @param sameDoc 本次提交是否未伴随新的 `onPageStarted`（rc19 新增）：
     *   判据由调用方给出——`onPageStarted` 会把「上次提交地址」清空，因此
     *   上次提交地址非空即说明自上次提交后没有新文档开始，是同文档地址变化
     *   （SPA 路由 / history 前进后退）。同文档变化不计跳数。
     * @return 发生实质跳变时返回结果；**null = 地址未变**（同页 reload / 重复回调 /
     *         仅锚点或斜杠差异），调用方据此不刷新状态、不计数、不上报。
     */
    fun evaluate(
        prevUrl: String,
        url: String,
        startUrl: String,
        hops: Int,
        sameDoc: Boolean = false,
    ): Step? {
        val from = normalize(prevUrl.ifEmpty { startUrl })
        val to = normalize(url)
        if (to.path.isEmpty()) return null
        // 没有任何基线（起始回调缺失）：无法判定是否发生跳转，宁可不上报，
        // 也不能把「首次提交」谎报成「一次重定向」。
        if (from.host.isEmpty() && from.path.isEmpty()) return null
        // 归一化后相同 → 只是写法差异或重复回调，不是重定向。
        if (from == to) return null
        return Step(
            hops = if (sameDoc) hops else hops + 1,
            hostChanged = from.host != to.host,
            pathChanged = from.path != to.path,
            sameDoc = sameDoc,
        )
    }

    /**
     * URL 归一化：仅用于**比较**，不对外输出、不落盘。
     *
     * 处理顺序：去锚点 → 分离 authority 与路径 → 去 userinfo → 去默认端口
     * → 域名小写（`Locale.US`，规避土耳其 'I' 大小写问题）→ 空路径补 `/`。
     * 保留查询串：`?p=1` 与 `?p=2` 是不同页面。
     */
    internal fun normalize(raw: String): Normalized {
        val s = raw.trim()
        val hash = s.indexOf('#')
        val noFragment = if (hash >= 0) s.substring(0, hash) else s
        val marker = noFragment.indexOf("://")
        val scheme = if (marker >= 0) noFragment.substring(0, marker).lowercase(Locale.US) else ""
        val rest = if (marker >= 0) noFragment.substring(marker + 3) else noFragment
        val slash = rest.indexOf('/')
        val authority = if (slash >= 0) rest.substring(0, slash) else rest
        val tail = if (slash >= 0) rest.substring(slash) else ""
        var host = authority
        val at = authority.indexOf('@')
        if (at >= 0) host = authority.substring(at + 1)
        var port = ""
        val colon = host.lastIndexOf(':')
        if (colon >= 0 && colon < host.length - 1) {
            val maybe = host.substring(colon + 1)
            if (maybe.isNotEmpty() && maybe.all { it.isDigit() }) {
                port = maybe
                host = host.substring(0, colon)
            }
        }
        if ((scheme == "http" && port == "80") || (scheme == "https" && port == "443")) port = ""
        val portPart = if (port.isEmpty()) "" else ":$port"
        val normalizedHost = "$host$portPart".lowercase(Locale.US)
        // 相对地址（无 authority）时 tail 即全部路径；绝对地址空路径按规范补 `/`。
        val path = tail.ifEmpty { if (marker >= 0) "/" else "" }
        return Normalized(host = normalizedHost, path = path)
    }
}
