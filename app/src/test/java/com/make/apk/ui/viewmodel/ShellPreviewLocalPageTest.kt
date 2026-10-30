package com.make.apk.ui.viewmodel

import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectFile
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.ShellAction
import com.make.apk.data.model.ShellMenuItem
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 壳动作「本地页面」（[ShellAction.OpenLocalPage]）在**预览侧**的消费测试。
 *
 * 钉三件事：
 * 1. **真被消费**：点击后内容区入口换成该页面文件（[ProjectEntry.BundledHtml] 形态，
 *    内容 = 该文件源码、入口名 = 该文件名），附加文件（css/js）继续随行走虚拟域名供给；
 * 2. **不是 URL**：绝不被拼成 `https://page/about.html` 之类的网址（模式隔离）；
 * 3. **取不到就忽略**：文件不存在 / 项目无本地文件（URL 项目）时不崩、不改内容区。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShellPreviewLocalPageTest {

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
    fun shellAction_localPage_loadsProjectFileAsEntry() = runTest(dispatcher) {
        directory.put(htmlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        vm.onShellAction(
            ShellMenuItem(id = "act", title = "关于", action = ShellAction.OpenLocalPage("page/about.html")),
        )

        val entry = vm.uiState.value.runtimeConfig?.entry
        assertTrue("本地页面必须是项目文件形态，不是网址", entry is ProjectEntry.BundledHtml)
        val page = entry as ProjectEntry.BundledHtml
        assertEquals("page/about.html", page.entryFile)
        assertEquals(ABOUT_PAGE_CONTENT, page.html)
        // 附加资源随行走虚拟域名供给（相对路径 css/js 仍可解析）。
        assertTrue(page.files.any { it.name == "css/style.css" })
        assertFalse("入口文件不得在附加文件集里重复", page.files.any { it.name == "page/about.html" })
        assertEquals("page/about.html", vm.uiState.value.currentUrl)
        assertFalse(vm.uiState.value.isExampleContent)
    }

    @Test
    fun shellAction_localPage_entryFileIsAlsoLoadable() = runTest(dispatcher) {
        directory.put(htmlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        vm.onShellAction(
            ShellMenuItem(id = "act", title = "首页", action = ShellAction.OpenLocalPage("index.html")),
        )

        val page = vm.uiState.value.runtimeConfig?.entry as ProjectEntry.BundledHtml
        assertEquals("index.html", page.entryFile)
        assertEquals(ENTRY_PAGE_CONTENT, page.html)
    }

    @Test
    fun shellAction_localPage_missingFile_isIgnoredWithoutCrash() = runTest(dispatcher) {
        directory.put(htmlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()
        val before = vm.uiState.value.runtimeConfig

        vm.onShellAction(
            ShellMenuItem(id = "act", title = "幽灵页", action = ShellAction.OpenLocalPage("page/ghost.html")),
        )

        assertEquals(
            "页面不存在：内容区入口必须保持不变",
            before,
            vm.uiState.value.runtimeConfig,
        )
        val entry = vm.uiState.value.runtimeConfig?.entry as ProjectEntry.BundledHtml
        assertEquals("index.html", entry.entryFile)
    }

    @Test
    fun shellAction_localPage_onUrlProject_isIgnored() = runTest(dispatcher) {
        // URL 项目没有本地文件：本地页面动作不可执行，忽略且不崩（模式隔离的另一面）。
        directory.put(urlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        vm.onShellAction(
            ShellMenuItem(id = "act", title = "本地", action = ShellAction.OpenLocalPage("index.html")),
        )

        assertEquals(
            ProjectEntry.Url("https://example.com"),
            vm.uiState.value.runtimeConfig?.entry,
        )
        assertEquals("https://example.com", vm.uiState.value.currentUrl)
    }

    @Test
    fun shellAction_localPage_thenUrl_switchesBackToUrlEntry() = runTest(dispatcher) {
        directory.put(htmlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        vm.onShellAction(
            ShellMenuItem(id = "a", title = "本地", action = ShellAction.OpenLocalPage("page/about.html")),
        )
        vm.onShellAction(
            ShellMenuItem(id = "b", title = "网页", action = ShellAction.NavigateUrl("https://example.org")),
        )

        assertEquals(
            ProjectEntry.Url("https://example.org"),
            vm.uiState.value.runtimeConfig?.entry,
        )
        assertEquals("https://example.org", vm.uiState.value.currentUrl)
    }

    // ── 辅助 ──────────────────────────────────────────────────────────

    private fun htmlProject(): Project = baseProject(ProjectType.HTML).copy(
        entry = ProjectEntry.BundledHtml(
            html = ENTRY_PAGE_CONTENT,
            entryFile = "index.html",
            files = listOf(
                ProjectFile(name = "css/style.css", content = "body{}"),
                ProjectFile(name = "page/about.html", content = ABOUT_PAGE_CONTENT),
            ),
        ),
    )

    private fun urlProject(): Project =
        baseProject(ProjectType.URL).copy(entry = ProjectEntry.Url("https://example.com"))

    private fun baseProject(type: ProjectType): Project = Project(
        id = 1L,
        projectType = type,
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

    private companion object {
        const val ENTRY_PAGE_CONTENT: String = "<html>入口</html>"
        const val ABOUT_PAGE_CONTENT: String = "<html>关于</html>"
    }
}
