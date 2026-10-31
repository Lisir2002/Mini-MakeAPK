package com.make.apk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.Paparazzi
import com.make.apk.data.model.LogEntry
import com.make.apk.ui.theme.MmaTheme
import com.make.apk.ui.theme.Spacing
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/**
 * 日志详情内容截图回归（Paparazzi，纯 JVM）。
 *
 * 为什么只截 [LogDetailContent] 而不截 `ModalBottomSheet`：
 * M3 的 ModalBottomSheet 依赖独立 window 与动画，layoutlib 下渲染不稳定；
 * 把内容抽成纯 Composable 后，这张快照才能真正守住视觉。
 *
 * **此前日志详情 0 张基线**——所以"INFO 与 DEBUG 徽标同色""WARN 用品牌蓝"
 * "徽标全圆药丸""分区标题与字段名同层级"这些缺陷全部上线而无人察觉。
 * 取样条目刻意选 WARN 级别，落在修复前的重灾区：琥珀容器、6dp 圆角、
 * 顶部动作行、标题与字段名的字号/字重差，任一回归都会在像素比对里爆出来。
 */
class LogDetailContentTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    /**
     * 固定时区与语言：详情页时间戳由 `SimpleDateFormat(Locale.getDefault())` + 默认时区格式化，
     * 不钉死则同一份代码在 CI（UTC）与本地（+08）渲染出不同字符串，基线永远对不上。
     */
    private lateinit var originalTimeZone: TimeZone
    private lateinit var originalLocale: Locale

    @Before
    fun pinTimeZone() {
        originalTimeZone = TimeZone.getDefault()
        originalLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.CHINA)
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
        Locale.setDefault(originalLocale)
    }

    @Test
    fun light() = paparazzi.snapshot {
        MmaTheme { Wall(entry = warnEntry()) }
    }

    @Test
    fun dark() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) { Wall(entry = warnEntry()) }
    }

    /** 最小态：无消息 / 无字段 / 无栈——验证空分区整块隐藏，不留空洞。 */
    @Test
    fun light_minimal() = paparazzi.snapshot {
        MmaTheme {
            Wall(
                entry = warnEntry().copy(
                    message = null,
                    fieldsJson = null,
                    stackTrace = null,
                ),
            )
        }
    }

    /**
     * 画布：surface 底色 + 上下留白。
     *
     * `LogDetailContent` 自带 verticalScroll 与水平 padding，直接铺根上会贴边；
     * 包一层 surface 底才看得出弹层的真实留白关系。
     */
    @Composable
    private fun Wall(entry: LogEntry) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = Spacing.space4),
        ) {
            LogDetailContent(
                entry = entry,
                onCopy = {},
                onDismiss = {},
            )
        }
    }

    /** 取样条目：WARN 级别 + 完整字段 + 异常栈 + 长消息（覆盖全部六个分区）。 */
    private fun warnEntry() = LogEntry(
        id = 1024L,
        timestampMs = 1_761_724_800_000L, // 2025-10-29 08:00:00.000 UTC（固定值，勿改——基线依赖）
        level = "WARN",
        category = "PREVIEW",
        event = "preview_blank_confirmed",
        message = "预览页连续 2 帧无可绘制内容，判定为空白并触发一次自愈重载。",
        fieldsJson = """{"blank_ms":5000,"load_id":"a1b2c3d4","url":"https://example.com/***","retry":1}""",
        stackTrace = "java.lang.IllegalStateException: webview detached\n" +
            "\tat com.make.apk.webcore.diagnostics.BlankDetector.check(BlankDetector.kt:88)\n" +
            "\tat com.make.apk.webcore.engine.WebViewEngine.onDraw(WebViewEngine.kt:241)",
        traceId = "trace-7f3a9c21",
        sessionId = "session-20251031-1200",
        sequence = 412L,
        threadName = "main",
        durationMs = 5012L,
        success = false,
    )
}
