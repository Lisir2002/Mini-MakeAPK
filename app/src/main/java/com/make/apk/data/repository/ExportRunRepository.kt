package com.make.apk.data.repository

import com.make.apk.data.local.AppDatabase
import com.make.apk.data.local.entity.ExportRunEntity
import com.make.apk.data.mapper.toDomain
import com.make.apk.data.mapper.toEntity
import com.make.apk.data.model.ExportRun
import com.make.apk.data.model.ExportStatus
import com.make.apk.logging.MmaEventLogger
import com.make.apk.logging.MmaLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * 导出运行仓储（§8.4）。
 *
 * 职责：记录**每次导出**的运行态（状态机 / 产物 / 耗时 / 错误）。
 * 项目稳定定义在 [ProjectRepository]，两者 1:N。
 *
 * 状态机守卫（承自旧构建历史仓储）：
 * 终态（SUCCESS / FAILED / CANCELLED）不可被迟到的工作协程覆盖——
 * "取消 + 迟到协程"的任意交错下，记录不出现假成功 / 假失败。
 */
class ExportRunRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val dao = database.exportRunDao()

    // ── 读 ────────────────────────────────────────────────────────────

    fun observeAll(): Flow<List<ExportRun>> =
        dao.observeAll().map { list -> list.map(ExportRunEntity::toDomain) }
            .catch { e ->
                MmaLogger.error("observe_export_runs_failed", e)
                emit(emptyList())
            }

    fun observeById(id: Long): Flow<ExportRun?> =
        dao.observeById(id).map { it?.toDomain() }
            .catch { e ->
                MmaLogger.error("observe_export_run_failed", e)
                emit(null)
            }

    fun observeByProject(projectId: Long): Flow<List<ExportRun>> =
        dao.observeByProject(projectId).map { list -> list.map(ExportRunEntity::toDomain) }
            .catch { e ->
                MmaLogger.error("observe_export_runs_by_project_failed", e)
                emit(emptyList())
            }

    fun observeByStatus(status: ExportStatus): Flow<List<ExportRun>> =
        dao.observeByStatus(status).map { list -> list.map(ExportRunEntity::toDomain) }
            .catch { e ->
                MmaLogger.error("observe_export_runs_by_status_failed", e)
                emit(emptyList())
            }

    fun observeCountByStatus(status: ExportStatus): Flow<Int> =
        dao.observeCountByStatus(status)
            .catch { e ->
                MmaLogger.error("observe_export_run_count_failed", e)
                emit(0)
            }

    fun observeCountByProject(projectId: Long): Flow<Int> =
        dao.observeCountByProject(projectId)
            .catch { e ->
                MmaLogger.error("observe_export_run_project_count_failed", e)
                emit(0)
            }

    suspend fun getById(id: Long): ExportRun? = dao.getById(id)?.toDomain()

    // ── 写 ────────────────────────────────────────────────────────────

    /** 登记一次新导出，返回其 id（后续用于更新状态）。 */
    suspend fun start(projectId: Long, status: ExportStatus = ExportStatus.RUNNING): Long {
        val now = clock()
        val id = dao.insert(
            ExportRunEntity(
                projectId = projectId,
                createdAt = now,
                updatedAt = now,
                status = status,
            ),
        )
        // W3 埋点：导出登记审计。
        MmaEventLogger.exportRunRecorded(runId = id, projectId = projectId, status = status.name)
        return id
    }

    /**
     * 标记成功。**终态守卫**：若记录已到终态（如已被取消），迟到的工作协程不得覆盖。
     */
    suspend fun markSucceeded(id: Long, outputApkPath: String, apkSizeBytes: Long? = null) {
        val entity = dao.getById(id) ?: return
        if (entity.status.isTerminal) return
        val now = clock()
        dao.update(
            entity.copy(
                status = ExportStatus.SUCCESS,
                updatedAt = now,
                outputApkPath = outputApkPath,
                apkSizeBytes = apkSizeBytes,
                durationMs = now - entity.createdAt,
                errorMessage = null,
            ),
        )
        // W3 埋点：导出终态（成功）。
        MmaEventLogger.exportRunStatus(
            runId = id,
            status = "SUCCESS",
            durationMs = now - entity.createdAt,
            success = true,
        )
    }

    /** 标记失败。同样带终态守卫——取消后协程的收尾逻辑不得把记录刷成失败。 */
    suspend fun markFailed(id: Long, errorMessage: String) {
        val entity = dao.getById(id) ?: return
        if (entity.status.isTerminal) return
        val now = clock()
        dao.update(
            entity.copy(
                status = ExportStatus.FAILED,
                updatedAt = now,
                durationMs = now - entity.createdAt,
                errorMessage = errorMessage,
            ),
        )
        // W3 埋点：导出终态（失败，原因经门面脱敏）。
        MmaEventLogger.exportRunStatus(
            runId = id,
            status = "FAILED",
            durationMs = now - entity.createdAt,
            success = false,
            reason = errorMessage,
        )
    }

    /** 标记取消。自身幂等：已是终态则忽略。 */
    suspend fun markCancelled(id: Long) {
        val entity = dao.getById(id) ?: return
        if (entity.status.isTerminal) return
        val now = clock()
        dao.update(
            entity.copy(
                status = ExportStatus.CANCELLED,
                updatedAt = now,
                durationMs = now - entity.createdAt,
                errorMessage = null,
            ),
        )
        // W3 埋点：导出终态（用户取消，不计成败）。
        MmaEventLogger.exportRunStatus(runId = id, status = "CANCELLED", durationMs = now - entity.createdAt)
    }

    suspend fun remove(id: Long) {
        dao.deleteById(id)
    }

    /** 清理早于 [cutoffMillis] 的运行记录，返回删除行数。 */
    suspend fun pruneBefore(cutoffMillis: Long): Int = dao.deleteOlderThan(cutoffMillis)

    /** 清理超过保留期的运行记录。 */
    suspend fun pruneExpired(retentionMillis: Long = DEFAULT_RETENTION_MILLIS): Int =
        pruneBefore(clock() - retentionMillis)

    companion object {
        /** 默认保留 30 天运行历史。 */
        const val DEFAULT_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
