package com.make.apk.ui.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.make.apk.data.model.ShellSpec

/**
 * 壳渲染环境的**唯一派生点**（app 侧）。
 *
 * ## 职责边界
 *
 * 这是 `ui/shell/` 包内**唯一**读取 `MaterialTheme` 的文件：
 * 壳内部的其它组件一律只吃 [ShellRenderEnv]，不直接碰主题色值——
 * 这样将来把壳代码搬进 `:shell-core`（独立 Android Library）时，
 * 编译期就能挡住"偷偷依赖 app 主题"的写法（设计文档 §5.2 / R8）。
 *
 * ## 强调色来源
 *
 * 壳强调色**默认跟随 L1 的项目主题色**（`WebRuntimeConfig.themeColor`），
 * 用户显式覆写时才用壳自己的颜色（[ShellTheme.resolveAccent]），
 * 避免出现"项目主题色"与"壳主题色"两个真值（设计文档 §1.2.1）。
 */
object ShellRenderEnvFactory {

    /**
     * 由项目主题色 + 壳主题覆写派生渲染环境。
     *
     * @param spec 壳契约（提供主题覆写）。
     * @param projectThemeColor L1 的项目主题色（ARGB）。
     */
    @Composable
    fun fromSpec(spec: ShellSpec, projectThemeColor: Int): ShellRenderEnv {
        val scheme = MaterialTheme.colorScheme
        val accent = Color(spec.theme.resolveAccent(projectThemeColor))
        return ShellRenderEnv(
            accent = accent,
            onAccent = scheme.onPrimary,
            accentContainer = scheme.primaryContainer,
            onAccentContainer = scheme.onPrimaryContainer,
            surface = scheme.surface,
            onSurface = scheme.onSurface,
            onSurfaceVariant = scheme.onSurfaceVariant,
            outline = scheme.outline,
            typography = MaterialTheme.typography,
            // 选中态标签：同字号 + 加粗（DESIGN_LANGUAGE §5 字重阶梯的 SemiBold 档），
            // 供 ShellSelectionStyle.TEXT 档表达"当前页"——该档没有指示条也没有背景。
            selectedLabelStyle = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            shapes = MaterialTheme.shapes,
            topBarHeight = TOP_BAR_HEIGHT,
            bottomBarHeight = BOTTOM_BAR_HEIGHT,
            indicatorHeight = INDICATOR_HEIGHT,
            spaceXs = SPACE_XS,
            spaceSm = SPACE_SM,
            spaceMd = SPACE_MD,
            spaceLg = SPACE_LG,
            drawerWidths = ShellDrawerWidths(
                small = DRAWER_WIDTH_SMALL,
                medium = DRAWER_WIDTH_MEDIUM,
                large = DRAWER_WIDTH_LARGE,
                minDp = DRAWER_WIDTH_MIN_DP,
                maxDp = DRAWER_WIDTH_MAX_DP,
            ),
        )
    }

    /** 顶栏高度（M3 顶栏规范值，与 `MmaTopBar` 对齐）。 */
    private val TOP_BAR_HEIGHT = 56.dp

    /** 底栏高度（M3 NavigationBar 规范值）。 */
    private val BOTTOM_BAR_HEIGHT = 80.dp // token-gate: allow:M3 NavigationBar 规范高度（组件尺寸，非间距语义）

    /** 顶栏 tab 选中指示器高度（M3 TabRow 规范值）。 */
    private val INDICATOR_HEIGHT = 2.dp

    /** 极小间距（与 app 侧 Spacing.space1 同值，壳包不依赖 app 主题包）。 */
    private val SPACE_XS = 3.dp

    /** 小间距（与 app 侧 Spacing.space2 同值）。 */
    private val SPACE_SM = 6.dp

    /** 中间距（与 app 侧 Spacing.space4 同值）。 */
    private val SPACE_MD = 12.dp

    /** 大间距（与 app 侧 Spacing.space6 同值）。 */
    private val SPACE_LG = 24.dp

    /** 抽屉宽度：窄档（M3 `NavigationDrawer` 常用区间下限）。 */
    private val DRAWER_WIDTH_SMALL = 240.dp // token-gate: allow:抽屉宽度档位属组件尺寸语义，非间距（与 topBarHeight 同性质）

    /** 抽屉宽度：中档（自定义档非法值时的回落档）。 */
    private val DRAWER_WIDTH_MEDIUM = 300.dp // token-gate: allow:抽屉宽度档位属组件尺寸语义，非间距

    /** 抽屉宽度：宽档（M3 `NavigationDrawer` 常用区间上限）。 */
    private val DRAWER_WIDTH_LARGE = 360.dp // token-gate: allow:抽屉宽度档位属组件尺寸语义，非间距

    /** 抽屉自定义宽度下限（低于此值抽屉内文字会换行挤压）。 */
    private const val DRAWER_WIDTH_MIN_DP = 200

    /** 抽屉自定义宽度上限（高于此值会挤掉内容区）。 */
    private const val DRAWER_WIDTH_MAX_DP = 400
}
