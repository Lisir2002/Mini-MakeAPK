package com.make.apk.webcore.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导航决策纯函数测试（W4 T05）：与旧单体 InAppWebViewClient 分支逐条对齐的等价性证明。
 */
class NavigationPolicyTest {

    // ── 应用内协议放行（旧 isInAppScheme 白名单）──

    @Test
    fun `https is in place`() {
        assertEquals(
            NavDecision.InPlace("https://example.com/a"),
            NavigationPolicy.decide("https://example.com/a", isMainFrame = true, fitToApp = true),
        )
    }

    @Test
    fun `http is in place by default`() {
        // W4 拍板 3：明文 http 主帧放行（埋点另在 onPageStarted 上报，此处验放行行为不变）。
        assertEquals(
            NavDecision.InPlace("http://example.com/"),
            NavigationPolicy.decide("http://example.com/", isMainFrame = true, fitToApp = true),
        )
    }

    @Test
    fun `local schemes are in place`() {
        val urls = listOf(
            "file:///data/index.html",
            "data:text/html,x",
            "about:blank",
            "javascript:void(0)",
            "blob:https://x/y",
            "content://media/x",
        )
        urls.forEach { url ->
            assertEquals(
                "url=$url",
                NavDecision.InPlace(url),
                NavigationPolicy.decide(url, isMainFrame = true, fitToApp = true),
            )
        }
    }

    // ── W2 ④ IN_PAGE_LINK：fitToApp=false 主帧 http/https 外开 ──

    @Test
    fun `in-page https link goes external when fitToApp is false and main frame`() {
        val decision = NavigationPolicy.decide("https://example.com/a", isMainFrame = true, fitToApp = false)
        assertTrue(decision is NavDecision.External)
        assertEquals("https://example.com/a", (decision as NavDecision.External).url)
    }

    @Test
    fun `in-page http link goes external when fitToApp is false and main frame`() {
        assertTrue(
            NavigationPolicy.decide("http://example.com/", isMainFrame = true, fitToApp = false)
                is NavDecision.External,
        )
    }

    @Test
    fun `subframe never goes external even when fitToApp is false`() {
        assertEquals(
            NavDecision.InPlace("https://ad.example.com/x"),
            NavigationPolicy.decide("https://ad.example.com/x", isMainFrame = false, fitToApp = false),
        )
    }

    @Test
    fun `local scheme never goes external even when fitToApp is false`() {
        // file/blob 等本地协议不外跳（旧 isHttpScheme 适用域）。
        assertEquals(
            NavDecision.InPlace("file:///data/index.html"),
            NavigationPolicy.decide("file:///data/index.html", isMainFrame = true, fitToApp = false),
        )
    }

    // ── intent:// 深链（执行回落另测，此处验判定路由）──

    @Test
    fun `intent url routes to intent decision`() {
        val url = "intent://scan/#Intent;scheme=zxing;package=com.x.end"
        assertEquals(
            NavDecision.Intent(url),
            NavigationPolicy.decide(url, isMainFrame = true, fitToApp = true),
        )
    }

    // ── 外部应用协议：可见提示（rc12：绝不静默）──

    @Test
    fun `custom scheme is blocked with notice`() {
        val decision = NavigationPolicy.decide("tel:10086", isMainFrame = true, fitToApp = true)
        assertTrue(decision is NavDecision.BlockWithNotice)
        assertEquals("应用内无法打开 tel 链接，已阻止跳转", (decision as NavDecision.BlockWithNotice).message)
    }

    @Test
    fun `market scheme is blocked with notice`() {
        val decision = NavigationPolicy.decide("market://details?id=com.x", isMainFrame = true, fitToApp = false)
        assertTrue(decision is NavDecision.BlockWithNotice)
    }

    @Test
    fun `schemeless url is blocked with unknown scheme notice`() {
        val decision = NavigationPolicy.decide("not-a-url", isMainFrame = true, fitToApp = true)
        assertTrue(decision is NavDecision.BlockWithNotice)
        assertEquals(
            "应用内无法打开 未知协议 链接，已阻止跳转",
            (decision as NavDecision.BlockWithNotice).message,
        )
    }

    // ── 边界 ──

    @Test
    fun `blank url is in place`() {
        assertEquals(
            NavDecision.InPlace(""),
            NavigationPolicy.decide("", isMainFrame = true, fitToApp = true),
        )
    }

    @Test
    fun `uppercase scheme is normalized`() {
        // scheme 判定大小写不敏感（旧代码 lowercase 后比对）。
        assertEquals(
            NavDecision.InPlace("HTTPS://EXAMPLE.COM/"),
            NavigationPolicy.decide("HTTPS://EXAMPLE.COM/", isMainFrame = true, fitToApp = true),
        )
    }
}
