package com.make.apk.ui.screens

import androidx.compose.runtime.Composable
import app.cash.paparazzi.Paparazzi
import com.make.apk.data.model.BuildEngine
import com.make.apk.data.model.BuildInputType
import com.make.apk.data.model.BuildRecord
import com.make.apk.data.model.BuildStatus
import com.make.apk.ui.navigation.DefaultMmaTabs
import com.make.apk.ui.navigation.MmaRoute
import com.make.apk.ui.components.MmaBottomBar
import com.make.apk.ui.theme.MmaTheme
import org.junit.Rule
import org.junit.Test

/**
 * 项目页（封装工作台）截图回归（Paparazzi，纯 JVM）。
 *
 * 覆盖「输入区 + 列表」合一构图的四种形态：亮/暗 × 空态 / 有记录。
 * 空态图钉图标 + 引导文案；有记录态钉项目卡片（状态 + 预览/删除操作）。
 */
class ProjectsScreenTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun lightEmpty() = paparazzi.snapshot {
        projectsBody(records = emptyList())
    }

    @Test
    fun darkEmpty() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) { projectsBody(records = emptyList()) }
    }

    @Test
    fun lightWithRecords() = paparazzi.snapshot {
        projectsBody(records = demoRecords())
    }

    @Test
    fun darkWithRecords() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) { projectsBody(records = demoRecords()) }
    }

    @Composable
    private fun projectsBody(records: List<BuildRecord>) {
        ProjectsScreen(
            records = records,
            onBuild = {},
            onOpen = {},
            onDelete = {},
            bottomBar = {
                MmaBottomBar(
                    tabs = DefaultMmaTabs,
                    currentRoute = MmaRoute.Projects.route,
                    onTabSelected = {},
                )
            },
        )
    }

    private fun demoRecords(): List<BuildRecord> = listOf(
        BuildRecord(
            id = 1,
            createdAt = 1_700_000_000_000,
            updatedAt = 1_700_000_060_000,
            inputType = BuildInputType.URL,
            sourceLabel = "example.com",
            sourceRef = "https://example.com",
            engine = BuildEngine.DEVICE_WEBVIEW,
            status = BuildStatus.SUCCESS,
        ),
        BuildRecord(
            id = 2,
            createdAt = 1_700_000_100_000,
            updatedAt = 1_700_000_180_000,
            inputType = BuildInputType.HTML,
            sourceLabel = "粘贴的 HTML",
            sourceRef = "<html>粘贴的 HTML</html>",
            engine = BuildEngine.DEVICE_WEBVIEW,
            status = BuildStatus.FAILED,
        ),
    )
}
