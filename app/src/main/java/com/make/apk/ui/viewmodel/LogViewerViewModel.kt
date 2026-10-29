package com.make.apk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.make.apk.data.model.LogEntry
import com.make.apk.data.repository.LogRepository
import com.make.apk.logging.LogGroup
import com.make.apk.logging.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * 日志查看页状态（rc9 自研分级展示：分组 → 级别 → 条目）。
 *
 * @param loading 是否仍在取数。
 * @param entries 过滤后的日志条目（时间倒序，来自最近 [LogRepository.RECENT_LIMIT] 条窗口）。
 * @param selectedLevels 级别筛选（空 = 全部）；语义来自 [LogLevel.severityWeight] 排序。
 * @param selectedGroups 分组筛选（空 = 全部）；语义来自 [LogGroup.order] 排序。
 * @param groupCounts 四大分组的条目计数（**基于过滤前**的全量窗口，一眼看清结构）。
 */
data class LogViewerUiState(
    val loading: Boolean = true,
    val entries: List<LogEntry> = emptyList(),
    val selectedLevels: Set<LogLevel> = emptySet(),
    val selectedGroups: Set<LogGroup> = emptySet(),
    val groupCounts: Map<LogGroup, Int> = emptyMap(),
) {
    /** 是否有任何筛选生效（决定"清除筛选"入口显隐）。 */
    val hasFilters: Boolean get() = selectedLevels.isNotEmpty() || selectedGroups.isNotEmpty()
}

/**
 * 日志查看页 VM：观察最近日志窗口 + 分组/级别双维筛选。
 *
 * 自研分级体系（rc9）：顶层 [LogGroup]（问题/行为/诊断/安全），中层 [LogLevel]
 * （致命→详细），底层类别与事件——三层语义全部定义在 logging 包枚举上，
 * 本 VM 只做筛选与计数，不自建任何分级概念（单一事实源）。
 */
class LogViewerViewModel(repository: LogRepository) : ViewModel() {

    private val selectedLevels = MutableStateFlow<Set<LogLevel>>(emptySet())
    private val selectedGroups = MutableStateFlow<Set<LogGroup>>(emptySet())

    val uiState: StateFlow<LogViewerUiState> = combine(
        repository.observeRecent(),
        selectedLevels,
        selectedGroups,
    ) { entries, levels, groups ->
        val filtered = entries.filter { entry ->
            val level = entry.levelEnum
            val group = entry.categoryEnum?.group
            (levels.isEmpty() || (level != null && level in levels)) &&
                (groups.isEmpty() || (group != null && group in groups))
        }
        LogViewerUiState(
            loading = false,
            entries = filtered,
            selectedLevels = levels,
            selectedGroups = groups,
            groupCounts = LogGroup.entries.associateWith { group ->
                entries.count { it.categoryEnum?.group == group }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogViewerUiState())

    /** 切换某级别筛选（再点一次取消）。 */
    fun toggleLevel(level: LogLevel) {
        selectedLevels.value = selectedLevels.value.symmetric(level)
    }

    /** 切换某分组筛选（再点一次取消）。 */
    fun toggleGroup(group: LogGroup) {
        selectedGroups.value = selectedGroups.value.symmetric(group)
    }

    /** 清除全部筛选。 */
    fun clearFilters() {
        selectedLevels.value = emptySet()
        selectedGroups.value = emptySet()
    }

    private fun <T> Set<T>.symmetric(item: T): Set<T> =
        if (item in this) this - item else this + item

    companion object {
        /** 工厂（应用级手工注入，与既有 VM 一致）。 */
        fun factory(repository: LogRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LogViewerViewModel(repository) as T
            }
    }
}
