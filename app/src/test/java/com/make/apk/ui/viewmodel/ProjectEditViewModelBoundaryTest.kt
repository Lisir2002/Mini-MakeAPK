package com.make.apk.ui.viewmodel

import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.VersionScheme
import com.make.apk.data.model.WebSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 编辑页 ViewModel **边界态**补测（评审重点 §7 边界状态）。
 *
 * 既有 `ProjectEditViewModelTest` / `ProjectEditViewModelContractTest` 已覆盖：
 * 加载回填、排除自身查重、save() 只改可变字段、dirty 推送策略、事件一次性等。
 * 本组补的是「边界态下不得误写/误发」的防御性断言：
 * - 加载未完成（`loading=true`）时 `canSave=false` 且 `save()` 是 no-op（不落库、不发 Saved）；
 * - 项目不存在（`projectNotFound=true`）时同样 no-op；
 * - 加载完成后 `loading` 翻转、`canSave` 恢复。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectEditViewModelBoundaryTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var directory: FakeProjectDirectory
    private lateinit var project: Project

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        directory = FakeProjectDirectory()
        project = Project(
            id = 7L,
            projectType = ProjectType.URL,
            apkName = "示例应用",
            packageName = "com.example.demo",
            versionScheme = VersionScheme.THREE_SEGMENT,
            versionName = "1.0.0",
            versionCode = 1,
            iconRef = null,
            themeColor = 0xFFE39E97.toInt(),
            entry = ProjectEntry.Url("https://example.com"),
            webSettings = WebSettings(),
            isolationId = "webapp_7",
            createdAt = 100L,
            updatedAt = 100L,
        )
        directory.put(project)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm(): ProjectEditViewModel =
        ProjectEditViewModel(projectId = project.id, directory = directory)

    // ── 加载态 ─────────────────────────────────────────────────────────

    @Test
    fun canSave_isFalseWhileStillLoading() = runTest(dispatcher) {
        // 构造后不推进调度 → 首帧 loading=true 尚未被加载结果翻转。
        val vm = newVm()

        assertTrue(vm.uiState.value.loading)
        assertFalse("加载未完成时禁止保存", vm.uiState.value.canSave)
    }

    @Test
    fun save_whileLoading_isNoOp() = runTest(dispatcher) {
        val vm = newVm()
        val events = mutableListOf<ProjectEditEvent>()
        val collector = launch { vm.events.toList(events) }

        // 加载未完成的初始态直接 save()。
        vm.save()
        advanceUntilIdle()

        assertNull("加载态 save() 不得写库", directory.lastUpdated)
        assertTrue("加载态 save() 不得发 Saved", events.none { it is ProjectEditEvent.Saved })
        collector.cancel()
    }

    @Test
    fun loading_flipsToFalseAfterProjectLoaded() = runTest(dispatcher) {
        val vm = newVm()
        assertTrue(vm.uiState.value.loading)

        advanceUntilIdle()

        assertFalse(vm.uiState.value.loading)
        assertTrue(vm.uiState.value.canSave)
    }

    // ── 不存在态 ───────────────────────────────────────────────────────

    @Test
    fun save_whenProjectNotFound_isNoOp() = runTest(dispatcher) {
        // 库里只有 id=999，查询 id=1 必然 notFound。
        directory.put(project.copy(id = 999L))
        val vm = ProjectEditViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.projectNotFound)
        assertFalse(vm.uiState.value.canSave)

        val events = mutableListOf<ProjectEditEvent>()
        val collector = launch { vm.events.toList(events) }

        vm.save()
        advanceUntilIdle()

        assertNull("notFound 时 save() 不得写库", directory.lastUpdated)
        assertTrue("notFound 时 save() 不得发 Saved", events.none { it is ProjectEditEvent.Saved })
        collector.cancel()
    }
}
