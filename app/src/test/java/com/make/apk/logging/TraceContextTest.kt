package com.make.apk.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 追踪上下文测试：关联 ID 必须不透明、临时、不跨会话稳定。 */
class TraceContextTest {

    @Test
    fun sessionIdIsStableWithinProcess() {
        assertEquals(TraceContext.sessionId, TraceContext.sessionId)
        assertTrue("会话 ID 应为非空十六进制串", TraceContext.sessionId.matches(Regex("[0-9a-f]{16}")))
    }

    @Test
    fun sequenceMonotonicallyIncreases() {
        val first = TraceContext.nextSequence()
        val second = TraceContext.nextSequence()
        assertTrue("序号应单调递增", second > first)
    }

    @Test
    fun newTraceIdDiffersEachTime() {
        assertNotEquals(TraceContext.newTraceId(), TraceContext.newTraceId())
    }

    @Test
    fun defaultTraceIdFallsBackToSession() {
        TraceContext.endTrace()
        assertEquals(TraceContext.sessionId, TraceContext.currentTraceId())
    }

    @Test
    fun beginAndEndTraceManageThreadLocal() {
        val trace = TraceContext.beginTrace()
        assertEquals(trace, TraceContext.currentTraceId())

        TraceContext.endTrace()
        assertEquals(TraceContext.sessionId, TraceContext.currentTraceId())
    }

    @Test
    fun withNewTraceRestoresPreviousValue() {
        val outer = TraceContext.beginTrace()
        val inner = TraceContext.withNewTrace { TraceContext.currentTraceId() }
        assertNotEquals("内层应是新 traceId", outer, inner)
        assertEquals("退出后应恢复外层 traceId", outer, TraceContext.currentTraceId())
        TraceContext.endTrace()
    }
}
