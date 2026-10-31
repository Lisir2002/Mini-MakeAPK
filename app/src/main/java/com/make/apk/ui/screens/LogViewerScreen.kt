package com.make.apk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.make.apk.data.model.LogEntry
import com.make.apk.logging.LogGroup
import com.make.apk.logging.LogLevel
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.components.MmaChip
import com.make.apk.ui.components.MmaScreen
import com.make.apk.ui.components.MmaTextButton
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.Spacing
import com.make.apk.ui.viewmodel.LogFilterState
import com.make.apk.ui.viewmodel.LogViewerCounts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 运行日志页（全量历史分页 + 单条详情）。
 *
 * 自上而下四层：
 * 1. **外带入口**：打开日志目录 / 导出诊断包（纯事件）；
 * 2. **分组汇总**：问题 / 行为 / 诊断 / 安全 四组计数（SQL 全量聚合，不随分页窗口变化）；
 * 3. **筛选芯片**：分组 + 级别（语义源 [LogGroup] / [LogLevel]）；
 * 4. **条目列表**：Paging3 分页，行内只显示摘要；完整内容在 [LogDetailSheet]。
 *
 * 三级加载态（Paging3 的 refresh / append 两阶段各自成态）：
 * - refresh Loading 且无数据 → "正在读取日志…"；
 * - refresh Error → 错误文案 + 「重试」；
 * - append Loading → 列表末尾"正在加载更多…"；
 * - append Error → 列表末尾"加载失败，点击重试"；
 * - 空库/空筛选结果 → "暂无匹配的日志"。
 *
 * 页面零 IO / 零 Intent / 零 Clipboard：磁盘与剪贴板一律在路由层
 * （见 `MmaNavHost`），这样截图测试可直接渲染本页。
 */
@Composable
fun LogViewerScreen(
    logs: LazyPagingItems<LogEntry>,
    counts: LogViewerCounts,
    filters: LogFilterState,
    onBack: () -> Unit,
    onToggleLevel: (LogLevel) -> Unit,
    onToggleGroup: (LogGroup) -> Unit,
    onClearFilters: () -> Unit,
    onOpenLogDirectory: () -> Unit,
    onExportLogs: () -> Unit,
    onOpenDetail: (LogEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val refreshState = logs.loadState.refresh

    MmaScreen(
        title = "运行日志",
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = Spacing.space5,
                vertical = Spacing.space4,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            item(key = "actions") {
                LogActionRow(
                    onOpenLogDirectory = onOpenLogDirectory,
                    onExportLogs = onExportLogs,
                )
            }
            item(key = "summary") {
                GroupSummaryRow(counts = counts)
            }
            item(key = "filters") {
                FilterRow(
                    filters = filters,
                    onToggleLevel = onToggleLevel,
                    onToggleGroup = onToggleGroup,
                    onClearFilters = onClearFilters,
                )
            }

            when {
                // 首次加载（或筛选切换后的重新加载）：还没有任何条目可显示。
                refreshState is LoadState.Loading && logs.itemCount == 0 -> item(key = "loading") {
                    StatusLine(text = "正在读取日志…")
                }

                // 刷新失败：给出可点的重试，而不是干等。
                refreshState is LoadState.Error -> item(key = "error") {
                    StatusLine(
                        text = "读取日志失败，请重试",
                        actionLabel = "重试",
                        onAction = { logs.retry() },
                    )
                }

                // 空态（含"筛选后无匹配"）。
                logs.itemCount == 0 -> item(key = "empty") {
                    StatusLine(text = "暂无匹配的日志")
                }

                else -> {
                    item(key = "list_header") {
                        Text(
                            text = "共 ${counts.total} 条",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(
                        count = logs.itemCount,
                        key = logs.itemKey { it.id },
                    ) { index ->
                        // 占位/被移除的位置返回 null（enablePlaceholders=false 下是移除态）。
                        logs[index]?.let { entry ->
                            LogRow(entry = entry, onClick = { onOpenDetail(entry) })
                        }
                    }
                    when (logs.loadState.append) {
                        is LoadState.Loading -> item(key = "append_loading") {
                            StatusLine(text = "正在加载更多…")
                        }

                        is LoadState.Error -> item(key = "append_error") {
                            StatusLine(
                                text = "加载失败，点击重试",
                                actionLabel = "重试",
                                onAction = { logs.retry() },
                            )
                        }

                        else -> Unit
                    }
                }
            }
        }
    }
}

/** 一行状态文案（可选内联「重试」按钮）。 */
@Composable
private fun StatusLine(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            MmaTextButton(label = actionLabel, onClick = onAction)
        }
    }
}

