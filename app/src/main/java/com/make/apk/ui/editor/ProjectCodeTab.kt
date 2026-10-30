package com.make.apk.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.make.apk.data.model.ProjectFile
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.components.DirectoryBrowser
import com.make.apk.ui.components.DirectoryBrowserCallbacks
import com.make.apk.ui.fs.FsItem
import com.make.apk.ui.fs.PasteRequest
import com.make.apk.ui.fs.RawFsEntry
import com.make.apk.ui.fs.buildFsItems
import com.make.apk.ui.fs.utf8ByteLength
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.Spacing

/**
 * 「项目代码」页（HTML 类专属）——**薄适配层**。
 *
 * ## 定位（本次重构）
 * 真实的目录树构建、路径校验、复制 / 改名 / 删除等**纯逻辑**已全部下沉到
 * [com.make.apk.ui.fs]（中性模型 + 纯函数）与可复用组件 [DirectoryBrowser]，
 * 本页不再持有任何树结构。它只做两件事：
 *
 * 1. 把业务数据（`entryFile` + `List<ProjectFile>`）转换成组件输入的
 *    中性节点列表 [FsItem]（入口 HTML 视作一个带「入口」徽标的文件并入列表）；
 * 2. 把组件的操作回调转接到 ViewModel（新建 / 重命名 / 删除 / 粘贴）。
 *
 * 对外参数语义尽量保持：
 * - [entryFile] / [files]：数据源；
 * - [onOpenFile]：打开文件编辑器（导航在 MmaNavHost 接线，本页不动）；
 * - [onFileAdded] / [onFolderAdded]：新建文件 / 文件夹（路径相对根）；
 * - [onFileRenamed]：重命名 / 移动（前缀替换，由 VM 落库）；
 * - [onFileDeleted]：删除（目录递归由 VM 统一处理）；
 * - [onFilesPasted]：粘贴（复制 / 剪切落库）。
 *
 * **空文件夹约定**：旧数据若无尾 `/` 目录条目，一切行为与旧版一致（见下方转换，只映射
 * 现有文件，目录由路径隐式推导）；新增空文件夹经 VM 落库为 `name/` 空条目。
 */
@Composable
fun ProjectCodeTab(
    entryFile: String,
    files: List<ProjectFile>,
    onOpenFile: (String) -> Unit,
    onFileAdded: (String) -> Unit,
    onFolderAdded: (String) -> Unit = {},
    onFileRenamed: (String, String) -> Unit = { _, _ -> },
    onFileDeleted: (String) -> Unit,
    onFilesPasted: (PasteRequest) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val items = remember(entryFile, files) {
        buildFsItems(
            entries = files.map { RawFsEntry(name = it.name, sizeBytes = utf8ByteLength(it.content)) },
            entryPath = entryFile,
        )
    }
    val control = LocalMmaDesign.current.control
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    CuteCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
            ) {
                Text(
                    text = TAB_TITLE,
                    style = typography.titleMedium,
                    color = colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = summarize(items),
                    style = typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
            DirectoryBrowser(
                items = items,
                callbacks = DirectoryBrowserCallbacks(
                    onOpenFile = onOpenFile,
                    onAddFile = onFileAdded,
                    onAddFolder = onFolderAdded,
                    onRename = onFileRenamed,
                    onDelete = onFileDeleted,
                    onPaste = onFilesPasted,
                ),
            )
        }
    }
}

/** 统计文案：`N 个文件 · M 个文件夹`（入口计入文件）。 */
private fun summarize(items: List<FsItem>): String {
    val files = items.count { !it.isDirectory }
    val dirs = items.count { it.isDirectory }
    return "$files 个文件 · $dirs 个文件夹"
}

// ── 常量（中文文案集中） ──────────────────────────────────────────────

private const val TAB_TITLE: String = "项目文件"
