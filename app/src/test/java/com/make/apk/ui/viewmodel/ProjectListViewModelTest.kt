package com.make.apk.ui.viewmodel

import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.VersionScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 项目列表 ViewModel 单测。
 *
 * 数据来自窄接口 [com.make.apk.ui.editor.ProjectDirectory]（内存实现），
 * 因此不依赖真实数据库。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var directory: FakeProjectDirectory

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        directory = FakeProjectDirectory()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun projects_flowEmitsData() = runTest(dispatcher) {
        directory.put(project(id = 1L, createdAt = 100L))
        directory.put(project(id = 2L, createdAt = 200L))
        val vm = ProjectListViewModel(directory)

        val items = vm.projects.first { it.isNotEmpty() }
        assertEquals(2, items.size)
        // 按创建时间倒序：新项目排前面
        assertEquals(2L, items.first().id)
    }

    @Test
    fun projects_emptyInitially() = runTest(dispatcher) {
        val vm = ProjectListViewModel(directory)
        assertTrue(vm.projects.value.isEmpty())
        assertEquals(0, vm.count.value)
    }

    @Test
    fun count_reflectsDirectorySize() = runTest(dispatcher) {
        directory.put(project(id = 3L, createdAt = 300L))
        val vm = ProjectListViewModel(directory)
        assertEquals(1, vm.count.first { it > 0 })
    }

    @Test
    fun delete_removesItemFromFlow() = runTest(dispatcher) {
        directory.put(project(id = 1L, createdAt = 100L))
        directory.put(project(id = 2L, createdAt = 200L))
        val vm = ProjectListViewModel(directory)
        assertEquals(2, vm.projects.first { it.size == 2 }.size)

        vm.delete(2L)

        // 列表由 Flow 驱动：删除后自动推送新列表，无需手动刷新
        val remaining = vm.projects.first { it.size == 1 }
        assertEquals(1L, remaining.single().id)
    }

    @Test
    fun delete_unknownId_isNoop() = runTest(dispatcher) {
        directory.put(project(id = 1L, createdAt = 100L))
        val vm = ProjectListViewModel(directory)

        vm.delete(999L)

        // 删除不存在的 id：不抛异常，列表原样保留
        assertEquals(1, vm.count.first { it == 1 })
    }

    private fun project(id: Long, createdAt: Long) = Project(
        id = id,
        projectType = ProjectType.URL,
        apkName = "应用$id",
        packageName = "com.example.app$id",
        versionScheme = VersionScheme.THREE_SEGMENT,
        versionName = "1.0.0",
        versionCode = 1,
        themeColor = 0,
        entry = ProjectEntry.Url("https://example.com"),
        isolationId = "webapp_$id",
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
