package com.make.apk.ui.viewmodel

import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.ShellAction
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.data.model.ShellNativeAction
import com.make.apk.data.model.ShellPresets
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.VersionScheme
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
 * 壳预览 ViewModel 补充测试（QA 独立补测 · 阶段 6.2）。
 *
 * ## 补什么
 *
 * 工程师既有的 [ShellPreviewViewModelTest] 已覆盖"可预览 / 非 URL 拦截 / 项目不存在 / 未知动作不崩"，
 * 但以下**评审明确点名**的路径未被钉死：
 *
 * 1. **切换子模板不动 `runtimeConfig`**——评审 D19 要求"子模板切换不重载 WebView"，
 *    且要求"VM 层验证 + 单测钉住"。工程师的测试断言的是"引用相同"，
 *    本测试补上"**切换全部三套模板后引用始终不变**"与"**切换不改变入口地址**"；
 * 2. **非法 projectId → `popBackStack`**（评审 D18）：VM 侧表现为 `projectNotFound`，
 *    且**不得**残留 `previewable = true`；
 * 3. **预置高亮模板 id 与实际渲染的壳必须一致**（评审偏差 3 的核心不变量）：
 *    工程师只覆盖了"项目未配置壳（回落 CLASSIC）"这一路，
 *    **未覆盖**"项目已配置壳（项目壳优先）"这一路 —— 见下方 `highlightedPreset_...` 用例。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShellPreviewViewModelExtraTest {

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

    // ── 1. 非法 projectId ───────────────────────────────────────────────

    // 说明：原「切换预置子模板不重载 WebView」一组用例已随功能删除而移除 ——
    // 预览页不再提供切壳入口（用户指令"预览就是预览"），该不变式失去载体。
    // 取而代之的是 §3 的「渲染的壳恒等于项目 spec」。

    @Test
    fun invalidProjectId_marksNotFoundAndIsNotPreviewable() = runTest(dispatcher) {
        // 目录里没有这个 id → 项目不存在
        val vm = ShellPreviewViewModel(projectId = 999L, directory = directory)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse("非法 id 不得停留在 loading", state.loading)
        assertTrue("非法 id 必须标记为项目不存在", state.projectNotFound)
        assertFalse("非法 id 不得可预览", state.previewable)
        assertNull("非法 id 不得有内容区配置", state.runtimeConfig)
    }

    @Test
    fun invalidProjectId_doesNotFallBackToAnyPreset() = runTest(dispatcher) {
        val vm = ShellPreviewViewModel(projectId = 999L, directory = directory)
        advanceUntilIdle()
        // 项目不存在时不应把某个预置模板当成"当前壳"呈现给用户
        assertTrue(vm.uiState.value.projectNotFound)
    }

    // ── 3. 渲染的壳恒等于项目 spec（预览 == 导出的核心不变量）────────────

    @Test
    fun renderedShell_equalsProjectSpec_whenProjectHasNoShell() = runTest(dispatcher) {
        directory.put(urlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(
            "未配置壳 → 必须渲染 ShellSpec.DEFAULT（不得偷偷套任何预置）",
            ShellSpec.DEFAULT,
            state.shellSpec,
        )
    }

    @Test
    fun renderedShell_equalsProjectSpec_whenProjectHasConfiguredShell() = runTest(dispatcher) {
        // 项目已配置壳（MINIMAL）→ 渲染的必须是项目那份，逐字段相等。
        directory.put(urlProject().copy(shellSpec = ShellPresets.MINIMAL.spec))
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(
            "项目已配置的壳必须被渲染",
            ShellPresets.MINIMAL.spec,
            state.shellSpec,
        )
    }

    @Test
    fun renderedShell_equalsProjectSpec_forEveryPreset() = runTest(dispatcher) {
        // 全景钉死：无论项目配成哪一套预置，预览渲染的都必须与项目 spec 逐字段相等。
        ShellPresets.ALL.forEach { preset ->
            directory.put(urlProject().copy(shellSpec = preset.spec))
            val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
            advanceUntilIdle()
            assertEquals(
                "项目配成 ${preset.id} → 预览必须渲染同一份",
                preset.spec,
                vm.uiState.value.shellSpec,
            )
        }
    }

    // ── 4. 动作模型的登记但不执行 ───────────────────────────────────────

    @Test
    fun nativeAction_isRegisteredButNotExecuted() = runTest(dispatcher) {
        directory.put(urlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        vm.onShellAction(
            ShellMenuItem(
                id = "act",
                title = "分享",
                action = ShellAction.Native(ShellNativeAction.SHARE),
            ),
        )
        advanceUntilIdle()

        assertEquals("原生动作不得改变内容区地址", "https://example.com", vm.uiState.value.currentUrl)
    }

    @Test
    fun internalPageAction_isRegisteredButNotExecuted() = runTest(dispatcher) {
        directory.put(urlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        vm.onShellAction(
            ShellMenuItem(
                id = "act",
                title = "关于",
                action = ShellAction.InternalPage("about"),
            ),
        )
        advanceUntilIdle()

        assertEquals("内置原生页不得改变内容区地址", "https://example.com", vm.uiState.value.currentUrl)
    }

    @Test
    fun unknownAction_onTopTab_doesNotCrash() = runTest(dispatcher) {
        directory.put(urlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        vm.onTopTabSelected(
            ShellMenuItem(id = "t", title = "神秘", action = ShellAction.Unknown),
        )
        advanceUntilIdle()

        assertEquals("未知动作不得改变内容区地址", "https://example.com", vm.uiState.value.currentUrl)
    }

    @Test
    fun emptyUrlAction_doesNotChangeContent() = runTest(dispatcher) {
        directory.put(urlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        vm.onShellAction(
            ShellMenuItem(id = "act", title = "空地址", action = ShellAction.NavigateUrl("")),
        )
        advanceUntilIdle()

        assertEquals("空地址动作不得改变内容区地址", "https://example.com", vm.uiState.value.currentUrl)
    }

    // ── 5. 非 URL 项目 ──────────────────────────────────────────────────

    @Test
    fun archiveProject_isBlockedWithVisibleMessage() = runTest(dispatcher) {
        directory.put(
            urlProject().copy(
                projectType = ProjectType.VUE,
                entry = ProjectEntry.Archive(assetRef = "webapp_1", kind = com.make.apk.data.model.ArchiveKind.GENERIC),
            ),
        )
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.previewable)
        assertEquals(ShellPreviewViewModel.UNSUPPORTED_ENTRY_MESSAGE, state.unsupportedMessage)
        assertNull(state.runtimeConfig)
    }

    // ── 脚手架 ──────────────────────────────────────────────────────────

    private fun urlProject(): Project = Project(
        id = 1L,
        projectType = ProjectType.URL,
        apkName = "示例应用",
        packageName = "com.example.demo",
        versionScheme = VersionScheme.THREE_SEGMENT,
        versionName = "1.0.0",
        versionCode = 1,
        themeColor = 0xFF2563EB.toInt(),
        entry = ProjectEntry.Url("https://example.com"),
        shellSpec = ShellSpec.DEFAULT,
        isolationId = "webapp_1",
        createdAt = 1L,
        updatedAt = 1L,
    )
}
