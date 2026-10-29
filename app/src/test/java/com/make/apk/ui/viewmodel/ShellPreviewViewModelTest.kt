package com.make.apk.ui.viewmodel

import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.ArchiveKind
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.data.model.ShellPresets
import com.make.apk.data.model.ShellDrawer
import com.make.apk.data.model.ShellSpec
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 壳预览 ViewModel 单测（阶段 6.2）。
 *
 * 覆盖三条关键行为：
 * 1. **URL 与 HTML 项目均可预览**：给出 runtimeConfig，不崩、不静默；
 * 2. **资产包（Archive）等不支持入口明确拦截**：给出可见提示，不崩、不静默；
 * 3. **切换预置子模板不重载 WebView**：`runtimeConfig` 对象保持同一引用；
 * 4. **壳动作落到内容区**：`NavigateUrl` 替换入口地址，示例地址被标记。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShellPreviewViewModelTest {

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
    fun urlProject_isPreviewable() = runTest(dispatcher) {
        directory.put(urlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertFalse(state.projectNotFound)
        assertTrue(state.previewable)
        assertNull(state.unsupportedMessage)
        assertNotNull(state.runtimeConfig)
        assertEquals("https://example.com", state.currentUrl)
    }

    @Test
    fun bundledHtmlProject_isPreviewable() = runTest(dispatcher) {
        directory.put(htmlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertFalse(state.projectNotFound)
        assertTrue(state.previewable)
        assertNull(state.unsupportedMessage)
        assertNotNull(state.runtimeConfig)
    }

    @Test
    fun unsupportedArchive_isBlockedWithVisibleMessage() = runTest(dispatcher) {
        directory.put(archiveProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertFalse(state.projectNotFound)
        assertFalse(state.previewable)
        assertEquals(ShellPreviewViewModel.UNSUPPORTED_ENTRY_MESSAGE, state.unsupportedMessage)
        assertNull(state.runtimeConfig)
    }

    @Test
    fun missingProject_isMarkedNotFound() = runTest(dispatcher) {
        val vm = ShellPreviewViewModel(projectId = 404L, directory = directory)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertTrue(state.projectNotFound)
        assertFalse(state.previewable)
    }

    @Test
    fun firstFrame_rendersProjectShellExactly_noPresetFallback() = runTest(dispatcher) {
        // **新不变式（钉死）**：预览渲染的壳恒等于项目自己存的 spec。
        // 曾有过"项目未配壳就偷偷套经典款"的兜底 —— 那会让预览（带顶底栏）
        // 与导出（四区全关的纯网页）不是同一个东西，据此调出的配置全是错的。
        directory.put(urlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(
            "项目未配置壳 → 必须原样渲染 ShellSpec.DEFAULT，绝不套任何预置",
            ShellSpec.DEFAULT,
            state.shellSpec,
        )
        assertFalse("未配置壳的项目不得凭空长出顶栏", state.shellSpec.topBar.enabled)
    }

    @Test
    fun projectWithConfiguredShell_previewsThatShell() = runTest(dispatcher) {
        // 项目已配置壳 → 预览用项目那份（"预览 == 导出"的前提）
        val configured = urlProject().copy(shellSpec = ShellPresets.MINIMAL.spec)
        directory.put(configured)
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        assertEquals(ShellPresets.MINIMAL.spec, vm.uiState.value.shellSpec)

        // 另一面：项目配置了**自定义壳**（与任何预置都不逐字段相等）→ 原样渲染。
        val custom = urlProject().copy(
            shellSpec = ShellSpec.DEFAULT.copy(drawer = ShellDrawer(enabled = true)),
        )
        directory.put(custom)
        val customVm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        assertEquals("自定义壳必须被原样渲染", custom.shellSpec, customVm.uiState.value.shellSpec)
    }

    @Test
    fun projectSpecUpdate_isReflectedInPreview() = runTest(dispatcher) {
        // 项目流是长流：spec 一改（用户在进阶配置页改完）预览必须跟随——
        // 这是"配完壳回来预览即见"的实现基础。
        directory.put(urlProject().copy(shellSpec = ShellSpec.DEFAULT))
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()
        assertEquals(ShellSpec.DEFAULT, vm.uiState.value.shellSpec)

        directory.put(urlProject().copy(shellSpec = ShellPresets.TABBED.spec))
        advanceUntilIdle()
        assertEquals(
            "项目 spec 更新后预览必须跟随（不允许停留在旧壳）",
            ShellPresets.TABBED.spec,
            vm.uiState.value.shellSpec,
        )
    }

    @Test
    fun projectUpdate_resetsSelectionState() = runTest(dispatcher) {
        // 新推送的 spec 可能已删掉条目 / 关掉抽屉，旧选中态会变成悬空引用 → 必须复位。
        directory.put(urlProject().copy(shellSpec = ShellPresets.TABBED.spec))
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        vm.onTopTabSelected(ShellPresets.TABBED.spec.topTabs.items[1])
        assertEquals(ShellPresets.TABBED.spec.topTabs.items[1].id, vm.uiState.value.selectedTopTabId)

        // 换一套**内容不同**的壳（模拟用户在进阶配置页改完落库）：
        // 项目流带去重，推送完全相同的值不会触发发射，故这里必须换 spec。
        directory.put(urlProject().copy(shellSpec = ShellPresets.MINIMAL.spec))
        advanceUntilIdle()
        assertNull(vm.uiState.value.selectedTopTabId)
        assertNull(vm.uiState.value.selectedBottomItemId)
    }

    @Test
    fun topTabSelection_updatesSelectionAndNavigates() = runTest(dispatcher) {
        directory.put(urlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        val item = ShellPresets.TABBED.spec.topTabs.items[1]
        vm.onTopTabSelected(item)

        val state = vm.uiState.value
        assertEquals(item.id, state.selectedTopTabId)
        assertEquals("https://example.org", state.currentUrl)
        assertTrue(state.isExampleContent)
        assertEquals(
            ProjectEntry.Url("https://example.org"),
            (state.runtimeConfig?.entry),
        )
    }

    @Test
    fun bottomItemSelection_navigatesToTargetUrl() = runTest(dispatcher) {
        directory.put(urlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        val item = ShellPresets.CLASSIC.spec.bottomBar.items[2]
        vm.onBottomItemSelected(item)

        assertEquals(item.id, vm.uiState.value.selectedBottomItemId)
        assertEquals("https://example.net", vm.uiState.value.currentUrl)
    }

    @Test
    fun shellAction_navigateUrl_changesContent() = runTest(dispatcher) {
        directory.put(urlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        vm.onShellAction(
            ShellMenuItem(
                id = "act",
                title = "刷新",
                action = com.make.apk.data.model.ShellAction.NavigateUrl("https://example.org"),
            ),
        )
        assertEquals("https://example.org", vm.uiState.value.currentUrl)
    }

    @Test
    fun shellAction_unknownType_doesNotCrash() = runTest(dispatcher) {
        directory.put(urlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        vm.onShellAction(
            ShellMenuItem(
                id = "act",
                title = "神秘",
                action = com.make.apk.data.model.ShellAction.Unknown,
            ),
        )
        // 入口地址不变（未知动作不执行，也不崩）
        assertEquals("https://example.com", vm.uiState.value.currentUrl)
    }

    @Test
    fun exampleUrlDetection_coversReservedDomains() {
        assertTrue(ShellPreviewViewModel.isExampleUrl("https://example.com"))
        assertTrue(ShellPreviewViewModel.isExampleUrl("https://example.org/x"))
        assertTrue(ShellPreviewViewModel.isExampleUrl("https://example.net"))
        assertFalse(ShellPreviewViewModel.isExampleUrl("https://my-site.cn"))
    }

    private fun urlProject(): Project = baseProject().copy(entry = ProjectEntry.Url("https://example.com"))

    private fun htmlProject(): Project = baseProject().copy(
        projectType = ProjectType.HTML,
        entry = ProjectEntry.BundledHtml("<html></html>"),
    )

    private fun archiveProject(): Project = baseProject().copy(
        projectType = ProjectType.HTML,
        entry = ProjectEntry.Archive("assets/demo.zip", ArchiveKind.VUE),
    )

    private fun baseProject(): Project = Project(
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
    )
}
