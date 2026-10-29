package com.make.apk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.make.apk.data.model.LogEntry
import com.make.apk.logging.LogGroup
import com.make.apk.logging.LogLevel
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.components.MmaChip
import com.make.apk.ui.components.MmaScreen
import com.make.apk.ui.components.MmaTextButton
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.Spacing
import com.make.apk.ui.viewmodel.LogViewerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 运行日志页（rc9 自研分级展示，用户需求"分级一目了然"）。
 *
 * 三层结构自上而下：
 * 1. **分组汇总**：问题 / 行为 / 诊断 / 安全 四组计数卡（语义源 [LogGroup]）；
 * 2. **筛选芯片**：级别（致命→详细，语义源 [LogLevel.severityWeight]）与分组；
 * 3. **条目列表**：级别徽标（按严重度取语义容器色）+ 类别 + 事件 + 消息 + 时间。
 *
 * 页面零业务判断：分级/分组/文案全部来自 logging 包枚举（单一事实源），
 * 本页只负责把语义映射成语义令牌颜色。
 */
@Composable
fun LogViewerScreen(
    uiState: com.make.apk.ui.viewmodel.LogViewerUiState,
    onBack: () -> Unit,
    onToggleLevel: (LogLevel) -> Unit,
    onToggleGroup: (LogGroup) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MmaScreen(
        title = "运行日志",
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = Spacing.space5,
                vertical = Spacing.space4,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            item(key = "summary") {
                GroupSummaryRow(counts = uiState.groupCounts)
            }
            item(key = "filters") {
                FilterRow(
                    selectedLevels = uiState.selectedLevels,
                    selectedGroups = uiState.selectedGroups,
                    hasFilters = uiState.hasFilters,
                    onToggleLevel = onToggleLevel,
                    onToggleGroup = onToggleGroup,
                    onClearFilters = onClearFilters,
                )
            }
            if (uiState.loading) {
                item(key = "loading") {
                    Text(
                        "正在读取日志…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (uiState.entries.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "暂无匹配的日志",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(uiState.entries, key = { it.id }) { entry ->
                    LogRow(entry = entry)
                }
            }
        }
    }
}

/** 四大分组计数卡（问题 → 行为 → 诊断 → 安全，顺序即 [LogGroup.order]）。 */
@Composable
private fun GroupSummaryRow(counts: Map<LogGroup, Int>) {
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
                    text = counts[group]?.toString() ?: "0",
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
    selectedLevels: Set<LogLevel>,
    selectedGroups: Set<LogGroup>,
    hasFilters: Boolean,
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
                    selected = group in selectedGroups,
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
                    selected = level in selectedLevels,
                    onClick = { onToggleLevel(level) },
                )
            }
            if (hasFilters) {
                MmaTextButton(label = "清除", onClick = onClearFilters) { contentColor ->
                    Text("清除筛选", color = contentColor)
                }
            }
        }
    }
}

/** 单条日志：级别徽标（语义容器色）+ 类别 + 事件/消息 + 时间。 */
@Composable
private fun LogRow(entry: LogEntry) {
    CuteCard(modifier = Modifier.fillMaxWidth()) {
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
