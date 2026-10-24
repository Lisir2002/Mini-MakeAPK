package com.web2apk.app.ui.screens

import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.web2apk.app.ui.theme.Web2ApkTheme
import org.junit.Rule
import org.junit.Test

/**
 * WebView 封装屏「外壳」截图回归测试（Paparazzi，纯 JVM）。
 *
 * 注意：真实 WebView 无法在 JVM 上渲染网页内容，故注入 FrameLayout 桩仅验证
 * 组合接线（modifier / 主题绑定）不漂移。完整网页视觉验证走两条路（见 docs/qa/screenshot-testing.md）：
 *   1) Roborazzi（JVM + Robolectric shadow WebView，可截图外壳）；
 *   2) 设备/模拟器插桩截图（真正渲染网页，最权威）。
 */
class WebViewScreenTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun shellLight() = paparazzi.snapshot {
        Web2ApkTheme(darkTheme = false) {
            WebViewScreen(
                url = "https://example.com",
                webViewFactory = { FrameLayout(it) },
            )
        }
    }
}
