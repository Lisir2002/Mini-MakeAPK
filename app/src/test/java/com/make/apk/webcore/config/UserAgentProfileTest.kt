package com.make.apk.webcore.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UA 归一化单测（rc17「某些链接永远白」专项）。
 *
 * 用真机（vivo V2324A / Android 16）的系统 UA 原文做样本——这串是问题现场的直接证据，
 * 改成编造的样本就测不出"Version/4.0 漏删"这类真实缺陷。
 */
class UserAgentProfileTest {

    /** 真机系统 WebView 的默认 UA：同时带 `; wv)` 与 `Version/4.0` 两个指纹。 */
    private val systemUa =
        "Mozilla/5.0 (Linux; Android 16; V2324A Build/BP2A.250705.008; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
            "Chrome/130.0.6723.69 Mobile Safari/537.36"

    private val mobilePrefix = "Mozilla/5.0 (Linux; Android 16; V2324A)"

    @Test
    fun `归一化后不残留任何 WebView 指纹`() {
        val ua = UserAgentProfile.mobileUserAgentOf(systemUa, mobilePrefix)

        assertFalse("不得残留 ; wv 标记：$ua", ua.contains("wv"))
        assertFalse("不得残留 Version/4.0（真 Chrome 没有）：$ua", ua.contains("Version/4.0"))
    }

    @Test
    fun `归一化后保留真实内核版本与移动标记`() {
        val ua = UserAgentProfile.mobileUserAgentOf(systemUa, mobilePrefix)

        assertTrue("必须保留系统内核的真实 Chrome 版本：$ua", ua.contains("Chrome/130.0.6723.69"))
        assertTrue("移动标记必须保留，否则站点返回桌面版：$ua", ua.contains("Mobile Safari"))
        assertTrue("AppleWebKit 段必须保留：$ua", ua.contains("AppleWebKit/537.36"))
    }

    @Test
    fun `归一化后括号段被整体替换为干净前缀`() {
        val ua = UserAgentProfile.mobileUserAgentOf(systemUa, mobilePrefix)

        assertTrue("应以干净前缀开头：$ua", ua.startsWith(mobilePrefix))
        assertFalse("Build 号不应再出现在 UA 里：$ua", ua.contains("Build/BP2A"))
    }

    @Test
    fun `归一化是幂等的`() {
        val once = UserAgentProfile.mobileUserAgentOf(systemUa, mobilePrefix)
        val twice = UserAgentProfile.mobileUserAgentOf(once, mobilePrefix)

        assertEquals(once, twice)
        assertEquals("重复归一化不得累积空格", once.count { it == ' ' }, twice.count { it == ' ' })
    }

    @Test
    fun `非常规 ROM 的 wv 变体也能清除`() {
        // 个别 ROM 写成 "; wv " 或单独 " wv)"，旧实现只 replace("; wv)", ")") 会漏
        assertFalse(UserAgentProfile.stripWebViewTokens("A; wv B").contains("wv"))
        assertFalse(UserAgentProfile.stripWebViewTokens("A wv) B").contains("wv"))
        assertFalse(UserAgentProfile.stripWebViewTokens("A; wv) B").contains("wv"))
    }

    @Test
    fun `无括号的异常 UA 原样返回不崩`() {
        assertEquals("garbage", UserAgentProfile.replaceBracketPrefix("garbage", mobilePrefix))
        assertEquals("", UserAgentProfile.replaceBracketPrefix("", mobilePrefix))
    }

    @Test
    fun `空 UA 不产生任何伪造串`() {
        assertEquals("", UserAgentProfile.mobileUserAgentOf("", mobilePrefix))
    }

    @Test
    fun `连续空格收敛为单空格`() {
        val ua = UserAgentProfile.stripWebViewTokens("A ) Version/4.0  Chrome/1 B")
        assertFalse("双空格必须收敛（替换后容易产生）：$ua", ua.contains("  "))
    }
}
