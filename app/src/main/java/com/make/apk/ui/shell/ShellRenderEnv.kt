package com.make.apk.ui.shell

import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

/**
 * 壳渲染环境（阶段 6.2 · L3）——壳渲染器**唯一**的视觉输入。
 *
 * ## 为什么要有 env
 *
 * 壳代码**不得**直接依赖 app 的 `MmaDesign` / 具体色值，
 * 否则导出侧（另一个 APK 工程）无色可用、也编译不过。
 * 故所有颜色、字体、形状与尺寸档位都由 app 侧 [ShellRenderEnvFactory] 派生后注入
 * （该文件是壳包内**唯一**接触 `MaterialTheme` 的地方）。
 *
 * ## 纯数据
 *
 * env 是**不可变数据类**（纯 Kotlin 值，无 `android.*` 依赖），
 * 便于 JVM 单测断言，也便于未来搬进 `:shell-core`。
 */
@Immutable
data class ShellRenderEnv(
    /** 强调色（来自项目主题色或壳覆写）。 */
    val accent: Color,
    /** 强调色之上的前景色。 */
    val onAccent: Color,
    /** 强调色容器（选中指示器 / 药丸背景）。 */
    val accentContainer: Color,
    /** 强调色容器上的前景色。 */
    val onAccentContainer: Color,
    /** 壳表面色。 */
    val surface: Color,
    /** 表面上的主文字色。 */
    val onSurface: Color,
    /** 表面上的次要文字色。 */
    val onSurfaceVariant: Color,
    /** 描边 / 分隔线色。 */
    val outline: Color,
    /** 字体阶梯（由宿主注入，壳内不直接读 MaterialTheme）。 */
    val typography: Typography,
    /**
     * 选中态标签文字样式（"当前页"强调用）。
     *
     * 供 [com.make.apk.data.model.ShellSelectionStyle.TEXT] 档使用：
     * 该档既没有指示条也没有背景，只能靠"文字颜色 + 字重"表达选中，
     * 故需要一枚**加粗**的标签样式——由宿主按主题派生，壳内不硬编码字重。
     */
    val selectedLabelStyle: TextStyle,
    /** 形状阶梯（由宿主注入）。 */
    val shapes: Shapes,
    /** 顶栏高度。 */
    val topBarHeight: Dp,
    /** 底栏高度。 */
    val bottomBarHeight: Dp,
    /** 顶栏 tab 指示器高度。 */
    val indicatorHeight: Dp,
    /** 极小间距（图标与文字之间）。 */
    val spaceXs: Dp,
    /** 小间距（行内元素之间）。 */
    val spaceSm: Dp,
    /** 中间距（分组之间）。 */
    val spaceMd: Dp,
    /** 大间距（区域之间）。 */
    val spaceLg: Dp,
    /** 抽屉宽度档位（阶段 6.5）。 */
    val drawerWidths: ShellDrawerWidths,
)

/**
 * 抽屉宽度档位（阶段 6.5）。
 *
 * 与 [ShellRenderEnv.topBarHeight] / [bottomBarHeight][ShellRenderEnv.bottomBarHeight] 同理：
 * 具体数值由宿主（[ShellRenderEnvFactory]）派生后注入，
 * 壳组件内部只说"取哪一档"，不写死尺寸——导出壳换一套数值即可整体换肤。
 *
 * @param small 窄档。
 * @param medium 中档（自定义档非法值时的回落档）。
 * @param large 宽档。
 * @param minDp 自定义档下限（低于此值抽屉会挤成一团）。
 * @param maxDp 自定义档上限（高于此值会挤掉内容区）。
 */
@Immutable
data class ShellDrawerWidths(
    val small: Dp,
    val medium: Dp,
    val large: Dp,
    val minDp: Int,
    val maxDp: Int,
)

/**
 * 当前壳的强调色（壳内所有强调色只从这里取）。
 *
 * 供壳内子组件使用；宿主一般通过 [ShellRenderEnv.accent] 传参即可。
 */
val LocalShellAccent: androidx.compose.runtime.ProvidableCompositionLocal<Color> =
    staticCompositionLocalOf { Color.Unspecified }
