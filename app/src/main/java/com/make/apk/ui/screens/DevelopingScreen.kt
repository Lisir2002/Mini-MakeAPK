package com.make.apk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.components.MmaScreen
import com.make.apk.ui.icons.MmaIcons
import com.make.apk.ui.theme.Spacing

/**
 * 开发中页——优雅占位（不误导、不空白）。
 *
 * 结构：居中大图标 + 一句话现状 + 路线图预告卡（预告而非承诺，
 * 文案明确「在建」避免用户误以为功能可用）。
 *
 * 主页级页面：顶栏仅标题（无返回），底栏为共享的顶级导航（见 MmaNavHost）。
 */
@Composable
fun DevelopingScreen(
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    MmaScreen(
        title = "开发中",
        bottomBar = bottomBar,
        modifier = modifier,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.space5),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.space4),
            ) {
                MmaIcon(
                    iconRes = MmaIcons.Developing,
                    contentDescription = null,
                    size = 64.dp,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "这里还在施工",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "一些好玩的正在路上，先不剧透。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CuteCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(Spacing.space4),
                        verticalArrangement = Arrangement.spacedBy(Spacing.space2),
                    ) {
                        Text(
                            "路线图预告",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "· 自定义主题（配色 / 圆角 / 磨砂）\n" +
                                "· 设置系统与数据备份\n" +
                                "· 云端构建（实验性）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
