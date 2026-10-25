package com.make.apk.ui.theme

import androidx.compose.ui.graphics.Color

// 设计令牌：OKLCH 来源见 /workspace/DESIGN_LANGUAGE.md §2
// 此处为精确转换后的 sRGB hex（oklch → srgb 计算），亮/暗双主题共用命名。
// 团队共享唯一来源：设计层与安卓层禁止各自发明色值。

// ── 明亮主题 ──
val md_bg_light                = Color(0xFFF9F8F5) // --bg            OKLCH(0.98 0.004 95)
val md_surface_light           = Color(0xFFFFFFFF) // --surface
val md_surface2_light          = Color(0xFFF6F5F3) // --surface-2     OKLCH(0.97 0.003 95)
val md_onSurface_light         = Color(0xFF2D2823) // --on-surface    OKLCH(0.28 0.012 60)
val md_onSurfaceVariant_light  = Color(0xFF6D6863) // --on-surface-variant OKLCH(0.52 0.01 60)
val md_outline_light           = Color(0xFFDFDEDB) // --outline       OKLCH(0.90 0.004 80)
val md_primary_light           = Color(0xFFF19E97) // --primary 珊瑚粉 OKLCH(0.78 0.10 25)
val md_onPrimary_light         = Color(0xFFFDFCF8) // --on-primary    OKLCH(0.99 0.005 95)
val md_primaryContainer_light  = Color(0xFFFFDFD8) // --primary-container OKLCH(0.93 0.04 30)
val md_onPrimaryContainer_light= Color(0xFF643B34) // OKLCH(0.40 0.06 30)
val md_success_light           = Color(0xFF88D1A2) // OKLCH(0.80 0.10 155)
val md_warning_light           = Color(0xFFE2BF76) // OKLCH(0.82 0.10 85)
val md_error_light             = Color(0xFFD55753) // OKLCH(0.62 0.16 25)

// ── 暗黑主题 ──
val md_bg_dark                 = Color(0xFF191512) // OKLCH(0.20 0.008 60)
val md_surface_dark            = Color(0xFF25211E) // OKLCH(0.25 0.008 60)
val md_surface2_dark           = Color(0xFF312D2A) // OKLCH(0.30 0.008 60)
val md_onSurface_dark          = Color(0xFFE5E4E2) // OKLCH(0.92 0.004 95)
val md_onSurfaceVariant_dark   = Color(0xFFA39D98) // OKLCH(0.70 0.01 60)
val md_outline_dark            = Color(0xFF4A4744) // OKLCH(0.40 0.006 60)
val md_primary_dark            = Color(0xFFFFABA3) // OKLCH(0.82 0.10 25)
val md_onPrimary_dark          = Color(0xFF231715) // OKLCH(0.22 0.02 30)
val md_primaryContainer_dark   = Color(0xFF5E362F) // OKLCH(0.38 0.06 30)
val md_onPrimaryContainer_dark = Color(0xFFF3C0B7) // OKLCH(0.85 0.06 30) 深色主色容器上的浅珊瑚字
val md_success_dark            = Color(0xFF6AC48C) // OKLCH(0.75 0.12 155)
val md_warning_dark            = Color(0xFFDBB155) // OKLCH(0.78 0.12 85)
val md_error_dark              = Color(0xFFF97770) // OKLCH(0.72 0.16 25) 比亮色提明度保对比
