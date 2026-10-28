package com.make.apk.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.VersionScheme
import com.make.apk.ui.theme.MmaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 列表页删除的**交互回归**（点击路径，Paparazzi 覆盖不到）。
 *
 * 为什么必须：仓储 `delete` 连隔离目录一并清理且无撤销，是整页破坏性最强的入口。
 * 静态快照只能证明"弹窗长什么样"，证明不了"单击不删、确认才删"——
 * 后者一旦回归（又把行内 `onDelete` 直接接到删除上）没有任何测试会红。
 *
 * 钉死四条：无删除回调时不渲染删除入口 / 单击只弹窗不落库 / 点确认才删且删对 id / 点取消不删。
 *
 * 运行环境：`createComposeRule()` 跑在 Robolectric 上（`ui-test-junit4` +
 * `ui-test-manifest`），无需模拟器，与既有 Robolectric 启动测试同一套配置。
 */
@RunWith(RobolectricTestRunner::class)
class ProjectsScreenDeleteConfirmTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── 删除二次确认（破坏性操作的唯一闸门）────────────────────────────

    @Test
    fun clickDelete_showsConfirmDialog_andDoesNotDeleteYet() {
        val deleted = mutableListOf<Long>()

        setProjectsScreen(onDeleteProject = { id -> deleted += id })

        // 列表里有两个项目 ⇒ 行内「删除」出现两次（弹窗按钮文案是「确认删除」，不参与精确匹配）
        composeRule.onAllNodesWithText("删除")[0].performClick()

        composeRule.onNodeWithText("删除项目").assertIsDisplayed()
        composeRule.onNodeWithText("确认删除").assertIsDisplayed()
        composeRule.onNodeWithText("取消").assertIsDisplayed()
        assertTrue("仅单击行内删除不得落库", deleted.isEmpty())
    }

    @Test
    fun confirmDelete_deletesThatProjectOnly() {
        val deleted = mutableListOf<Long>()

        setProjectsScreen(onDeleteProject = { id -> deleted += id })

        composeRule.onAllNodesWithText("删除")[1].performClick()
        composeRule.onNodeWithText("确认删除").performClick()

        assertEquals("确认后必须删除，且只删被点的那一个", listOf(2L), deleted)
        composeRule.onNodeWithText("删除项目").assertDoesNotExist()
    }

    @Test
    fun cancelDelete_doesNotDelete() {
        val deleted = mutableListOf<Long>()

        setProjectsScreen(onDeleteProject = { id -> deleted += id })

        composeRule.onAllNodesWithText("删除")[0].performClick()
        composeRule.onNodeWithText("取消").performClick()

        assertTrue("取消后不得落库", deleted.isEmpty())
        composeRule.onNodeWithText("删除项目").assertDoesNotExist()
    }

    @Test
    fun noDeleteCallback_rowDeleteButtonIsAbsent() {
        setProjectsScreen(onDeleteProject = null)

        composeRule.onNodeWithText("删除").assertDoesNotExist()
    }

    // ── 辅助 ─────────────────────────────────────────────────────────────

    private fun setProjectsScreen(onDeleteProject: ((Long) -> Unit)?) {
        composeRule.setContent {
            MmaTheme {
                ProjectsScreen(
                    projects = demoProjects(),
                    onOpenProject = {},
                    onDeleteProject = onDeleteProject,
                    onCreateProject = {},
                )
            }
        }
    }

    private fun demoProjects(): List<Project> = listOf(
        Project(
            id = 1,
            projectType = ProjectType.URL,
            apkName = "示例网址",
            packageName = "com.example.web",
            versionScheme = VersionScheme.THREE_SEGMENT,
            versionName = "1.0.0",
            versionCode = 1,
            themeColor = 0xFF2563EB.toInt(),
            entry = ProjectEntry.Url("https://example.com"),
            isolationId = "webapp_1",
            createdAt = 1_700_000_000_000,
            updatedAt = 1_700_000_060_000,
        ),
        Project(
            id = 2,
            projectType = ProjectType.HTML,
            apkName = "粘贴的网页",
            packageName = "com.example.paste",
            versionScheme = VersionScheme.TWO_SEGMENT,
            versionName = "1.0",
            versionCode = 2,
            themeColor = 0xFF7FA8C9.toInt(),
            entry = ProjectEntry.BundledHtml("<html><body>你好</body></html>"),
            isolationId = "webapp_2",
            createdAt = 1_700_000_100_000,
            updatedAt = 1_700_000_180_000,
        ),
    )
}
