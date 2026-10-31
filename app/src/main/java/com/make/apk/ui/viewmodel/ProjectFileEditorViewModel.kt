package com.make.apk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.make.apk.data.model.ProjectEntry
import com.make.apk.logging.LogFields
import com.make.apk.logging.MmaEventLogger
import com.make.apk.logging.MmaLogger
import com.make.apk.ui.editor.ProjectDirectory
import com.make.apk.ui.editor.RepositoryProjectDirectory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 文件编辑器 ViewModel（rc11）——单文件的加载与保存。
 *
 * 职责极窄：按 (projectId, fileName) 读项目 → 取文件内容（[ProjectEntry.BundledHtml.contentOf]，
 * 入口文件回落 html 字段）；保存走 `entry.withFile`（入口名写回 html，附加文件 upsert 进
 * files），整份项目落库——与项目编辑页共用同一持久化通路，无第二真值源。
 */
class ProjectFileEditorViewModel(
    private val projectId: Long,
    private val fileName: String,
    private val directory: ProjectDirectory,
) : ViewModel() {

    /** 页面状态。 */
    data class State(
        val loading: Boolean = true,
        val notFound: Boolean = false,
        /** 文件内容（入口文件回落 html 字段；不存在为空串）。 */
        val content: String = "",
        val saving: Boolean = false,
    )

    private val _uiState = MutableStateFlow(State())
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    /** 保存成功一次性事件（返回代码页由 NavHost 执行，本页不管导航）。 */
    private val savedChannel = Channel<Unit>(Channel.BUFFERED)
    val saved: Flow<Unit> = savedChannel.receiveAsFlow()

    private var loaded: com.make.apk.data.model.Project? = null

    init {
        viewModelScope.launch {
            directory.observeById(projectId).collect { project ->
                if (project == null) {
                    if (_uiState.value.loading) {
                        _uiState.value = _uiState.value.copy(loading = false, notFound = true)
                    }
                    return@collect
                }
                loaded = project
                val entry = project.entry as? ProjectEntry.BundledHtml
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    notFound = entry == null,
                    content = entry?.contentOf(fileName) ?: "",
                )
            }
        }
    }

    /** 保存文件内容（与当前内容相同也允许——幂等落库）。 */
    fun save(content: String) {
        val project = loaded ?: return
        if (_uiState.value.saving) return
        val entry = project.entry as? ProjectEntry.BundledHtml ?: return
        _uiState.value = _uiState.value.copy(saving = true)
        viewModelScope.launch {
            runCatching {
                directory.update(project.copy(entry = entry.withFile(fileName, content)))
            }
                .onSuccess {
                    // W3 埋点：项目文件保存审计（crud 收口 `<domain>_<action>` 事件名）。
                    MmaEventLogger.crud(
                        "project_file",
                        "save",
                        targetId = projectId,
                        success = true,
                        extra = mapOf("file" to fileName),
                    )
                    _uiState.value = _uiState.value.copy(saving = false, content = content)
                    savedChannel.send(Unit)
                }
                .onFailure { throwable ->
                    MmaLogger.error("project_file_save_failed", throwable)
                    _uiState.value = _uiState.value.copy(saving = false)
                }
        }
    }
}

/** 手工 DI 工厂。 */
class ProjectFileEditorViewModelFactory(
    private val projectId: Long,
    private val fileName: String,
    private val repository: com.make.apk.data.repository.ProjectRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ProjectFileEditorViewModel(
            projectId = projectId,
            fileName = fileName,
            directory = RepositoryProjectDirectory(repository),
        ) as T
}
