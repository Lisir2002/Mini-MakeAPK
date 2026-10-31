package com.make.apk.ui.viewmodel

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.make.apk.data.local.AppDatabase
import com.make.apk.data.local.entity.LogEntryEntity
import com.make.apk.data.repository.LogRepository
import com.make.apk.logging.LogGroup
import com.make.apk.logging.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 日志查看页 VM 单测（Robolectric + in-memory Room，与本项目既有 VM 测试同风格）。
 *
 * 覆盖三件事：
 * 1. **计数**：`counts` 来自 SQL 全量聚合，切筛选不影响它（分组卡是"结构概览"，
 *    若跟着筛选择空会让用户失去参照）；
 * 2. **筛选状态**：`toggleGroup` / `toggleLevel` / `clearFilters` 的可逆语义；
 * 3. **筛选下推**：DAO 的 `pagingSourceFiltered` 只返回命中类别的行——
 *    这是"全量历史分页 + 筛选"的正确性根基（若下推失效，分页窗口内的过滤会漏数据）。
 *
 * 关于 `logs`（PagingData）：断言 PagingData 内容需 `paging-testing` 的 `asSnapshot()`，
 * 本模块未引入该依赖；故改为在 DAO 层用 `PagingSource.load()` 直接验证同一份
 * 查询参数产出的行集，等价且零新增依赖。
 *
 * 关于调度器：本类用 `runBlocking` + 真实 `Dispatchers.Unconfined` 作 Main，而不是
 * `runTest` + `StandardTestDispatcher`。原因——Room 的 Flow 首帧由**真实查询线程**投递，
 * 而 `runTest` 的虚拟时间会在"队列空但外部信号未到"时立刻推进到超时，
 * 于是等待计数首帧的断言会假失败。这里要的是真实等待，不是时间跳进。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogViewerViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var database: AppDatabase
    private lateinit var repository: LogRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LogRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    // ── 计数 ─────────────────────────────────────────────────────────

    @Test
    fun counts_aggregatesEveryGroupFromFullTable() = runBlocking {
        seed(
            entry(id = 1L, category = "ERROR", level = "ERROR"),      // 问题
            entry(id = 2L, category = "CRASH", level = "FATAL"),      // 问题
            entry(id = 3L, category = "OPERATION", level = "INFO"),   // 行为
            entry(id = 4L, category = "SECURITY", level = "INFO"),    // 安全
        )

        val vm = LogViewerViewModel(repository)
        val counts = withTimeout(TIMEOUT_MS) { vm.counts.first { it.total == 4 } }

        assertEquals(4, counts.total)
        assertEquals(2, counts.groupCounts[LogGroup.PROBLEM])
        assertEquals(1, counts.groupCounts[LogGroup.BEHAVIOR])
        assertEquals(1, counts.groupCounts[LogGroup.SECURITY])
        assertEquals(0, counts.groupCounts[LogGroup.DIAGNOSTIC])
    }

    @Test
    fun counts_emptyTable_isZeroWithAllGroupsPresent() = runBlocking {
        val vm = LogViewerViewModel(repository)
        val counts = withTimeout(TIMEOUT_MS) { vm.counts.first { it.groupCounts.isNotEmpty() } }

        assertEquals(0, counts.total)
        // 四个分组恒在（缺省 0），UI 不必再判空。
        assertEquals(LogGroup.entries.toSet(), counts.groupCounts.keys)
        assertTrue(counts.groupCounts.values.all { it == 0 })
    }

    // ── 筛选状态 ─────────────────────────────────────────────────────

    @Test
    fun toggleGroup_setsThenClearsGroup() = runBlocking {
        val vm = LogViewerViewModel(repository)
        assertFalse(vm.filterState.value.hasFilters)

        vm.toggleGroup(LogGroup.PROBLEM)
        assertEquals(setOf(LogGroup.PROBLEM), vm.filterState.value.groups)
        assertTrue(vm.filterState.value.hasFilters)

        // 再点一次取消（对称切换）
        vm.toggleGroup(LogGroup.PROBLEM)
        assertTrue(vm.filterState.value.groups.isEmpty())
        assertFalse(vm.filterState.value.hasFilters)
    }

    @Test
    fun toggleGroup_doesNotChangeCounts() = runBlocking {
        seed(
            entry(id = 1L, category = "ERROR", level = "ERROR"),
            entry(id = 2L, category = "OPERATION", level = "INFO"),
        )
        val vm = LogViewerViewModel(repository)
        withTimeout(TIMEOUT_MS) { vm.counts.first { it.total == 2 } }

        vm.toggleGroup(LogGroup.BEHAVIOR)

        // 分组卡保持全量口径：筛选只影响列表，不影响"结构概览"。
        val counts = vm.counts.value
        assertEquals(2, counts.total)
        assertEquals(1, counts.groupCounts[LogGroup.BEHAVIOR])
    }

    @Test
    fun toggleLevel_andClearFilters_resetAllDimensions() = runBlocking {
        val vm = LogViewerViewModel(repository)

        vm.toggleLevel(LogLevel.ERROR)
        vm.toggleGroup(LogGroup.SECURITY)
        assertEquals(setOf(LogLevel.ERROR), vm.filterState.value.levels)
        assertEquals(setOf(LogGroup.SECURITY), vm.filterState.value.groups)

        vm.clearFilters()

        assertEquals(emptySet<LogLevel>(), vm.filterState.value.levels)
        assertEquals(emptySet<LogGroup>(), vm.filterState.value.groups)
        assertFalse(vm.filterState.value.hasFilters)
    }

    // ── 筛选下推（DAO 层验证同一份查询参数）──────────────────────────

    @Test
    fun filteredPagingSource_returnsOnlyRowsOfSelectedGroup() = runBlocking {
        seed(
            entry(id = 1L, category = "ERROR", level = "ERROR"),
            entry(id = 2L, category = "CRASH", level = "FATAL"),
            entry(id = 3L, category = "OPERATION", level = "INFO"),
        )

        val page = loadPage(
            levels = emptySet(),
            groups = setOf(LogGroup.PROBLEM),
        )

        assertEquals(2, page.data.size)
        assertTrue(page.data.all { it.category == "ERROR" || it.category == "CRASH" })
    }

    @Test
    fun filteredPagingSource_combinesLevelAndGroupFilters() = runBlocking {
        seed(
            entry(id = 1L, category = "ERROR", level = "WARN"),
            entry(id = 2L, category = "ERROR", level = "ERROR"),
            entry(id = 3L, category = "OPERATION", level = "ERROR"),
        )

        val page = loadPage(
            levels = setOf(LogLevel.ERROR),
            groups = setOf(LogGroup.PROBLEM),
        )

        assertEquals(1, page.data.size)
        assertEquals(2L, page.data.single().id)
    }

    @Test
    fun filteredPagingSource_emptyFilters_returnsEverythingOrderedByTimeDesc() = runBlocking {
        seed(
            entry(id = 1L, category = "ERROR", level = "ERROR", timestampMs = 100L),
            entry(id = 2L, category = "OPERATION", level = "INFO", timestampMs = 300L),
            entry(id = 3L, category = "SECURITY", level = "INFO", timestampMs = 200L),
        )

        val page = loadPage(levels = emptySet(), groups = emptySet())

        assertEquals(3, page.data.size)
        assertEquals(listOf(2L, 3L, 1L), page.data.map { it.id })
    }

    // ── 辅助 ─────────────────────────────────────────────────────────

    /** 用与 VM 完全相同的参数展开逻辑加载一页（证明"下推的那份参数"就是筛选用到的）。 */
    private suspend fun loadPage(
        levels: Set<LogLevel>,
        groups: Set<LogGroup>,
    ): PagingSource.LoadResult.Page<Int, LogEntryEntity> {
        val source = database.logEntryDao().pagingSourceFiltered(
            LogRepository.levelsArg(levels),
            LogRepository.categoriesArg(groups),
        )
        val result = source.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = PAGE_SIZE,
                placeholdersEnabled = false,
            ),
        )
        return result as PagingSource.LoadResult.Page<Int, LogEntryEntity>
    }

    private fun seed(vararg entries: LogEntryEntity) {
        database.logEntryDao().insertAllBlocking(entries.toList())
    }

    private fun entry(
        id: Long,
        category: String,
        level: String,
        timestampMs: Long = id * 1_000L,
    ) = LogEntryEntity(
        id = id,
        timestampMs = timestampMs,
        level = level,
        category = category,
        event = "test_event_$id",
        message = "消息 $id",
        fieldsJson = """{"index":$id}""",
        stackTrace = null,
        traceId = "trace-$id",
        sessionId = "session-test",
        sequence = id,
        threadName = "main",
        durationMs = null,
        success = null,
    )

    private companion object {
        /** Room Flow 首帧走真实查询线程，给足等待窗口（不依赖虚拟时间）。 */
        const val TIMEOUT_MS = 10_000L
        const val PAGE_SIZE = 50
    }
}
