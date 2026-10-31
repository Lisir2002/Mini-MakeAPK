package com.make.apk.webcore.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RedirectTracker] 判定回归。
 *
 * 覆盖目标是「白屏时长归因」的可信度：
 * - 跳数虚高会把「等网络」误判成「站点反复跳转」；
 * - 把 URL 写进字段会被脱敏器裁成同值，事件读起来像「X 重定向回 X」。
 */
class RedirectTrackerTest {

    private val start = "https://www.baidu.com/"
    private val hop1 = "https://mbd.baidu.com/"
    private val hop2 = "https://baijiahao.baidu.com/"

    @Test
    fun `首次提交且等于起始地址不计跳`() {
        assertNull(RedirectTracker.evaluate("", start, start, 0))
    }

    @Test
    fun `首次提交即重定向按第一跳计`() {
        val s = RedirectTracker.evaluate("", hop1, start, 0)
        checkNotNull(s)
        assertEquals(1, s.hops)
        assertTrue("跨站跳转必须标记 host 变化", s.hostChanged)
    }

    @Test
    fun `连续 302 逐跳累加`() {
        var prev = ""
        var hops = 0
        val recorded = mutableListOf<Int>()
        for (url in listOf(start, hop1, hop2)) {
            val s = RedirectTracker.evaluate(prev, url, start, hops)
            if (s != null) {
                prev = url
                hops = s.hops
                recorded += s.hops
            }
        }
        assertEquals("两次跨站跳转应为 2 跳", 2, hops)
        assertEquals(listOf(1, 2), recorded)
    }

    @Test
    fun `同地址重复回调不计数不上报`() {
        assertNull(RedirectTracker.evaluate(hop1, hop1, start, 1))
    }

    @Test
    fun `回退到起始地址仍计一跳`() {
        val s = RedirectTracker.evaluate(hop1, start, start, 1)
        checkNotNull(s)
        assertEquals(2, s.hops)
        assertTrue(s.hostChanged)
    }

    // ── 规范化防误报 ──

    @Test
    fun `末尾补斜杠不算重定向`() {
        assertNull(
            "https://mimo.mi.com → https://mimo.mi.com/ 只是规范化差异",
            RedirectTracker.evaluate("", "https://mimo.mi.com/", "https://mimo.mi.com", 0),
        )
    }

    @Test
    fun `域名大小写与默认端口不算重定向`() {
        assertNull(
            RedirectTracker.evaluate("", "https://MIMO.mi.com:443/x", "https://mimo.mi.com/x", 0),
        )
    }

    @Test
    fun `仅 fragment 变化不算重定向`() {
        assertNull("同文档锚点跳转不是重定向", RedirectTracker.evaluate("/a", "/a#sec", "/a", 0))
    }

    @Test
    fun `查询串变化算重定向`() {
        val s = RedirectTracker.evaluate("/a?p=1", "/a?p=2", "/a?p=1", 0)
        checkNotNull(s)
        assertEquals(1, s.hops)
        assertFalse("同站跳转不应标记 host 变化", s.hostChanged)
        assertTrue(s.pathChanged)
    }

    @Test
    fun `空地址不改变状态`() {
        assertNull(RedirectTracker.evaluate(start, "", start, 0))
        assertNull(RedirectTracker.evaluate(start, "   ", start, 0))
    }

    @Test
    fun `起始地址缺失时不臆造来源`() {
        // 内核未回调 onPageStarted 的极端情况：直接返回 null，不记为重定向。
        assertNull(RedirectTracker.evaluate("", hop1, "", 0))
    }
}
