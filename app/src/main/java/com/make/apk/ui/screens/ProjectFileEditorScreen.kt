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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.make.apk.ui.code.CodeEditorCore
import com.make.apk.ui.components.MmaScreen
import com.make.apk.ui.components.MmaTextButton
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.icons.MmaIcons
import com.make.apk.ui.theme.Spacing

/**
 * 文件编辑器 / 查看器页（rc11）——项目代码页点击文件进入的独立子页。
 *
 * ## 自研内核
 *
 * 编辑与查看共用 `com.make.apk.ui.code.CodeEditorCore`（行号槽 + 正则高亮 +
 * VisualTransformation），区别仅在 `readOnly`：
 * - **编辑态**：可改内容，顶栏「保存」落库（保存后返回项目代码页）；
 * - **查看态**：纯只读渲染（"查看器"），长内容 / 借阅场景免误触。
 *
 * 页面**无业务状态**：内容与保存由 VM / 调用方负责，本页只持「模式 + 草稿」两个
 * 交互态（重建丢失等价于未保存，无副作用）。
 */
@Composable
fun ProjectFileEditorScreen(
    fileName: String,
    content: String,
    loading: Boolean,
    notFound: Boolean,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 查看器 / 编辑器模式（交互态：初值查看器，编辑需显式进入——减少误触改码）。
    var editorMode by rememberSaveable { mutableStateOf(Mode.VIEWER) }
    // 草稿：随 VM 内容初始化；保存后 VM 推回同值，不会打断输入。
    var draft by remember(content) { mutableStateOf(content) }

    MmaScreen(
        title = fileName.ifBlank { "文件" },
        onBack = onBack,
        modifier = modifier,
        actions = {
            MmaTextButton(
                label = if (editorMode == Mode.EDITOR) MODE_EDITING_LABEL else MODE_EDIT_LABEL,
                onClick = {
                    editorMode = if (editorMode == Mode.EDITOR) Mode.VIEWER else Mode.EDITOR
                },
            )
            if (editorMode == Mode.EDITOR) {
                MmaTextButton(
                    label = SAVE_LABEL,
                    onClick = { onSave(draft) },
                    enabled = draft != content,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                loading -> LoadingPlaceholder()
                notFound -> NotFoundPlaceholder()
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.space5, vertical = Spacing.space3),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space2),
                ) {
                    Text(
                        text = if (editorMode == Mode.EDITOR) EDITOR_HINT else VIEWER_HINT,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CodeEditorCore(
                        value = draft,
                        onValueChange = { draft = it },
                        fileName = fileName,
                        readOnly = editorMode == Mode.VIEWER,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }
    }
}

/** 编辑器模式（查看器 / 编辑器）。 */
private enum class Mode { VIEWER, EDITOR }

/** 加载占位。 */
@Composable
private fun LoadingPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = LOADING_TEXT,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 文件不存在占位。 */
@Composable
private fun NotFoundPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        com.make.apk.ui.icons.MmaIcon(
            iconRes = MmaIcons.SentimentDissatisfied,
            contentDescription = null,
            size = 48.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = NOT_FOUND_TEXT,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val MODE_EDIT_LABEL: String = "编辑"
private const val MODE_EDITING_LABEL: String = "查看"
private const val SAVE_LABEL: String = "保存"
private const val LOADING_TEXT: String = "正在加载文件…"
private const val NOT_FOUND_TEXT: String = "文件不存在或已被删除"
private const val EDITOR_HINT: String = "编辑中；改完点右上角「保存」落库，切换「查看」即只读预览。"
private const val VIEWER_HINT: String = "查看器（只读）；点右上角「编辑」修改内容。"
