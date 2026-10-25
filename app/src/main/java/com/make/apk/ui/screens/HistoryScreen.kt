package com.make.apk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.make.apk.data.model.BuildRecord
import com.make.apk.data.model.BuildStatus
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.components.MmaScreen
import com.make.apk.ui.theme.Spacing

/**
 * 构建历史页（**无状态**：列表由调用方观察数据层后传入）。
 *
 * 主页级页面：顶栏仅标题（无返回），底栏为共享的顶级导航（见 MmaNavHost）。
 * 数据来自加密 SQLite（Room + SQLCipher），表变更自动推送。
 * 删除按钮保留文字而非纯图标，确保可访问性（WCAG 2.2 AA：可读标签 + 48dp 触控区）。
 */
@Composable
fun HistoryScreen(
    records: List<BuildRecord>,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    MmaScreen(
        title = "构建历史",
        bottomBar = bottomBar,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.space5),
            verticalArrangement = Arrangement.spacedBy(Spacing.space4),
        ) {
            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "还没有构建记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space3),
                ) {
                    items(items = records, key = { it.id }) { record ->
                        HistoryRow(
                            record = record,
                            onOpen = { onOpen(record.id) },
                            onDelete = { onDelete(record.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    record: BuildRecord,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CuteCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space2),
        ) {
            Text(
                record.sourceLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    statusLabel(record.status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space1)) {
                    TextButton(onClick = onOpen) { Text("预览") }
                    TextButton(onClick = onDelete) { Text("删除") }
                }
            }
        }
    }
}

private fun statusLabel(status: BuildStatus): String = when (status) {
    BuildStatus.QUEUED -> "排队中"
    BuildStatus.RUNNING -> "构建中"
    BuildStatus.SUCCESS -> "成功"
    BuildStatus.FAILED -> "失败"
    BuildStatus.CANCELLED -> "已取消"
}
