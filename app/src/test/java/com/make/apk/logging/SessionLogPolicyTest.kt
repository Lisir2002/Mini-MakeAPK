package com.make.apk.logging

import java.text.SimpleDateFormat
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话日志目录策略（W3 ⑦，纯 JVM）：
 * - 会话目录名形态（日期层级 / 时刻段 / 短码）与**命名安全铁律**（无 `:` `/` 空格）；
 * - 同一秒内不同会话靠短码去重（不依赖毫秒抖动）；
 * - 过期清理**只删合法日期目录名**——用户自建目录（含形似日期的非法名）永不入选。
 */
class SessionLogPolicyTest {

    /** 与实现同一语义的本地日期（测试内自算，锁定"启动日归属"而非具体时区值）。 */
    private fun localDateOf(timestampMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(Date(timestampMs))

    @Test
    fun sessionDirName_matchesTwoLevelLayout() {
        val now = System.currentTimeMillis()
        val name = SessionLogPolicy.sessionDirName(now, "abcdef1234567890")
        val expectedDate = localDateOf(now)
        assertTrue(
            "应为 日期/session-HHmmss_短码 两级：$name",
            name.matches(Regex("""$expectedDate/session-\d{6}_[0-9a-f]{4}""")),
        )
        assertTrue("应以启动日为日期层级（跨午夜不切目录）：$name", name.startsWith("$expectedDate/"))
    }

    @Test
    fun sessionDirName_sameSecondDifferentSessions_doNotCollide() {
        // 同一毫秒启动的两个会话：短码取 sessionId 前 4 位 hex，必然不同。
        val now = System.currentTimeMillis()
        val a = SessionLogPolicy.sessionDirName(now, "aaaa1111")
        val b = SessionLogPolicy.sessionDirName(now, "bbbb2222")
        assertTrue("同秒会话应靠短码去重", a != b)
        assertTrue(a.endsWith("_aaaa"))
        assertTrue(b.endsWith("_bbbb"))
    }

    @Test
    fun sessionDirName_filenameSafety_neverContainsColonSlashSpace() {
        // 文件名安全铁律：session 含 ':''/'' '、全非法字符、过短三种边界。
        listOf("zz:/ xy", "::::", "ab", "").forEach { sessionId ->
            val name = SessionLogPolicy.sessionDirName(0L, sessionId)
            assertFalse("不得含冒号: $name", name.substringAfter('/').contains(':'))
            assertFalse("不得含空格: $name", name.contains(' '))
            // 段内不得再出现 '/'（两级布局的 '/' 仅在日期与会话段之间）
            assertEquals("目录段数应为 2：$name", 2, name.split('/').size)
            val shortCode = name.substringAfterLast('_')
            assertTrue("短码必须是 4 位十六进制: $name", shortCode.matches(Regex("""[0-9a-f]{4}""")))
        }
    }

    @Test
    fun isValidDateDirName_isStrict() {
        assertTrue(SessionLogPolicy.isValidDateDirName("2026-10-28"))
        // 非法名一律 false（永不删除的用户目录保护前提）
        listOf("2026-10-28备份", "20261028", "backup", "2026-13-99", "2026-10-28 ", "x2026-10-28")
            .forEach { assertFalse("应判非法：$it", SessionLogPolicy.isValidDateDirName(it)) }
    }

    @Test
    fun expiredDateDirs_keepsRetentionWindowAndToday() {
        val existing = listOf("2026-10-28", "2026-10-15", "2026-10-14", "2026-10-13", "2026-10-01")
        // today=2026-10-28、保留 14 天：阈值=2026-10-14；≥阈值的保留（含当天与窗口边界）。
        assertEquals(
            listOf("2026-10-01", "2026-10-13"),
            SessionLogPolicy.expiredDateDirs(existing, "2026-10-28", 14),
        )
    }

    @Test
    fun expiredDateDirs_neverReturnsIllegalNames() {
        // 非法名（即使看起来很老）永不入选——用户自建目录保护。
        val existing = listOf("2020-01-01备份", "old-stuff", "20261028", "2020-01-01 ")
        assertTrue(SessionLogPolicy.expiredDateDirs(existing, "2026-10-28", 14).isEmpty())
    }

    @Test
    fun expiredDateDirs_retentionZeroOrHuge() {
        // retentionDays=0：除今天全部过期（仅限合法日期名）。
        assertEquals(
            listOf("2026-10-27"),
            SessionLogPolicy.expiredDateDirs(listOf("2026-10-27", "2026-10-28"), "2026-10-28", 0),
        )
        // retentionDays 巨大：没有目录早于阈值 → 空。
        assertTrue(
            SessionLogPolicy.expiredDateDirs(listOf("2020-01-01"), "2026-10-28", 100_000).isEmpty(),
        )
    }

    @Test
    fun expiredDateDirs_malformedTodayAbortsBatch() {
        // today 解析失败 → 整批放弃（返回空），绝不瞎删。
        val existing = listOf("2020-01-01")
        assertTrue(SessionLogPolicy.expiredDateDirs(existing, "not-a-date", 14).isEmpty())
    }
}
