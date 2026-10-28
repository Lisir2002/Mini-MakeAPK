package com.make.apk.ui.editor

import com.make.apk.data.model.Project
import com.make.apk.data.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow

/**
 * 项目目录（数据出口的**窄接口**）。
 *
 * 存在的意义：ViewModel 只依赖"项目数据"这一抽象，而不直接依赖
 * [ProjectRepository]（后者需要真实 `AppDatabase`，无法在纯 JVM 单测里构造）。
 *
 * 生产实现：[RepositoryProjectDirectory]（委托给仓储）；
 * 测试实现：内存假实现（单测无需数据库）。
 *
 * 约定：
 * - 写方法一律 `suspend`（Room 自带 IO 调度，调用方**不要**手动切线程）；
 * - 读方法返回 `Flow`（表变更自动推送）；
 * - [create] / [update] 抛 [com.make.apk.data.model.DuplicatePackageNameException]
 *   表示包名被占用。
 */
interface ProjectDirectory {

    /** 新建项目，返回新 id（隔离 id 由仓储回填）。 */
    suspend fun create(project: Project): Long

    /** 更新项目（包名唯一性在仓储层重新拦截）。 */
    suspend fun update(project: Project)

    /** 删除项目。 */
    suspend fun delete(id: Long)

    /** 按 id 取项目（一次性查询）。 */
    suspend fun getById(id: Long): Project?

    /** 按包名取项目（一次性查询，用于包名占用判定）。 */
    suspend fun getByPackageName(packageName: String): Project?

    /** 观察单个项目（不存在时持续为 null）。 */
    fun observeById(id: Long): Flow<Project?>

    /** 观察全部项目（按创建时间倒序）。 */
    fun observeAllByCreatedAt(): Flow<List<Project>>

    /** 观察项目总数。 */
    fun observeCount(): Flow<Int>
}

/**
 * [ProjectDirectory] 的仓储适配器（生产环境唯一实现）。
 *
 * 只是薄薄一层转发：把"仓储的方法名"翻译成"目录的语义名"，
 * 让 ViewModel 不必知道底层是 Room 还是别的什么。
 */
class RepositoryProjectDirectory(
    private val repository: ProjectRepository,
) : ProjectDirectory {

    override suspend fun create(project: Project): Long = repository.create(project)

    override suspend fun update(project: Project) = repository.update(project)

    override suspend fun delete(id: Long) = repository.delete(id)

    override suspend fun getById(id: Long): Project? = repository.getById(id)

    override suspend fun getByPackageName(packageName: String): Project? =
        repository.getByPackageName(packageName)

    override fun observeById(id: Long): Flow<Project?> = repository.observeById(id)

    override fun observeAllByCreatedAt(): Flow<List<Project>> = repository.observeAllByCreatedAt()

    override fun observeCount(): Flow<Int> = repository.observeCount()
}

/**
 * 资产包探测结果（IO 层的客观事实，交给纯函数校验器判定）。
 */data class ArchiveProbeResult(
    /** 文件是否存在。 */
    val exists: Boolean,
    /** 文件大小（字节）；不存在时为 0。 */
    val sizeBytes: Long,
    /** 文件扩展名（含点，小写）；不存在时为空串。 */
    val extension: String,
)

/**
 * 资产包探测器（IO 抽象）。
 *
 * 把"文件是否存在 / 多大"这类**必须碰文件系统**的判断隔离出去，
 * 使 [ProjectValidator] 保持纯函数、可在 JVM 单测里跑。
 */
interface ArchiveProbe {

    /** 探测指定路径的资产包。 */
    fun probe(path: String): ArchiveProbeResult
}

/**
 * 基于本地文件系统的 [ArchiveProbe] 默认实现。
 *
 * 纯 `java.io` 实现，不依赖 `android.*`（便于在 JVM 单测里直接使用）。
 */
class FileArchiveProbe : ArchiveProbe {

    override fun probe(path: String): ArchiveProbeResult {
        // 现状说明：这里在主线程做磁盘 IO（isFile / length），由 AddProjectViewModel.revalidate()
        // 同步调用。阶段 3 的 UI 通路只覆盖 URL / HTML（VUE / PHP 为占位、不可保存），
        // 归档探测当前没有真实调用点；待后续阶段接入 zip 选择器时再改为 Dispatchers.IO 异步探测。
        val file = java.io.File(path)
        if (!file.isFile) {
            return ArchiveProbeResult(exists = false, sizeBytes = 0L, extension = "")
        }
        val name = file.name
        val dot = name.lastIndexOf('.')
        val extension = if (dot > 0) name.substring(dot).lowercase() else ""
        return ArchiveProbeResult(
            exists = true,
            sizeBytes = file.length(),
            extension = extension,
        )
    }
}
