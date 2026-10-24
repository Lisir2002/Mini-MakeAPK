package com.make.apk.logging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 限流测试：用可注入时钟做确定性断言。 */
class RateLimiterTest {

    private var now = 1_000L

    private fun limiter(max: Int, windowMs: Long = 60_000L) =
        RateLimiter(windowMs = windowMs, maxPerWindow = max, clock = { now })

    @Test
    fun allowsUpToMaxWithinWindow() {
        val limiter = limiter(max = 3)
        assertTrue(limiter.allow("build_failed"))
        assertTrue(limiter.allow("build_failed"))
        assertTrue(limiter.allow("build_failed"))
        assertFalse("超过配额应被拒绝", limiter.allow("build_failed"))
    }

    @Test
    fun differentKeysAreCountedIndependently() {
        val limiter = limiter(max = 1)
        assertTrue(limiter.allow("a"))
        assertTrue(limiter.allow("b"))
        assertFalse(limiter.allow("a"))
    }

    @Test
    fun windowRolloverResetsCount() {
        val limiter = limiter(max = 1, windowMs = 100)
        assertTrue(limiter.allow("a"))
        assertFalse(limiter.allow("a"))

        now += 100
        assertTrue("窗口滚动后应重新放行", limiter.allow("a"))
    }

    @Test
    fun resetClearsState() {
        val limiter = limiter(max = 1)
        assertTrue(limiter.allow("a"))
        assertFalse(limiter.allow("a"))

        limiter.reset()
        assertTrue("reset 后应重新放行", limiter.allow("a"))
    }
}
