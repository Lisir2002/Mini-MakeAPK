package com.make.apk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.make.apk.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

/**
 * 项目数据访问对象。
 *
 * 高性能约定（与既有 DAO 一致）：
 * - **写操作全部 suspend**：调用方在 `Dispatchers.IO` 执行，绝不阻塞主线程。
 * - **读操作返回 Flow**：Room 在表变更时增量重查并推送，UI 无需手动刷新。
 */
@Dao
interface ProjectDao {

    // ── 写 ────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    // ── 读 ────────────────────────────────────────────────────────────

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Long): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeById(id: Long): Flow<ProjectEntity?>

    /** 全部项目（按更新时间倒序——最近改动的排前面）。 */
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    /** 全部项目（按创建时间倒序——新项目排前面）。 */
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun observeAllByCreatedAt(): Flow<List<ProjectEntity>>

    /** 全部项目一次性快照（启动对账用：不需要响应后续变更）。 */
    @Query("SELECT * FROM projects ORDER BY id ASC")
    suspend fun observeAllSnapshot(): List<ProjectEntity>

    /**
     * 包名占用查询（包名唯一性强制拦截，§2.6 / §6.1）。
     *
     * @param excludeId 编辑场景下排除自身（改包名时不能与自己冲突）；新建传 0。
     */
    @Query("SELECT COUNT(*) FROM projects WHERE packageName = :packageName AND id != :excludeId")
    suspend fun countByPackageName(packageName: String, excludeId: Long = 0L): Int

    /** 按包名精确查询（用于"打开已存在项目"等场景）。 */
    @Query("SELECT * FROM projects WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackageName(packageName: String): ProjectEntity?

    /** 归档待对账项目（§8.5 D-M2）：入口为资产包且类型待嗅探纠正。 */
    @Query("SELECT * FROM projects WHERE entry_json LIKE '%\"archive\"%'")
    fun observeArchiveProjects(): Flow<List<ProjectEntity>>

    /** 归档待对账项目（一次性快照，供首启对账使用）。 */
    @Query("SELECT * FROM projects WHERE entry_json LIKE '%\"archive\"%'")
    suspend fun getArchiveProjects(): List<ProjectEntity>

    @Query("SELECT COUNT(*) FROM projects")
    fun observeCount(): Flow<Int>
}
