package com.make.apk.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 间距令牌——DESIGN_LANGUAGE.md §4 `--space-1..7` 的 Compose 派生（1:1 命名便于审计）。
 *
 * 业务代码一律引用本对象，禁止裸写 dp 字面量（CI 门禁见 scripts/gitops/check_token_usage.py）。
 * 间距遵循 8pt 栅格：4 的倍数（4/8/12/16/24/32/48）。
 */
object Spacing {
    /** 4dp——紧凑间距（图标与文字、行内元素）。 */
    val space1 = 4.dp

    /** 8dp——栅格基础单位。 */
    val space2 = 8.dp

    /** 12dp——列表项内分组间距。 */
    val space3 = 12.dp

    /** 16dp——卡片内边距、内容分组间距。 */
    val space4 = 16.dp

    /** 24dp——页面级内边距。 */
    val space5 = 24.dp

    /** 32dp——大分组间距。 */
    val space6 = 32.dp

    /** 48dp——页面级大留白。 */
    val space7 = 48.dp
}
