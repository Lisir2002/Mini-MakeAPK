package com.make.apk.ui.components

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下拉刷新状态机回归（纯 JVM，无 UI）。
 *
 * 钉住三条**结构性**保证（rc12 线上反馈「下拉无效 + 定死在那里」的对应修复）：
 * 1. 阻尼 / 封顶 / 上滑回收的位移语义；
 * 2. 达阈值才刷新，未达阈值只回弹；
 * 3. **不可能卡死**：刷新永久挂起（超时）或抛异常，都必须复位到 `distance == 0`。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MmaPullToRefreshStateTest {

    /** 帧时钟替身：每次取帧推进 16ms，让 `animate` 在测试里能真正跑完。 */
    private class TestFrameClock : MonotonicFrameClock {
        private var now = 0L
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            now += 16_000_000L
            return onFrame(now)
        }
    }

    private fun newState(scheduler: TestCoroutineScheduler = TestCoroutineScheduler()): MmaPullToRefreshState {
        val clock = TestFrameClock()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler) + clock)
        return MmaPullToRefreshState(
            triggerPx = TRIGGER_PX,
            maxPx = MAX_PX,
            dragMultiplier = DRAG_MULTIPLIER,
            scope = scope,
        )
    }

    @Test
    fun `下拉位移阻尼并封顶`() {
        val state = newState()
        // 手指 100px → 内容 60px（阻尼 0.6）
        val consumed = state.onPostScroll(Offset.Zero, Offset(0f, 100f), NestedScrollSource.UserInput)
        assertEquals(100f, consumed.y, EPS)
        assertEquals(60f, state.distance, EPS)
        // 继续拉到超过上限：位移停在 72px，上抛消费量随之收敛
        state.onPostScroll(Offset.Zero, Offset(0f, 500f), NestedScrollSource.UserInput)
        assertEquals(MAX_PX, state.distance, EPS)
    }

    @Test
    fun `上滑优先收回指示器`() {
        val state = newState()
        state.onPostScroll(Offset.Zero, Offset(0f, 100f), NestedScrollSource.UserInput)
        val consumed = state.onPreScroll(Offset(0f, -40f), NestedScrollSource.UserInput)
        assertEquals(-40f, consumed.y, EPS)
        assertEquals(60f - 40f * DRAG_MULTIPLIER, state.distance, EPS)
    }

    @Test
    fun `未达阈值松手只回弹不刷新`() = runTest {
        val state = newState(this.testScheduler)
        var calls = 0
        state.refreshAction = { calls++ }
        state.onPostScroll(Offset.Zero, Offset(0f, 40f), NestedScrollSource.UserInput)

        assertNotNull(state.settle())
        advanceUntilIdle()

        assertEquals(0, calls)
        assertFalse(state.isRefreshing)
        assertEquals(0f, state.distance, EPS)
    }

    @Test
    fun `达阈值松手触发刷新并复位`() = runTest {
        val state = newState(this.testScheduler)
        var calls = 0
        state.refreshAction = { calls++ }
        state.onPostScroll(Offset.Zero, Offset(0f, 300f), NestedScrollSource.UserInput)
        assertTrue(state.armed)

        assertNotNull(state.settle())
        advanceUntilIdle()

        assertEquals(1, calls)
        assertFalse("刷新结束后必须复位，否则指示器会定死在屏幕上", state.isRefreshing)
        assertEquals(0f, state.distance, EPS)
    }

    @Test
    fun `刷新永久挂起时超时兜底复位`() = runTest {
        val state = newState(this.testScheduler)
        state.refreshAction = { awaitCancellation() }
        state.onPostScroll(Offset.Zero, Offset(0f, 300f), NestedScrollSource.UserInput)

        assertNotNull(state.settle())
        advanceUntilIdle()

        assertFalse("挂起也必须复位（超时兜底）", state.isRefreshing)
        assertEquals(0f, state.distance, EPS)
    }

    @Test
    fun `刷新抛异常时同样复位`() = runTest {
        val state = newState(this.testScheduler)
        state.refreshAction = { error("refresh boom") }
        state.onPostScroll(Offset.Zero, Offset(0f, 300f), NestedScrollSource.UserInput)

        assertNotNull(state.settle())
        advanceUntilIdle()

        assertFalse(state.isRefreshing)
        assertEquals(0f, state.distance, EPS)
    }

    @Test
    fun `刷新中重复结算不叠加任务`() = runTest {
        val state = newState(this.testScheduler)
        var calls = 0
        state.refreshAction = { calls++ }
        state.onPostScroll(Offset.Zero, Offset(0f, 300f), NestedScrollSource.UserInput)

        assertNotNull(state.settle())
        assertNull("刷新中再次结算应被忽略", state.settle())
        advanceUntilIdle()

        assertEquals(1, calls)
    }

    @Test
    fun `fling 结束也会结算`() = runTest {
        val state = newState(this.testScheduler)
        var calls = 0
        state.refreshAction = { calls++ }
        state.onPostScroll(Offset.Zero, Offset(0f, 300f), NestedScrollSource.UserInput)
        // 慢速松手不派发 fling、快速松手派发：两条路径都必须结算。
        state.onPreFling(Velocity(0f, 0f))
        advanceUntilIdle()

        assertEquals(1, calls)
        assertFalse(state.isRefreshing)
        assertEquals(0f, state.distance, EPS)
    }

    @Test
    fun `关闭开关后手势不再生效`() {
        val state = newState()
        state.enabled = false
        val consumed = state.onPostScroll(Offset.Zero, Offset(0f, 300f), NestedScrollSource.UserInput)
        assertEquals(0f, consumed.y, EPS)
        assertEquals(0f, state.distance, EPS)
    }

    private companion object {
        const val TRIGGER_PX: Float = 56f
        const val MAX_PX: Float = 72f
        const val DRAG_MULTIPLIER: Float = 0.6f
        const val EPS: Float = 0.01f
    }
}
