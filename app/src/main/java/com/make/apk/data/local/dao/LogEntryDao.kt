package com.make.apk.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.make.apk.data.local.entity.LogEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 日志条目 DAO。
 *
 * 关于 `insertBlocking` 为什么**不是** suspend：
 * 日志写入跑在 [com.make.apk.logging.AsyncLogDispatcher] 的专用后台线程上，
 * 那里没有协程上下文；Room 的非 suspend DAO 方法只要不在主线程调用即可安全执行。
 * 面向调用方的查询/清理仍用 suspend，由调用方切到 Dispatchers.IO。
 */
@Dao
interface LogEntryDao {

    // ── 写 ────────────────────────────────────────────────────────────

    /** 阻塞式插入：仅供日志后台线程调用，**禁止在主线程调用**。 */
    @Insert
    fun insertBlocking(entry: LogEntryEntity): Long

    /** 阻塞式批量插入（单事务）。 */
    @Insert
    fun insertAllBlocking(entries: List<LogEntryEntity>): List<Long>

    /** 按时间清理（保留期到期）。 */
    @Query("DELETE FROM log_entries WHERE timestampMs < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long): Int

    /** 按类别分别清理（各类别保留期不同）。 */
    @Query("DELETE FROM log_entries WHERE category = :category AND timestampMs < :beforeMs")
    suspend fun deleteByCategoryOlderThan(category: String, beforeMs: Long): Int

    // ── 读 ────────────────────────────────────────────────────────────

    @Query("SELECT * FROM log_entries ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entries WHERE category = :category ORDER BY timestampMs DESC LIMIT :limit")
    fun observeByCategory(category: String, limit: Int): Flow<List<LogEntryEntity>>

    /** 按事件名查询（排查某类问题发生了几次）。 */
    @Query("SELECT * FROM log_entries WHERE event = :event ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun queryByEvent(event: String, limit: Int): List<LogEntryEntity>

    /** 导出用：按时间正序取出区间内全部条目。 */
    @Query("SELECT * FROM log_entries WHERE timestampMs >= :fromMs ORDER BY timestampMs ASC")
    suspend fun querySince(fromMs: Long): List<LogEntryEntity>

    @Query("SELECT COUNT(*) FROM log_entries")
    suspend fun count(): Int

    /** 长列表分页（Paging3）。 */
    @Query("SELECT * FROM log_entries ORDER BY timestampMs DESC")
    fun pagingSource(): PagingSource<Int, LogEntryEntity>
}
