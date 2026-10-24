package com.make.apk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.make.apk.data.model.BuildRecord
import com.make.apk.data.repository.BuildHistoryRepository
import com.make.apk.logging.LogFields
import com.make.apk.logging.MmaLogger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 构建历史 ViewModel。
 *
 * 列表直接来自仓储的 Flow（加密数据库），表变更自动推送，无需手动刷新。
 * 删除属于**用户操作**，按日志层规范记一条 OPERATION 审计日志。
 */
class HistoryViewModel(
    private val repository: BuildHistoryRepository,
) : ViewModel() {

    val records: StateFlow<List<BuildRecord>> =
        repository.observeAll()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun delete(recordId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.remove(recordId)
                MmaLogger.operation(
                    event = "history_item_deleted",
                    fields = mapOf(LogFields.SUCCESS to true, "record_id" to recordId),
                )
            }.onFailure { throwable ->
                MmaLogger.error("history_delete_failed", throwable)
            }
        }
    }
}

/** 手工 DI 工厂。 */
class HistoryViewModelFactory(
    private val repository: BuildHistoryRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        HistoryViewModel(repository) as T
}
