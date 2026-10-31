package com.make.apk.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.make.apk.data.model.LogEntry
import com.make.apk.logging.LogDetailFormatter
import com.make.apk.ui.components.LogLevelBadge
import com.make.apk.ui.components.MmaTextButton
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.Spacing

/**
 * 单条日志的**完整详情**（底部弹层）。
 *
 * 为什么用弹层而不是跳新页：日志详情是"看一眼就走"的上下文查阅，
 * 跳页会打断用户的翻列表节奏（返回后还得重新定位）。
 *
 * 结构：本文件只做「容器」，内容在 [LogDetailContent]——把 ModalBottomSheet 与内容拆开，
 * 内容才能进 Paparazzi 快照（弹层类组件在 layoutlib 下无法稳定渲染，这也是此前
 * 日志详情 0 张基线、设计缺陷一路无人发现的原因）。
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
        LogDetailContent(
            entry = entry,
            onCopy = { onCopy(LogDetailFormatter.fullText(entry)) },
            onDismiss = onDismiss,
        )
    }
}

/**
 * 详情内容（**无弹层容器，可直接截图**）。分区顺序即排障视线顺序：
 *
 * 1. **动作**：复制全部 / 关闭——放**顶部**。历史实现放在内容末尾，长消息或长栈时
 *    必须一路滚到底才够得着关闭；打开即用的动作应该一进来就在眼前。
 * 2. **概览**：级别徽标 + 类别/分组 + 事件 + 完整时间（毫秒级）；
 * 3. **消息**：完整正文，不截断，`SelectionContainer` 便于长按选取片段；
 * 4. **结构化字段**：`fieldsJson` 逐行 `key: value`（空则整块隐藏）；
 * 5. **异常栈**：等宽字体 + 横向滚动（**纵向交给外层统一滚动**）；
 * 6. **上下文**：traceId / sessionId / sequence / threadName / durationMs / success / id。
 *
 * @param entry 日志条目。
 * @param onCopy 复制回调（由 [LogDetailSheet] 注入 `LogDetailFormatter.fullText`）。
 * @param onDismiss 关闭回调。
 */
@Composable
fun LogDetailContent(
    entry: LogEntry,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.space5)
            .padding(bottom = Spacing.space6),
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        // ── 1. 动作（顶部常驻位置，打开即可用）──────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space1, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MmaTextButton(label = "复制全部", onClick = onCopy)
            MmaTextButton(label = "关闭", onClick = onDismiss)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── 2. 概览 ────────────────────────────────────────────────
        OverviewSection(entry = entry)

        // ── 3. 消息（完整，可选中）────────────────────────────────
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

        // ── 4. 结构化字段（空则整块隐藏）──────────────────────────
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

        // ── 5. 异常栈（等宽 + 仅横向滚动）─────────────────────────
        entry.stackTrace?.takeIf { it.isNotBlank() }?.let { stack ->
            DetailSection(title = "异常栈") {
                SelectionContainer {
                    Text(
                        text = stack,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // 这里**只做横向滚动**：栈帧单行超宽要能看全尾部。
                        // 纵向刻意不挂 scroll——外层已是 verticalScroll，同向嵌套滚动
                        // 会让内层吞掉手势，手指在栈区域时页面滚不动（历史缺陷）。
                        // 栈很长时由外层一次性滚过，靠的是「一个滚动容器」原则。
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }

        // ── 6. 上下文 ─────────────────────────────────────────────
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
    }
}

/** 概览区：级别徽标 + 类别/分组 + 事件 + 完整时间。 */
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
            LogLevelBadge(level = entry.levelEnum)
            Text(
                text = entry.categoryEnum?.let { "${it.label} · ${it.group.label}" } ?: entry.category,
                style = typography.labelLarge,
                color = colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            // 事件名是全屏最该先被读到的一行，用已定义的 title 档（18sp/600）。
            // 历史实现用未定义的 titleSmall（回落 M3 默认 14sp），比全局标题矮两档。
            text = entry.event,
            style = typography.titleLarge,
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

/**
 * 分区容器：标题 + 内容。
 *
 * 标题用 `labelLarge`（13sp/600）+ `onSurface`：此前是 `labelMedium`（12sp/500）+
 * `onSurfaceVariant`，与字段名 `bodySmall`（12sp/400）**同字号近同色**，
 * "异常栈"标题和 `traceId` 键看起来像同一层级，分区感整个塌掉。
 * 现在靠 字号 / 字重 / 颜色深浅 三重区分。
 */
@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space2)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

/** 一行 `label: value`：label 最小定宽（冒号对齐），value 撑满剩余并允许换行。 */
@Composable
private fun FieldRow(label: String, value: String) {
    val colorScheme = MaterialTheme.colorScheme
    SelectionContainer {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(
                    min = LocalMmaDesign.current.control.fieldLabelMinWidth,
                ),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface,
                // 无 weight 时超长 value 会把 label 挤窄、冒号再次错位。
                modifier = Modifier.weight(1f),
            )
        }
    }
}
