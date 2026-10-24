package com.make.apk.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.make.apk.data.local.entity.BuildRecordEntity
import com.make.apk.data.model.BuildStatus
import kotlinx.coroutines.flow.Flow

/**
 * 构建记录数据访问对象。
 *
 * 高性能约定：
 * - **写操作全部 suspend**：调用方在 `Dispatchers.IO` 执行，绝不阻塞主线程。
 * - **读操作返回 Flow**：Room 在表变更时增量重查并推送，UI 无需手动刷新。
 * - **批量写走 [insertAll]**：由调用方用 `withTransaction` 包成单事务，
 *   避免逐条自动提交带来的 fsync 放大（这是 SQLite 最常见的性能陷阱）。
 */
@Dao
interface BuildRecordDao {

    // ── 写 ────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: BuildRecordEntity): Long

    /** 批量插入：务必在事务中调用。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<BuildRecordEntity>): List<Long>

    @Update
    suspend fun update(record: BuildRecordEntity)

    /** 仅更新状态列，避免整行回写。 */
    @Query("UPDATE build_records SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: BuildStatus, updatedAt: Long): Int

    @Query("DELETE FROM build_records WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    /** 按时间清理历史（保留近期记录，控制 DB 体积）。 */
    @Query("DELETE FROM build_records WHERE createdAt < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long): Int

    @Query("DELETE FROM build_records")
    suspend fun clearAll(): Int

    // ── 读 ────────────────────────────────────────────────────────────

    @Query("SELECT * FROM build_records WHERE id = :id")
    suspend fun getById(id: Long): BuildRecordEntity?

    @Query("SELECT * FROM build_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BuildRecordEntity>>

    @Query("SELECT * FROM build_records WHERE id = :id")
    fun observeById(id: Long): Flow<BuildRecordEntity?>

    @Query("SELECT * FROM build_records WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: BuildStatus): Flow<List<BuildRecordEntity>>

    @Query("SELECT COUNT(*) FROM build_records WHERE status = :status")
    fun observeCountByStatus(status: BuildStatus): Flow<Int>

    /** Paging3 分页源：Room 生成 LimitOffsetPagingSource，按需取页。 */
    @Query("SELECT * FROM build_records ORDER BY createdAt DESC")
    fun pagingSource(): PagingSource<Int, BuildRecordEntity>
}
