package com.make.apk.webcore.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RedirectTracker] 判定回归。
 *
 * 覆盖目标是「白屏时长归因」的可信度：跳数多算一次会把网络等待误判成渲染故障，
 * 少算一次则会让真正的 302 空窗从日志里彻底消失。
 */
class RedirectTrackerTest {

    private val start = "https://www.baidu.com/"
    private val hop1 = "https://mbd.baidu.com/"
    private val hop2 = "https://baijiahao.baidu.com/"

    @Test
    fun `首次提交且等于起始地址不计跳`() {
        val s = RedirectTracker.evaluate(prevUrl = "", url = start, startUrl = start, hops = 0)
        assertTrue(s.changed)
        assertEquals(0, s.hops)
        assertFalse("起始地址本身不是重定向", s.isHop)
    }

    @Test
    fun `首次提交即重定向按第一跳计`() {
        val s = RedirectTracker.evaluate(prevUrl = "", url = hop1, startUrl = start, hops = 0)
        assertTrue(s.changed)
        assertEquals(1, s.hops)
        assertTrue(s.isHop)
        assertEquals(start, s.fromUrl)
    }

    @Test
    fun `连续 302 逐跳累加且来源连续`() {
        var prev = ""
        var hops = 0
        val seen = mutableListOf<Pair<String, String>>()
        for (url in listOf(start, hop1, hop2)) {
            val s = RedirectTracker.evaluate(prev, url, start, hops)
            if (s.changed) {
                prev = url
                hops = s.hops
            }
            if (s.isHop) seen += s.fromUrl to url
        }
        assertEquals(2, hops)
        assertEquals(listOf(start to hop1, hop1 to hop2), seen)
    }

    @Test
    fun `同地址重复回调不计数不上报`() {
        val s = RedirectTracker.evaluate(prevUrl = hop1, url = hop1, startUrl = start, hops = 1)
        assertFalse("reload / 重复回调不得刷屏", s.changed)
        assertEquals(1, s.hops)
        assertFalse(s.isHop)
    }

    @Test
    fun `回退到起始地址仍计一跳`() {
        // 部分站点 302 出去又回跳回来；"回到起点"不是"没有重定向"。
        val s = RedirectTracker.evaluate(prevUrl = hop1, url = start, startUrl = start, hops = 1)
        assertTrue(s.changed)
        assertEquals(2, s.hops)
        assertTrue(s.isHop)
        assertEquals(hop1, s.fromUrl)
    }

    @Test
    fun `空地址不改变状态`() {
        val s = RedirectTracker.evaluate(prevUrl = start, url = "", startUrl = start, hops = 0)
        assertFalse(s.changed)
        assertEquals(0, s.hops)
        assertFalse(s.isHop)
    }

    @Test
    fun `起始地址缺失时不臆造来源`() {
        // 内核未回调 onPageStarted 的极端情况：跳数仍可信，但 from_url 必须留空。
        val s = RedirectTracker.evaluate(prevUrl = "", url = hop1, startUrl = "", hops = 0)
        assertTrue(s.changed)
        assertEquals(1, s.hops)
        assertFalse("无来源可写则不上报", s.isHop)
        assertEquals("", s.fromUrl)
    }
}