/** 外带入口行：「打开日志目录」+「导出诊断包」（纯事件，样式与筛选行同源）。 */
@Composable
private fun LogActionRow(
    onOpenLogDirectory: () -> Unit,
    onExportLogs: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MmaTextButton(label = "打开日志目录", onClick = onOpenLogDirectory)
        MmaTextButton(label = "导出诊断包", onClick = onExportLogs)
    }
}

/** 四大分组计数卡（问题 → 行为 → 诊断 → 安全，顺序即 [LogGroup.order]）。 */
@Composable
private fun GroupSummaryRow(counts: LogViewerCounts) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space3),
    ) {
        LogGroup.entries.sortedBy { it.order }.forEach { group ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = Spacing.space3, vertical = Spacing.space3),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = (counts.groupCounts[group] ?: 0).toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = group.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 筛选行：分组芯片 + 级别芯片 + 清除入口（有筛选才显示）。 */
@Composable
private fun FilterRow(
    filters: LogFilterState,
    onToggleLevel: (LogLevel) -> Unit,
    onToggleGroup: (LogGroup) -> Unit,
    onClearFilters: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space2)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
        ) {
            LogGroup.entries.sortedBy { it.order }.forEach { group ->
                MmaChip(
                    label = group.label,
                    selected = group in filters.groups,
                    onClick = { onToggleGroup(group) },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogLevel.entries.sortedByDescending { it.severityWeight }.forEach { level ->
                MmaChip(
                    label = level.label,
                    selected = level in filters.levels,
                    onClick = { onToggleLevel(level) },
                )
            }
            if (filters.hasFilters) {
                MmaTextButton(label = "清除", onClick = onClearFilters) { contentColor ->
                    Text("清除筛选", color = contentColor)
                }
            }
        }
    }
}

/**
 * 单条日志摘要行：级别徽标 + 类别 + 事件 + 消息（最多 3 行）+ 时间。
 *
 * **行内只放摘要**——`fieldsJson` / `stackTrace` / trace 上下文全部留给详情页，
 * 这样长列表滚动开销恒定，也不会把卡片撑成半屏。
 */
@Composable
private fun LogRow(entry: LogEntry, onClick: () -> Unit) {
    CuteCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(Spacing.space4),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space3),
            verticalAlignment = Alignment.Top,
        ) {
            LevelBadge(levelEnum = entry.levelEnum)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.space1),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space2)) {
                    Text(
                        text = entry.categoryEnum?.label ?: entry.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = entry.event,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                entry.message?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = formatTimestamp(entry.timestampMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 级别徽标：按严重度取语义容器色（令牌化，无硬编码色）。 */
@Composable
private fun LevelBadge(levelEnum: LogLevel?) {
    val colorScheme = MaterialTheme.colorScheme
    val (container, content) = when (levelEnum?.severityWeight) {
        in 4..5 -> colorScheme.errorContainer to colorScheme.onErrorContainer
        3 -> colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer
        2 -> colorScheme.secondaryContainer to colorScheme.onSecondaryContainer
        else -> colorScheme.surfaceVariant to colorScheme.onSurfaceVariant
    }
    Text(
        text = levelEnum?.label ?: "未知",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = content,
        modifier = Modifier
            .clip(ControlShapes.pill)
            .background(container)
            .padding(horizontal = Spacing.space2, vertical = Spacing.space1),
    )
}

private fun formatTimestamp(timestampMs: Long): String =
    SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
