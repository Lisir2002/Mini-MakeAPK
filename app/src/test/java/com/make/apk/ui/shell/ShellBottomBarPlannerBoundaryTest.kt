package com.make.apk.ui.shell

import com.make.apk.data.model.ShellMenuItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底栏渲染计划（[ShellBottomBarPlanner]）**边界**补测（QA 独立补测 · 阶段 6.2）。
 *
 * ## 补什么
 *
 * 工程师既有的 [ShellBottomBarPlannerTest] 覆盖了"3 个 / 8 个超 5 / 含不可见 / maxItems=0 / 空列表"，
 * 但**恰好等于上限**与**超出 1 个**这两个最容易被 off-by-one 写错的边界没有钉死：
 *
 * - 恰好 `maxItems` 个 → 必须**一条不丢**（`droppedCount == 0`）；
 * - 超出 1 个 → 必须**只丢 1 个**（`droppedCount == 1`，且保留的仍是前 N 条）；
 * - 超出多个 → 丢弃数必须精确等于"可见数 - 上限"。
 *
 * 这些断言直接对应评审要求"**超 `maxItems` 截断并上报，不得静默截断**"——
 * 丢多了是数据丢失，丢少了是"以为没丢其实丢了"，两者都是用户配置被静默破坏。
 */
class ShellBottomBarPlannerBoundaryTest {

    @Test
    fun plan_exactlyMaxItems_keepsEverything() {
        val items = (1..5).map { item("i$it") }
        val plan = ShellBottomBarPlanner.plan(items, maxItems = 5)
        assertEquals(5, plan.visibleItems.size)
        assertEquals(0, plan.droppedCount)
        assertFalse("恰好等于上限不算截断", plan.truncated)
        assertEquals(listOf("i1", "i2", "i3", "i4", "i5"), plan.visibleItems.map { it.id })
    }

    @Test
    fun plan_oneOverMaxItems_dropsExactlyOne() {
        val items = (1..6).map { item("i$it") }
        val plan = ShellBottomBarPlanner.plan(items, maxItems = 5)
        assertEquals(5, plan.visibleItems.size)
        assertEquals(1, plan.droppedCount)
        assertTrue(plan.truncated)
        assertEquals(listOf("i1", "i2", "i3", "i4", "i5"), plan.visibleItems.map { it.id })
    }

    @Test
    fun plan_manyOverMaxItems_dropsExactExcess() {
        val items = (1..12).map { item("i$it") }
        val plan = ShellBottomBarPlanner.plan(items, maxItems = 5)
        assertEquals(5, plan.visibleItems.size)
        assertEquals(7, plan.droppedCount)
    }

    @Test
    fun plan_singleItemMaxOfOne_keepsIt() {
        val plan = ShellBottomBarPlanner.plan(listOf(item("only")), maxItems = 1)
        assertEquals(1, plan.visibleItems.size)
        assertEquals(0, plan.droppedCount)
    }

    @Test
    fun plan_singleItemOverMaxOfOne_dropsIt() {
        val plan = ShellBottomBarPlanner.plan(listOf(item("a"), item("b")), maxItems = 1)
        assertEquals(listOf("a"), plan.visibleItems.map { it.id })
        assertEquals(1, plan.droppedCount)
    }

    @Test
    fun plan_invisibleItemsDoNotConsumeQuota_norCountAsDropped() {
        // 不可见条目既不占额度，也不该被算成"被丢弃"（用户没丢东西，只是关掉了显示）。
        val items = listOf(
            item("a"),
            item("b", visible = false),
            item("c"),
            item("d", visible = false),
            item("e"),
        )
        val plan = ShellBottomBarPlanner.plan(items, maxItems = 3)
        assertEquals(listOf("a", "c", "e"), plan.visibleItems.map { it.id })
        assertEquals(0, plan.droppedCount)
    }

    @Test
    fun plan_invisibleItemsBeyondQuota_areNotDoubleCountedAsDropped() {
        // 6 个条目、其中 2 个不可见、上限 3：可见 4 个 → 丢 1 个（不是 3 个）。
        val items = listOf(
            item("a"),
            item("b"),
            item("c"),
            item("d", visible = false),
            item("e", visible = false),
            item("f"),
        )
        val plan = ShellBottomBarPlanner.plan(items, maxItems = 3)
        assertEquals(listOf("a", "b", "c"), plan.visibleItems.map { it.id })
        assertEquals(1, plan.droppedCount)
    }

    private fun item(id: String, visible: Boolean = true): ShellMenuItem =
        ShellMenuItem(id = id, title = id, visible = visible)
}
