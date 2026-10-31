package com.make.apk.webcore.config

import android.app.Application
import android.content.Context
import android.os.Build
import com.make.apk.webcore.diagnostics.WebViewEventHook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

/**
 * 预览态存储隔离测试（W4 T05，拍板 2 契约：first-wins + 告警埋点）。
 *
 * Robolectric 提供 WebView.setDataDirectorySuffix 的 shadow；两个 SDK 档位分别覆盖：
 * - API 28+：真设置后缀 + first-wins 冲突告警；
 * - API 24-27：平台不支持 → unsupported 告警 + first-wins 占位（防重复告警）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WebViewStorageIsolationTest {

    private val events = mutableListOf<Pair<String, Map<String, Any?>>>()
    private var context: Context? = null

    @Before
    fun setUp() {
        WebViewStorageIsolation.resetForTest()
        events.clear()
        WebViewEventHook.reporter = { event, fields -> events.add(event to fields) }
        context = org.robolectric.RuntimeEnvironment.getApplication()
    }

    @After
    fun tearDown() {
        WebViewEventHook.reporter = null
        WebViewStorageIsolation.resetForTest()
    }

    @Test
    @Config(sdk = [28])
    fun `first caller wins on api 28 plus`() {
        WebViewStorageIsolation.ensureApplied(context!!, "webapp_item1")
        // 首次调用：无任何告警（设置成功）。
        assertTrue(events.isEmpty())
        // 后到者（不同 isolationId）：first-wins 回落共享 + conflict 告警。
        WebViewStorageIsolation.ensureApplied(context!!, "webapp_item2")
        assertEquals(1, events.size)
        val (event, fields) = events[0]
        assertEquals(WebViewStorageIsolation.EVENT_CONFLICT, event)
        assertEquals("webapp_item1", fields["first"])
        assertEquals("webapp_item2", fields["request"])
        // 同 isolationId 幂等重入：无重复告警。
        events.clear()
        WebViewStorageIsolation.ensureApplied(context!!, "webapp_item1")
        assertTrue(events.isEmpty())
    }

    @Test
    @Config(sdk = [24])
    fun `unsupported platform warns once and keeps first-wins placeholder`() {
        WebViewStorageIsolation.ensureApplied(context!!, "webapp_item1")
        assertEquals(1, events.size)
        val (event, fields) = events[0]
        assertEquals(WebViewStorageIsolation.EVENT_UNSUPPORTED, event)
        assertEquals(Build.VERSION.SDK_INT, fields["sdk"])
        // first-wins 占位生效：后续调用不再重复告警。
        events.clear()
        WebViewStorageIsolation.ensureApplied(context!!, "webapp_item2")
        assertTrue(events.isEmpty())
    }

    @Test
    fun `suffix is sanitized to safe characters`() {
        assertEquals("MakeApk_20260101_120000_item3", WebViewStorageIsolation.sanitizeSuffix("MakeApk_20260101_120000_item3"))
        // 路径注入字符替换为下划线。
        assertEquals("a_b_c", WebViewStorageIsolation.sanitizeSuffix("a/b/c"))
        assertEquals("dot_dot", WebViewStorageIsolation.sanitizeSuffix("dot.dot"))
        // 限长 64。
        assertEquals(64, WebViewStorageIsolation.sanitizeSuffix("x".repeat(100)).length)
        // 全空白兜底。
        assertEquals("webapp_default", WebViewStorageIsolation.sanitizeSuffix(""))
    }
}
