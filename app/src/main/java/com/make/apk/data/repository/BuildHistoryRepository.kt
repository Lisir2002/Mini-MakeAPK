package com.make.apk.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.make.apk.data.local.AppDatabase
import com.make.apk.data.local.entity.BuildRecordEntity
import com.make.apk.data.mapper.toDomain
import com.make.apk.data.mapper.toEntity
import com.make.apk.data.model.BuildEngine
import com.make.apk.data.model.BuildInputType
import com.make.apk.data.model.BuildRecord
import com.make.apk.data.model.BuildStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 构建历史仓储：数据层对上层的**唯一出口**。
 *
 * 约定：
 * - 对外只暴露领域模型 [BuildRecord]，永不泄漏 Room 实体。
 * - 读：返回 `Flow`，表变更自动推送（UI 只需 collect）。
 * - 写：`suspend`，由调用方在 `Dispatchers.IO` 中调用。
 * - [clock] 可注入，便于单元测试确定性地断言耗时字段。
 */
class BuildHistoryRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val dao = database.buildRecordDao()

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20
        /** 默认保留 30 天历史。 */
        const val DEFAULT_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
    }

    // ── 读 ────────────────────────────────────────────────────────────

    /** 全部历史（按创建时间倒序）。数据量可控；长列表请用 [pagedHistory]。 */
    fun observeAll(): Flow<List<BuildRecord>> =
        dao.observeAll().map { list -> list.map(BuildRecordEntity::toDomain) }

    fun observeById(id: Long): Flow<BuildRecord?> =
        dao.observeById(id).map { it?.toDomain() }

    fun observeByStatus(status: BuildStatus): Flow<List<BuildRecord>> =
        dao.observeByStatus(status).map { list -> list.map(BuildRecordEntity::toDomain) }

    fun observeCountByStatus(status: BuildStatus): Flow<Int> =
        dao.observeCountByStatus(status)

    /**
     * 分页历史流（Paging3）。ViewModel 侧用 `.cachedIn(viewModelScope)` 缓存。
     * 只有可见页会被查询，避免一次性把全部记录读进内存。
     */
    fun pagedHistory(pageSize: Int = DEFAULT_PAGE_SIZE): Flow<PagingData<BuildRecord>> =
        Pager(
            config = PagingConfig(pageSize = pageSize, enablePlaceholders = false),
        ) { dao.pagingSource() }
            .flow
            // PagingData.map 的 transform 是 suspend，不能传函数引用，必须用 lambda。
            .map { page -> page.map { entity -> entity.toDomain() } }

    // ── 写 ────────────────────────────────────────────────────────────

    /** 登记一次新构建，返回其 id（后续用于更新状态）。 */
    suspend fun start(
        inputType: BuildInputType,
        sourceLabel: String,
        sourceRef: String,
        engine: BuildEngine,
        appName: String? = null,
        packageName: String? = null,
    ): Long {
        val now = clock()
        return dao.insert(
            BuildRecordEntity(
                createdAt = now,
                updatedAt = now,
                inputType = inputType,
                sourceLabel = sourceLabel,
                sourceRef = sourceRef,
                engine = engine,
                status = BuildStatus.RUNNING,
                appName = appName,
                packageName = packageName,
            ),
        )
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
                status = BuildStatus.SUCCESS,
                updatedAt = now,
                outputApkPath = outputApkPath,
                apkSizeBytes = apkSizeBytes,
                durationMs = now - entity.createdAt,
                errorMessage = null,
            ),
        )
    }

    /**
     * 标记失败。同样带 [markSucceeded] 同款终态守卫——
     * 取消后协程的收尾逻辑不得把记录刷成失败。
     */
    suspend fun markFailed(id: Long, errorMessage: String) {
        val entity = dao.getById(id) ?: return
        if (entity.status.isTerminal) return
        val now = clock()
        dao.update(
            entity.copy(
                status = BuildStatus.FAILED,
                updatedAt = now,
                durationMs = now - entity.createdAt,
                errorMessage = errorMessage,
            ),
        )
    }

    /**
     * 标记取消。自身幂等：已是终态则忽略，
     * 保证"取消 + 迟到协程"的任意交错下记录不出现假成功/假失败。
     */
    suspend fun markCancelled(id: Long) {
        val entity = dao.getById(id) ?: return
        if (entity.status.isTerminal) return
        val now = clock()
        dao.update(
            entity.copy(
                status = BuildStatus.CANCELLED,
                updatedAt = now,
                durationMs = now - entity.createdAt,
                errorMessage = null,
            ),
        )
    }

    suspend fun remove(id: Long) {
        dao.deleteById(id)
    }

    /** 清理早于 [cutoffMillis] 的历史，返回删除行数。 */
    suspend fun pruneBefore(cutoffMillis: Long): Int =
        dao.deleteOlderThan(cutoffMillis)

    /** 清理超过保留期的历史。 */
    suspend fun pruneExpired(retentionMillis: Long = DEFAULT_RETENTION_MILLIS): Int =
        pruneBefore(clock() - retentionMillis)

    /**
     * 全量替换（用于导入/恢复场景）。
     * 包在**单个事务**内：否则 N 次独立写 = N 次 fsync，是典型性能陷阱。
     */
    suspend fun replaceAll(records: List<BuildRecord>) {
        database.withTransaction {
            dao.clearAll()
            dao.insertAll(records.map(BuildRecord::toEntity))
        }
    }
}
