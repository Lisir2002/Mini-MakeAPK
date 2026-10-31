package com.make.apk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.make.apk.data.model.LogEntry
import com.make.apk.logging.LogDetailFormatter
import com.make.apk.logging.LogLevel
import com.make.apk.ui.components.MmaTextButton
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.Spacing

/**
 * 单条日志的**完整详情**（底部弹层）。
 *
 * 为什么用弹层而不是跳新页：日志详情是"看一眼就走"的上下文查阅，
 * 跳页会打断用户的翻列表节奏（返回后还得重新定位）。
 *
 * 六个分区（顺序即排障视线顺序）：
 * 1. **概览**：级别徽标 + 类别 + 事件 + 完整时间（毫秒级）；
 * 2. **消息**：完整正文，**不截断**，`SelectionContainer` 便于长按选取片段；
 * 3. **结构化字段**：`fieldsJson` 逐行 `key: value`（空则整块隐藏）；
 * 4. **异常栈**：等宽字体 + 横向滚动 + 最大高度限制（长栈不把弹层撑到顶）；
 * 5. **上下文**：traceId / sessionId / sequence / threadName / durationMs / success / id；
 * 6. **底部「复制全部」**：文本与界面同源（[LogDetailFormatter.fullText]）。
 *
 * 本组件零 IO：复制通过 [onCopy] 交回路由层（剪贴板实现在 `logging/LogClipboard.kt`）。
 *
 * @param entry 要展示的日志（已是完整领域对象，详情不做二次查询）。
 * @param onDismiss 关闭回调（下滑 / 点遮罩 / 点外部）。
 * @param onCopy 复制回调，参数为整条日志的纯文本。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDetailSheet(
    entry: LogEntry,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.space5)
                .padding(bottom = Spacing.space6),
            verticalArrangement = Arrangement.spacedBy(Spacing.space4),
        ) {
            // ── 1. 概览 ────────────────────────────────────────────────
            OverviewSection(entry = entry)

            // ── 2. 消息（完整，可选中）────────────────────────────────
            entry.message?.takeIf { it.isNotBlank() }?.let { message ->
                DetailSection(title = "消息") {
                    SelectionContainer {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // ── 3. 结构化字段（空则整块隐藏）──────────────────────────
            val fields = LogDetailFormatter.formatFields(entry.fieldsJson)
            if (fields.isNotEmpty()) {
                DetailSection(title = "结构化字段") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space1)) {
                        fields.forEach { (key, value) ->
                            FieldRow(label = key, value = value)
                        }
                    }
                }
            }

            // ── 4. 异常栈（等宽 + 横向滚动 + 高度上限）────────────────
            entry.stackTrace?.takeIf { it.isNotBlank() }?.let { stack ->
                DetailSection(title = "异常栈") {
                    SelectionContainer {
                        Text(
                            text = stack,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            // 长栈单行超宽 → 横向滚动，避免逐行硬折行导致栈帧错位。
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = Spacing.space7 * 8)
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState()),
                        )
                    }
                }
            }

            // ── 5. 上下文 ─────────────────────────────────────────────
            DetailSection(title = "上下文") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.space1)) {
                    FieldRow(label = "traceId", value = entry.traceId)
                    FieldRow(label = "sessionId", value = entry.sessionId)
                    FieldRow(label = "sequence", value = entry.sequence.toString())
                    FieldRow(label = "threadName", value = entry.threadName)
                    entry.durationMs?.let { FieldRow(label = "durationMs", value = it.toString()) }
                    entry.success?.let { FieldRow(label = "success", value = it.toString()) }
                    FieldRow(label = "id", value = entry.id.toString())
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── 6. 底部动作 ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MmaTextButton(
                    label = "复制全部",
                    onClick = { onCopy(LogDetailFormatter.fullText(entry)) },
                )
                MmaTextButton(label = "关闭", onClick = onDismiss)
            }
        }
    }
}

/** 概览区：级别徽标 + 类别 / 分组 + 事件 + 完整时间。 */
@Composable
private fun OverviewSection(entry: LogEntry) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space2)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailLevelBadge(levelEnum = entry.levelEnum)
            Text(
                text = entry.categoryEnum?.let { "${it.label} · ${it.group.label}" } ?: entry.category,
                style = typography.labelLarge,
                color = colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = entry.event,
            style = typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
        )
        Text(
            text = LogDetailFormatter.formatTimestamp(entry.timestampMs),
            style = typography.labelMedium,
            color = colorScheme.onSurfaceVariant,
        )
    }
}

/** 分区容器：小标题 + 内容，统一间距与分隔线。 */
@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space2)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/** 一行 `label: value`（label 定宽对齐，value 可换行）。 */
@Composable
private fun FieldRow(label: String, value: String) {
    SelectionContainer {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 详情页级别徽标（与列表行同源语义：按严重度取容器色）。 */
@Composable
private fun DetailLevelBadge(levelEnum: LogLevel?) {
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

