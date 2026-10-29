package com.make.apk.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.make.apk.ui.components.CuteButton
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.components.MmaDialog
import com.make.apk.ui.components.MmaIconAction
import com.make.apk.ui.components.MmaTextButton
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.icons.MmaIcons
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.Spacing

/**
 * 「项目代码」页（HTML 类专属，类型隔离）——**文件目录树浏览**。
 *
 * ## 定位（rc11 重写）
 *
 * 本页只做一件事：展示项目下的文件目录（支持文件夹与各类文件），点击文件进入
 * 独立编辑器 / 查看器页（自研编辑器核心，见 `com.make.apk.ui.code`）。
 * 源码内容一律在编辑器页维护，本页**不再承载任何代码编辑区**。
 *
 * - 入口文件带「入口」徽标（首屏加载文件）；入口文件名仍在「基本信息」卡设置；
 * - 附加文件支持 `css/style.css` 路径形式，按 `/` 分层渲染为可折叠目录；
 * - 新建 / 删除即改项目文件集，保存后随项目落库（预览与导出同源消费）。
 *
 * **校验零判断**：新建文件名的合法性在本页做**结构性**校验（非空 / 无非法字符 /
 * 不重名），不属于表单校验体系（文件名不进 EditField）。
 */
@Composable
fun ProjectCodeTab(
    entryFile: String,
    fileNames: List<String>,
    onOpenFile: (String) -> Unit,
    onFileAdded: (String) -> Unit,
    onFileDeleted: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val tree = remember(entryFile, fileNames) { buildFileTree(entryFile, fileNames) }

    CuteCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            Text(
                text = "项目文件",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = TREE_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DirRows(
                nodes = tree,
                depth = 0,
                expanded = expanded,
                onOpenFile = onOpenFile,
                onRequestDelete = { pendingDelete = it },
            )
            CuteButton(
                text = "新建文件",
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showAddDialog) {
        NewFileDialog(
            existingNames = fileNames,
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                onFileAdded(name)
                showAddDialog = false
            },
        )
    }

    pendingDelete?.let { name ->
        MmaDialog(
            onDismissRequest = { pendingDelete = null },
            title = DELETE_FILE_TITLE,
            confirmLabel = DELETE_FILE_CONFIRM,
            onConfirm = {
                onFileDeleted(name)
                pendingDelete = null
            },
            destructive = true,
            content = {
                Text(
                    text = "「$name」将从项目中移除，此操作不可撤销。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

// ── 文件树 ──────────────────────────────────────────────────────────

/** 文件条目（叶子）。 */
private data class FileLeaf(
    val name: String,
    val path: String,
    val isEntry: Boolean,
)

/** 目录节点（可折叠，含子目录与文件）。 */
private data class DirNode(
    val name: String,
    val path: String,
    val dirs: List<DirNode>,
    val files: List<FileLeaf>,
)

/** 扁平文件名列表 → 层级树（文件夹在前，同层按名排序；入口文件必存在）。 */
private fun buildFileTree(entryFile: String, fileNames: List<String>): List<DirNode> {
    val allNames = buildList {
        add(entryFile)
        addAll(fileNames.filter { it != entryFile })
    }
    fun build(paths: List<String>, prefix: String, depth: Int): List<DirNode> {
        if (depth > MAX_TREE_DEPTH || paths.isEmpty()) return emptyList()
        val groups = paths.groupBy { path ->
            val rest = path.removePrefix(prefix).trimStart('/')
            rest.substringBefore('/')
        }
        return groups.entries
            .sortedBy { it.key.lowercase() }
            .map { (key, children) ->
                val nodePath = if (prefix.isEmpty()) key else "$prefix/$key"
                if (children.size == 1 && children.first() == nodePath) {
                    // 纯文件（无子路径）：仍然建模为 DirNode，files 挂自身叶子。
                    DirNode(
                        name = key,
                        path = nodePath,
                        dirs = emptyList(),
                        files = listOf(FileLeaf(key, nodePath, isEntry = nodePath == entryFile)),
                    )
                } else {
                    DirNode(
                        name = key,
                        path = nodePath,
                        dirs = build(children.filter { it != nodePath }, nodePath, depth + 1),
                        files = children.filter { it == nodePath }
                            .map { FileLeaf(key, nodePath, isEntry = nodePath == entryFile) },
                    )
                }
            }
    }
    return build(allNames, prefix = "", depth = 0)
}

/** 树行（递归）：目录节点可折叠展开，文件叶子可点击打开 + 删除（入口除外）。 */
@Composable
private fun DirRows(
    nodes: List<DirNode>,
    depth: Int,
    expanded: MutableMap<String, Boolean>,
    onOpenFile: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
) {
    nodes.forEach { node ->
        // 纯文件（无子目录、仅自身叶子）直接渲染文件行，不套文件夹行。
        if (node.dirs.isEmpty() && node.files.size == 1 && node.files.first().name == node.name) {
            FileRow(node.files.first(), depth, onOpenFile, onRequestDelete)
            return@forEach
        }
        val open = expanded[node.path] ?: true
        FolderRow(node = node, depth = depth, open = open, onToggle = { expanded[node.path] = !open })
        if (open) {
            DirRows(node.dirs, depth + 1, expanded, onOpenFile, onRequestDelete)
            node.files.forEach { leaf ->
                FileRow(leaf, depth + 1, onOpenFile, onRequestDelete)
            }
        }
    }
}

/** 文件夹行。 */
@Composable
private fun FolderRow(node: DirNode, depth: Int, open: Boolean, onToggle: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ControlShapes.itemSingle)
            .clickable(onClick = onToggle)
            .padding(start = Spacing.space2 + (LocalMmaDesign.current.control.menuItemIcon) * depth, top = Spacing.space2, bottom = Spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        MmaIcon(
            iconRes = MmaIcons.Folder,
            contentDescription = null,
            size = LocalMmaDesign.current.control.menuItemIcon,
            tint = colorScheme.primary,
        )
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (open) "收起" else "展开",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
        )
    }
}

/** 文件行：点击打开编辑器 / 查看器；入口带徽标；非入口可删除。 */
@Composable
private fun FileRow(
    node: FileLeaf,
    depth: Int,
    onOpenFile: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ControlShapes.itemSingle)
            .background(colorScheme.surfaceVariant)
            .clickable { onOpenFile(node.path) }
            .padding(
                start = Spacing.space2 + LocalMmaDesign.current.control.menuItemIcon * depth,
                top = Spacing.space2,
                bottom = Spacing.space2,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        MmaIcon(
            iconRes = MmaIcons.File,
            contentDescription = null,
            size = LocalMmaDesign.current.control.menuItemIcon,
            tint = colorScheme.onSurfaceVariant,
        )
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (node.isEntry) {
            Text(
                text = ENTRY_BADGE,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onPrimaryContainer,
            )
        } else {
            MmaIconAction(
                iconRes = MmaIcons.Delete,
                contentDescription = "删除文件 ${node.path}",
                onClick = { onRequestDelete(node.path) },
                tint = colorScheme.error,
            )
        }
    }
}

/** 新建文件弹窗：名字 + 结构性校验（非空 / 无非法字符 / 不重名）。 */
@Composable
private fun NewFileDialog(
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim().trimStart('/')
    val error = when {
        trimmed.isEmpty() -> NAME_EMPTY_MESSAGE
        trimmed.contains('\\') || trimmed.contains("..") -> NAME_ILLEGAL_MESSAGE
        existingNames.any { it.equals(trimmed, ignoreCase = true) } -> NAME_DUPLICATE_MESSAGE
        else -> null
    }
    MmaDialog(
        onDismissRequest = onDismiss,
        title = NEW_FILE_TITLE,
        confirmLabel = NEW_FILE_CONFIRM,
        onConfirm = { onConfirm(trimmed) },
        confirmEnabled = error == null,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.space2)) {
                ProjectTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = NEW_FILE_LABEL,
                    placeholder = NEW_FILE_PLACEHOLDER,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    message = error?.let { FieldMessage(it, FieldMessage.Severity.ERROR) },
                )
                Text(
                    text = NEW_FILE_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

// ── 常量 ────────────────────────────────────────────────────────────

/** 目录树最大深度（防病态路径撑爆渲染；超出按文件平铺截断）。 */
private const val MAX_TREE_DEPTH: Int = 12

private const val TREE_HINT: String =
    "点击文件进入编辑器 / 查看器；文件夹自动按路径折叠。附加文件（css / js 等）在预览与导出中直接生效。"
private const val ENTRY_BADGE: String = "入口"
private const val NEW_FILE_TITLE: String = "新建文件"
private const val NEW_FILE_CONFIRM: String = "创建"
private const val NEW_FILE_LABEL: String = "文件名"
private const val NEW_FILE_PLACEHOLDER: String = "css/style.css"
private const val NEW_FILE_HINT: String = "支持子目录路径（如 css/style.css）；重名或非法名字无法创建。"
private const val NAME_EMPTY_MESSAGE: String = "文件名不能为空"
private const val NAME_ILLEGAL_MESSAGE: String = "文件名包含非法字符"
private const val NAME_DUPLICATE_MESSAGE: String = "同名文件已存在"
private const val DELETE_FILE_TITLE: String = "删除文件"
private const val DELETE_FILE_CONFIRM: String = "删除"
