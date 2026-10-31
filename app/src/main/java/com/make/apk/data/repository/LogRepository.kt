package com.make.apk.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.make.apk.data.local.AppDatabase
import com.make.apk.data.mapper.toDomain
import com.make.apk.data.model.LogEntry
import com.make.apk.logging.LogCategory
import com.make.apk.logging.LogFields
import com.make.apk.logging.LogGroup
import com.make.apk.logging.LogLevel
import com.make.apk.logging.MmaLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 日志仓储：日志的**查询 / 清理 / 导出**出口。
 *
 * 当前阶段**不做云端上报**（后端要等 Phase 2），日志只留本地加密库 + 轮转文件，
 * 通过 [exportDiagnostics] 生成诊断包由用户主动导出。
 *
 * 合规要点（见 `docs/logging.md` §6）：
 * - 各类别保留期不同（操作 180 天、安全审计 3 年、性能 30 天…），由 [pruneExpired] 按类别分别清理；
 * - **导出日志本身是一次敏感操作**，必须记一条 [LogCategory.SECURITY] 审计日志，且记在导出之前。
 */
class LogRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val dao = database.logEntryDao()

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
        private const val DEFAULT_PAGE_SIZE = 50

        /** 日志查看页默认拉取的最近条数。 */
        const val RECENT_LIMIT: Int = 500

        /**
         * 级别筛选 → SQL 参数（空集合展开为全量枚举）。
         *
         * 排序：非空时按 name 升序，保证同一个筛选**集合**永远产出同一份参数列表
         * （[Set] 迭代顺序随插入顺序变，直接 `map` 会让 PagingSource 每次都是新键，
         * 白白触发一次无效重载）。
         *
         * `internal` 是为了让参数展开逻辑可被单元测试直接覆盖（无需造 Room 库）。
         */
        internal fun levelsArg(levels: Set<LogLevel>): List<String> =
            if (levels.isEmpty()) {
                LogLevel.entries.map { it.name }
            } else {
                levels.map { it.name }.sorted()
            }

        /**
         * 分组筛选 → SQL 参数（空集合展开为全量类别）。
         *
         * 非空时按 [LogCategory.group] 展开为类别名列表；顺序跟随 [LogCategory.entries]
         * （声明顺序即稳定顺序），理由同 [levelsArg]。
         *
         * `internal` 便于单测——尤其"某个分组到底包含哪些类别"这种映射必须可断言。
         */
        internal fun categoriesArg(groups: Set<LogGroup>): List<String> =
            if (groups.isEmpty()) {
                LogCategory.entries.map { it.name }
            } else {
                LogCategory.entries.filter { it.group in groups }.map { it.name }
            }
    }

    // ── 读 ────────────────────────────────────────────────────────────

    fun observeAll(): Flow<List<LogEntry>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** 最近 [limit] 条（日志查看页专用：大库不全量推送）。 */
    fun observeRecent(limit: Int = RECENT_LIMIT): Flow<List<LogEntry>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    fun observeByCategory(category: LogCategory, limit: Int = 200): Flow<List<LogEntry>> =
        dao.observeByCategory(category.name, limit).map { list -> list.map { it.toDomain() } }

    fun pagedLogs(pageSize: Int = DEFAULT_PAGE_SIZE): Flow<PagingData<LogEntry>> =
        Pager(PagingConfig(pageSize = pageSize, enablePlaceholders = false)) { dao.pagingSource() }
            .flow
            .map { page -> page.map { it.toDomain() } }

    /**
     * 全量分页（带筛选）：级别 × 分组双维，筛选条件下推到 SQL。
     *
     * 空集合语义 = 不过滤。但 SQL 的 `IN ()` 非法，故参数构造阶段就把
     * "空"展开成全量枚举（[levelsArg] / [categoriesArg]），查询本身只有一种形态。
     *
     * @param levels 级别筛选（空 = 全部级别）。
     * @param groups 分组筛选（空 = 全部分组）。
     * @param pageSize 每页条数。
     */
    fun pagedLogs(
        levels: Set<LogLevel>,
        groups: Set<LogGroup>,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Flow<PagingData<LogEntry>> {
        val levelArgs = levelsArg(levels)
        val categoryArgs = categoriesArg(groups)
        return Pager(PagingConfig(pageSize = pageSize, enablePlaceholders = false)) {
            dao.pagingSourceFiltered(levelArgs, categoryArgs)
        }
            .flow
            .map { page -> page.map { it.toDomain() } }
    }

    /**
     * 分组计数（SQL 聚合，**全量口径**，不随分页窗口变化）。
     *
     * 解析失败的行（历史数据里出现未登记的类别名）直接跳过——计数是展示用的
     * 辅助信息，任何一条脏数据都不该让整页读不出来。
     */
    fun observeGroupCounts(): Flow<Map<LogGroup, Int>> =
        dao.observeCategoryCounts().map { rows ->
            val totals = MutableList(LogGroup.entries.size) { 0 }
            val groupIndex = LogGroup.entries.associateWith { it.ordinal }
            rows.forEach { row ->
                val category = runCatching { LogCategory.valueOf(row.category) }.getOrNull()
                    ?: return@forEach
                val index = groupIndex[category.group] ?: return@forEach
                totals[index] += row.count
            }
            LogGroup.entries.associateWith { totals[it.ordinal] }
        }

    /**
     * 级别筛选 → SQL 参数（实现在伴生对象，便于无 DB 单测）。
     */
    private fun levelsArg(levels: Set<LogLevel>): List<String> = Companion.levelsArg(levels)

    /** 分组筛选 → SQL 参数（实现在伴生对象）。 */
    private fun categoriesArg(groups: Set<LogGroup>): List<String> = Companion.categoriesArg(groups)

    suspend fun querySince(fromMs: Long): List<LogEntry> = withContext(Dispatchers.IO) {
        dao.querySince(fromMs).map { it.toDomain() }
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) { dao.count() }

    // ── 清理 ──────────────────────────────────────────────────────────

    /**
     * 按类别各自的保留期清理过期日志。
     * 不落盘的类别（LIFECYCLE / TRACE）天然不参与。
     */
    suspend fun pruneExpired(): Int = withContext(Dispatchers.IO) {
        val now = clock()
        var removed = 0
        LogCategory.entries
            .filter { it.persistToDatabase && it.retentionDays > 0 }
            .forEach { category ->
                val cutoff = now - category.retentionDays * DAY_MILLIS
                removed += dao.deleteByCategoryOlderThan(category.name, cutoff)
            }
        removed
    }

    // ── 导出 ──────────────────────────────────────────────────────────

    /**
     * 导出诊断包（纯文本）。
     *
     * **先记审计日志再导出**——导出日志属于敏感操作，必须留下"谁在何时导出了日志"的证据。
     */
    suspend fun exportDiagnostics(sinceMs: Long): String {
        MmaLogger.security(
            event = "log_export",
            message = "导出诊断包",
            fields = mapOf(
                LogFields.COUNT to 0,
                LogFields.SUCCESS to true,
                "since_ms" to sinceMs,
            ),
        )
        MmaLogger.flushBlocking()

        val entries = querySince(sinceMs)
        return buildString {
            append("MMA 诊断包\n")
            append("生成时间=${clock()}\n")
            append("条目数=${entries.size}\n")
            append("-".repeat(60)).append('\n')
            entries.forEach { entry -> append(entry.formatLine()).append('\n') }
        }
    }
}
