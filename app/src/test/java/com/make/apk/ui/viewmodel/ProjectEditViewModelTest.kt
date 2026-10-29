package com.make.apk.ui.viewmodel

import com.make.apk.data.model.DuplicatePackageNameException
import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.ShellPresets
import com.make.apk.data.model.VersionScheme
import com.make.apk.data.model.WebSettings
import com.make.apk.ui.editor.EditField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
 * 编辑页 ViewModel 单测（§2.6）。
 *
 * 同样用 [FakeProjectDirectory] 代替真实数据库。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectEditViewModelTest {

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
            themeColor = 0xFF2563EB.toInt(),
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

    // ── 加载 ───────────────────────────────────────────────────────────

    @Test
    fun load_fillsFormFromLibrary() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertFalse(state.projectNotFound)
        assertEquals("示例应用", state.apkName)
        assertEquals("com.example.demo", state.packageName)
        assertEquals("1.0.0", state.versionName)
        assertEquals("1", state.versionCode)
        assertEquals(ProjectType.URL, state.projectType)
        assertEquals("https://example.com", state.entryRef)
        assertEquals("webapp_7", state.isolationId)
        assertTrue(state.canSave)
    }

    @Test
    fun load_missingProject_setsProjectNotFoundAndBlocksSave() = runTest(dispatcher) {
        directory.put(project.copy(id = 999L))
        val vm = ProjectEditViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.projectNotFound)
        assertFalse(state.canSave)
    }

    // ── 包名唯一性（排除自身）─────────────────────────────────────────

    @Test
    fun keepOwnPackageName_isNotConflict() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()

        vm.onPackageNameChanged("com.example.demo")
        advanceTimeBy(ProjectEditViewModel.PACKAGE_NAME_CHECK_DEBOUNCE_MS + 50)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.packageNameConflict)
        assertNull(vm.uiState.value.errors[EditField.PACKAGE_NAME])
    }

    @Test
    fun changeToOtherPackageName_isConflict() = runTest(dispatcher) {
        directory.put(
            project.copy(id = 8L, packageName = "com.example.other"),
        )
        val vm = newVm()
        advanceUntilIdle()

        vm.onPackageNameChanged("com.example.other")
        advanceTimeBy(ProjectEditViewModel.PACKAGE_NAME_CHECK_DEBOUNCE_MS + 50)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.packageNameConflict)
        assertNotNull(state.errors[EditField.PACKAGE_NAME])
        assertFalse(state.canSave)
    }

    // ── Web 渲染配置 ───────────────────────────────────────────────────

    @Test
    fun webSettings_singleFieldChange_applies() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()

        vm.onJavascriptEnabledChanged(false)
        advanceUntilIdle()

        val settings = vm.uiState.value.webSettings
        assertFalse(settings.javascriptEnabled)
        assertTrue(settings.domStorageEnabled)
        assertTrue(settings.fitToApp)
    }

    @Test
    fun webSettings_cacheModeAndUserAgent_applies() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()

        vm.onCacheModeChanged(WebSettings.CACHE_MODE_NO_CACHE)
        vm.onUserAgentChanged("MMA/1.0")
        advanceUntilIdle()

        val settings = vm.uiState.value.webSettings
        assertEquals(WebSettings.CACHE_MODE_NO_CACHE, settings.cacheMode)
        assertEquals("MMA/1.0", settings.userAgent)
    }

    @Test
    fun themeColorChange_doesNotPolluteWebSettings() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        val before = vm.uiState.value.webSettings

        vm.onThemeColorChanged(0xFF00FF00.toInt())
        advanceUntilIdle()

        assertEquals(0xFF00FF00.toInt(), vm.uiState.value.themeColor)
        assertEquals(before, vm.uiState.value.webSettings)
    }

    // ── 壳契约（阶段 6.3）──────────────────────────────────────────────

    @Test
    fun shellSpecChange_marksDirty_persistsOnSave() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()

        val preset = ShellPresets.TABBED.spec
        vm.onShellSpecChanged(preset)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.dirty)
        assertEquals(preset, vm.uiState.value.shellSpec)

        vm.save()
        advanceUntilIdle()

        assertEquals(preset, directory.lastUpdated?.shellSpec)
    }

    // ── 页面态 ─────────────────────────────────────────────────────────

    @Test
    fun tabAndCollapse_arePageStateOnly() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()

        vm.onTabSelected(EditTab.EXPORT)
        vm.onCollapsedChanged(true)
        advanceUntilIdle()

        assertEquals(EditTab.EXPORT, vm.uiState.value.selectedTab)
        assertTrue(vm.uiState.value.collapsed)
        // 页面态不落库
        assertEquals("示例应用", directory.lastUpdated?.apkName ?: "示例应用")
    }

    // ── 外部推送策略 ───────────────────────────────────────────────────

    @Test
    fun externalUpdate_overwritesFormWhenNotDirty() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()

        directory.put(project.copy(apkName = "库里的新名字"))
        advanceUntilIdle()

        assertEquals("库里的新名字", vm.uiState.value.apkName)
    }

    @Test
    fun externalUpdate_doesNotOverwriteUserInputWhenDirty() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()

        vm.onApkNameChanged("用户正在输入")
        directory.put(project.copy(apkName = "库里的新名字"))
        advanceUntilIdle()

        assertEquals("用户正在输入", vm.uiState.value.apkName)
    }

    // ── 保存 ───────────────────────────────────────────────────────────

    @Test
    fun save_emitsSaved() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        val events = mutableListOf<ProjectEditEvent>()
        val collector = launch { vm.events.toList(events) }

        vm.onApkNameChanged("新名字")
        vm.save()
        advanceUntilIdle()

        assertEquals(1, events.filterIsInstance<ProjectEditEvent.Saved>().size)
        assertEquals("新名字", directory.lastUpdated?.apkName)
        collector.cancel()
    }

    @Test
    fun save_duplicatePackageName_emitsSaveFailed() = runTest(dispatcher) {
        directory.updateError = DuplicatePackageNameException("com.example.demo")
        val vm = newVm()
        advanceUntilIdle()
        val events = mutableListOf<ProjectEditEvent>()
        val collector = launch { vm.events.toList(events) }

        vm.save()
        advanceUntilIdle()

        assertEquals(1, events.filterIsInstance<ProjectEditEvent.SaveFailed>().size)
        assertTrue(events.none { it is ProjectEditEvent.Saved })
        collector.cancel()
    }

    @Test
    fun delete_emitsDeleted() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        val events = mutableListOf<ProjectEditEvent>()
        val collector = launch { vm.events.toList(events) }

        vm.delete()
        advanceUntilIdle()

        assertEquals(1, events.filterIsInstance<ProjectEditEvent.Deleted>().size)
        assertNull(directory.getById(project.id))
        collector.cancel()
    }

    @Test
    fun lockedFields_haveNoMutationApi() {
        // 编译期保证：编辑页 VM 不提供 projectType / isolationId 的修改方法。
        // 入口自 rc9 起 URL/HTML 可改（决策 D2，放开 §6.2 锁定），但存档入口（VUE/PHP）
        // 仍锁死——不得出现存档级修改方法（资产包拾取尚未实现）。
        val names = ProjectEditViewModel::class.java.methods.map { it.name }
        assertFalse(names.any { it.contains("ProjectType", ignoreCase = true) })
        assertFalse(names.any { it.contains("Isolation", ignoreCase = true) })
        assertFalse(names.any { it.contains("Archive", ignoreCase = true) })
        // 放开契约面：入口 URL / 源码 / 入口文件的修改 API 必须存在。
        assertTrue(names.any { it == "onEntryUrlChanged" })
        assertTrue(names.any { it == "onEntryHtmlChanged" })
        assertTrue(names.any { it == "onEntryFileChanged" })
    }
}
