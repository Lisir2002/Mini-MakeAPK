package com.make.apk.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.Paparazzi
import com.make.apk.ui.navigation.DefaultMmaTabs
import com.make.apk.ui.navigation.MmaRoute
import com.make.apk.ui.theme.MmaTheme
import com.make.apk.ui.theme.Spacing
import org.junit.Rule
import org.junit.Test

/**
 * 三段式骨架截图回归（Paparazzi，纯 JVM）。
 *
 * 锁定「顶栏 + 主页面 + 底栏」槽位嵌入布局的构图不漂移：
 * 亮/暗 × 仅标题 / 带返回 / 带底栏 六种组合，覆盖顶级页与流程子页的全部 chrome 形态。
 * 内容哨兵文本必须紧贴 innerPadding 下沿——若 content 忘记消费 innerPadding，
 * 文本会被顶栏盖住，本组基线即报警（Scaffold 第一常见错误的视觉防线）。
 */
class MmaScreenTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun lightTitleOnly() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) { demoBody(title = "页面标题") }
    }

    @Test
    fun darkTitleOnly() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) { demoBody(title = "页面标题") }
    }

    @Test
    fun lightWithBack() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            demoBody(title = "子页面", onBack = {})
        }
    }

    @Test
    fun darkWithBack() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) {
            demoBody(title = "子页面", onBack = {})
        }
    }

    @Test
    fun lightWithBottomBar() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            demoBody(
                title = "MMA",
                bottomBar = {
                    MmaBottomBar(
                        tabs = DefaultMmaTabs,
                        currentRoute = MmaRoute.Home.route,
                        onTabSelected = {},
                    )
                },
            )
        }
    }

    @Test
    fun darkWithBottomBar() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) {
            demoBody(
                title = "MMA",
                bottomBar = {
                    MmaBottomBar(
                        tabs = DefaultMmaTabs,
                        currentRoute = MmaRoute.Home.route,
                        onTabSelected = {},
                    )
                },
            )
        }
    }

    /** 骨架用法示例：内容消费 innerPadding（真实页面照此接入）。 */
    @Composable
    private fun demoBody(
        title: String,
        onBack: (() -> Unit)? = null,
        bottomBar: @Composable () -> Unit = {},
    ) {
        MmaScreen(
            title = title,
            onBack = onBack,
            bottomBar = bottomBar,
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(Spacing.space5),
            ) {
                Text(
                    text = "内容区（已消费 innerPadding）",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
