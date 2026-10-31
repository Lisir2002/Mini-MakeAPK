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

    /** 最近 N 条（日志查看页用：大库下避免全量推送）。 */
    @Query("SELECT * FROM log_entries ORDER BY timestampMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<LogEntryEntity>>

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

    /**
     * 带筛选的分页（级别 + 类别双维，参数**永不为空**——调用方负责补全量）。
     *
     * 为什么补全量而不动态拼 SQL：SQL 的 `IN ()` 空集合是语法错误，
     * 且 Room 的 `:list` 绑定空列表会展开成非法 SQL。调用方
     * （[com.make.apk.data.repository.LogRepository.levelsArg] / `categoriesArg`）
     * 在空筛选时补成"全量枚举"，等价于不过滤，同时保持查询计划单一（走 timestampMs 索引）。
     */
    @Query(
        "SELECT * FROM log_entries WHERE level IN (:levels) AND category IN (:categories) " +
            "ORDER BY timestampMs DESC",
    )
    fun pagingSourceFiltered(
        levels: List<String>,
        categories: List<String>,
    ): PagingSource<Int, LogEntryEntity>

    /** 按类别计数（分组汇总用；**不受分页窗口限制**，全量口径）。 */
    @Query("SELECT category AS category, COUNT(*) AS count FROM log_entries GROUP BY category")
    fun observeCategoryCounts(): Flow<List<CategoryCountRow>>
}
