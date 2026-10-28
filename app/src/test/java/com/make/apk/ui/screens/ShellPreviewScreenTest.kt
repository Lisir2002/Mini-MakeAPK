package com.make.apk.ui.screens

import android.widget.FrameLayout
import app.cash.paparazzi.Paparazzi
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.ShellAction
import com.make.apk.data.model.ShellDrawer
import com.make.apk.data.model.ShellIconKeys
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.data.model.ShellPresets
import com.make.apk.data.model.VersionScheme
import com.make.apk.data.model.WebRuntimeConfig
import com.make.apk.data.model.WebSettings
import com.make.apk.ui.theme.MmaTheme
import com.make.apk.ui.viewmodel.ShellPreviewUiState
import org.junit.Rule
import org.junit.Test

/**
 * 壳预览屏（[ShellPreviewScreen]）截图回归（Paparazzi，纯 JVM）。
 *
 * 覆盖三种形态：
 * - **可预览**（URL 项目）：全屏壳 + 预置子模板选择条 + 内容区；
 * - **不可预览**（非 URL 项目）：居中可见提示（不静默失败、不崩）；
 * - **加载中**：居中加载提示。
 *
 * 真实 WebView 无法在 JVM 上渲染网页内容，故注入 FrameLayout 桩，仅验证组合接线不漂移。
 */
class ShellPreviewScreenTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun previewableUrlProject_light() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) { previewBody(state = previewableState()) }
    }

    @Test
    fun previewableUrlProject_dark() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) { previewBody(state = previewableState()) }
    }

    @Test
    fun previewableTopTabsSwipePager_light() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) { previewBody(state = swipedTopTabsState()) }
    }

    @Test
    fun previewableDrawerOpen_light() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) { previewBody(state = drawerOpenState()) }
    }

    @Test
    fun unsupportedNonUrlProject_light() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            previewBody(
                state = ShellPreviewUiState(
                    loading = false,
                    previewable = false,
                    apkName = "粘贴的网页",
                    unsupportedMessage = "当前仅支持 URL 项目预览",
                ),
            )
        }
    }

    @Test
    fun loading_light() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            previewBody(state = ShellPreviewUiState(loading = true))
        }
    }

    @androidx.compose.runtime.Composable
    private fun previewBody(state: ShellPreviewUiState) {
        ShellPreviewScreen(
            state = state,
            onBack = {},
            onTopTabSelected = {},
            onBottomItemSelected = {},
            onDrawerItemSelected = {},
            onDrawerOpenChanged = {},
            onShellAction = {},
            webViewFactory = { FrameLayout(it) },
        )
    }

    private fun previewableState(): ShellPreviewUiState = ShellPreviewUiState(
        loading = false,
        previewable = true,
        apkName = "示例应用",
        runtimeConfig = SAMPLE_CONFIG,
        shellSpec = ShellPresets.TABBED.spec,
        currentUrl = "https://example.com",
        isExampleContent = true,
    )

    /** 顶栏 tab 开启滑动切页（swipeEnabled = true）→ 内容区走 HorizontalPager 分支。 */
    private fun swipedTopTabsState(): ShellPreviewUiState {
        val base = ShellPresets.TABBED.spec
        val spec = base.copy(topTabs = base.topTabs.copy(swipeEnabled = true))
        return ShellPreviewUiState(
            loading = false,
            previewable = true,
            apkName = "示例应用",
            runtimeConfig = SAMPLE_CONFIG,
            shellSpec = spec,
            selectedTopTabId = spec.topTabs.items.first().id,
            currentUrl = "https://example.com",
            isExampleContent = true,
        )
    }

    /** 抽屉开启：抽屉区开启且有菜单项 → 顶栏出现汉堡入口，抽屉内容可见。 */
    private fun drawerOpenState(): ShellPreviewUiState {
        val base = ShellPresets.CLASSIC.spec
        val spec = base.copy(
            drawer = ShellDrawer(
                enabled = true,
                items = listOf(
                    ShellMenuItem(
                        id = "itm_drawer_home",
                        title = "首页",
                        iconRef = ShellIconKeys.HOME,
                        action = ShellAction.NavigateUrl(ShellPresets.EXAMPLE_URL),
                        selected = true,
                    ),
                    ShellMenuItem(
                        id = "itm_drawer_settings",
                        title = "设置",
                        iconRef = ShellIconKeys.PROFILE,
                        action = ShellAction.NavigateUrl(ShellPresets.EXAMPLE_URL_SECOND),
                    ),
                ),
            ),
        )
        return ShellPreviewUiState(
            loading = false,
            previewable = true,
            apkName = "示例应用",
            runtimeConfig = SAMPLE_CONFIG,
            shellSpec = spec,
            selectedDrawerItemId = "itm_drawer_home",
            drawerOpen = true,
            contentPaused = true,
            currentUrl = "https://example.com",
            isExampleContent = true,
        )
    }

    private companion object {
        val SAMPLE_CONFIG = WebRuntimeConfig(
            projectType = ProjectType.URL,
            apkName = "示例应用",
            packageName = "com.example.sample",
            versionScheme = VersionScheme.THREE_SEGMENT,
            versionName = "1.0.0",
            versionCode = 1,
            themeColor = 0xFF2563EB.toInt(),
            entry = ProjectEntry.Url("https://example.com"),
            webSettings = WebSettings(),
            isolationId = "webapp_preview",
        )
    }
}
