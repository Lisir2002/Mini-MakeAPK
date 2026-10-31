package com.make.apk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.make.apk.data.model.LogEntry
import com.make.apk.data.repository.LogRepository
import com.make.apk.logging.LogGroup
import com.make.apk.logging.LogLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 日志筛选状态（级别 × 分组双维，空集合 = 不过滤）。
 *
 * @param levels 级别筛选，语义来自 [LogLevel.severityWeight]。
 * @param groups 分组筛选，语义来自 [LogGroup.order]。
 */
data class LogFilterState(
    val levels: Set<LogLevel> = emptySet(),
    val groups: Set<LogGroup> = emptySet(),
) {
    /** 是否有任何筛选生效（决定"清除筛选"入口显隐）。 */
    val hasFilters: Boolean get() = levels.isNotEmpty() || groups.isNotEmpty()
}

/**
 * 日志页计数（顶部汇总卡）。
 *
 * @param total 库内日志总数（与列表同一全量口径，**不随分页窗口变化**）。
 * @param groupCounts 四大分组计数；四个 group 恒在，无数据为 0。
 */
data class LogViewerCounts(
    val total: Int = 0,
    val groupCounts: Map<LogGroup, Int> = emptyMap(),
)

/**
 * 日志查看页 VM：全量历史分页 + 分组/级别双维筛选。
 *
 * 与 rc9 版本的差别：不再只取最近 [LogRepository.RECENT_LIMIT] 条在内存里过滤，
 * 而是把筛选**下推**给 SQL（[LogRepository.pagedLogs]），列表用 Paging3 分页，
 * 因此"历史全量"可达而内存恒定。
 *
 * 三级语义来源不变：分组/级别/类别全部来自 logging 包枚举，本 VM 只做筛选与计数。
 */
class LogViewerViewModel(repository: LogRepository) : ViewModel() {

    private val repository: LogRepository = repository

    private val _filterState = MutableStateFlow(LogFilterState())

    /** 当前筛选状态（列表与芯片共用同一事实源）。 */
    val filterState: StateFlow<LogFilterState> = _filterState

    /**
     * 计数状态。
     *
     * `total` 取分组计数之和，而不是额外调一次 `repository.count()`：
     * - `count()` 是一次性快照，日志随时在写，它会立刻过期；
     * - 分组聚合来自同一条 Room `Flow`（`observeCategoryCounts`），随表变化自动刷新，
     *   两者口径天然一致（都用全量 SQL 聚合）。
     * 代价：类别名无法解析的历史脏行不计入 total——但它们同样不会出现在列表里
     * （`categoriesArg` 也只枚举合法类别），故"总数"与"列表可翻到的条数"保持一致。
     */
    val counts: StateFlow<LogViewerCounts> = repository.observeGroupCounts()
        .map { groupCounts ->
            LogViewerCounts(total = groupCounts.values.sum(), groupCounts = groupCounts)
        }
        // 读库失败（加密库打不开 / 表被清空）→ 展示零态而不是让页面挂在 loading 上。
        .catch { emit(LogViewerCounts()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LogViewerCounts(),
        )

    /**
     * 分页日志流：筛选变化即重订阅（[flatMapLatest] 取消旧分页流水），
     * [cachedIn] 让配置变更/重组回退时不重跑查询。
     */
    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalPagingApi::class)
    val logs: Flow<PagingData<LogEntry>> = _filterState
        .flatMapLatest { state ->
            repository.pagedLogs(levels = state.levels, groups = state.groups)
        }
        .cachedIn(viewModelScope)

    /** 切换某级别筛选（再点一次取消）。 */
    fun toggleLevel(level: LogLevel) {
        _filterState.value = _filterState.value.copy(
            levels = _filterState.value.levels.symmetric(level),
        )
    }

    /** 切换某分组筛选（再点一次取消）。 */
    fun toggleGroup(group: LogGroup) {
        _filterState.value = _filterState.value.copy(
            groups = _filterState.value.groups.symmetric(group),
        )
    }

    /** 清除全部筛选。 */
    fun clearFilters() {
        _filterState.value = LogFilterState()
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
