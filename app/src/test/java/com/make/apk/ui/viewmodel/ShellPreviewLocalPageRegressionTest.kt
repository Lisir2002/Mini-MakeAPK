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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 预览侧消费「本地页面」的**回归**测试（QA 补，评审发现）。
 *
 * 与 [ShellPreviewLocalPageTest] 的分工：那份钉"单次的取页行为"，本份钉
 * **多次来回切换**与**非法 path 的隔离性**——这是多 tab 指向不同本地页面时的主路径，
 * 也是"预览 == 导出"同源不变式最容易破的地方。
 *
 * - 底栏 / 抽屉 / 顶栏动作指向本地页面时，页面之间必须能**来回切换**；
 * - 访问子页面后**入口页**仍必须可解析（入口是首屏，丢不得）；
 * - 越界 / 目录 / 网址形态的 path 一律忽略，且**绝不**把内容区换成 URL 入口。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShellPreviewLocalPageRegressionTest {

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

    // ── 来回切换（多本地页面 tab 的主路径）──────────────────────────

    @Test
    fun localPage_canSwitchBackToAPreviouslyVisitedPage() = runTest(dispatcher) {
        directory.put(htmlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        vm.onBottomItemSelected(item("tab-about", ShellAction.OpenLocalPage("page/about.html")))
        vm.onBottomItemSelected(item("tab-contact", ShellAction.OpenLocalPage("page/contact.html")))
        assertEquals(
            "page/contact.html",
            (vm.uiState.value.runtimeConfig?.entry as? ProjectEntry.BundledHtml)?.entryFile,
        )

        // 再切回第一个页面：必须仍然取得到（而不是"点了没反应"）。
        vm.onBottomItemSelected(item("tab-about", ShellAction.OpenLocalPage("page/about.html")))

        assertEquals(
            "切回已访问过的本地页面必须仍可解析（多 tab 来回切是主路径）",
            "page/about.html",
            (vm.uiState.value.runtimeConfig?.entry as? ProjectEntry.BundledHtml)?.entryFile,
        )
        assertEquals(ABOUT_CONTENT, (vm.uiState.value.runtimeConfig?.entry as? ProjectEntry.BundledHtml)?.html)
    }

    @Test
    fun localPage_entryPageRemainsReachableAfterVisitingASubPage() = runTest(dispatcher) {
        directory.put(htmlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        vm.onBottomItemSelected(item("tab-sub", ShellAction.OpenLocalPage("page/about.html")))
        assertEquals(
            "page/about.html",
            (vm.uiState.value.runtimeConfig?.entry as? ProjectEntry.BundledHtml)?.entryFile,
        )

        // 回到首页（入口文件）：入口是首屏，访问过子页面后也必须还能回去。
        vm.onBottomItemSelected(item("tab-home", ShellAction.OpenLocalPage("index.html")))

        assertEquals(
            "入口页在访问子页面后必须仍可解析",
            "index.html",
            (vm.uiState.value.runtimeConfig?.entry as? ProjectEntry.BundledHtml)?.entryFile,
        )
        assertEquals(ENTRY_CONTENT, (vm.uiState.value.runtimeConfig?.entry as? ProjectEntry.BundledHtml)?.html)
    }

    @Test
    fun localPage_samePageTwice_isIdempotent() = runTest(dispatcher) {
        directory.put(htmlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()

        repeat(3) {
            vm.onBottomItemSelected(item("tab-about", ShellAction.OpenLocalPage("page/about.html")))
        }

        val entry = vm.uiState.value.runtimeConfig?.entry as ProjectEntry.BundledHtml
        assertEquals("page/about.html", entry.entryFile)
        assertEquals(ABOUT_CONTENT, entry.html)
    }

    // ── 隔离性：非法 / 网址形态 path 一律忽略 ────────────────────────

    @Test
    fun localPage_unsafeOrMissingPaths_areIgnoredAndNeverBecomeUrl() = runTest(dispatcher) {
        directory.put(htmlProject())
        val vm = ShellPreviewViewModel(projectId = 1L, directory = directory)
        advanceUntilIdle()
        val before = vm.uiState.value.runtimeConfig

        listOf(
            "ghost.html", // 不存在
            "../outside.html", // 越界相对路径
            "docs/", // 目录条目
            "https://example.com", // 网址形态（手动改 JSON 的脏数据）
        ).forEach { path ->
            vm.onBottomItemSelected(item("tab-x", ShellAction.OpenLocalPage(path)))
            assertEquals("path=$path 必须被忽略，内容区不变", before, vm.uiState.value.runtimeConfig)
            assertTrue(
                "path=$path 绝不得把内容区换成 URL 入口",
                vm.uiState.value.runtimeConfig?.entry is ProjectEntry.BundledHtml,
            )
        }
    }

    // ── 辅助 ──────────────────────────────────────────────────────────

    private fun item(id: String, action: ShellAction): ShellMenuItem =
        ShellMenuItem(id = id, title = "条目", action = action)

    private fun htmlProject(): Project = baseProject().copy(
        entry = ProjectEntry.BundledHtml(
            html = ENTRY_CONTENT,
            entryFile = "index.html",
            files = listOf(
                ProjectFile(name = "css/style.css", content = "body{}"),
                ProjectFile(name = "page/about.html", content = ABOUT_CONTENT),
                ProjectFile(name = "page/contact.html", content = CONTACT_CONTENT),
            ),
        ),
    )

    private fun baseProject(): Project = Project(
        id = 1L,
        projectType = ProjectType.HTML,
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
        const val ENTRY_CONTENT: String = "<html>入口</html>"
        const val ABOUT_CONTENT: String = "<html>关于</html>"
        const val CONTACT_CONTENT: String = "<html>联系</html>"
    }
}
