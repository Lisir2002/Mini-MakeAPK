package com.make.apk.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.icons.MmaIcons
import com.make.apk.ui.theme.Spacing
import com.make.apk.ui.LocalHazeState
import dev.chrisbanes.haze.HazeState

/**
 * 统一顶栏（三段式槽位的「顶栏」段，见 [MmaScreen]）。
 *
 * 收敛历史上 History/Preview 两处各自手写且已漂移的「返回+标题」Row，是顶栏的唯一事实来源。
 * - 不采用 M3 `TopAppBar`：其仍带 `@ExperimentalMaterial3Api`，OptIn 会沿调用链扩散到全部页面；
 *   且默认样式面向 M3 规范，需多层定制才能贴近本项目「可爱圆润、无分隔线、暖背景」的设计语言。
 * - 状态栏避让：`statusBarsPadding()`（foundation 稳定 API）。
 * - [onBack] 为 null 时只显示标题（顶级页面，如首页/历史）；非空时显示返回按钮（子页面）。
 *
 * @param translucent 磨砂开关（默认 false——截图路径零影响）；true 且 [hazeState] 非空时走真磨砂，
 *   否则半透明近似。磨砂源由 App 壳层统一提供（见 [com.make.apk.ui.LocalHazeState]）。
 * @param hazeState Haze 捕获状态；默认读 [com.make.apk.ui.LocalHazeState]（截图测试不提供即为 null）。
 */
@Composable
fun MmaTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    translucent: Boolean = false,
    hazeState: HazeState? = LocalHazeState.current,
    actions: @Composable RowScope.() -> Unit = {},
) {
    MmaTranslucentSurface(
        modifier = modifier.fillMaxWidth(),
        translucent = translucent,
        hazeState = hazeState,
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .heightIn(min = 56.dp)
                .padding(horizontal = Spacing.space2, vertical = Spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    MmaIcon(
                        iconRes = MmaIcons.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.space1))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.weight(1f))
            actions()
        }
    }
}
