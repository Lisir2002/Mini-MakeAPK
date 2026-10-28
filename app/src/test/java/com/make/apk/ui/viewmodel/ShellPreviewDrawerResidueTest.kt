package com.make.apk.ui.viewmodel

import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.ShellAction
import com.make.apk.data.model.ShellDrawer
import com.make.apk.data.model.ShellIconKeys
import com.make.apk.data.model.ShellMenuItem
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
 * 壳预览抽屉**运行态复位**单测（QA 独立补测 · 阶段 6.5，纯 JVM）。
 *
 * ## 补什么
 *
 * [ShellPreviewDrawerTest] 覆盖了"用户主动开合 / 切预置模板"两条路径的复位；
 * 本测试补的是**第三条路径**：项目数据在预览期间被重新推送（`observeById` 是长流，
 * 项目行任何变更都会重新发射）时，VM 侧的抽屉运行态（`drawerOpen` / `contentPaused` /
 * `selectedDrawerItemId`）是否有残留。
 *
 * 关键失败场景：项目把抽屉区关掉后（`drawer.enabled = false`），
 * [com.make.apk.ui.screens.ShellPreviewScreen] 不再渲染 `ShellDrawerSection`
 * → 用户**没有任何手段**再关抽屉 → 若 `drawerOpen` / `contentPaused` 不复位，
 * 内容区 WebView 将**永久停在 `onPause()`**，表现为"内容永远不刷新/不响应"。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShellPreviewDrawerResidueTest {

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

    // ── 项目重推送：抽屉区被关闭 ────────────────────────────────────────

    @Test
    fun projectUpdate_disablingDrawer_resetsDrawerRuntimeState() = runTest(dispatcher) {
        directory.put(drawerProject(pauseContent = true))
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        vm.onDrawerOpenChanged(true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.drawerOpen)
        assertTrue(vm.uiState.value.contentPaused)

        // 项目在预览期间被改：抽屉区关掉（数据层重新推送，等价于 Room 行变更）。
        val drawerOff = drawerProject(pauseContent = true)
            .copy(shellSpec = ShellSpec(topBar = ShellTopBar(enabled = true, showTitle = true)))
        directory.put(drawerOff)
        advanceUntilIdle()

        assertEquals(drawerOff.shellSpec, vm.uiState.value.shellSpec)
        assertFalse("抽屉区被关闭后不得残留展开态", vm.uiState.value.drawerOpen)
        assertFalse("抽屉区被关闭后 WebView 不得停在暂停态", vm.uiState.value.contentPaused)
        assertNull("旧抽屉菜单项 id 不得残留", vm.uiState.value.selectedDrawerItemId)
    }

    // ── 项目重推送：抽屉区仍在，但条目被删光 ────────────────────────────

    @Test
    fun projectUpdate_keepingDrawer_clearsStaleSelectionOnly() = runTest(dispatcher) {
        directory.put(drawerProject(pauseContent = true))
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        vm.onDrawerOpenChanged(true)
        vm.onDrawerItemSelected(drawerItems()[1])
        advanceUntilIdle()
        assertEquals("itm_drawer_about", vm.uiState.value.selectedDrawerItemId)

        // 抽屉区仍开启，但菜单条目被删光（渲染层给出"暂无菜单项"占位）。
        val emptied = drawerProject(pauseContent = true).let { project ->
            project.copy(
                shellSpec = project.shellSpec.copy(
                    drawer = project.shellSpec.drawer.copy(items = emptyList()),
                ),
            )
        }
        directory.put(emptied)
        advanceUntilIdle()

        // 条目已不存在 → 高亮 id 是悬空引用：渲染层虽会回落，但状态不应留着脏值。
        assertNull("被删条目的 id 不得残留在选中态里", vm.uiState.value.selectedDrawerItemId)
    }

    // ── 抽屉条目点击 → 关抽屉 → 暂停必须解除 ────────────────────────────

    @Test
    fun drawerItemNavigate_resumesContentAfterClose() = runTest(dispatcher) {
        directory.put(drawerProject(pauseContent = true))
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        vm.onDrawerOpenChanged(true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.contentPaused)

        // 渲染层约定顺序：先回调条目选中（加载新地址），再回调收起（解除暂停）。
        vm.onDrawerItemSelected(drawerItems()[1])
        vm.onDrawerOpenChanged(false)
        advanceUntilIdle()

        assertEquals("https://example.com/about", vm.uiState.value.currentUrl)
        assertFalse("抽屉收起后内容区必须恢复运行", vm.uiState.value.contentPaused)
        assertFalse(vm.uiState.value.drawerOpen)
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
        themeColor = 0xFF2563EB.toInt(),
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
