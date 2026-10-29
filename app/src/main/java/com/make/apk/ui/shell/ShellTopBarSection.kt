package com.make.apk.ui.shell

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.data.model.ShellTopBar

/**
 * 壳顶栏区（[ShellTopBar] → 原生顶栏等价物）。
 *
 * ## 约束
 *
 * - **无状态**：所有交互出口化为回调（[onBack] / [onShellAction]），本组件不持有任何选择态；
 * - **不读 app 主题**：颜色 / 字体 / 尺寸一律取自 [ShellRenderEnv]；
 * - 图标通过 [ShellIconResolver] 解析逻辑键 → drawable，壳内不出现任何 res id 字面量。
 *
 * @param topBar 顶栏契约（`enabled = false` 时本组件不应被渲染）。
 * @param env 渲染环境（颜色 / 字体 / 尺寸）。
 * @param title 标题回退值（项目名）；`titleOverride` 非空时以其为准。
 * @param iconResolver 逻辑图标键解析器。
 * @param onBack 返回按钮回调；null = 不显示返回按钮。
 * @param onShellAction 动作按钮回调（宿主决定如何执行动作，如 `NavigateUrl` 交给 WebView 加载）。
 * @param onOpenDrawer 抽屉入口（汉堡按钮）回调；null = 不显示抽屉入口。
 *   由 [com.make.apk.data.model.ShellDrawer.enabled] 决定宿主是否传入，
 *   因此"没有配置抽屉的项目，预览里也绝不会出现汉堡按钮"。
 * @param onResetToEntry 回入口（home 图标）回调；null = 不显示该按钮。
 *   由宿主在「顶栏 tab 区开启」时传入，点击即跳转/复位到入口根 tab。
 */
@Composable
fun ShellTopBarSection(
    topBar: ShellTopBar,
    env: ShellRenderEnv,
    title: String,
    iconResolver: ShellIconResolver,
    onBack: (() -> Unit)?,
    onShellAction: (ShellMenuItem) -> Unit,
    onOpenDrawer: (() -> Unit)? = null,
    onResetToEntry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = env.surface,
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .height(env.topBarHeight)
                .padding(horizontal = env.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onOpenDrawer != null) {
                // 抽屉入口：仅当契约里抽屉区开启时宿主才传本回调，
                // 故"顶栏是否出现汉堡按钮"完全由 ShellSpec 决定（预览 == 导出）。
                ShellIconButton(
                    iconRes = iconResolver.resolve(MmaShellIconResolver.KEY_MENU),
                    contentDescription = "打开菜单",
                    tint = env.accent,
                    onClick = onOpenDrawer,
                )
                Spacer(modifier = Modifier.width(env.spaceXs))
            }
            if (onBack != null && topBar.showBackButton) {
                ShellIconButton(
                    iconRes = iconResolver.resolve(MmaShellIconResolver.KEY_BACK),
                    contentDescription = "返回",
                    tint = env.accent,
                    onClick = onBack,
                )
                Spacer(modifier = Modifier.width(env.spaceXs))
            }
            if (onResetToEntry != null) {
                // 回入口：仅当宿主传入时显示（宿主在顶栏 tab 区开启时才传），
                // 与「返回」「抽屉」并列构成左侧导航簇。
                ShellIconButton(
                    iconRes = iconResolver.resolve(MmaShellIconResolver.KEY_HOME),
                    contentDescription = "回入口",
                    tint = env.accent,
                    onClick = onResetToEntry,
                )
                Spacer(modifier = Modifier.width(env.spaceXs))
            }
            if (topBar.showTitle) {
                Text(
                    text = topBar.titleOverride ?: title,
                    style = env.typography.titleLarge,
                    color = env.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            topBar.actions
                .filter { it.visible }
                .forEach { action ->
                    ShellIconButton(
                        iconRes = iconResolver.resolve(action.iconRef),
                        contentDescription = action.title,
                        tint = env.onSurface,
                        onClick = { onShellAction(action) },
                    )
                }
        }
    }
}

/**
 * 壳内统一的图标按钮（图标缺失时退化为空按钮但仍保留可点击区域与描述）。
 *
 * 抽出来是为了让顶栏动作按钮与将来其它区的图标按钮**尺寸一致**，
 * 且避免各处重复写 `IconButton` + `painterResource` 的样板。
 */
@Composable
internal fun ShellIconButton(
    iconRes: Int?,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(SHELL_ICON_SIZE),
            )
        }
    }
}

/** 壳图标尺寸（与 app 侧 MmaIcon 默认一致：24dp）。 */
private val SHELL_ICON_SIZE = 24.dp
