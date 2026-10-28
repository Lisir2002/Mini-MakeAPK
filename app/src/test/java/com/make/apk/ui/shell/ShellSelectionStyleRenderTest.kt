package com.make.apk.ui.shell

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.make.apk.data.model.ShellIconKeys
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.data.model.ShellSelectionStyle
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.ShellTopTabs
import com.make.apk.ui.theme.MmaTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 顶栏 tab **选中态样式三档**的渲染效果补测（QA 独立补测 · 阶段 6.2）。
 *
 * ## 补什么（为什么必须在"渲染层"测，契约层测不出来）
 *
 * `ShellSelectionStyle` 有三档（[ShellSelectionStyle.INDICATOR] / [ShellSelectionStyle.PILL] /
 * [ShellSelectionStyle.TEXT]），评审明确要求"**三档都要生效**"。
 * 契约层只能证明"字段被正确读出"，**证明不了"三档画出来不一样"**——
 * 而后者才是用户能感知的东西（用户在 6.3 编辑器里选「文字」与选「指示器」必须看到差别）。
 *
 * 本测试用 Robolectric 原生图形模式把壳顶栏渲染成 [Bitmap] 后**逐像素比对**，
 * 把"三档必须互不相同"钉成可执行断言。
 *
 * 期望（依据设计文档 §1.2.4 与 `ShellTopTabsSection` 自身 KDoc）：
 * - [ShellSelectionStyle.INDICATOR]：底部一条强调色指示条（无药丸背景）；
 * - [ShellSelectionStyle.PILL]：药丸背景（**无指示条**）；
 * - [ShellSelectionStyle.TEXT]：仅文字颜色变化（**无背景、无指示条**）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellSelectionStyleRenderTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun indicatorStyle_differsFromTextStyle() {
        assertStylesDiffer(ShellSelectionStyle.INDICATOR, ShellSelectionStyle.TEXT)
    }

    @Test
    fun indicatorStyle_differsFromPillStyle() {
        assertStylesDiffer(ShellSelectionStyle.INDICATOR, ShellSelectionStyle.PILL)
    }

    @Test
    fun textStyle_differsFromPillStyle() {
        assertStylesDiffer(ShellSelectionStyle.TEXT, ShellSelectionStyle.PILL)
    }

    @Test
    fun allThreeStyles_areMutuallyDistinct() {
        val indicator = renderTopTabs(ShellSelectionStyle.INDICATOR)
        val pill = renderTopTabs(ShellSelectionStyle.PILL)
        val text = renderTopTabs(ShellSelectionStyle.TEXT)
        assertTrue(
            "三档选中样式必须两两不同（否则用户在 6.3 编辑器里选「文字」与选「指示器」毫无区别）",
            !indicator.sameAs(pill) && !pill.sameAs(text) && !indicator.sameAs(text),
        )
    }

    // ── 断言与渲染脚手架 ────────────────────────────────────────────────

    private fun assertStylesDiffer(a: ShellSelectionStyle, b: ShellSelectionStyle) {
        val bitmapA = renderTopTabs(a)
        val bitmapB = renderTopTabs(b)
        assertFalse(
            "$a 与 $b 两档渲染结果逐像素相同 —— 说明该档选中样式未生效",
            bitmapA.sameAs(bitmapB),
        )
    }

    /** 渲染只有顶栏 tab 的壳，返回其位图。 */
    private fun renderTopTabs(style: ShellSelectionStyle): Bitmap {
        // 用 activity 级 setContent（而非 rule 级）：同一测试内可多次重绘以比对两档。
        composeRule.activity.setContent {
            MmaTheme(darkTheme = false) {
                val spec = ShellSpec(
                    topTabs = ShellTopTabs(
                        enabled = true,
                        items = listOf(
                            ShellMenuItem(
                                id = "itm_home",
                                title = "首页",
                                iconRef = ShellIconKeys.HOME,
                                selected = true,
                            ),
                            ShellMenuItem(
                                id = "itm_discover",
                                title = "发现",
                                iconRef = ShellIconKeys.DISCOVER,
                            ),
                        ),
                        selectionStyle = style,
                    ),
                )
                ShellHost(spec = spec, title = "示例应用", onBack = {}) { contentModifier ->
                    Box(
                        modifier = contentModifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val view = composeRule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }
}
