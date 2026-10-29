package com.make.apk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 形状：黑白为主 + 蓝色点缀（DESIGN_LANGUAGE §3）。圆角收紧为中性利落档。
// M3 五档 ↔ 规范值的映射（DESIGN_LANGUAGE §3「Compose M3 映射」）：
//   extraSmall=6(sm) small=12(md) medium=12(md) large=16(lg) extraLarge=999(pill 胶囊)。
//   --radius-xl(20dp) 供 bottomSheet 顶部圆角，经门禁白名单（COMPONENT_SIZES 的 20），
//   不在 Shapes 五档内，需用时局部 RoundedCornerShape(20.dp)（仅 theme/ 令牌定义处允许）。
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),   // --radius-sm  小组件/标签
    small      = RoundedCornerShape(12.dp),  // --radius-md  卡片/输入框（默认）
    medium     = RoundedCornerShape(12.dp),  // --radius-md  M3 Card 默认档
    large      = RoundedCornerShape(16.dp),  // --radius-lg  大卡片/sheet
    extraLarge = RoundedCornerShape(999.dp), // --radius-pill 胶囊按钮/底栏指示
)

// 字体：系统无衬线优先（DESIGN_LANGUAGE §5）。
// 接入方式：将 Inter / Noto Sans SC 放入 res/font，用 Font(R.font.inter) 构造 FontFamily；
// 当前回退系统无衬线，禁止默认 Roboto 作为主视觉字。
// 字阶严格对齐 design-tokens.json typography：display28 / headline22 / title18 /
// body-lg16 / body-md14 / label13 / caption12（行高/字重同契约）。
private val AppFontFamily = FontFamily.SansSerif

val AppTypography = Typography(
    displayLarge  = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp), // --display  28/34/600
    headlineMedium= TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp), // --headline 22/28/600
    titleLarge   = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp), // --title    18/24/600
    bodyLarge    = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp), // --body-lg  16/24/400
    bodyMedium   = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 21.sp), // --body-md  14/21/400
    labelLarge   = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium,   fontSize = 13.sp, lineHeight = 18.sp), // --label    13/18/500
    labelMedium  = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp), // --caption  12/16/500
)

// 槽位覆盖原则（反"珊瑚粉残留"）：**凡是 UI 会用到的槽，一律显式传入**。
// `lightColorScheme()` 未覆盖的槽会回落 Material 3 默认配色（tertiaryContainer 默认粉、
// errorContainer 默认浅粉、surfaceContainerLow 默认带紫调 #F7F2FA），与「黑白为主 +
// 蓝色点缀」冲突；用户可见的"旧版硬编码色彩残留"即由此而来。故此处逐槽锁死。
private val LightColorScheme = lightColorScheme(
    background           = md_bg_light,
    surface              = md_surface_light,
    surfaceVariant       = md_surface2_light,
    surfaceContainerLow  = md_surface2_light,
    onSurface            = md_onSurface_light,
    onSurfaceVariant     = md_onSurfaceVariant_light,
    outline              = md_outline_light,
    outlineVariant       = md_outlineVariant_light,
    primary              = md_primary_light,
    onPrimary            = md_onPrimary_light,
    primaryContainer     = md_primaryContainer_light,
    onPrimaryContainer   = md_onPrimaryContainer_light,
    secondary            = md_secondary_light,
    onSecondary          = md_onSecondary_light,
    secondaryContainer   = md_secondaryContainer_light,
    onSecondaryContainer = md_onSecondaryContainer_light,
    tertiary             = md_tertiary_light,
    onTertiary           = md_onTertiary_light,
    tertiaryContainer    = md_tertiaryContainer_light,
    onTertiaryContainer  = md_onTertiaryContainer_light,
    error                = md_error_light,
    errorContainer       = md_errorContainer_light,
    onErrorContainer     = md_onErrorContainer_light,
    scrim                = md_bg_dark,
)

private val DarkColorScheme = darkColorScheme(
    background           = md_bg_dark,
    surface              = md_surface_dark,
    surfaceVariant       = md_surface2_dark,
    surfaceContainerLow  = md_surface2_dark,
    onSurface            = md_onSurface_dark,
    onSurfaceVariant     = md_onSurfaceVariant_dark,
    outline              = md_outline_dark,
    outlineVariant       = md_outlineVariant_dark,
    primary              = md_primary_dark,
    onPrimary            = md_onPrimary_dark,
    primaryContainer     = md_primaryContainer_dark,
    onPrimaryContainer   = md_onPrimaryContainer_dark,
    secondary            = md_secondary_dark,
    onSecondary          = md_onSecondary_dark,
    secondaryContainer   = md_secondaryContainer_dark,
    onSecondaryContainer = md_onSecondaryContainer_dark,
    tertiary             = md_tertiary_dark,
    onTertiary           = md_onTertiary_dark,
    tertiaryContainer    = md_tertiaryContainer_dark,
    onTertiaryContainer  = md_onTertiaryContainer_dark,
    error                = md_error_dark,
    errorContainer       = md_errorContainer_dark,
    onErrorContainer     = md_onErrorContainer_dark,
    scrim                = md_bg_dark,
)

/**
 * 应用主题——装配颜色/形状/字体 + 设计令牌（[LocalMmaDesign]）。
 *
 * @param darkTheme 暗色开关（默认跟随系统）。
 * @param design 设计令牌实例；Phase-2 自定义主题入口，缺省为紧凑默认档。
 */
@Composable
fun MmaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    design: MmaDesign = MmaDesign(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    CompositionLocalProvider(LocalMmaDesign provides design) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}
