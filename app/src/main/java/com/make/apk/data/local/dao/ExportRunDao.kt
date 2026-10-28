package com.make.apk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.make.apk.data.local.entity.ExportRunEntity
import com.make.apk.data.model.ExportStatus
import kotlinx.coroutines.flow.Flow

/**
 * 导出运行数据访问对象。
 *
 * 写操作 suspend、读操作 Flow——与项目 DAO 保持同一套约定。
 */
@Dao
interface ExportRunDao {

    // ── 写 ────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(run: ExportRunEntity): Long

    @Update
    suspend fun update(run: ExportRunEntity)

    @Query("DELETE FROM export_runs WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM export_runs WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: Long): Int

    /** 按时间清理历史（保留近期记录，控制 DB 体积）。 */
    @Query("DELETE FROM export_runs WHERE createdAt < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long): Int

    // ── 读 ────────────────────────────────────────────────────────────

    @Query("SELECT * FROM export_runs WHERE id = :id")
    suspend fun getById(id: Long): ExportRunEntity?

    @Query("SELECT * FROM export_runs WHERE id = :id")
    fun observeById(id: Long): Flow<ExportRunEntity?>

    /** 全部运行记录（按创建时间倒序）。 */
    @Query("SELECT * FROM export_runs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ExportRunEntity>>

    /** 单个项目的导出历史（按创建时间倒序）。 */
    @Query("SELECT * FROM export_runs WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeByProject(projectId: Long): Flow<List<ExportRunEntity>>

    @Query("SELECT * FROM export_runs WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: ExportStatus): Flow<List<ExportRunEntity>>

    @Query("SELECT COUNT(*) FROM export_runs WHERE status = :status")
    fun observeCountByStatus(status: ExportStatus): Flow<Int>

    @Query("SELECT COUNT(*) FROM export_runs WHERE projectId = :projectId")
    fun observeCountByProject(projectId: Long): Flow<Int>
}
