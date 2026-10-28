package com.make.apk.ui.viewmodel

import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.ShellAction
import com.make.apk.data.model.ShellDrawer
import com.make.apk.data.model.ShellIconKeys
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.data.model.ShellPresets
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.ShellTopBar
import com.make.apk.data.model.VersionScheme
import com.make.apk.data.model.WebSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 壳预览抽屉行为单测（阶段 6.5，纯 JVM）。
 *
 * 覆盖四条关键行为：
 * 1. **开合状态是运行态**：抽屉展开会被记进 `drawerOpen`，但**不回写** `ShellSpec`（契约不变量）；
 * 2. **暂停按契约走**：`pauseContentWhileOpen = true` 时展开即暂停内容区，收起即恢复；
 * 3. **暂停可关闭**：`pauseContentWhileOpen = false` 时无论开合都不暂停（用户明确选择不省这点资源）；
 * 4. **菜单项点击**：更新高亮项并按动作加载目标地址。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShellPreviewDrawerTest {

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

    // ── 开合状态 ────────────────────────────────────────────────────────

    @Test
    fun drawerStartsClosed() = runTest(dispatcher) {
        directory.put(drawerProject(pauseContent = true))
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.drawerOpen)
        assertFalse(vm.uiState.value.contentPaused)
        assertNull(vm.uiState.value.selectedDrawerItemId)
    }

    @Test
    fun drawerOpenThenClose_updatesStateWithoutTouchingSpec() = runTest(dispatcher) {
        directory.put(drawerProject(pauseContent = true))
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()
        val specBefore = vm.uiState.value.shellSpec

        vm.onDrawerOpenChanged(true)
        assertTrue(vm.uiState.value.drawerOpen)

        vm.onDrawerOpenChanged(false)
        assertFalse(vm.uiState.value.drawerOpen)
        assertFalse(vm.uiState.value.contentPaused)

        // 关键不变量：运行态不回写契约。
        assertEquals(specBefore, vm.uiState.value.shellSpec)
    }

    // ── pauseContentWhileOpen ──────────────────────────────────────────

    @Test
    fun drawerOpen_pausesContentWhenSpecRequiresIt() = runTest(dispatcher) {
        directory.put(drawerProject(pauseContent = true))
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        vm.onDrawerOpenChanged(true)
        assertTrue(vm.uiState.value.contentPaused)
    }

    @Test
    fun drawerOpen_doesNotPauseWhenSpecDisablesIt() = runTest(dispatcher) {
        directory.put(drawerProject(pauseContent = false))
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        vm.onDrawerOpenChanged(true)
        assertTrue(vm.uiState.value.drawerOpen)
        assertFalse(vm.uiState.value.contentPaused)
    }

    // ── 菜单项 ──────────────────────────────────────────────────────────

    @Test
    fun drawerItemSelected_updatesHighlightAndNavigates() = runTest(dispatcher) {
        directory.put(drawerProject(pauseContent = true))
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        vm.onDrawerItemSelected(drawerItems()[1])
        advanceUntilIdle()

        assertEquals("itm_drawer_about", vm.uiState.value.selectedDrawerItemId)
        assertEquals("https://example.com/about", vm.uiState.value.currentUrl)
    }

    @Test
    fun selectPreset_closesDrawerAndClearsSelection() = runTest(dispatcher) {
        directory.put(drawerProject(pauseContent = true))
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        vm.onDrawerOpenChanged(true)
        vm.onDrawerItemSelected(drawerItems()[1])
        advanceUntilIdle()
        assertTrue(vm.uiState.value.drawerOpen)

        vm.selectPreset(ShellPresets.MINIMAL.id)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.drawerOpen)
        assertFalse(vm.uiState.value.contentPaused)
        assertNull(vm.uiState.value.selectedDrawerItemId)
    }

    // ── 数据 ────────────────────────────────────────────────────────────

    private fun drawerItems(): List<ShellMenuItem> = listOf(
        ShellMenuItem(
            id = "itm_drawer_home",
            title = "首页",
            iconRef = ShellIconKeys.HOME,
            action = ShellAction.NavigateUrl("https://example.com/home"),
            selected = true,
        ),
        ShellMenuItem(
            id = "itm_drawer_about",
            title = "关于",
            iconRef = ShellIconKeys.PROFILE,
            action = ShellAction.NavigateUrl("https://example.com/about"),
        ),
    )

    private fun drawerProject(pauseContent: Boolean): Project = Project(
        id = 1L,
        projectType = ProjectType.URL,
        apkName = "示例应用",
        packageName = "com.example.demo",
        versionScheme = VersionScheme.THREE_SEGMENT,
        versionName = "1.0.0",
        versionCode = 1,
        themeColor = 0xFFE39E97.toInt(),
        entry = ProjectEntry.Url("https://example.com"),
        webSettings = WebSettings(),
        isolationId = "webapp_1",
        createdAt = 1L,
        updatedAt = 1L,
        shellSpec = ShellSpec(
            topBar = ShellTopBar(enabled = true, showTitle = true, showBackButton = true),
            drawer = ShellDrawer(
                enabled = true,
                items = drawerItems(),
                pauseContentWhileOpen = pauseContent,
            ),
        ),
    )
}
