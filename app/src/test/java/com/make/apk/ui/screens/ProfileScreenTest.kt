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
 * 我的页（个人中心）截图回归（Paparazzi，纯 JVM）。
 *
 * 亮/暗两态：钉「信息卡 + 四条数据驱动条目」构图不漂移。
 * 存储状态行在 Paparazzi 下读 MmaStorage.isAuthorized（layoutlib 环境未授权，
 * 走「未授权」分支——真实设备授权后文案自动切换，无需另录 golden）。
 */
class ProfileScreenTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun light() = paparazzi.snapshot {
        profileBody()
    }

    @Test
    fun dark() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) { profileBody() }
    }

    @Composable
    private fun profileBody() {
        ProfileScreen(
            bottomBar = {
                MmaBottomBar(
                    tabs = DefaultMmaTabs,
                    currentRoute = MmaRoute.Profile.route,
                    onTabSelected = {},
                )
            },
            onFeatureNotReady = {},
        )
    }
}
