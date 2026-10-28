package com.make.apk.ui.viewmodel

import com.make.apk.data.model.ArchiveKind
import com.make.apk.data.model.DuplicatePackageNameException
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
 * 添加页 ViewModel 单测（§2.6）。
 *
 * 用 [FakeProjectDirectory]（内存实现）替代真实数据库：
 * ViewModel 依赖的是**窄接口**，因此单测无需 Android 运行环境，快且稳。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddProjectViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var directory: FakeProjectDirectory
    private lateinit var vm: AddProjectViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        directory = FakeProjectDirectory()
        vm = AddProjectViewModel(directory = directory, archiveProbe = AlwaysMissingArchiveProbe)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 初始状态 ───────────────────────────────────────────────────────

    @Test
    fun initialState_hasExpectedDefaults() {
        val state = vm.uiState.value
        assertNull(state.projectType)
        assertEquals("", state.urlInput)
        assertEquals("", state.htmlInput)
        assertNull(state.archivePath)
        assertEquals("", state.apkName)
        assertEquals("", state.packageName)
        assertEquals(VersionScheme.THREE_SEGMENT, state.versionScheme)
        assertEquals("1.0.0", state.versionName)
        assertEquals("1", state.versionCode)
        assertFalse(state.canSave)
        assertFalse(state.dirty)
    }

    // ── 校验 ───────────────────────────────────────────────────────────

    @Test
    fun fillValidFields_canSaveBecomesTrue() = runTest(dispatcher) {
        vm.onProjectTypeSelected(ProjectType.URL)
        vm.onUrlChanged("https://example.com")
        vm.onApkNameChanged("示例应用")
        vm.onPackageNameChanged("com.example.demo")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.errors.isEmpty())
        assertTrue(state.canSave)
    }

    @Test
    fun blankApkName_producesError() = runTest(dispatcher) {
        vm.onProjectTypeSelected(ProjectType.URL)
        vm.onUrlChanged("https://example.com")
        vm.onApkNameChanged("  ")
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.errors[AddField.APK_NAME])
        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun invalidPackageName_producesError() = runTest(dispatcher) {
        vm.onPackageNameChanged("Com.Example")
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.errors[AddField.PACKAGE_NAME])
    }

    @Test
    fun invalidUrl_producesEntryError() = runTest(dispatcher) {
        vm.onProjectTypeSelected(ProjectType.URL)
        vm.onUrlChanged("javascript:alert(1)")
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.errors[AddField.ENTRY_URL])
    }

    @Test
    fun packageNameConflict_blocksSaveAndSetsError() = runTest(dispatcher) {
        directory.packageIndex.value = mapOf(
            "com.example.taken" to existingProject(id = 9L, packageName = "com.example.taken"),
        )
        vm.onProjectTypeSelected(ProjectType.URL)
        vm.onUrlChanged("https://example.com")
        vm.onApkNameChanged("示例应用")
        vm.onPackageNameChanged("com.example.taken")
        advanceTimeBy(AddProjectViewModel.PACKAGE_NAME_CHECK_DEBOUNCE_MS + 50)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.packageNameConflict)
        assertNotNull(state.errors[AddField.PACKAGE_NAME])
        assertFalse(state.canSave)
    }

    @Test
    fun packageNameCheck_isDebouncedAndOnlyForValidFormat() = runTest(dispatcher) {
        vm.onPackageNameChanged("Com.Bad")
        advanceTimeBy(AddProjectViewModel.PACKAGE_NAME_CHECK_DEBOUNCE_MS + 50)
        advanceUntilIdle()

        // 格式非法 → 不查库，也不报占用
        assertFalse(vm.uiState.value.packageNameConflict)
    }

    // ── 交互行为 ───────────────────────────────────────────────────────

    @Test
    fun switchVersionScheme_resetsVersionNameAndKeepsVersionCode() = runTest(dispatcher) {
        vm.onVersionSchemeChanged(VersionScheme.TWO_SEGMENT)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(VersionScheme.TWO_SEGMENT, state.versionScheme)
        assertEquals("1.0", state.versionName)
        assertEquals("1", state.versionCode)
    }

    @Test
    fun switchProjectType_clearsSourceInputs() = runTest(dispatcher) {
        vm.onProjectTypeSelected(ProjectType.URL)
        vm.onUrlChanged("https://example.com")
        vm.onHtmlChanged("<html></html>")
        advanceUntilIdle()

        vm.onProjectTypeSelected(ProjectType.HTML)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(ProjectType.HTML, state.projectType)
        assertEquals("", state.urlInput)
        assertEquals("", state.htmlInput)
        assertNull(state.archivePath)
    }

    @Test
    fun selectArchive_setsPathAndKind() = runTest(dispatcher) {
        vm.onProjectTypeSelected(ProjectType.VUE)
        vm.onArchivePicked("/tmp/bundle.zip", ArchiveKind.VUE)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("/tmp/bundle.zip", state.archivePath)
        assertEquals(ArchiveKind.VUE, state.archiveKind)
    }

    @Test
    fun selectProjectType_firstTime_doesNotMarkDirty() = runTest(dispatcher) {
        // 准星首帧会回传默认项（URL），那是渲染副产物而非用户操作 ⇒ dirty 必须保持 false，
        // 否则阶段 4/5 接上「放弃修改？」后会出现「空表单返回也弹确认」。
        vm.onProjectTypeSelected(ProjectType.URL)
        advanceUntilIdle()

        assertEquals(ProjectType.URL, vm.uiState.value.projectType)
        assertFalse("首次回传默认类型不算改动", vm.uiState.value.dirty)
    }

    @Test
    fun selectProjectType_afterFirstTime_marksDirty() = runTest(dispatcher) {
        vm.onProjectTypeSelected(ProjectType.URL)
        advanceUntilIdle()

        // 第二次起才是真正的「切换类型」：清空来源 + 标记改动
        vm.onProjectTypeSelected(ProjectType.HTML)
        advanceUntilIdle()

        assertTrue("已有选中项后再切类型属用户改动", vm.uiState.value.dirty)
    }

    @Test
    fun editTextField_marksDirty() = runTest(dispatcher) {
        vm.onApkNameChanged("示例应用")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.dirty)
    }

    // ── 保存 ───────────────────────────────────────────────────────────

    @Test
    fun save_success_emitsSavedExactlyOnce() = runTest(dispatcher) {
        val events = mutableListOf<AddProjectEvent>()
        val collector = launch { vm.events.toList(events) }

        vm.onProjectTypeSelected(ProjectType.URL)
        vm.onUrlChanged("https://example.com")
        vm.onApkNameChanged("示例应用")
        vm.onPackageNameChanged("com.example.demo")
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        val saved = events.filterIsInstance<AddProjectEvent.Saved>()
        assertEquals(1, saved.size)
        assertEquals(1L, saved.first().projectId)
        collector.cancel()
    }

    @Test
    fun save_duplicatePackageName_emitsSaveFailedAndSetsError() = runTest(dispatcher) {
        directory.createError = DuplicatePackageNameException("com.example.demo")
        val events = mutableListOf<AddProjectEvent>()
        val collector = launch { vm.events.toList(events) }

        vm.onProjectTypeSelected(ProjectType.URL)
        vm.onUrlChanged("https://example.com")
        vm.onApkNameChanged("示例应用")
        vm.onPackageNameChanged("com.example.demo")
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        assertEquals(1, events.filterIsInstance<AddProjectEvent.SaveFailed>().size)
        assertTrue(events.none { it is AddProjectEvent.Saved })
        assertNotNull(vm.uiState.value.errors[AddField.PACKAGE_NAME])
        collector.cancel()
    }

    @Test
    fun save_genericException_emitsSaveFailedOnce() = runTest(dispatcher) {
        directory.createError = IllegalStateException("磁盘满了")
        val events = mutableListOf<AddProjectEvent>()
        val collector = launch { vm.events.toList(events) }

        vm.onProjectTypeSelected(ProjectType.URL)
        vm.onUrlChanged("https://example.com")
        vm.onApkNameChanged("示例应用")
        vm.onPackageNameChanged("com.example.demo")
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        // 保存失败的**唯一**通知出口是 SaveFailed 事件（状态里没有 savingError 字段）
        val failures = events.filterIsInstance<AddProjectEvent.SaveFailed>()
        assertEquals(1, failures.size)
        assertEquals("磁盘满了", failures.first().message)
        assertTrue(events.none { it is AddProjectEvent.Saved })
        assertFalse("失败后必须解除保存中，否则保存按钮永久置灰", vm.uiState.value.saving)
        collector.cancel()
    }

    @Test
    fun save_withInvalidFields_doesNotWrite() = runTest(dispatcher) {
        vm.onProjectTypeSelected(ProjectType.HTML)
        // HTML 入口为空 → 不允许保存
        vm.onApkNameChanged("示例应用")
        vm.onPackageNameChanged("com.example.demo")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canSave)

        vm.save()
        advanceUntilIdle()

        assertNull(directory.lastCreated)
    }

    // ── 辅助 ───────────────────────────────────────────────────────────

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

    /** 探测不到任何文件的假探测器（单测里路径一律视为不存在）。 */
    private object AlwaysMissingArchiveProbe : ArchiveProbe {
        override fun probe(path: String): ArchiveProbeResult =
            ArchiveProbeResult(exists = false, sizeBytes = 0L, extension = "")
    }
}
