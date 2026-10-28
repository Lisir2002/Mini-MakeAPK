package com.make.apk.ui.shell

import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.make.apk.data.model.ShellDrawer
import com.make.apk.data.model.ShellDrawerWidth
import com.make.apk.data.model.ShellDrawerWidthMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 抽屉宽度解析（[resolveDrawerWidth]）的边界测试（阶段 6.5，纯 JVM）。
 *
 * 为什么单独测：宽度是**唯一带自定义数值**的抽屉字段，
 * 非法值（0 / 负数 / 超大）若不在解析层兜住，会直接变成"抽屉挤成一团"或"内容区被挤没"，
 * 而这类问题在截图上表现为布局塌陷，不易定位到具体字段。
 *
 * 档位数值由宿主注入（[ShellRenderEnv.drawerWidths]），故这里断言的是
 * **"取哪一档 / 如何兜底"** 的解析逻辑，而非某个固定 dp 值。
 */
class ShellDrawerWidthTest {

    // ── 三档固定宽度 ────────────────────────────────────────────────────

    @Test
    fun small_returnsSmallWidth() {
        assertEquals(WIDTHS.small, resolveDrawerWidth(drawer(mode = ShellDrawerWidthMode.SMALL), env()))
    }

    @Test
    fun medium_returnsMediumWidth() {
        assertEquals(WIDTHS.medium, resolveDrawerWidth(drawer(mode = ShellDrawerWidthMode.MEDIUM), env()))
    }

    @Test
    fun large_returnsLargeWidth() {
        assertEquals(WIDTHS.large, resolveDrawerWidth(drawer(mode = ShellDrawerWidthMode.LARGE), env()))
    }

    // ── 自定义档：合法值原样，非法值回落，超界值钳制 ──────────────────────

    @Test
    fun custom_validValue_isUsedAsIs() {
        assertEquals(
            280.dp,
            resolveDrawerWidth(drawer(mode = ShellDrawerWidthMode.CUSTOM, customDp = 280), env()),
        )
    }

    @Test
    fun custom_zero_fallsBackToMedium() {
        assertEquals(
            WIDTHS.medium,
            resolveDrawerWidth(drawer(mode = ShellDrawerWidthMode.CUSTOM, customDp = 0), env()),
        )
    }

    @Test
    fun custom_negative_fallsBackToMedium() {
        assertEquals(
            WIDTHS.medium,
            resolveDrawerWidth(drawer(mode = ShellDrawerWidthMode.CUSTOM, customDp = -10), env()),
        )
    }

    @Test
    fun custom_tooSmall_isClampedToMinimum() {
        assertEquals(
            WIDTHS.minDp.dp,
            resolveDrawerWidth(drawer(mode = ShellDrawerWidthMode.CUSTOM, customDp = 64), env()),
        )
    }

    @Test
    fun custom_tooLarge_isClampedToMaximum() {
        assertEquals(
            WIDTHS.maxDp.dp,
            resolveDrawerWidth(drawer(mode = ShellDrawerWidthMode.CUSTOM, customDp = 1000), env()),
        )
    }

    @Test
    fun custom_ignoredWhenModeIsNotCustom() {
        // 非 CUSTOM 档下 customDp 不应生效（避免"改了档位却还留着旧数值"的脏状态）。
        assertEquals(
            WIDTHS.small,
            resolveDrawerWidth(drawer(mode = ShellDrawerWidthMode.SMALL, customDp = 380), env()),
        )
    }

    @Test
    fun widthsFollowInjectedEnv() {
        // 换一套注入档位 → 解析结果整体跟随（导出壳换肤的基础）。
        val custom = ShellDrawerWidths(
            small = 100.dp,
            medium = 150.dp,
            large = 200.dp,
            minDp = 90,
            maxDp = 210,
        )
        assertEquals(100.dp, resolveDrawerWidth(drawer(ShellDrawerWidthMode.SMALL), env(custom)))
        assertEquals(150.dp, resolveDrawerWidth(drawer(ShellDrawerWidthMode.MEDIUM), env(custom)))
        assertEquals(200.dp, resolveDrawerWidth(drawer(ShellDrawerWidthMode.LARGE), env(custom)))
    }

    private fun drawer(mode: ShellDrawerWidthMode, customDp: Int = 0): ShellDrawer =
        ShellDrawer(width = ShellDrawerWidth(mode = mode, customDp = customDp))

    private companion object {
        /** 测试用档位（数值不参与断言，只用于验证"取哪一档"）。 */
        val WIDTHS = ShellDrawerWidths(
            small = 240.dp,
            medium = 300.dp,
            large = 360.dp,
            minDp = 200,
            maxDp = 400,
        )

        fun env(widths: ShellDrawerWidths = WIDTHS): ShellRenderEnv {
            val typography = Typography()
            return ShellRenderEnv(
                accent = Color.Unspecified,
                onAccent = Color.Unspecified,
                accentContainer = Color.Unspecified,
                onAccentContainer = Color.Unspecified,
                surface = Color.Unspecified,
                onSurface = Color.Unspecified,
                onSurfaceVariant = Color.Unspecified,
                outline = Color.Unspecified,
                typography = typography,
                selectedLabelStyle = typography.labelLarge,
                shapes = Shapes(),
                topBarHeight = 56.dp,
                bottomBarHeight = 80.dp,
                indicatorHeight = 2.dp,
                spaceXs = 3.dp,
                spaceSm = 6.dp,
                spaceMd = 12.dp,
                spaceLg = 24.dp,
                drawerWidths = widths,
            )
        }
    }
}
