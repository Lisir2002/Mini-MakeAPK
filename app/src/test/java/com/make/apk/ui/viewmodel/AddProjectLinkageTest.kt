package com.make.apk.ui.viewmodel

import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectDefaults
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.VersionScheme
import com.make.apk.ui.editor.AddField
import com.make.apk.ui.editor.ArchiveProbe
import com.make.apk.ui.editor.ArchiveProbeResult
import com.make.apk.ui.editor.ProjectDirectory
import com.make.apk.ui.editor.ProjectValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 阶段 3 **校验联动与端到端落库**补测（QA 独立追加）。
 *
 * 既有覆盖了"每一步会不会报错"，缺的是三条链路级事实：
 * 1. **链路闭环**：URL / HTML 两种项目从"选类型 → 填来源 → 保存 → 列表可见"
 *    真的走通，且落库的 `ProjectEntry` 形态与 `projectType` 严格对应
 *    （`ProjectListViewModel` 复用同一个 [FakeProjectDirectory]，直接验证"列表可见"）；
 * 2. **段式切换重置**：已有用例只验证"从默认值切过去会重置"，未覆盖
 *    "**用户手输过** versionName 再切段式也必须重置"——那才是真正会
 *    让人卡住的分支（不重置则旧格式的错误会赖着不走，且用户无从得知要改）；
 * 3. **防抖真的省了 IO**：用计数目录证明"快速连敲"只查库 1 次、
 *    "格式非法"查库 0 次——这是"300ms 防抖 + 仅合法才查库"两条约束的硬证据，
 *    而不是只观测结果（结果相同不代表中间没打库）。
 *
 * 另补一条 WARNING 语义：**只有警告时必须仍可保存**（否则软/硬上限的区分失去意义）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddProjectLinkageTest {

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

    // ── 链路闭环：URL ──────────────────────────────────────────────────

    @Test
    fun urlFlow_savePersistsUrlEntry_andBecomesVisibleInList() = runTest(dispatcher) {
        val events = mutableListOf<AddProjectEvent>()
        val collector = launch { vm.events.toList(events) }

        vm.onProjectTypeSelected(ProjectType.URL)
        vm.onUrlChanged("  https://example.com  ")
        advanceUntilIdle()

        // 未手填 → 名称/包名由 URL 自动建议回填
        assertEquals("example.com", vm.uiState.value.apkName)
        assertEquals("com.example", vm.uiState.value.packageName)
        assertTrue(vm.uiState.value.canSave)

        vm.save()
        advanceUntilIdle()

        val saved = directory.lastCreated
        assertNotNull(saved)
        checkNotNull(saved)
        assertEquals(ProjectType.URL, saved.projectType)
        assertTrue("入口必须是 Url 形态", saved.entry is ProjectEntry.Url)
        assertEquals("入口 url 必须 trim", "https://example.com", (saved.entry as ProjectEntry.Url).url)
        assertEquals("保存时不得自赋 isolationId", "", saved.isolationId)
        assertEquals(ProjectDefaults.DEFAULT_VERSION_SCHEME, saved.versionScheme)
        assertEquals(ProjectDefaults.DEFAULT_VERSION_CODE, saved.versionCode)
        assertEquals(ProjectDefaults.DEFAULT_WEB_SETTINGS, saved.webSettings)

        assertEquals(1, events.filterIsInstance<AddProjectEvent.Saved>().size)

        // 列表可见：同一个目录喂给列表 VM，新建的项目必须立刻出现在列表里
        val listVm = ProjectListViewModel(directory)
        val visible = listVm.projects.first { it.isNotEmpty() }
        assertEquals(1, visible.size)
        assertEquals(saved.apkName, visible.single().apkName)
        assertEquals(1, listVm.count.first { it > 0 })
        collector.cancel()
    }

    // ── 链路闭环：HTML ─────────────────────────────────────────────────

    @Test
    fun htmlFlow_savePersistsBundledHtmlEntry_andBecomesVisibleInList() = runTest(dispatcher) {
        val events = mutableListOf<AddProjectEvent>()
        val collector = launch { vm.events.toList(events) }

        vm.onProjectTypeSelected(ProjectType.HTML)
        vm.onHtmlChanged("  <html><body>你好</body></html>  ")
        advanceUntilIdle()

        // HTML 无域名可依据：只有应用名有建议，包名必须手填
        assertEquals(ProjectValidator.DEFAULT_HTML_NAME, vm.uiState.value.apkName)
        assertEquals("", vm.uiState.value.packageName)
        assertNotNull(vm.uiState.value.errors[AddField.PACKAGE_NAME])

        vm.onPackageNameChanged("com.example.paste")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canSave)

        vm.save()
        advanceUntilIdle()

        val saved = directory.lastCreated
        assertNotNull(saved)
        checkNotNull(saved)
        assertEquals(ProjectType.HTML, saved.projectType)
        assertTrue("入口必须是 BundledHtml 形态", saved.entry is ProjectEntry.BundledHtml)
        assertEquals(
            "多行 HTML 必须 trim 再入库",
            "<html><body>你好</body></html>",
            (saved.entry as ProjectEntry.BundledHtml).html,
        )

        val listVm = ProjectListViewModel(directory)
        val visible = listVm.projects.first { it.isNotEmpty() }
        assertEquals(1, visible.size)
        assertEquals(ProjectType.HTML, visible.single().projectType)
        collector.cancel()
    }

    // ── 段式切换：手输过的版本号也要重置 ───────────────────────────────

    @Test
    fun switchVersionScheme_resetsEvenUserTypedVersionName() = runTest(dispatcher) {
        vm.onProjectTypeSelected(ProjectType.URL)
        vm.onUrlChanged("https://example.com")
        vm.onApkNameChanged("示例应用")
        vm.onPackageNameChanged("com.example.demo")
        vm.onVersionNameChanged("2.3.4") // 手输、且当前段式（三段）下合法
        advanceUntilIdle()
        assertEquals("2.3.4", vm.uiState.value.versionName)
        assertTrue(vm.uiState.value.errors.isEmpty())

        vm.onVersionSchemeChanged(VersionScheme.TWO_SEGMENT)
        advanceUntilIdle()

        // 切段式必须把"手输的三段版本号"重置为二段默认值，否则会留下一个
        // 用户看不懂的错误，且"造成错误的输入"用户并没有主动改过。
        assertEquals(
            ProjectDefaults.defaultVersionName(VersionScheme.TWO_SEGMENT),
            vm.uiState.value.versionName,
        )
        assertTrue(vm.uiState.value.errors.isEmpty())
        assertTrue(vm.uiState.value.canSave)

        // 反向：二段手输值 → 切回三段同样重置
        vm.onVersionNameChanged("9.9")
        advanceUntilIdle()
        vm.onVersionSchemeChanged(VersionScheme.THREE_SEGMENT)
        advanceUntilIdle()
        assertEquals(
            ProjectDefaults.defaultVersionName(VersionScheme.THREE_SEGMENT),
            vm.uiState.value.versionName,
        )
    }

    // ── 警告不阻断保存 ─────────────────────────────────────────────────

    @Test
    fun onlyWarnings_stillAllowsSave() = runTest(dispatcher) {
        vm.onProjectTypeSelected(ProjectType.URL)
        vm.onUrlChanged("https://example.com")
        // 31..50 字：超过软上限（30）但未超硬上限（50）→ 只给警告
        vm.onApkNameChanged(NAME_OF_40)
        vm.onPackageNameChanged("com.example.demo")
        vm.onIconChanged("icons/logo.jpg") // 非 png → 另一条警告
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNotNull(state.warnings[AddField.APK_NAME])
        assertNotNull(state.warnings[AddField.ICON])
        assertTrue("存在错误即不得保存", state.errors.isEmpty())
        assertTrue("警告不得阻断保存", state.canSave)

        vm.save()
        advanceUntilIdle()
        assertNotNull(directory.lastCreated)
    }

    // ── 防抖：真的只查库一次 / 非法格式一次不查 ────────────────────────

    @Test
    fun rapidPackageNameTyping_queriesExactlyOnce() = runTest(dispatcher) {
        val counting = CountingProjectDirectory(directory)
        val vm = AddProjectViewModel(directory = counting, archiveProbe = AlwaysMissingProbe)

        // 防抖窗口内连敲 5 次有效包名，只有最后一次真正落到库查询
        listOf("a.b", "a.bc", "a.bcd", "a.bcde", "com.example.final").forEach {
            vm.onPackageNameChanged(it)
        }
        // 窗口未过：一次库都不能查（每敲一次就查一次是本 FTA 要防的行为）
        assertEquals("防抖窗口内不得提前查库", 0, counting.packageQueries.size)

        advanceTimeBy(AddProjectViewModel.PACKAGE_NAME_CHECK_DEBOUNCE_MS + 50)
        advanceUntilIdle()

        assertEquals("连敲必须合并成一次查询", listOf("com.example.final"), counting.packageQueries)

        // 新的输入过了下一个防抖窗口，才产生第二次查询（证明门是"每隔 300ms"而非"只查一次"）
        vm.onPackageNameChanged("com.example.second")
        advanceTimeBy(AddProjectViewModel.PACKAGE_NAME_CHECK_DEBOUNCE_MS + 50)
        advanceUntilIdle()

        assertEquals(
            listOf("com.example.final", "com.example.second"),
            counting.packageQueries,
        )
    }

    @Test
    fun illegalPackageName_neverQueriesDirectory() = runTest(dispatcher) {
        val counting = CountingProjectDirectory(directory)
        val vm = AddProjectViewModel(directory = counting, archiveProbe = AlwaysMissingProbe)

        // 三种非法形态：大写、单段、空段；格式非法的包名不可能与库中记录相同，查了也是白查
        listOf("Com.Bad", "single", "com..x").forEach { vm.onPackageNameChanged(it) }
        advanceTimeBy(AddProjectViewModel.PACKAGE_NAME_CHECK_DEBOUNCE_MS + 50)
        advanceUntilIdle()

        assertTrue("格式非法一律不得查库", counting.packageQueries.isEmpty())
        assertNotNull(vm.uiState.value.errors[AddField.PACKAGE_NAME])
    }

    // ── 辅助 ───────────────────────────────────────────────────────────

    /**
     * 计数版目录：只装饰 [getByPackageName]，用来证明"查了几次库"。
     *
     * 只看最终状态无法区分"防抖生效"与"查了 5 次但结论相同"，故在此计数。
     */
    private class CountingProjectDirectory(
        private val delegate: ProjectDirectory,
    ) : ProjectDirectory by delegate {

        /** 每次包名查询的入参（按调用顺序）。 */
        val packageQueries = mutableListOf<String>()

        override suspend fun getByPackageName(packageName: String): Project? {
            packageQueries += packageName
            return delegate.getByPackageName(packageName)
        }
    }

    private object AlwaysMissingProbe : ArchiveProbe {
        override fun probe(path: String): ArchiveProbeResult =
            ArchiveProbeResult(exists = false, sizeBytes = 0L, extension = "")
    }

    private companion object {
        /** 40 字应用名：超软上限（30）未超硬上限（50）→ 只出警告。 */
        val NAME_OF_40: String = "应用".repeat(20)
    }
}
