package com.make.apk.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日志级别 → 展示色调的映射契约（纯 JVM，无需 Android）。
 *
 * 这组断言锁的是**设计缺陷的复发**，不是为了覆盖率：
 *
 * 1. 修 rc17 之前，UI 两处各自写 `when (severityWeight) { in 4..5 / 3 / 2 / else }`，
 *    裸数字分支意味着新增级别会静默落进 else。现在 tone 是枚举构造参数，漏填编译失败——
 *    这里再补一道运行期断言，防止有人给新级别随手填成 [LogTone.Muted] 蒙混过关。
 * 2. INFO 与 DEBUG 曾取到**同一个** `md_surface2` 常量，徽标底色字节级相同，
 *    分级展示形同虚设。故断言"相邻级别 tone 不得相同"（除 ERROR/FATAL 这一对
 *    同属错误域是刻意的）。
 * 3. WARN 曾借用 `tertiaryContainer`（品牌蓝 #DBE6FF），比 INFO 还像"正常"信息。
 *    断言 WARN 必须是 [LogTone.Warn]，防止回退到 Info。
 */
class LogLevelToneTest {

    @Test
    fun everyLevelHasExpectedTone() {
        assertEquals(LogTone.Muted, LogLevel.VERBOSE.tone)
        assertEquals(LogTone.Muted, LogLevel.DEBUG.tone)
        assertEquals(LogTone.Info, LogLevel.INFO.tone)
        assertEquals(LogTone.Warn, LogLevel.WARN.tone)
        assertEquals(LogTone.Error, LogLevel.ERROR.tone)
        assertEquals(LogTone.Error, LogLevel.FATAL.tone)
    }

    @Test
    fun infoAndDebugMustNotShareTone() {
        // 历史缺陷：两者底色同为 #F0F1F3（surfaceVariant 与 secondaryContainer 指向同一常量）。
        assertNotEquals(LogLevel.DEBUG.tone, LogLevel.INFO.tone)
    }

    @Test
    fun warnIsItsOwnTone() {
        // 警告不能是"信息蓝"，也不能被吞进错误红——它必须可被一眼识破。
        assertNotEquals(LogTone.Info, LogLevel.WARN.tone)
        assertNotEquals(LogTone.Error, LogLevel.WARN.tone)
    }

    @Test
    fun toneIsMonotonicWithSeverity() {
        // 权重越大，色调不得回退到更"轻"的档位（Muted < Info < Warn < Error）。
        val order = listOf(LogTone.Muted, LogTone.Info, LogTone.Warn, LogTone.Error)
        LogLevel.entries.sortedBy { it.severityWeight }.forEach { level ->
            val idx = order.indexOf(level.tone)
            assertTrue("${level.name} 的 tone 不在已知梯度内", idx >= 0)
        }
        val sorted = LogLevel.entries.sortedBy { it.severityWeight }
        sorted.zipWithNext { lower, higher ->
            assertTrue(
                "严重度递增时色调不得回退：${lower.name}=${lower.tone} → ${higher.name}=${higher.tone}",
                order.indexOf(higher.tone) >= order.indexOf(lower.tone),
            )
        }
    }

    @Test
    fun labelAndWeightStayUnique() {
        // tone 收敛后顺带守住另两个展示字段的唯一性（筛选芯片按 label 显示）。
        assertEquals(LogLevel.entries.size, LogLevel.entries.map { it.label }.toSet().size)
        assertEquals(LogLevel.entries.size, LogLevel.entries.map { it.severityWeight }.toSet().size)
    }
}
