package com.make.apk.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.make.apk.data.model.BuildRecord
import com.make.apk.data.model.BuildStatus
import com.make.apk.ui.components.CuteButton
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.components.MmaScreen
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.icons.MmaIcons
import com.make.apk.ui.theme.Spacing

/**
 * 项目页（startDestination）——纯项目列表。
 *
 * 布局改造（2026-10-26 rc4）：从「输入区 + 列表」合一一屏，改为**以列表为主体**——
 * 首页只呈现项目列表，构建入口收进右上角「＋」按钮，点击展开输入框（粘贴网址变 App），
 * 不构建时整屏都是列表，焦点更聚焦。空态给出「点右上角 ＋ 新建」引导。
 *
 * **无状态约定**：列表 records 由调用方（MmaNavHost 观察加密数据库）传入；
 * 输入框内容内部 remember，构建态用 showInput 切换显隐（半无状态，测试可经真实交互验证）。
 *
 * 主页级页面：顶栏仅标题（无返回），底栏为共享的顶级导航（见 MmaNavHost）。
 */
@Composable
fun ProjectsScreen(
    records: List<BuildRecord>,
    onBuild: (String) -> Unit,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    var showInput by remember { mutableStateOf(false) }

    MmaScreen(
        title = "项目",
        bottomBar = bottomBar,
        modifier = modifier,
        // 构建入口：右上角「＋」按钮，点击展开/收起输入框（纯列表 ⇄ 新建）。
        actions = {
            IconButton(onClick = { showInput = !showInput }) {
                MmaIcon(
                    iconRes = MmaIcons.Add,
                    contentDescription = "新建项目",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ── 新建输入区（按需展开，默认隐藏，列表占满整屏）──────────────
            AnimatedVisibility(visible = showInput) {
                Column(
                    modifier = Modifier.padding(
                        start = Spacing.space5,
                        end = Spacing.space5,
                        top = Spacing.space3,
                        bottom = Spacing.space2,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space3),
                ) {
                    Text(
                        "新建项目",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        placeholder = {
                            Text(
                                "https://… 或 <html>…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                        minLines = 2,
                    )
                    CuteButton(
                        text = "变成 App ↘",
                        onClick = {
                            onBuild(input)
                            input = ""
                            showInput = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ── 项目列表（滚动）/ 空态 ─────────────────────────────────
            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.space2),
                    ) {
                        MmaIcon(
                            iconRes = MmaIcons.Folder,
                            contentDescription = null,
                            size = 36.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "还没有项目，点右上角 ＋ 新建一个",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        horizontal = Spacing.space5,
                        vertical = Spacing.space2,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space3),
                ) {
                    items(items = records, key = { it.id }) { record ->
                        ProjectRow(
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
private fun ProjectRow(
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
