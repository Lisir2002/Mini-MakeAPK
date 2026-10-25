package com.make.apk.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.make.apk.BuildConfig
import com.make.apk.storage.MmaStorage
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.components.MmaScreen
import com.make.apk.ui.icons.MmaIcons
import com.make.apk.ui.theme.Spacing

/** 开源仓库地址（GitHub 主页；版本升级 Phase-2 做站内检测前，先给真实可访入口）。 */
private const val REPO_URL = "https://github.com/Lisir2002/Mini-MakeAPK"

/**
 * 我的页——个人中心。
 *
 * 结构：顶部信息卡（应用名 / 版本号 / MMAFile 存储状态，全部真实读取，
 * 非占位文本）+ 数据驱动条目列表（软件设置 / 关于软件 / 开源仓库 / 版本升级）。
 *
 * 交互现状：开源仓库为唯一真实跳转（系统浏览器 Intent）；其余条目 Phase-2 占位，
 * 点击给轻反馈（由调用方注入 onClick，本页不持有业务逻辑）。
 *
 * 主页级页面：顶栏仅标题（无返回），底栏为共享的顶级导航（见 MmaNavHost）。
 */
@Composable
fun ProfileScreen(
    bottomBar: @Composable () -> Unit = {},
    onFeatureNotReady: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 存储状态查询失败时降级为「未授权」而非崩溃（异常环境如截图测试的
    // layoutlib stub 会缺 PackageManager；真实设备该调用不抛）。
    val storageReady = runCatching { MmaStorage.isAuthorized(context) }.getOrDefault(false)

    val items = listOf(
        ProfileItem(
            iconRes = MmaIcons.Notifications,
            title = "软件设置",
            subtitle = "通知、构建偏好等",
            onClick = { onFeatureNotReady("软件设置") },
        ),
        ProfileItem(
            iconRes = MmaIcons.CheckCircle,
            title = "关于软件",
            subtitle = "版本信息与开源许可",
            onClick = { onFeatureNotReady("关于软件") },
        ),
        ProfileItem(
            iconRes = MmaIcons.Folder,
            title = "开源仓库",
            subtitle = "GitHub：Mini-MakeAPK",
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
                }
            },
        ),
        ProfileItem(
            iconRes = MmaIcons.InstallMobile,
            title = "版本升级",
            subtitle = "当前 ${BuildConfig.VERSION_NAME}",
            onClick = { onFeatureNotReady("版本升级") },
        ),
    )

    MmaScreen(
        title = "我的",
        bottomBar = bottomBar,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.space5),
            verticalArrangement = Arrangement.spacedBy(Spacing.space4),
        ) {
            // ── 顶部信息卡：真实读取，非占位 ──────────────────────────
            CuteCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(Spacing.space4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
                ) {
                    MmaIcon(
                        iconRes = MmaIcons.Folder,
                        contentDescription = null,
                        size = 48.dp,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space1)) {
                        Text(
                            "MMA 打包器",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "版本 ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (storageReady) {
                                "MMAFile 存储：已就绪"
                            } else {
                                "MMAFile 存储：未授权（日志暂存应用私有目录）"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── 条目列表：数据驱动 ────────────────────────────────────
            items.forEach { item ->
                CuteCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = item.onClick,
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.space4),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
                    ) {
                        MmaIcon(
                            iconRes = item.iconRes,
                            contentDescription = null,
                            size = 24.dp,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (item.subtitle != null) {
                                Text(
                                    item.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
