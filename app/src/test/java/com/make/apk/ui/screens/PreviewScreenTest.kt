package com.make.apk.ui.screens

import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.make.apk.data.model.BuildEngine
import com.make.apk.data.model.BuildInputType
import com.make.apk.data.model.BuildRecord
import com.make.apk.data.model.BuildStatus
import com.make.apk.ui.theme.MmaTheme
import org.junit.Rule
import org.junit.Test

/**
 * 预览页「外壳」截图回归。
 *
 * 与 `WebViewScreenTest` 同理：真实 WebView 无法在 JVM 渲染网页，
 * 故注入 FrameLayout 桩，只锁定**外壳构图**（顶栏返回 + 内容区占比）不漂移。
 * 预览是流程子页：顶栏带返回箭头、无底栏，亮/暗两态均入基线。
 */
class PreviewScreenTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val record = BuildRecord(
        id = 1,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_005_000L,
        inputType = BuildInputType.URL,
        sourceLabel = "example.com",
        sourceRef = "https://example.com",
        engine = BuildEngine.DEVICE_WEBVIEW,
        status = BuildStatus.SUCCESS,
        outputApkPath = "/data/local/tmp/mma/app.apk",
    )

    @Test
    fun shellLight() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            PreviewScreen(
                record = record,
                onBack = {},
                webViewFactory = { FrameLayout(it) },
            )
        }
    }

    @Test
    fun shellDark() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) {
            PreviewScreen(
                record = record,
                onBack = {},
                webViewFactory = { FrameLayout(it) },
            )
        }
    }
}
