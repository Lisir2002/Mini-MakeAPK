package com.make.apk.ui.screens

import app.cash.paparazzi.Paparazzi
import com.make.apk.data.model.BuildEngine
import com.make.apk.data.model.BuildInputType
import com.make.apk.data.model.BuildRecord
import com.make.apk.data.model.BuildStatus
import com.make.apk.ui.theme.MmaTheme
import org.junit.Rule
import org.junit.Test

/**
 * 构建中页面截图回归（Paparazzi，纯 JVM）。
 *
 * 覆盖亮/暗两态：进度动效与来源名在两种主题下都不能溢出或对比度不足。
 * 动效本身是无限动画，Paparazzi 会冻结在初始帧，因此本测试锁的是**构图**而非动画过程。
 */
class BuildingScreenTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val running = BuildRecord(
        id = 1,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_001_000L,
        inputType = BuildInputType.URL,
        sourceLabel = "example.com",
        sourceRef = "https://example.com",
        engine = BuildEngine.DEVICE_WEBVIEW,
        status = BuildStatus.RUNNING,
    )

    @Test
    fun lightRunning() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            BuildingScreen(record = running, onCancel = {})
        }
    }

    @Test
    fun darkRunning() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) {
            BuildingScreen(record = running, onCancel = {})
        }
    }
}
