package com.make.apk.ui.viewmodel

import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.ui.editor.AddField
import com.make.apk.ui.editor.ArchiveProbe
import com.make.apk.ui.editor.ArchiveProbeResult
import com.make.apk.ui.editor.displayLabelOf
import com.make.apk.ui.editor.isPending
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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
 * 阶段 3 **范围决策**的行为补测（QA 独立追加）。
 *
 * 产品定案：本阶段只做 `URL` / `HTML` 两条完整链路，`VUE` / `PHP` 仅占位——
 * 准星仍四项可见可选，来源区给「敬请期待」说明，**保存门禁必须拦截**。
 *
 * 既有 `AddProjectViewModelTest` / `AddProjectViewModelContractTest` 覆盖的是
 * 「选了归档怎么存」这一方向，**反向的"选了 VUE/PHP 能不能存"没有任何断言**；
 * 而这条恰恰是本次范围决策唯一不可退让的硬保证——一旦将来有人误接了 UI 或
 * 改坏了 `entryValid()`，"VUE 可保存"就会静默溜上线。本组用例把它钉死：
 *
 * 1. VUE / PHP 在任何"其余字段全合法"的组合下都不能落库（含 `save()` 强攻）；
 * 2. 占位 → 回到可用类型时，残留的 `ENTRY_ARCHIVE` 错误必须被清干净；
 * 3. §9 四项并列设计：`isPending()` 只认 VUE/PHP，四项标签非空且互不重复。
 *
 * 另补 2 项真实场景缺失：`errors` 为空但 `entryValid()` 为假（纯空白入口）
 * 时也必须拦住——防止有人把 `canSave` 简化成只看 `errors`。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddProjectScopeTest {

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

    // ── VUE / PHP 保存门禁（范围决策的硬保证）──────────────────────────

    @Test
    fun vueSelected_saveGateBlocksWrite() = runTest(dispatcher) {
        val events = mutableListOf<AddProjectEvent>()
        val collector = launch { vm.events.toList(events) }

        // 除入口外全部合法：本阶段不存在任何能设置 archivePath 的 UI 通路
        vm.onProjectTypeSelected(ProjectType.VUE)
        fillAllTextFields()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse("VUE 占位项必须不可保存", state.canSave)
        assertNotNull(state.errors[AddField.ENTRY_ARCHIVE])
        assertNull(state.archivePath)

        vm.save()
        advanceUntilIdle()

        assertNull("VUE 项目不得落库", directory.lastCreated)
        assertTrue(directory.projects.value.isEmpty())
        assertTrue(events.none { it is AddProjectEvent.Saved })
        collector.cancel()
    }

    @Test
    fun phpSelected_saveGateBlocksWrite() = runTest(dispatcher) {
        val events = mutableListOf<AddProjectEvent>()
        val collector = launch { vm.events.toList(events) }

        vm.onProjectTypeSelected(ProjectType.PHP)
        fillAllTextFields()
        advanceUntilIdle()

        assertFalse("PHP 占位项必须不可保存", vm.uiState.value.canSave)
        assertNotNull(vm.uiState.value.errors[AddField.ENTRY_ARCHIVE])

        vm.save()
        advanceUntilIdle()

        assertNull("PHP 项目不得落库", directory.lastCreated)
        assertTrue(events.none { it is AddProjectEvent.Saved })
        collector.cancel()
    }

    @Test
    fun vueSelected_becomesSavable_afterSwitchingBackToUrl() = runTest(dispatcher) {
        vm.onProjectTypeSelected(ProjectType.VUE)
        fillAllTextFields()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canSave)

        // 回到 URL：粘错的占位状态不得污染可用链路（残留 ENTRY_ARCHIVE 错误必须被清掉）
        vm.onProjectTypeSelected(ProjectType.URL)
        vm.onUrlChanged("https://example.com")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull("切回 URL 后不得残留 ENTRY_ARCHIVE 错误", state.errors[AddField.ENTRY_ARCHIVE])
        assertTrue(state.errors.isEmpty())
        assertTrue(state.canSave)
    }

    // ── 纯空白入口：errors 与 entryValid 双闸都生效 ────────────────────

    @Test
    fun whitespaceOnlyUrl_blocksSaveAndWrite() = runTest(dispatcher) {
        vm.onProjectTypeSelected(ProjectType.URL)
        vm.onUrlChanged("   ")
        vm.onApkNameChanged("示例应用")
        vm.onPackageNameChanged("com.example.demo")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNotNull(state.errors[AddField.ENTRY_URL])
        assertFalse("空白网址不得保存", state.canSave)

        vm.save()
        advanceUntilIdle()
        assertNull(directory.lastCreated)
    }

    @Test
    fun emptyHtml_fallsBackToTemplate_andSaves() = runTest(dispatcher) {
        // rc9 类型隔离：新建 HTML 不再强制先写代码——代码兜底默认模板，
        // 入口文件兜底 index.html（「项目代码」页后续维护源码）。
        vm.onProjectTypeSelected(ProjectType.HTML)
        vm.onHtmlChanged("   \n  ")
        vm.onApkNameChanged("示例应用")
        vm.onPackageNameChanged("com.example.demo")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull("空代码不得阻断保存（兜底默认模板）", state.errors[AddField.ENTRY_HTML])
        assertTrue(state.canSave)

        vm.save()
        advanceUntilIdle()
        val created = directory.lastCreated
        assertNotNull("空代码 HTML 项目应可落库", created)
        val entry = created!!.entry as ProjectEntry.BundledHtml
        assertEquals(ProjectEntry.BundledHtml.DEFAULT_TEMPLATE, entry.html)
        assertEquals(ProjectEntry.BundledHtml.DEFAULT_ENTRY_FILE, entry.entryFile)
    }

    @Test
    fun entryFile_customName_persistsToEntry() = runTest(dispatcher) {
        vm.onProjectTypeSelected(ProjectType.HTML)
        vm.onEntryFileChanged("main.htm")
        vm.onApkNameChanged("示例应用")
        vm.onPackageNameChanged("com.example.demo")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.canSave)
        vm.save()
        advanceUntilIdle()
        val entry = directory.lastCreated!!.entry as ProjectEntry.BundledHtml
        assertEquals("main.htm", entry.entryFile)
    }

    @Test
    fun entryFile_withPathOrBadExtension_blocksSave() = runTest(dispatcher) {
        vm.onProjectTypeSelected(ProjectType.HTML)
        vm.onApkNameChanged("示例应用")
        vm.onPackageNameChanged("com.example.demo")

        vm.onEntryFileChanged("a/b.html")
        advanceUntilIdle()
        assertNotNull("含路径成分的入口文件必须被拦", vm.uiState.value.errors[AddField.ENTRY_FILE])
        assertFalse(vm.uiState.value.canSave)

        vm.onEntryFileChanged("home.php")
        advanceUntilIdle()
        assertNotNull("非 html/htm 扩展名必须被拦", vm.uiState.value.errors[AddField.ENTRY_FILE])
        assertFalse(vm.uiState.value.canSave)

        vm.onEntryFileChanged("")
        advanceUntilIdle()
        assertNull("空文件名回落默认 index.html，必须放行", vm.uiState.value.errors[AddField.ENTRY_FILE])
        assertTrue(vm.uiState.value.canSave)
    }

    // ── §9 四项并列设计（占位项仍可见可选）────────────────────────────

    @Test
    fun isPending_onlyVueAndPhp() {
        assertTrue(isPending(ProjectType.VUE))
        assertTrue(isPending(ProjectType.PHP))
        assertFalse("URL 是本阶段完整链路，不得标记为占位", isPending(ProjectType.URL))
        assertFalse("HTML 是本阶段完整链路，不得标记为占位", isPending(ProjectType.HTML))
    }

    @Test
    fun fourTypes_areAllVisible_selectableAndDistinguishable() {
        val labels = ProjectType.entries.map { displayLabelOf(it) }

        assertEquals(4, labels.size)
        assertTrue("四项标签必须非空（ TalkBack 不能念空串）", labels.all { it.isNotBlank() })
        assertEquals("四项标签必须互不相同（否则准星无法区分）", labels.size, labels.toSet().size)
    }

    // ── 辅助 ───────────────────────────────────────────────────────────

    /** 把文本类字段填满到合法（入口由各用例按类型提供，本方法不涉及 archive）。 */
    private fun fillAllTextFields() {
        vm.onApkNameChanged("示例应用")
        vm.onPackageNameChanged("com.example.demo")
    }

    /** 本阶段归档探测无真实调用点：路径一律视为不存在。 */
    private object AlwaysMissingProbe : ArchiveProbe {
        override fun probe(path: String): ArchiveProbeResult =
            ArchiveProbeResult(exists = false, sizeBytes = 0L, extension = "")
    }
}
