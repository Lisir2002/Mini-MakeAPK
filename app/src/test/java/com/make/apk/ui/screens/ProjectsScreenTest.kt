package com.make.apk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.cash.paparazzi.Paparazzi
import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.VersionScheme
import com.make.apk.ui.components.MmaBottomBar
import com.make.apk.ui.navigation.DefaultMmaTabs
import com.make.apk.ui.navigation.MmaRoute
import com.make.apk.ui.theme.MmaTheme
import org.junit.Rule
import org.junit.Test

/**
 * 项目页（纯列表 · `Project` 模型）截图回归（Paparazzi，纯 JVM）。
 *
 * 覆盖纯列表构图的四种形态：亮/暗 × 空态 / 有项目。
 * 空态文件夹图标 + 引导文案；有项目态钉「图标 + 名称 + 类型·版本 + 删除」行。
 * 右上角「＋」新建入口是交互态，不在静态基线范围内。
 *
 * 另钉删除二次确认弹窗的内容（亮/暗）：弹窗是破坏性操作的唯一闸门，
 * 其**交互正确性由 `ProjectsScreenDeleteConfirmTest` 覆盖**，这里只钉视觉。
 */
class ProjectsScreenTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun lightEmpty() = paparazzi.snapshot {
        projectsBody(projects = emptyList())
    }

    @Test
    fun darkEmpty() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) { projectsBody(projects = emptyList()) }
    }

    @Test
    fun lightWithRecords() = paparazzi.snapshot {
        projectsBody(projects = demoProjects())
    }

    @Test
    fun darkWithRecords() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) { projectsBody(projects = demoProjects()) }
    }

    // ── 删除二次确认弹窗（内容层，无 window 外壳）───────────────────────

    @Test
    fun deleteConfirmDialogLight() = paparazzi.snapshot {
        deleteConfirmBody(darkTheme = false)
    }

    @Test
    fun deleteConfirmDialogDark() = paparazzi.snapshot {
        deleteConfirmBody(darkTheme = true)
    }

    @Composable
    private fun projectsBody(projects: List<Project>) {
        ProjectsScreen(
            projects = projects,
            onOpenProject = {},
            onDeleteProject = {},
            bottomBar = {
                MmaBottomBar(
                    tabs = DefaultMmaTabs,
                    currentRoute = MmaRoute.Projects.route,
                    onTabSelected = {},
                )
            },
            // 与真实页面一致：右上角「＋」存在（空态文案也指向它）
            onCreateProject = {},
        )
    }

    @Composable
    private fun deleteConfirmBody(darkTheme: Boolean) {
        MmaTheme(darkTheme = darkTheme) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                DeleteConfirmContent(
                    apkName = "示例网址",
                    onConfirm = {},
                    onDismiss = {},
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
