package com.make.apk.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.Paparazzi
import com.make.apk.data.model.ShellAction
import com.make.apk.data.model.ShellDrawer
import com.make.apk.data.model.ShellDrawerSource
import com.make.apk.data.model.ShellIconKeys
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.data.model.ShellPresets
import com.make.apk.data.model.ShellSpec
import com.make.apk.ui.theme.MmaTheme
import org.junit.Rule
import org.junit.Test

/**
 * 壳渲染器（[ShellHost]）截图回归（Paparazzi，纯 JVM）。
 *
 * 覆盖：
 * - **四区形态**：默认壳（四区全关）→ 裸内容区；全开壳 → 顶栏 + 顶栏 tab + 内容 + 底栏；
 * - **三套预置子模板**：经典款 / 极简款 / 内容款；
 * - 亮 / 暗两态。
 *
 * 这里的快照是"壳形态"的视觉基线；导出壳（阶段 6.4+）复用同一份 `ShellSpec`，
 * 未来做"预览 == 导出"对照时以这些快照为参照。
 */
class ShellHostTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    // ── 四区形态 ────────────────────────────────────────────────────────

    @Test
    fun defaultSpec_allZonesOff_light() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) { shellBody(spec = ShellSpec.DEFAULT) }
    }

    @Test
    fun defaultSpec_allZonesOff_dark() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) { shellBody(spec = ShellSpec.DEFAULT) }
    }

    @Test
    fun allZonesOn_light() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) { shellBody(spec = ShellPresets.TABBED.spec) }
    }

    @Test
    fun allZonesOn_dark() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) { shellBody(spec = ShellPresets.TABBED.spec) }
    }

    // ── 三套预置子模板 ──────────────────────────────────────────────────

    @Test
    fun presetClassic_light() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) { shellBody(spec = ShellPresets.CLASSIC.spec) }
    }

    @Test
    fun presetMinimal_light() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) { shellBody(spec = ShellPresets.MINIMAL.spec) }
    }

    @Test
    fun presetTabbed_light() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) { shellBody(spec = ShellPresets.TABBED.spec) }
    }

    // ── 抽屉（阶段 6.5） ────────────────────────────────────────────────

    @Test
    fun drawerClosed_light() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) { drawerBody(open = false) }
    }

    @Test
    fun drawerOpen_native_light() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) { drawerBody(open = true) }
    }

    @Test
    fun drawerOpen_webSource_light() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            drawerBody(open = true, source = ShellDrawerSource.WEB)
        }
    }

    @Composable
    private fun drawerBody(
        open: Boolean,
        source: ShellDrawerSource = ShellDrawerSource.NATIVE,
    ) {
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
                        id = "itm_drawer_discover",
                        title = "发现",
                        iconRef = ShellIconKeys.DISCOVER,
                        action = ShellAction.NavigateUrl(ShellPresets.EXAMPLE_URL_SECOND),
                    ),
                    ShellMenuItem(
                        id = "itm_drawer_profile",
                        title = "我的",
                        iconRef = ShellIconKeys.PROFILE,
                        action = ShellAction.NavigateUrl(ShellPresets.EXAMPLE_URL_THIRD),
                    ),
                ),
                contentSource = source,
                webMenuUrl = WEB_MENU_URL,
            ),
        )
        ShellHost(
            spec = spec,
            title = "示例应用",
            onBack = {},
            drawerOpen = open,
            selectedDrawerItemId = "itm_drawer_home",
        ) { contentModifier ->
            Box(
                modifier = contentModifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
        }
    }

    private companion object {
        /** Web 覆盖层占位卡片上展示的目标地址。 */
        const val WEB_MENU_URL: String = "https://example.com/menu"
    }

    @Composable
    private fun shellBody(spec: ShellSpec) {
        ShellHost(
            spec = spec,
            title = "示例应用",
            onBack = {},
        ) { contentModifier ->
            Box(
                modifier = contentModifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
        }
    }
}
