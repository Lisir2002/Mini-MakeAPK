package com.make.apk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.make.apk.logging.LogTone

/**
 * 日志色调的**容器色对**（底色 + 前景色）。
 *
 * @param container 容器底色（徽标背景）。
 * @param content 前景色（徽标文字），保证在 [container] 上达到 WCAG AA 正文对比度。
 */
data class ToneColors(
    val container: Color,
    val content: Color,
)

/**
 * [LogTone] → 色对的**唯一映射点**（rc17）。
 *
 * 为什么必须收在这一处：修复前日志列表与详情弹层各自持有一份 46 行逐字相同的
 * `when (severityWeight)` 取色代码——`LogLevel` 的 KDoc 明明写着"展示语义只在这一处
 * 定义…不允许各自再拼"，代码却在 UI 层复制了两份。任何一处改动都会漏掉另一处。
 *
 * 取色来源一律是**既有令牌**，不发明色值：
 * - [LogTone.Muted] → `surfaceVariant`（surface-2 灰）；
 * - [LogTone.Info]  → `primaryContainer`（`--info` 在 design-tokens 中即复用 `--primary`）；
 * - [LogTone.Warn]  → [warnColors]（由 `--warning` #D97706 派生；M3 无 warning 槽）；
 * - [LogTone.Error] → `errorContainer`。
 */
@Composable
@ReadOnlyComposable
fun LogTone.colors(): ToneColors {
    val colorScheme = MaterialTheme.colorScheme
    return when (this) {
        LogTone.Muted -> ToneColors(
            container = colorScheme.surfaceVariant,
            content = colorScheme.onSurfaceVariant,
        )

        LogTone.Info -> ToneColors(
            container = colorScheme.primaryContainer,
            content = colorScheme.onPrimaryContainer,
        )

        LogTone.Warn -> warnColors()

        LogTone.Error -> ToneColors(
            container = colorScheme.errorContainer,
            content = colorScheme.onErrorContainer,
        )
    }
}

/**
 * 警告色对（`--warning` 派生）。M3 的 ColorScheme 没有 warning 槽，
 * 故与错误容器同一套做法：由语义单色派生容器/前景对，亮暗各一份。
 *
 * 与日志徽标**共用**：全局提示气泡（`MmaNotice`）的 Warn 也走这里，
 * 避免"警告一处琥珀、一处品牌蓝"的分裂。
 */
@Composable
@ReadOnlyComposable
fun warnColors(): ToneColors = if (isSurfaceDark()) {
    ToneColors(container = md_warnContainer_dark, content = md_onWarnContainer_dark)
} else {
    ToneColors(container = md_warnContainer_light, content = md_onWarnContainer_light)
}

/** 成功色对（`--success` 派生）。理由同 [warnColors]。 */
@Composable
@ReadOnlyComposable
fun successColors(): ToneColors = if (isSurfaceDark()) {
    ToneColors(container = md_successContainer_dark, content = md_onSuccessContainer_dark)
} else {
    ToneColors(container = md_successContainer_light, content = md_onSuccessContainer_light)
}

/**
 * 当前配色是否为暗色。
 *
 * 不用 `isSystemInDarkTheme()`——主题可被显式覆盖（`MmaTheme(darkTheme = true)` 的快照
 * 与自定义主题），系统值会与实际渲染的配色不一致；按 `surface` 相对亮度判定才跟手。
 */
@Composable
@ReadOnlyComposable
internal fun isSurfaceDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
