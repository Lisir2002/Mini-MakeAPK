package com.make.apk.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.navigation.MmaTab
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.TopBarVariant
import com.make.apk.ui.LocalHazeState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

/**
 * 统一底栏（三段式槽位的「底栏」段，见 [MmaScreen]）——顶级导航。
 *
 * 仅主页级页面（底栏 tab 对应的根页面）显示；流程子页（构建中/结果/预览）不传底栏槽位。
 * 选中态：图标/文字走 primary，指示块走 primaryContainer（DESIGN_LANGUAGE §8 底栏规范）。
 * 底栏自带系统导航栏 inset 处理；Paparazzi 下 insets 为 0，不影响截图构图。
 *
 * 磨砂（translucent=true 且 [hazeState] 非空）：NavigationBar 自身即 Surface，
 * 不能再套一层 Surface（会双重底色），故走「containerColor 透明 + modifier.hazeChild」
 * 路线，样式令牌与 [MmaTranslucentSurface] 同源（[hazeChromeStyle]）。
 * 磨砂源由 App 壳层统一提供（见 [com.make.apk.ui.LocalHazeState]）。
 *
 * @param tabs tab 清单（[com.make.apk.ui.navigation.DefaultMmaTabs] 或用户自定义过滤/重排后的列表）。
 * @param currentRoute 当前路由（NavHost 观测），与 tab.route 精确匹配判定选中态。
 * @param onTabSelected 选中回调（由调用方执行导航，本组件不做导航决策）。
 * @param translucent 磨砂开关；null（默认）跟随 [com.make.apk.ui.theme.ScaffoldStyle]——
 *   `translucentChrome && bottomBarVariant == Translucent`。截图路径保持 null 且令牌默认关。
 * @param hazeState Haze 捕获状态；默认读 [com.make.apk.ui.LocalHazeState]（截图测试不提供即为 null）。
 */
@Composable
fun MmaBottomBar(
    tabs: List<MmaTab>,
    currentRoute: String?,
    onTabSelected: (MmaTab) -> Unit,
    modifier: Modifier = Modifier,
    translucent: Boolean? = null,
    hazeState: HazeState? = LocalHazeState.current,
) {
    val scaffoldStyle = LocalMmaDesign.current.scaffold
    val spec = LocalMmaDesign.current.translucent
    val surfaceColor = MaterialTheme.colorScheme.surface
    // 参数优先、令牌兜底；blurState 非空 ⇔ 真磨砂态
    val chromeTranslucent = translucent
        ?: (scaffoldStyle.translucentChrome && scaffoldStyle.bottomBarVariant == TopBarVariant.Translucent)
    val blurState = if (chromeTranslucent) hazeState else null

    NavigationBar(
        modifier = blurState?.let { state ->
            modifier.hazeChild(
                state = state,
                shape = RectangleShape,
                style = hazeChromeStyle(spec, surfaceColor),
            )
        } ?: modifier,
        containerColor = when {
            blurState != null -> Color.Transparent // 视觉由 hazeChild 的 tint 提供
            chromeTranslucent -> surfaceColor.copy(alpha = spec.alpha)
            else -> surfaceColor
        },
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        tabs.forEach { tab ->
            val selected = tab.route == currentRoute
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    MmaIcon(
                        iconRes = tab.iconRes,
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
