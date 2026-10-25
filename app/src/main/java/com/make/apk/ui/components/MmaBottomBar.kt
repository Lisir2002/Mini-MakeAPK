package com.make.apk.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.navigation.MmaTab

/**
 * 统一底栏（三段式槽位的「底栏」段，见 [MmaScreen]）——顶级导航。
 *
 * 仅主页级页面（底栏 tab 对应的根页面）显示；流程子页（构建中/结果/预览）不传底栏槽位。
 * 选中态：图标/文字走 primary，指示块走 primaryContainer（DESIGN_LANGUAGE §8 底栏规范）。
 * 底栏自带系统导航栏 inset 处理；Paparazzi 下 insets 为 0，不影响截图构图。
 *
 * @param tabs tab 清单（[com.make.apk.ui.navigation.DefaultMmaTabs] 或用户自定义过滤/重排后的列表）。
 * @param currentRoute 当前路由（NavHost 观测），与 tab.route 精确匹配判定选中态。
 * @param onTabSelected 选中回调（由调用方执行导航，本组件不做导航决策）。
 */
@Composable
fun MmaBottomBar(
    tabs: List<MmaTab>,
    currentRoute: String?,
    onTabSelected: (MmaTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
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
