package com.make.apk.ui.screens

import android.content.Context
import androidx.activity.ComponentActivity
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * 外链执行边缘（[openExternal]）契约测试（W2 ④ QA 补测）。
 *
 * 钉住「绝不崩溃」「scheme 白名单」「成功即拉起 ACTION_VIEW + CATEGORY_BROWSABLE」
 * 三条核心契约：
 * - 合法 https 地址 → startActivity 被调用，Intent 动作与类别正确（双侧共用本文件，
 *   预览 == 导出同一边缘）；
 * - 空地址 → 直接失败返回 false，不发起任何 Intent；
 * - `file:///` 与 `intent://` → scheme 白名单拒绝，返回 false 且不发起 Intent
 *   （QA Minor-F1：白名单收口在 openExternal 单点，三个调用点全经过这里）。
 */
@RunWith(RobolectricTestRunner::class)
class ExternalUrlLauncherTest {

    @Test
    fun httpsUrl_launchesActionViewWithBrowsableCategory() {
        val context = Robolectric.setupActivity(ComponentActivity::class.java)
        val launched = openExternal(context, "https://example.com/page")

        assertTrue("合法 https 地址应成功拉起", launched)
        val intent = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(
            "必须携带 CATEGORY_BROWSABLE（限制交给可安全浏览的处理器）",
            intent.categories.contains(Intent.CATEGORY_BROWSABLE),
        )
        assertEquals("https://example.com/page", intent.data.toString())
    }

    @Test
    fun blankUrl_failsWithoutLaunching() {
        val context = Robolectric.setupActivity(ComponentActivity::class.java)
        assertFalse("空串应直接失败", openExternal(context, ""))
        assertFalse("空白串应直接失败", openExternal(context, "   "))
        assertEquals("失败路径不得发出任何 Intent", null, shadowOf(context).nextStartedActivity)
    }

    @Test
    fun fileSchemeUrl_isRejectedWithoutLaunching() {
        // file:// 交给系统处理器 = 导出壳内页资产暴露给任意 App，必须拒绝（走 WebView 承载）。
        val context = Robolectric.setupActivity(ComponentActivity::class.java)
        assertFalse(
            "file:/// 应被 scheme 白名单拒绝",
            openExternal(context, "file:///data/local/tmp/evil.html"),
        )
        assertEquals("被拒的 scheme 不得发出任何 Intent", null, shadowOf(context).nextStartedActivity)
    }

    @Test
    fun intentSchemeUrl_isRejectedWithoutLaunching() {
        // intent:// 是 Chrome 私有深链语法，可绕过浏览器直达任意组件，必须拒绝。
        val context = Robolectric.setupActivity(ComponentActivity::class.java)
        assertFalse(
            "intent:// 应被 scheme 白名单拒绝",
            openExternal(context, "intent://example.com#Intent;scheme=https;end"),
        )
        assertEquals("被拒的 scheme 不得发出任何 Intent", null, shadowOf(context).nextStartedActivity)
    }
}
