package com.make.apk.ui.viewmodel

import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
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
 * 编辑页 ViewModel **契约补测**（阶段 2 审查补充）。
 *
 * 补的缺口：
 * - 查包名时**排除自身**（且仓储 `update()` 的 `excludeId` 语义与 VM 一致）；
 * - 改成他人包名 → 冲突；改回自己包名 → 冲突解除；
 * - 非法包名必须清掉占用结论；
 * - `dirty == false` 时外部推送可覆盖表单，`dirty == true` 时不可；
 * - 锁死项（projectType / entry / isolationId）无修改 API；
 * - 切换版本段式 → 重置 `versionName`，`versionCode` 不变。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectEditViewModelContractTest {

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

    // ── 排除自身 ────────────────────────────────────────────────────────

    @Test
    fun ownPackageName_isNeverConflict_evenWhenReChecked() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()

        vm.onPackageNameChanged("com.example.demo")
        advanceTimeBy(ProjectEditViewModel.PACKAGE_NAME_CHECK_DEBOUNCE_MS + 50)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.packageNameConflict)
        assertNull(vm.uiState.value.errors[EditField.PACKAGE_NAME])
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun changingToOtherPackageName_thenBackToOwn_clearsConflict() = runTest(dispatcher) {
        directory.put(project.copy(id = 8L, packageName = "com.example.other"))
        val vm = newVm()
        advanceUntilIdle()

        vm.onPackageNameChanged("com.example.other")
        advanceTimeBy(ProjectEditViewModel.PACKAGE_NAME_CHECK_DEBOUNCE_MS + 50)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.packageNameConflict)

        vm.onPackageNameChanged("com.example.demo")
        advanceTimeBy(ProjectEditViewModel.PACKAGE_NAME_CHECK_DEBOUNCE_MS + 50)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.packageNameConflict)
        assertNull(vm.uiState.value.errors[EditField.PACKAGE_NAME])
    }

    @Test
    fun illegalPackageName_clearsConflict() = runTest(dispatcher) {
        directory.put(project.copy(id = 8L, packageName = "com.example.other"))
        val vm = newVm()
        advanceUntilIdle()

        vm.onPackageNameChanged("com.example.other")
        advanceTimeBy(ProjectEditViewModel.PACKAGE_NAME_CHECK_DEBOUNCE_MS + 50)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.packageNameConflict)

        vm.onPackageNameChanged("Com.Bad")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.packageNameConflict)
        assertNotNull(vm.uiState.value.errors[EditField.PACKAGE_NAME])
        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun save_keepsLockedFieldsUnchanged() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()

        vm.onApkNameChanged("新名字")
        vm.save()
        advanceUntilIdle()

        val saved = directory.lastUpdated
        assertNotNull(saved)
        assertEquals("新名字", saved?.apkName)
        assertEquals(ProjectType.URL, saved?.projectType)
        assertEquals("https://example.com", (saved?.entry as? ProjectEntry.Url)?.url)
        assertEquals("webapp_7", saved?.isolationId)
    }

    // ── 外部推送策略 ────────────────────────────────────────────────────

    @Test
    fun externalPush_overwritesAllFields_whenNotDirty() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()

        directory.put(project.copy(apkName = "库里名字", packageName = "com.example.pushed"))
        advanceUntilIdle()

        assertEquals("库里名字", vm.uiState.value.apkName)
        assertEquals("com.example.pushed", vm.uiState.value.packageName)
    }

    @Test
    fun externalPush_doesNotOverwriteUserInput_whenDirty() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()

        vm.onApkNameChanged("用户输入")
        vm.onPackageNameChanged("com.example.mine")
        directory.put(project.copy(apkName = "库里名字", packageName = "com.example.pushed"))
        advanceUntilIdle()

        assertEquals("用户输入", vm.uiState.value.apkName)
        assertEquals("com.example.mine", vm.uiState.value.packageName)
    }

    @Test
    fun externalPush_stillRefreshesReadOnlyFields_whenDirty() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()

        vm.onApkNameChanged("用户输入")
        directory.put(project.copy(isolationId = "webapp_7_v2"))
        advanceUntilIdle()

        // 只读字段允许刷新
        assertEquals("webapp_7_v2", vm.uiState.value.isolationId)
        // 可编辑字段不被覆盖
        assertEquals("用户输入", vm.uiState.value.apkName)
    }

    // ── 版本段式 ────────────────────────────────────────────────────────

    @Test
    fun switchVersionScheme_resetsVersionName_keepsVersionCode() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()

        vm.onVersionCodeChanged("42")
        vm.onVersionSchemeChanged(VersionScheme.TWO_SEGMENT)
        advanceUntilIdle()

        assertEquals("1.0", vm.uiState.value.versionName)
        assertEquals("42", vm.uiState.value.versionCode)
    }

    // ── 锁死项 ──────────────────────────────────────────────────────────

    @Test
    fun lockedFields_exposeNoMutationApi() {
        val names = ProjectEditViewModel::class.java.methods.map { it.name }
        listOf("ProjectType", "Entry", "Isolation").forEach { token ->
            assertFalse(
                "编辑页不应提供与 $token 相关的修改方法",
                names.any { it.contains(token, ignoreCase = true) },
            )
        }
    }

    // ── 不存在的 id ─────────────────────────────────────────────────────

    @Test
    fun nonExistentProjectId_setsProjectNotFound_andDoesNotCrash() = runTest(dispatcher) {
        val vm = ProjectEditViewModel(projectId = 0L, directory = directory)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.projectNotFound)
        assertFalse(vm.uiState.value.canSave)
        assertFalse(vm.uiState.value.loading)
    }

    @Test
    fun negativeProjectId_doesNotCrash() = runTest(dispatcher) {
        val vm = ProjectEditViewModel(projectId = -1L, directory = directory)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.projectNotFound)
        assertFalse(vm.uiState.value.canSave)
    }

    // ── 事件一次性 ──────────────────────────────────────────────────────

    @Test
    fun save_emitsSavedExactlyOnce() = runTest(dispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        val events = mutableListOf<ProjectEditEvent>()
        val collector = launch { vm.events.toList(events) }

        vm.save()
        vm.save()
        advanceUntilIdle()

        assertEquals(1, events.filterIsInstance<ProjectEditEvent.Saved>().size)
        collector.cancel()
    }
}
