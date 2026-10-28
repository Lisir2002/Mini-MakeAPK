package com.make.apk.ui.viewmodel

import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
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
 * 1. **非 URL 项目明确拦截**：给出可见提示，不崩、不静默；
 * 2. **切换预置子模板不重载 WebView**：`runtimeConfig` 对象保持同一引用；
 * 3. **壳动作落到内容区**：`NavigateUrl` 替换入口地址，示例地址被标记。
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
    fun nonUrlProject_isBlockedWithVisibleMessage() = runTest(dispatcher) {
        directory.put(htmlProject())
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
    fun firstFrame_highlightedPresetMatchesRenderedShell() = runTest(dispatcher) {
        // 状态一致性：选择条高亮的模板 id 必须与实际生效的壳契约来自同一套预置，
        // 不允许出现"高亮经典款、渲染裸壳（DEFAULT）"。
        directory.put(urlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        val state = vm.uiState.value
        val preset = ShellPresets.byId(state.selectedPresetId)
        assertEquals(preset.spec, state.shellSpec)
        assertTrue(state.shellSpec.topBar.enabled)

        // **不变式（钉死）**：高亮的预置 id 解析回的契约，必须与实际渲染的壳**逐字段相等**。
        // 高亮项与渲染项"同源"——由渲染中的壳反查得到，不允许各写各的。
        assertNotNull("项目未配置壳 → 必须高亮默认预置「经典款」", state.selectedPresetId)
        assertEquals(
            "高亮的预置模板必须与实际渲染的壳一致（不允许'高亮经典款、渲染别的壳'）",
            ShellPresets.byId(state.selectedPresetId).spec,
            state.shellSpec,
        )
    }

    @Test
    fun projectWithConfiguredShell_previewsThatShell() = runTest(dispatcher) {
        // 项目已配置壳 → 预览用项目那份（"预览 == 导出"的前提）
        val configured = urlProject().copy(shellSpec = ShellPresets.MINIMAL.spec)
        directory.put(configured)
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        assertEquals(ShellPresets.MINIMAL.spec, vm.uiState.value.shellSpec)

        // 不变式（钉死）：高亮项与渲染项**同源**——项目壳恰好等于某个预置时，
        // 高亮的必须是**那个**预置（由渲染中的壳反查得到），绝不是恒定的"经典款"。
        assertEquals(
            "项目已配置的壳恰好等于「极简款」→ 高亮的必须是「极简款」，不得是「经典款」",
            ShellPresets.MINIMAL.id,
            vm.uiState.value.selectedPresetId,
        )
        assertEquals(
            "高亮的预置模板必须与实际渲染的壳一致",
            ShellPresets.byId(vm.uiState.value.selectedPresetId).spec,
            vm.uiState.value.shellSpec,
        )

        // 另一面：项目配置了**自定义壳**（与任何预置都不逐字段相等）→
        // 不高亮任何预置（null），选择条据此显示"自定义"态；渲染的仍是项目自己那份。
        val custom = urlProject().copy(
            shellSpec = ShellSpec.DEFAULT.copy(drawer = ShellDrawer(enabled = true)),
        )
        directory.put(custom)
        val customVm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        assertEquals("自定义壳必须被原样渲染", custom.shellSpec, customVm.uiState.value.shellSpec)
        assertNull("自定义壳不属于任何预置 → 不得高亮任何预置", customVm.uiState.value.selectedPresetId)
    }

    @Test
    fun selectPreset_changesShellOnly_andKeepsRuntimeConfigReference() = runTest(dispatcher) {
        directory.put(urlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        val before = vm.uiState.value.runtimeConfig
        vm.selectPreset(ShellPresets.TABBED.id)

        val after = vm.uiState.value
        assertEquals(ShellPresets.TABBED.id, after.selectedPresetId)
        assertEquals(ShellPresets.TABBED.spec, after.shellSpec)
        // 关键：内容运行时配置**同一引用** → WebView 的去重判定命中 → 不 reload
        assertTrue(before === after.runtimeConfig)
    }

    @Test
    fun selectPreset_resetsSelectionState() = runTest(dispatcher) {
        directory.put(urlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        vm.onTopTabSelected(ShellPresets.TABBED.spec.topTabs.items[1])
        assertEquals(ShellPresets.TABBED.spec.topTabs.items[1].id, vm.uiState.value.selectedTopTabId)

        vm.selectPreset(ShellPresets.CLASSIC.id)
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
