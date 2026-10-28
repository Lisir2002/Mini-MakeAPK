package com.make.apk.ui.shell

import com.make.apk.data.model.ShellMenuItem

/**
 * 底栏渲染计划（纯数据，可 JVM 单测）。
 *
 * 壳渲染器把"截断"这件事**显式建模**而不是隐式丢弃：
 * 超出 [com.make.apk.data.model.ShellBottomBar.maxItems] 的条目必须被**截断**，
 * 且宿主必须能感知到"被截掉了几个"——否则用户配了 8 个 tab 却只看到 5 个，
 * 且**没有任何提示**（静默失败是本项目明令禁止的）。
 */
data class ShellBottomBarPlan(
    /** 实际渲染的条目（已按 [com.make.apk.data.model.ShellBottomBar.maxItems] 截断）。 */
    val visibleItems: List<ShellMenuItem>,
    /** 因超出上限而被丢弃的条目数（0 = 未截断）。 */
    val droppedCount: Int,
) {
    /** 是否发生了截断。 */
    val truncated: Boolean get() = droppedCount > 0
}

/**
 * 底栏渲染计划计算（纯函数，无 Compose / 无 Android 依赖）。
 *
 * 规则：
 * 1. 先过滤 `visible = false` 的条目（保留配置但不渲染）；
 * 2. 再按 `maxItems` 截断（`maxItems <= 0` 视为不限制，避免配置异常导致底栏整体消失）。
 */
object ShellBottomBarPlanner {

    /**
     * 计算底栏渲染计划。
     *
     * @param items 底栏条目（契约原样）。
     * @param maxItems 条目数上限（<= 0 表示不限制）。
     */
    fun plan(items: List<ShellMenuItem>, maxItems: Int): ShellBottomBarPlan {
        val visible = items.filter { it.visible }
        if (maxItems <= 0 || visible.size <= maxItems) {
            return ShellBottomBarPlan(visibleItems = visible, droppedCount = 0)
        }
        return ShellBottomBarPlan(
            visibleItems = visible.take(maxItems),
            droppedCount = visible.size - maxItems,
        )
    }
}
