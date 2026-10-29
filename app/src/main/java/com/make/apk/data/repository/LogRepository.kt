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
