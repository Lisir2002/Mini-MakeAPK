package com.make.apk.ui.viewmodel

import com.make.apk.data.model.ProjectFile
import com.make.apk.data.model.ProjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 编辑页状态派生「本地页面候选」（[ProjectEditUiState.localPageFileNames]）测试。
 *
 * 钉三件事：
 * 1. **入口在前 + 只收页面文件**：入口文件是第一候选，附加文件里只有 `.html` / `.htm`
 *    入选（CSS / JS / 图片不可作为加载目标）；
 * 2. **URL 项目无本地页面**：既无入口源码也无页面文件 → 空集（编辑器据此禁用该模式）；
 * 3. **无重复**：入口与附加文件同名时不重复出现。
 */
class ProjectEditLocalPagesTest {

    @Test
    fun htmlProject_entryFirst_thenHtmlFilesOnly() {
        val state = ProjectEditUiState(
            projectType = ProjectType.HTML,
            entryFile = "index.html",
            entryHtml = "<html></html>",
            projectFiles = listOf(
                ProjectFile("css/style.css", "body{}"),
                ProjectFile("page/about.html", "<html></html>"),
                ProjectFile("app.js", "console.log()"),
                ProjectFile("legacy.htm", "<html></html>"),
            ),
        )
        assertEquals(
            listOf("index.html", "page/about.html", "legacy.htm"),
            state.localPageFileNames,
        )
    }

    @Test
    fun urlProject_hasNoLocalPages() {
        val state = ProjectEditUiState(
            projectType = ProjectType.URL,
            entryUrl = "https://example.com",
        )
        assertTrue("URL 项目没有本地页面", state.localPageFileNames.isEmpty())
    }

    @Test
    fun htmlProjectWithoutEntrySource_doesNotListBlankEntry() {
        // 入口源码为空 → 入口文件不可选（渲染出来是空白页），仅列真正有内容的附加页面。
        val state = ProjectEditUiState(
            projectType = ProjectType.HTML,
            entryFile = "index.html",
            entryHtml = "",
            projectFiles = listOf(ProjectFile("page/about.html", "<html></html>")),
        )
        assertEquals(listOf("page/about.html"), state.localPageFileNames)
    }

    @Test
    fun entryFileAppearsOnlyOnce_whenAlsoListedAsFile() {
        val state = ProjectEditUiState(
            projectType = ProjectType.HTML,
            entryFile = "main.html",
            entryHtml = "<html></html>",
            projectFiles = listOf(
                ProjectFile("main.html", "<html>附加里的同名</html>"),
                ProjectFile("page/about.html", "<html></html>"),
            ),
        )
        assertEquals(listOf("main.html", "page/about.html"), state.localPageFileNames)
    }

    @Test
    fun blankEntryFile_fallsBackToDefaultEntryName() {
        val state = ProjectEditUiState(
            projectType = ProjectType.HTML,
            entryFile = "   ",
            entryHtml = "<html></html>",
        )
        assertEquals(listOf("index.html"), state.localPageFileNames)
    }

    @Test
    fun isLocalPageFileName_acceptsHtmlAndHtmOnly() {
        assertTrue(isLocalPageFileName("index.html"))
        assertTrue(isLocalPageFileName("page/about.HTML"))
        assertTrue(isLocalPageFileName("legacy.htm"))
        assertTrue(!isLocalPageFileName("style.css"))
        assertTrue(!isLocalPageFileName("app.js"))
        assertTrue(!isLocalPageFileName("logo.png"))
        assertTrue(!isLocalPageFileName("README"))
    }
}
