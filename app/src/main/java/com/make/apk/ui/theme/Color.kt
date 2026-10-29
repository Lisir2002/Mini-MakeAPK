package com.make.apk.ui.theme

import androidx.compose.ui.graphics.Color

// 设计令牌：OKLCH 来源见 /workspace/docs/design/tokens/design-tokens.json（唯一事实来源）。
// 此处为精确转换后的 sRGB hex（oklch → srgb 计算），亮/暗双主题共用命名。
// 团队共享唯一来源：设计层与安卓层禁止各自发明色值。
// 2025 改版：珊瑚粉可爱风 → 黑白为主 + 蓝色点缀（见 DESIGN_LANGUAGE.md §0/§2）。

// ── 明亮主题 ──
val md_bg_light                = Color(0xFFF6F7F9) // --bg                 OKLCH(0.9759 0.0029 264.5)
val md_surface_light           = Color(0xFFFFFFFF) // --surface            OKLCH(1.0000 0.0000 89.9)
val md_surface2_light          = Color(0xFFF0F1F3) // --surface-2          OKLCH(0.9579 0.0029 264.5)
val md_onSurface_light         = Color(0xFF14171A) // --on-surface         OKLCH(0.2027 0.0076 248.2)
val md_onSurfaceVariant_light  = Color(0xFF5B616E) // --on-surface-variant OKLCH(0.4921 0.0221 265.8)
val md_outline_light           = Color(0xFFE2E5E9) // --outline            OKLCH(0.9208 0.0063 255.5)
val md_outlineVariant_light    = Color(0xFFEDEFF2) // --outline-variant    OKLCH(0.9515 0.0046 258.3)
val md_primary_light           = Color(0xFF2563EB) // --primary 蓝点缀     OKLCH(0.5461 0.2152 262.9)
val md_onPrimary_light         = Color(0xFFFFFFFF) // --on-primary         OKLCH(1.0000 0.0000 89.9)
val md_primaryContainer_light  = Color(0xFFE6EFFF) // --primary-container  OKLCH(0.9501 0.0236 262.1)
val md_onPrimaryContainer_light= Color(0xFF0B3FA8) // --on-primary-container OKLCH(0.4110 0.1765 262.3)
val md_success_light           = Color(0xFF16A34A) // --success            OKLCH(0.6271 0.1699 149.2)
val md_warning_light           = Color(0xFFD97706) // --warning            OKLCH(0.6658 0.1574 58.3)
val md_error_light             = Color(0xFFDC2626) // --error              OKLCH(0.5771 0.2152 27.3)

// ── 暗黑主题 ──
val md_bg_dark                 = Color(0xFF0E0F11) // --bg                 OKLCH(0.1682 0.0045 264.4)
val md_surface_dark            = Color(0xFF16181B) // --surface            OKLCH(0.2082 0.0067 258.4)
val md_surface2_dark           = Color(0xFF1E2125) // --surface-2          OKLCH(0.2463 0.0088 255.6)
val md_onSurface_dark          = Color(0xFFF2F4F6) // --on-surface         OKLCH(0.9662 0.0034 247.9)
val md_onSurfaceVariant_dark   = Color(0xFF9BA1A8) // --on-surface-variant OKLCH(0.7063 0.0123 252.1)
val md_outline_dark            = Color(0xFF2A2E33) // --outline            OKLCH(0.2993 0.0107 254.0)
val md_outlineVariant_dark     = Color(0xFF23272C) // --outline-variant    OKLCH(0.2708 0.0109 254.0)
val md_primary_dark            = Color(0xFF5B8DEF) // --primary 蓝点缀     OKLCH(0.6545 0.1562 262.5)
val md_onPrimary_dark          = Color(0xFF0A1A33) // --on-primary         OKLCH(0.2195 0.0538 259.0)
val md_primaryContainer_dark   = Color(0xFF15315E) // --primary-container  OKLCH(0.3184 0.0872 259.5)
val md_onPrimaryContainer_dark = Color(0xFFC7DBFF) // --on-primary-container OKLCH(0.8882 0.0543 262.2)
val md_success_dark            = Color(0xFF22C55E) // --success            OKLCH(0.7227 0.1920 149.6)
val md_warning_dark            = Color(0xFFF59E0B) // --warning            OKLCH(0.7686 0.1647 70.1)
val md_error_dark              = Color(0xFFF87171) // --error              OKLCH(0.7106 0.1661 22.2)

