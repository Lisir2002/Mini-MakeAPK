package com.make.apk.ui.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 间距阶梯令牌——DESIGN_LANGUAGE.md §4 `--space-1..7` 的可覆盖形态。
 *
 * 业务代码不直接引用本类，而是引用 [Spacing] 代理对象（默认值即此处），
 * 保证「零迁移」：将来做自定义主题只需在 [MmaDesign] 中换一个 [SpacingScale] 实例。
 *
 * 默认值为紧凑档（2025-09 全局温和收紧 -25%，DESIGN_LANGUAGE §4「紧凑模式」）：
 * 原 4/8/12/16/24/32/48 → 3/6/9/12/18/24/36。圆角/字号/图标不受影响。
 */
data class SpacingScale(
    /** 3dp——紧凑间距（图标与文字、行内元素）。 */
    val space1: Dp = 3.dp,

    /** 6dp——栅格基础单位。 */
    val space2: Dp = 6.dp,

    /** 9dp——列表项内分组间距。 */
    val space3: Dp = 9.dp,

    /** 12dp——卡片内边距、内容分组间距。 */
    val space4: Dp = 12.dp,

    /** 18dp——页面级内边距。 */
    val space5: Dp = 18.dp,

    /** 24dp——大分组间距。 */
    val space6: Dp = 24.dp,

    /** 36dp——页面级大留白。 */
    val space7: Dp = 36.dp,
)

/**
 * 透明度阶梯令牌（预留，Phase-2 自定义主题消费）。
 *
 * 当前代码未直接使用；先固定档位语义，避免自定义主题期各处裸写 alpha 字面量。
 */
data class AlphaScale(
    /** 0.04——极轻底色（pressed 态、进度轨道）。 */
    val a1: Float = 0.04f,

    /** 0.08——轻底色（disabled 容器、分割线）。 */
    val a2: Float = 0.08f,

    /** 0.16——分割线/弱边框。 */
    val a3: Float = 0.16f,

    /** 0.38——弱化内容（占位符、次级说明）。 */
    val a4: Float = 0.38f,

    /** 0.60——半透明容器（磨砂容器底色基准）。 */
    val a5: Float = 0.60f,

    /** 0.88——近实色（磨砂容器最终不透明度）。 */
    val a6: Float = 0.88f,

    /** 1.00——实色。 */
    val a7: Float = 1f,
)

/** 表面材质档位——骨架「插拔换样式」的粒度单位（DESIGN_LANGUAGE §7 规划）。 */
enum class MaterialTier {
    /** 纯色平面：默认档，最省电，磨砂关闭时使用。 */
    Flat,

    /** 纯色 + 阴影抬升：层次感来自 elevation 而非透明。 */
    Elevated,

    /** 半透明磨砂：API 31+ 真模糊（Haze），以下版本降级半透明纯色。 */
    Translucent,
}

/**
 * 磨砂参数——仅 [MaterialTier.Translucent] 档位消费（DESIGN_LANGUAGE §7 规划）。
 *
 * @param alpha 容器底色不透明度（最终视觉不透明度，低于此值的内容透出）。
 * @param blurRadius 背景模糊半径；API < 31 降级时忽略。
 * @param noise 噪点强度（0 = 纯净磨砂；用于抵消低端机色带）。
 * @param borderAlpha 1dp 内描边不透明度（磨砂容器边缘提亮，iOS 风关键细节）。
 */
data class TranslucentSpec(
    val alpha: Float = 0.88f,
    val blurRadius: Dp = 20.dp,
    val noise: Float = 0.05f,
    val borderAlpha: Float = 0.12f,
)

/** 顶栏变体——[ScaffoldStyle] 的插拔选项。 */
enum class TopBarVariant {
    /** 实色顶栏（默认，最省电）。 */
    Solid,

    /** 磨砂顶栏：滚动时背后内容透出（Phase-2 自定义主题消费）。 */
    Translucent,
}

/**
 * 骨架（Scaffold）样式令牌——底栏/顶栏的插拔换样式入口。
 *
 * @param topBarVariant 顶栏默认变体。
 * @param bottomBarVariant 底栏默认变体。
 * @param translucentChrome 磨砂总开关；关闭时即使变体为 Translucent 也按实色渲染（省电兜底）。
 */
data class ScaffoldStyle(
    val topBarVariant: TopBarVariant = TopBarVariant.Solid,
    val bottomBarVariant: TopBarVariant = TopBarVariant.Solid,
    val translucentChrome: Boolean = false,
)

/**
 * 设计令牌聚合体——自定义主题的唯一换装入口。
 *
 * 架构（零迁移模式）：
 * - [LocalMmaDesign] 承载当前实例，[MmaTheme] 装配默认值；
 * - [Spacing] 等代理对象读 `LocalMmaDesign.current`，默认值即现状，
 *   业务代码写法不变；
 * - Phase-2 自定义主题只需 `CompositionLocalProvider(LocalMmaDesign provides MmaDesign(spacing = …))`，
 *   无需改动任何页面。
 *
 * @param spacing 间距阶梯。
 * @param alpha 透明度阶梯（预留）。
 * @param defaultMaterial 组件默认材质档位（预留；当前组件均按 Flat 渲染）。
 * @param translucent 磨砂参数（预留，[MaterialTier.Translucent] 档消费）。
 * @param scaffold 骨架样式（预留，Phase-2 顶栏/底栏插拔消费）。
 */
data class MmaDesign(
    val spacing: SpacingScale = SpacingScale(),
    val alpha: AlphaScale = AlphaScale(),
    val defaultMaterial: MaterialTier = MaterialTier.Flat,
    val translucent: TranslucentSpec = TranslucentSpec(),
    val scaffold: ScaffoldStyle = ScaffoldStyle(),
)

/** 设计令牌 CompositionLocal——自定义主题的覆盖点。 */
val LocalMmaDesign: ProvidableCompositionLocal<MmaDesign> = compositionLocalOf { MmaDesign() }
