package com.make.apk.webcore.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.make.apk.data.model.ProgressColorToken
import com.make.apk.data.model.ProgressThickness

/**
 * 进度条色彩令牌 → 具体颜色。
 *
 * 从 `MaterialTheme.colorScheme` 解析（自动跟随明/暗主题与整体设计语言），
 * 绝不硬编码色值（设计令牌铁律）；与导出壳共用本文件，故预览 == 导出同源取色。
 *
 * 与旧单体 progressColorOf（WebViewScreen.kt :821-827）等值迁移。
 */
internal fun progressColorOf(colorScheme: ColorScheme, token: ProgressColorToken): Color =
    when (token) {
        ProgressColorToken.PRIMARY -> colorScheme.primary
        ProgressColorToken.ON_SURFACE_VARIANT -> colorScheme.onSurfaceVariant
        ProgressColorToken.TERTIARY -> colorScheme.tertiary
        ProgressColorToken.OUTLINE -> colorScheme.outline
    }

/** 进度条粗细档位 → dp（3 / 6 / 9 均落在设计令牌尺寸白名单）。 */
internal val ProgressThickness.strokeDp: Dp
    get() = when (this) {
        ProgressThickness.THIN -> 3.dp
        ProgressThickness.MEDIUM -> 6.dp
        ProgressThickness.THICK -> 9.dp
    }

/**
 * 自定义线性进度条（预览与导出壳共用，故两侧一致）。
 *
 * 本项目解析到的 Material3 版本（1.3.0）的 [androidx.compose.material3.LinearProgressIndicator]
 * 尚未提供 `strokeWidth` / `() -> Float` 重载，无法表达「粗细档位」。
 * 用两层 [Box] 自绘：轨道（底色）+ 进度（强调色），高度即粗细 [thickness]，
 * 颜色走令牌解析（[progressColorOf]），不引入硬编码色块，且不依赖升级 Material3。
 *
 * 与旧单体 ShellLoadingProgressIndicator（WebViewScreen.kt :845-866）**逐行等值迁移**，
 * 仅可见性 private → public（跨文件消费）。
 */
@Composable
public fun ShellLoadingProgressIndicator(
    progressFraction: Float,
    color: Color,
    trackColor: Color,
    thickness: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                .height(thickness)
                .background(color),
        )
    }
}
