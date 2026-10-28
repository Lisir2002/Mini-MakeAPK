package com.make.apk.ui.viewmodel

import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.VersionScheme
import com.make.apk.ui.editor.AddField
import com.make.apk.ui.editor.ArchiveProbe
import com.make.apk.ui.editor.ArchiveProbeResult
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
 * 添加页 ViewModel **契约补测**（阶段 2 审查补充）。
 *
 * 补的是既有 `AddProjectViewModelTest` 的覆盖缺口：
 * - 切换项目类型后，应用名 / 包名**不得被建议逻辑覆盖用户已填的值**；
 * - 未手填时才允许建议回填；
 * - 切换项目类型必须**清空**三种来源输入；
 * - 包名格式非法时必须**清掉**旧的占用结论（防抖竞态）；
 * - 旧查询结果**不得覆盖**新输入；
 * - 连续两次 `save()` 只能写库一次；`Saved` 事件只能发一次；
 * - `canSave` 与 `errors` 保持自洽（有错必不能保存）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddProjectViewModelContractTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var directory: FakeProjectDirectory
    private lateinit var vm: AddProjectViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        directory = FakeProjectDirectory()
        vm = AddProjectViewModel(directory = directory, archiveProbe = AlwaysMissingProbe)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 切换类型：清空来源输入 ───────────────────────────────────────────

    @Test
    fun switchProjectType_clearsAllThreeSourceInputs() = runTest(dispatcher) {
        vm.onProjectTypeSelected(ProjectType.URL)
        vm.onUrlChanged("https://example.com")
        vm.onHtmlChanged("<html></html>")
        vm.onArchivePicked("/tmp/a.zip", com.make.apk.data.model.ArchiveKind.VUE)
        advanceUntilIdle()

        vm.onProjectTypeSelected(ProjectType.HTML)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("", state.urlInput)
        assertEquals("", state.htmlInput)
        assertNull(state.archivePath)
    }

    // ── 切换类型：不得覆盖用户已填的名称 ────────────────────────────────

    @Test
    fun switchProjectType_doesNotOverwriteManuallyEditedApkName() = runTest(dispatcher) {
        vm.onProjectTypeSelected(ProjectType.URL)
        vm.onUrlChanged("https://example.com")
        vm.onApkNameChanged("用户手动名")
        advanceUntilIdle()

        vm.onProjectTypeSelected(ProjectType.HTML)
        advanceUntilIdle()

        assertEquals("用户手动名", vm.uiState.value.apkName)
    }

    @Test
    fun switchProjectType_doesNotOverwriteManuallyEditedPackageName() = runTest(dispatcher) {
        vm.onProjectTypeSelected(ProjectType.URL)
        vm.onUrlChanged("https://example.com")
        vm.onPackageNameChanged("com.manual.name")
        advanceUntilIdle()

        vm.onProjectTypeSelected(ProjectType.HTML)
        advanceUntilIdle()

        assertEquals("com.manual.name", vm.uiState.value.packageName)
    }

    @Test
    fun switchProjectType_doesNotOverwriteManuallyEditedNames_whenArchiveTypeSelected() =
        runTest(dispatcher) {
            vm.onProjectTypeSelected(ProjectType.URL)
            vm.onUrlChanged("https://example.com")
            vm.onApkNameChanged("手动应用名")
            vm.onPackageNameChanged("com.manual.archive")
            advanceUntilIdle()

            vm.onProjectTypeSelected(ProjectType.VUE)
            advanceUntilIdle()

            assertEquals("手动应用名", vm.uiState.value.apkName)
            assertEquals("com.manual.archive", vm.uiState.value.packageName)
        }

    // ── 未手填时：建议可以回填 ──────────────────────────────────────────

    @Test
    fun switchProjectType_fillsSuggestion_whenUserNeverEditedName() = runTest(dispatcher) {
        vm.onProjectTypeSelected(ProjectType.URL)
        vm.onUrlChanged("https://www.example.com")
        advanceUntilIdle()

        // 未手填 → URL 类型应自动跟随 host 建议
        assertEquals("example.com", vm.uiState.value.apkName)
        assertEquals("com.example", vm.uiState.value.packageName)

        vm.onProjectTypeSelected(ProjectType.HTML)
        advanceUntilIdle()

        // HTML 类型建议为默认名（未手填 → 允许回填）
        assertEquals("我的网页", vm.uiState.value.apkName)
    }

    @Test
    fun applySuggestion_restoresAutoFollowMode() = runTest(dispatcher) {
        vm.onProjectTypeSelected(ProjectType.URL)
        vm.onUrlChanged("https://www.example.com")
        vm.onApkNameChanged("手动")
        advanceUntilIdle()
        assertEquals("手动", vm.uiState.value.apkName)

        vm.applySuggestionApkName()
        advanceUntilIdle()

        assertEquals("example.com", vm.uiState.value.apkName)
        assertFalse(vm.uiState.value.apkNameEdited)
    }

    // ── 防抖与占用结论 ──────────────────────────────────────────────────

    @Test
    fun illegalPackageName_clearsPreviousConflict() = runTest(dispatcher) {
        directory.packageIndex.value = mapOf(
            "com.example.taken" to existingProject(id = 9L, packageName = "com.example.taken"),
        )
        vm.onPackageNameChanged("com.example.taken")
        advanceTimeBy(AddProjectViewModel.PACKAGE_NAME_CHECK_DEBOUNCE_MS + 50)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.packageNameConflict)

        // 改成非法格式 → 旧 job 已取消，占用结论必须被清掉
        vm.onPackageNameChanged("Com.Invalid")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.packageNameConflict)
        assertNotNull(vm.uiState.value.errors[AddField.PACKAGE_NAME])
        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun emptyPackageName_clearsConflict() = runTest(dispatcher) {
        directory.packageIndex.value = mapOf(
            "com.example.taken" to existingProject(id = 9L, packageName = "com.example.taken"),
        )
        vm.onPackageNameChanged("com.example.taken")
        advanceTimeBy(AddProjectViewModel.PACKAGE_NAME_CHECK_DEBOUNCE_MS + 50)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.packageNameConflict)

        vm.onPackageNameChanged("")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.packageNameConflict)
    }

    @Test
    fun staleCheckResult_doesNotOverwriteNewerInput() = runTest(dispatcher) {
        directory.packageIndex.value = mapOf(
            "com.example.taken" to existingProject(id = 9L, packageName = "com.example.taken"),
        )
        // 第一次输入（会被占用），但在防抖窗口内改成合法且空闲的包名
        vm.onPackageNameChanged("com.example.taken")
        advanceTimeBy(AddProjectViewModel.PACKAGE_NAME_CHECK_DEBOUNCE_MS + 50)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.packageNameConflict)

        vm.onPackageNameChanged("com.example.free")
        // 旧 job 已取消；新 job 未到点前，"占用"结论短暂残留（防抖窗口内可接受）
        advanceTimeBy(AddProjectViewModel.PACKAGE_NAME_CHECK_DEBOUNCE_MS + 50)
        advanceUntilIdle()
        // 新查询（空闲包名）完成后必须是"无冲突 + 无错误"，旧查询结果不得覆盖新输入
        assertFalse(vm.uiState.value.packageNameConflict)
        assertNull(vm.uiState.value.errors[AddField.PACKAGE_NAME])
    }

    @Test
    fun packageNameCheck_isNotTriggered_forIllegalFormat() = runTest(dispatcher) {
        vm.onPackageNameChanged("com..x")
        advanceTimeBy(AddProjectViewModel.PACKAGE_NAME_CHECK_DEBOUNCE_MS + 50)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.packageNameConflict)
        assertNotNull(vm.uiState.value.errors[AddField.PACKAGE_NAME])
    }

    // ── canSave 与 errors 自洽 ──────────────────────────────────────────

    @Test
    fun canSave_isFalseWheneverAnyErrorExists() = runTest(dispatcher) {
        vm.onProjectTypeSelected(ProjectType.URL)
        vm.onUrlChanged("https://example.com")
        vm.onApkNameChanged("示例应用")
        vm.onPackageNameChanged("com.example.demo")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canSave)

        // 制造入口错误
        vm.onUrlChanged("")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.errors.isNotEmpty())
        assertFalse(vm.uiState.value.canSave)

        // 制造版本号错误
        vm.onUrlChanged("https://example.com")
        vm.onVersionNameChanged("abc")
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.errors[AddField.VERSION_NAME])
        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun canSave_isFalse_whenProjectTypeNotSelected() = runTest(dispatcher) {
        vm.onApkNameChanged("示例应用")
        vm.onPackageNameChanged("com.example.demo")
        advanceUntilIdle()

        assertNull(vm.uiState.value.projectType)
        assertFalse(vm.uiState.value.canSave)
    }

    // ── 保存：幂等与事件一次性 ──────────────────────────────────────────

    @Test
    fun doubleSave_writesOnlyOnce() = runTest(dispatcher) {
        val events = mutableListOf<AddProjectEvent>()
        val collector = launch { vm.events.toList(events) }

        vm.onProjectTypeSelected(ProjectType.URL)
        vm.onUrlChanged("https://example.com")
        vm.onApkNameChanged("示例应用")
        vm.onPackageNameChanged("com.example.demo")
        advanceUntilIdle()

        vm.save()
        vm.save()
        vm.save()
        advanceUntilIdle()

        assertEquals(1, directory.projects.value.size)
        assertEquals(1, events.filterIsInstance<AddProjectEvent.Saved>().size)
        collector.cancel()
    }

    @Test
    fun save_doesNotSelfAssignIsolationId() = runTest(dispatcher) {
        vm.onProjectTypeSelected(ProjectType.URL)
        vm.onUrlChanged("https://example.com")
        vm.onApkNameChanged("示例应用")
        vm.onPackageNameChanged("com.example.demo")
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        // isolationId 由仓储回填，VM 提交时必须是空串
        assertEquals("", directory.lastCreated?.isolationId)
        assertEquals(0L, directory.lastCreated?.id)
    }

    // ── 版本段式切换 ────────────────────────────────────────────────────

    @Test
    fun hugeVersionSegment_doesNotThrowFromInputHandler() = runTest(dispatcher) {
        // 回归：超长数字段曾让校验器抛 NumberFormatException，
        // 异常会从 `onVersionNameChanged` → `revalidate()` 一路逃逸到 UI 层（崩溃）。
        vm.onVersionNameChanged("10000000000.0.0")
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.errors[AddField.VERSION_NAME])
        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun switchVersionScheme_resetsVersionName_keepsVersionCode() = runTest(dispatcher) {
        vm.onVersionCodeChanged("42")
        vm.onVersionSchemeChanged(VersionScheme.TWO_SEGMENT)
        advanceUntilIdle()

        assertEquals("1.0", vm.uiState.value.versionName)
        assertEquals("42", vm.uiState.value.versionCode)

        vm.onVersionSchemeChanged(VersionScheme.THREE_SEGMENT)
        advanceUntilIdle()

        assertEquals("1.0.0", vm.uiState.value.versionName)
        assertEquals("42", vm.uiState.value.versionCode)
    }

    // ── 辅助 ────────────────────────────────────────────────────────────

    private fun existingProject(id: Long, packageName: String) =
        com.make.apk.data.model.Project(
            id = id,
            projectType = ProjectType.URL,
            apkName = "已有应用",
            packageName = packageName,
            versionScheme = VersionScheme.THREE_SEGMENT,
            versionName = "1.0.0",
            versionCode = 1,
            themeColor = 0,
            entry = com.make.apk.data.model.ProjectEntry.Url("https://example.com"),
            isolationId = "webapp_$id",
            createdAt = 1L,
            updatedAt = 1L,
        )

    private object AlwaysMissingProbe : ArchiveProbe {
        override fun probe(path: String): ArchiveProbeResult =
            ArchiveProbeResult(exists = false, sizeBytes = 0L, extension = "")
    }
}