// ── 蓝色点缀家族（中性黑白为主，蓝为唯一点缀；无亮/暗后缀，双主题共用 scale） ──
val md_blue_50  = Color(0xFFEFF4FF) // OKLCH(0.9666 0.0157 266.3)
val md_blue_100 = Color(0xFFDBE6FF) // OKLCH(0.9243 0.0361 266.7)
val md_blue_200 = Color(0xFFBFD3FF) // OKLCH(0.8665 0.0651 265.8)
val md_blue_300 = Color(0xFF93B4FF) // OKLCH(0.7748 0.1139 265.6)
val md_blue_400 = Color(0xFF5B8DEF) // OKLCH(0.6545 0.1562 262.5)
val md_blue_500 = Color(0xFF3B6FE0) // OKLCH(0.5686 0.1814 263.2)
val md_blue_600 = Color(0xFF2563EB) // OKLCH(0.5461 0.2152 262.9)
val md_blue_700 = Color(0xFF1D4FD0) // OKLCH(0.4827 0.2060 263.9)
val md_blue_800 = Color(0xFF1A41A8) // OKLCH(0.4186 0.1713 264.3)
val md_blue_900 = Color(0xFF15346F) // OKLCH(0.3392 0.1090 261.7)

// info 语义色复用 primary（亮/暗同 primary 组），不单独发明色值。
val md_info_light  = md_primary_light
val md_info_dark   = md_primary_dark

// ── 次级强调槽（secondary / tertiary）：显式锁死，禁止回落 M3 默认 ──
//
// 根因：`lightColorScheme()` / `darkColorScheme()` 只覆盖我们传入的槽，**未传入的槽回落
// Material 3 默认色**——`tertiaryContainer` 默认是粉/珊瑚色系（亮色 #FFD8E4、暗色 #633B48），
// 与「黑白为主 + 蓝色点缀」直接冲突，表现为"旧版珊瑚粉残留"（缓存模式选中态、
// 下拉选中态、通知与日志级别徽标都命中这条）。故此处把 secondary / tertiary 两组全部
// 显式定义；取值一律**派生自既有令牌**（不发明新色相）：
//   secondary = 中性灰（次级信息 / 中性容器）——取 on-surface-variant / surface-2；
//   tertiary  = 蓝色家族次级（主色之外的一档强调蓝）——取 blue scale。
val md_secondary_light             = md_onSurfaceVariant_light // OKLCH(0.4921 0.0221 265.8)
val md_onSecondary_light           = md_surface_light
val md_secondaryContainer_light    = md_surface2_light
val md_onSecondaryContainer_light  = md_onSurface_light
val md_tertiary_light              = md_blue_500               // OKLCH(0.5686 0.1814 263.2)
val md_onTertiary_light            = md_surface_light
val md_tertiaryContainer_light     = md_blue_100               // OKLCH(0.9243 0.0361 266.7)
val md_onTertiaryContainer_light   = md_blue_900               // OKLCH(0.3392 0.1090 261.7)

val md_secondary_dark              = md_onSurfaceVariant_dark  // OKLCH(0.7063 0.0123 252.1)
val md_onSecondary_dark            = md_bg_dark
val md_secondaryContainer_dark     = md_surface2_dark
val md_onSecondaryContainer_dark   = md_onSurface_dark
val md_tertiary_dark               = md_blue_200               // OKLCH(0.8665 0.0651 265.8)
val md_onTertiary_dark             = md_blue_900
val md_tertiaryContainer_dark      = md_blue_900               // OKLCH(0.3392 0.1090 261.7)
val md_onTertiaryContainer_dark    = md_blue_100

// ── 错误容器槽：同样禁止回落 M3 默认（默认亮色为浅粉 #F9DEDC）──
// 由 --error 派生（同色相，仅调明度/彩度），不引入新色相。
val md_errorContainer_light        = Color(0xFFFBE9E9) // 派生于 --error #DC2626（提亮）
val md_onErrorContainer_light      = Color(0xFF7F1D1D) // 派生于 --error #DC2626（压暗）
val md_errorContainer_dark         = Color(0xFF4E1517) // 派生于 --error #F87171（压暗）
val md_onErrorContainer_dark       = Color(0xFFFFDAD6) // 派生于 --error #F87171（提亮）
