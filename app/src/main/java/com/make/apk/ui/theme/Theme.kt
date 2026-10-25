package com.make.apk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 形状：iOS 风大圆角（DESIGN_LANGUAGE §3）。禁止尖角（<4dp）。
// M3 五档 ↔ 规范四值的映射（DESIGN_LANGUAGE §3「Compose M3 映射」）：
//   extraSmall=8(sm) small=16(md) medium=16(md) large=24(lg) extraLarge=999(pill 胶囊)。
//   注意：需要 24dp 圆角时显式用 shapes.large；extraLarge 是胶囊档，勿用于普通卡片。
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // --radius-sm  小组件/标签
    small      = RoundedCornerShape(16.dp),  // --radius-md  卡片/输入框（默认）
    medium     = RoundedCornerShape(16.dp),  // --radius-md  M3 Card 默认档
    large      = RoundedCornerShape(24.dp),  // --radius-lg  大卡片/sheet
    extraLarge = RoundedCornerShape(999.dp), // --radius-pill 胶囊按钮/底栏指示
)

// 字体：圆体优先（DESIGN_LANGUAGE §5）。
// 接入方式：将 Nunito/Quicksand/Baloo2 放入 res/font，用 Font(R.font.nunito) 构造 FontFamily；
// 当前回退系统无衬线，禁止默认 Roboto 作为主视觉字。
private val AppFontFamily = FontFamily.SansSerif

val AppTypography = Typography(
    displayLarge  = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium= TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 29.sp),
    titleLarge   = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp),
    bodyLarge    = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium   = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge   = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium  = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp), // 底栏 tab 标签（补齐 §5 阶梯既有档位，防掉 Roboto）
)

private val LightColorScheme = lightColorScheme(
    background         = md_bg_light,
    surface            = md_surface_light,
    surfaceVariant     = md_surface2_light,
    onSurface          = md_onSurface_light,
    onSurfaceVariant   = md_onSurfaceVariant_light,
    outline            = md_outline_light,
    primary            = md_primary_light,
    onPrimary          = md_onPrimary_light,
    primaryContainer   = md_primaryContainer_light,
    onPrimaryContainer = md_onPrimaryContainer_light,
    error              = md_error_light,
)

private val DarkColorScheme = darkColorScheme(
    background         = md_bg_dark,
    surface            = md_surface_dark,
    surfaceVariant     = md_surface2_dark,
    onSurface          = md_onSurface_dark,
    onSurfaceVariant   = md_onSurfaceVariant_dark,
    outline            = md_outline_dark,
    primary            = md_primary_dark,
    onPrimary          = md_onPrimary_dark,
    primaryContainer   = md_primaryContainer_dark,
    onPrimaryContainer = md_onPrimaryContainer_dark,
    error              = md_error_dark,
)

@Composable
fun MmaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
