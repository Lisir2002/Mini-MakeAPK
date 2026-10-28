package com.make.apk.ui.shell

import com.make.apk.data.model.ShellMenuItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底栏渲染计划（[ShellBottomBarPlanner]）单测——纯函数，无 Compose / 无 Android 依赖。
 *
 * 核心契约：**截断必须显式可见**（`droppedCount > 0`），不允许静默丢弃用户配置。
 */
class ShellBottomBarPlannerTest {

    @Test
    fun plan_keepsAllItemsWhenWithinLimit() {
        val items = listOf(item("a"), item("b"), item("c"))
        val plan = ShellBottomBarPlanner.plan(items, maxItems = 5)
        assertEquals(3, plan.visibleItems.size)
        assertEquals(0, plan.droppedCount)
        assertFalse(plan.truncated)
    }

    @Test
    fun plan_truncatesAndReportsDroppedCount() {
        val items = (1..8).map { item("i$it") }
        val plan = ShellBottomBarPlanner.plan(items, maxItems = 5)
        assertEquals(5, plan.visibleItems.size)
        assertEquals(3, plan.droppedCount)
        assertTrue(plan.truncated)
        // 截断保留前 N 条（顺序稳定）
        assertEquals(listOf("i1", "i2", "i3", "i4", "i5"), plan.visibleItems.map { it.id })
    }

    @Test
    fun plan_filtersInvisibleItemsBeforeTruncating() {
        val items = listOf(
            item("a"),
            item("b", visible = false),
            item("c"),
            item("d", visible = false),
        )
        val plan = ShellBottomBarPlanner.plan(items, maxItems = 2)
        // 不可见条目不占额度
        assertEquals(listOf("a", "c"), plan.visibleItems.map { it.id })
        assertEquals(0, plan.droppedCount)
    }

    @Test
    fun plan_treatsNonPositiveMaxAsUnlimited() {
        val items = (1..8).map { item("i$it") }
        val plan = ShellBottomBarPlanner.plan(items, maxItems = 0)
        assertEquals(8, plan.visibleItems.size)
        assertEquals(0, plan.droppedCount)
    }

    @Test
    fun plan_returnsEmptyForEmptyItems() {
        val plan = ShellBottomBarPlanner.plan(emptyList(), maxItems = 5)
        assertTrue(plan.visibleItems.isEmpty())
        assertEquals(0, plan.droppedCount)
    }

    private fun item(id: String, visible: Boolean = true): ShellMenuItem =
        ShellMenuItem(id = id, title = id, visible = visible)
}
