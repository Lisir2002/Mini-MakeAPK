package com.make.apk.data.repository

import com.make.apk.logging.LogCategory
import com.make.apk.logging.LogGroup
import com.make.apk.logging.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分页筛选参数展开的单测（纯 JVM，不碰数据库）。
 *
 * 这一层是"筛选下推 SQL"的正确性咽喉：`IN ()` 空集合是非法 SQL，
 * 一旦展开写错，用户看到的就是**空列表**（而不是报错），最难被发现。
 * 故对"空 → 全量""非空 → 仅所选""分组 → 类别映射"逐条钉死。
 */
class LogPagingArgsTest {

    // ── levelsArg ────────────────────────────────────────────────────

    @Test
    fun levelsArg_empty_expandsToAllLevels() {
        val args = LogRepository.levelsArg(emptySet())

        assertEquals(LogLevel.entries.size, args.size)
        assertEquals(LogLevel.entries.map { it.name }, args)
    }

    @Test
    fun levelsArg_nonEmpty_keepsOnlySelectedAndSorted() {
        // 故意乱序传入：验证输出恒定（PagingSource 参数稳定 = 不触发无谓重载）。
        val args = LogRepository.levelsArg(setOf(LogLevel.WARN, LogLevel.ERROR, LogLevel.DEBUG))

        assertEquals(listOf("DEBUG", "ERROR", "WARN"), args)
    }

    @Test
    fun levelsArg_singleLevel_returnsOnlyThatLevel() {
        assertEquals(listOf("FATAL"), LogRepository.levelsArg(setOf(LogLevel.FATAL)))
    }

    // ── categoriesArg ────────────────────────────────────────────────

    @Test
    fun categoriesArg_empty_expandsToAllCategories() {
        val args = LogRepository.categoriesArg(emptySet())

        assertEquals(LogCategory.entries.size, args.size)
        assertEquals(LogCategory.entries.map { it.name }, args)
    }

    @Test
    fun categoriesArg_problemGroup_mapsToCrashAndError() {
        val args = LogRepository.categoriesArg(setOf(LogGroup.PROBLEM))

        assertEquals(listOf(LogCategory.CRASH.name, LogCategory.ERROR.name), args)
    }

    @Test
    fun categoriesArg_behaviorGroup_mapsToOperationAndStartup() {
        val args = LogRepository.categoriesArg(setOf(LogGroup.BEHAVIOR))

        assertEquals(listOf(LogCategory.OPERATION.name, LogCategory.STARTUP.name), args)
    }

    @Test
    fun categoriesArg_diagnosticGroup_mapsToAllDiagnosticCategories() {
        val args = LogRepository.categoriesArg(setOf(LogGroup.DIAGNOSTIC))

        val expected = LogCategory.entries
            .filter { it.group == LogGroup.DIAGNOSTIC }
            .map { it.name }
        assertTrue(expected.isNotEmpty())
        assertEquals(expected, args)
    }

    @Test
    fun categoriesArg_securityGroup_mapsToSecurityOnly() {
        val args = LogRepository.categoriesArg(setOf(LogGroup.SECURITY))

        assertEquals(listOf(LogCategory.SECURITY.name), args)
    }

    @Test
    fun categoriesArg_multipleGroups_unionsTheirCategories() {
        val args = LogRepository.categoriesArg(setOf(LogGroup.PROBLEM, LogGroup.SECURITY))

        val expected = LogCategory.entries
            .filter { it.group == LogGroup.PROBLEM || it.group == LogGroup.SECURITY }
            .map { it.name }
        assertEquals(expected, args)
    }

    @Test
    fun categoriesArg_isStableAcrossEquivalentSets() {
        // 同一组筛选、不同插入顺序 → 参数列表必须逐字相同。
        val first = LogRepository.categoriesArg(setOf(LogGroup.DIAGNOSTIC, LogGroup.PROBLEM))
        val second = LogRepository.categoriesArg(setOf(LogGroup.PROBLEM, LogGroup.DIAGNOSTIC))

        assertEquals(first, second)
    }
}
