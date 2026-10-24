package com.make.apk.ui.screens

import app.cash.paparazzi.Paparazzi
import com.make.apk.ui.theme.MmaTheme
import org.junit.Rule
import org.junit.Test

/**
 * 首屏截图回归测试（Paparazzi，纯 JVM）。
 * 覆盖：亮色空态 / 暗色空态 / 亮色已填入 URL 三种关键视觉，锁定「可爱简约·iOS 风」首屏布局不漂移。
 */
class HomeScreenTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun lightEmpty() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            HomeScreen(onBuild = {})
        }
    }

    @Test
    fun darkEmpty() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) {
            HomeScreen(onBuild = {})
        }
    }

    @Test
    fun lightFilled() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            HomeScreen(onBuild = {}, initialInput = "https://example.com")
        }
    }
}
