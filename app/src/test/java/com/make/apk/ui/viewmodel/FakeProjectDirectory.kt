package com.make.apk.ui.viewmodel

import com.make.apk.data.model.Project
import com.make.apk.ui.editor.ProjectDirectory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * 内存版 [ProjectDirectory]（单测用）。
 *
 * 存在的意义：ViewModel 依赖的是**窄接口**，因此单测无需真实数据库
 * （`AppDatabase` 需要 Android 运行环境，构造代价高且易引入不稳定因素）。
 *
 * 行为：
 * - [projects] 为按 id 存放的项目表（观察用）；
 * - [packageIndex] 为"包名 → 项目"索引，用于包名占用判定；
 * - [createBehavior] / [updateBehavior] 可注入异常，模拟仓储拦截。
 */
class FakeProjectDirectory : ProjectDirectory {

    /** 项目表（id → 项目）。 */
    val projects = MutableStateFlow<Map<Long, Project>>(emptyMap())

    /** 包名索引（包名 → 项目）。 */
    val packageIndex = MutableStateFlow<Map<String, Project>>(emptyMap())

    /** 新建时抛出的异常（null 表示正常新建）。 */
    var createError: Throwable? = null

    /** 更新时抛出的异常（null 表示正常更新）。 */
    var updateError: Throwable? = null

    /** 新建时的 id 分配器。 */
    var nextId: Long = 1L

    /** 最近一次新建的项目（断言用）。 */
    var lastCreated: Project? = null
        private set

    /** 最近一次更新的项目（断言用）。 */
    var lastUpdated: Project? = null
        private set

    /** 放置一个项目（同时登记包名索引）。 */
    fun put(project: Project) {
        projects.value = projects.value + (project.id to project)
        packageIndex.value = packageIndex.value + (project.packageName to project)
    }

    override suspend fun create(project: Project): Long {
        createError?.let { throw it }
        val id = nextId++
        lastCreated = project
        put(project.copy(id = id))
        return id
    }

    override suspend fun update(project: Project) {
        updateError?.let { throw it }
        lastUpdated = project
        put(project)
    }

    override suspend fun delete(id: Long) {
        projects.value = projects.value.filterKeys { it != id }
        packageIndex.value = packageIndex.value.filterValues { it.id != id }
    }

    override suspend fun getById(id: Long): Project? = projects.value[id]

    override suspend fun getByPackageName(packageName: String): Project? =
        packageIndex.value[packageName]

    override fun observeById(id: Long): Flow<Project?> = projects.map { it[id] }

    override fun observeAllByCreatedAt(): Flow<List<Project>> =
        projects.map { map -> map.values.sortedByDescending { it.createdAt } }

    override fun observeCount(): Flow<Int> = projects.map { it.size }
}
