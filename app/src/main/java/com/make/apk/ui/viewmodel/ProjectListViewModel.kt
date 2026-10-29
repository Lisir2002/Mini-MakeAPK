package com.make.apk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.make.apk.data.model.Project
import com.make.apk.data.repository.ProjectRepository
import com.make.apk.logging.LogFields
import com.make.apk.logging.MmaLogger
import com.make.apk.ui.editor.ProjectDirectory
import com.make.apk.ui.editor.RepositoryProjectDirectory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 项目列表 ViewModel（项目工作台的数据源）。
 *
 * 列表直接来自仓储的 `Flow`（加密数据库），表变更自动推送，无需手动刷新。
 */
class ProjectListViewModel(
    private val directory: ProjectDirectory,
) : ViewModel() {

    /** 全部项目（按创建时间倒序——新项目排前面）。 */
    val projects: StateFlow<List<Project>> =
        directory.observeAllByCreatedAt()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /** 项目总数。 */
    val count: StateFlow<Int> =
        directory.observeCount()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0,
            )

    /**
     * 下拉刷新（rc11）：显式重读一次项目流。
     *
     * 列表本身由 `Flow` 驱动（表变更自动推送）；此处的语义是**用户主动要求对账**——
     * 挂起到重查完成再返回，让下拉指示器覆盖真实的重读时长。
     */
    suspend fun refresh() {
        runCatching { directory.observeAllByCreatedAt().first() }
            .onSuccess {
                MmaLogger.operation(
                    event = "projects_refreshed",
                    message = "项目列表下拉刷新",
                    fields = mapOf(LogFields.SUCCESS to true),
                )
            }
            .onFailure { throwable -> MmaLogger.error("projects_refresh_failed", throwable) }
    }

    /**
     * 删除项目。
     *
     * 列表由 `Flow` 驱动，删除成功后自动推送新列表，无需调用方手动刷新。
     * 失败只记结构化日志（不抛给 UI）：删除是幂等的最佳努力操作，
     * 抛出去也只会让页面弹一个用户看不懂的异常。
     */
    fun delete(id: Long) {
        viewModelScope.launch {
            runCatching { directory.delete(id) }
                .onFailure { throwable -> MmaLogger.error("project_delete_failed", throwable) }
        }
    }
}

/**
 * 手工 DI 工厂（本项目未引入 Hilt，保持轻量）。
 */
class ProjectListViewModelFactory(
    private val repository: ProjectRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        ProjectListViewModel(RepositoryProjectDirectory(repository)) as T
}
