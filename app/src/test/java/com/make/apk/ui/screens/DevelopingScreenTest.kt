package com.make.apk.ui.screens

import androidx.compose.runtime.Composable
import app.cash.paparazzi.Paparazzi
import com.make.apk.ui.navigation.DefaultMmaTabs
import com.make.apk.ui.navigation.MmaRoute
import com.make.apk.ui.components.MmaBottomBar
import com.make.apk.ui.theme.MmaTheme
import org.junit.Rule
import org.junit.Test

/**
 * 开发中页（优雅占位）截图回归（Paparazzi，纯 JVM）。
 *
 * 亮/暗两态：钉「居中图标 + 现状文案 + 路线图预告卡」构图不漂移。
 */
class DevelopingScreenTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun light() = paparazzi.snapshot {
        developingBody()
    }

    @Test
    fun dark() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) { developingBody() }
    }

    @Composable
    private fun developingBody() {
        DevelopingScreen(
            bottomBar = {
                MmaBottomBar(
                    tabs = DefaultMmaTabs,
                    currentRoute = MmaRoute.Developing.route,
                    onTabSelected = {},
                )
            },
        )
    }
}
