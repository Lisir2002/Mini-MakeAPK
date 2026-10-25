package com.make.apk.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * 间距令牌——DESIGN_LANGUAGE.md §4 `--space-1..7` 的 Compose 派生（1:1 命名便于审计）。
 *
 * 业务代码一律引用本对象，禁止裸写 dp 字面量（CI 门禁见 scripts/gitops/check_token_usage.py）。
 *
 * 代理化架构（2025-09）：本对象全部为 @Composable getter，实读 [LocalMmaDesign].current，
 * 默认值即紧凑阶梯 3/6/9/12/18/24/36（见 [SpacingScale]）。调用点写法不变，
 * Phase-2 自定义主题只需替换 [MmaDesign] 实例即可整体换装。
 */
object Spacing {
    /** 3dp——紧凑间距（图标与文字、行内元素）。 */
    val space1: Dp
        @Composable get() = LocalMmaDesign.current.spacing.space1

    /** 6dp——栅格基础单位。 */
    val space2: Dp
        @Composable get() = LocalMmaDesign.current.spacing.space2

    /** 9dp——列表项内分组间距。 */
    val space3: Dp
        @Composable get() = LocalMmaDesign.current.spacing.space3

    /** 12dp——卡片内边距、内容分组间距。 */
    val space4: Dp
        @Composable get() = LocalMmaDesign.current.spacing.space4

    /** 18dp——页面级内边距。 */
    val space5: Dp
        @Composable get() = LocalMmaDesign.current.spacing.space5

    /** 24dp——大分组间距。 */
    val space6: Dp
        @Composable get() = LocalMmaDesign.current.spacing.space6

    /** 36dp——页面级大留白。 */
    val space7: Dp
        @Composable get() = LocalMmaDesign.current.spacing.space7
}
