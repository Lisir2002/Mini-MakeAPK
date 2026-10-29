package com.make.apk.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.make.apk.data.model.ProjectFile
import com.make.apk.ui.components.CuteButton
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.components.MmaDialog
import com.make.apk.ui.components.MmaIconAction
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.icons.MmaIcons
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.Spacing
import java.util.Locale

/**
 * 「项目代码」页（HTML 类专属，类型隔离）——**真实目录结构浏览**。
 *
 * ## 定位（rc12 重写：从"路径列表"到"真实目录"）
 *
 * 上一版只是把 `css/style.css` 这类路径按 `/` 拆成"文件夹行 + 文件行"，观感更像
 * **扁平路径列表**而非目录。本次按文件管理器 / IDE 资源管理器的通行约定重建：
 *
 * - **层级**：缩进表达层级（每层 12dp，深于 4 层封顶——窄屏不再无限右移）；
 * - **排序**：文件夹优先、文件其次，同层按名称排序（GitHub / VS Code / Material Files 一致）；
 * - **图标**：文件夹有**开 / 闭两态**，箭头随展开旋转 90°（折叠朝右、展开朝下）；
 * - **元数据**：文件行右对齐显示大小（只保留一项，窄屏不挤）；
 * - **导航**：顶部面包屑显示当前所处目录，点任一段直达该层（回上层 = 收起其后分支）；
 * - **默认展开**：仅第一层目录展开，更深层折叠（"默认全展开"是反模式）；
 * - **无障碍**：文件夹带 `stateDescription`（已展开 / 已折叠），文件带完整操作描述。
 *
 * 源码内容一律在编辑器页维护，本页**不再承载任何代码编辑区**。
 *
 * **校验零判断**：新建文件名的合法性在本页做**结构性**校验（非空 / 无非法字符 /
 * 不重名），不属于表单校验体系（文件名不进 EditField）。
 */
@Composable
fun ProjectCodeTab(
    entryFile: String,
    files: List<ProjectFile>,
    onOpenFile: (String) -> Unit,
    onFileAdded: (String) -> Unit,
    onFileDeleted: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val root = remember(entryFile, files) { buildFileTree(entryFile, files) }
    // 面包屑 = 当前所处目录（最深的已展开目录）；不缓存——直接读 expanded 才能随展开变化重组。
    val currentPath = deepestExpandedPath(root, expanded)

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
                    text = "项目文件",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = summarize(root),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PathBar(
                path = currentPath,
                onNavigate = { target -> collapseBelow(expanded, target) },
            )
            DirRows(
                nodes = root.children,
                depth = 0,
                expanded = expanded,
                onOpenFile = onOpenFile,
                onRequestDelete = { pendingDelete = it },
            )
            Text(
                text = TREE_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            existingNames = files.map { it.name },
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

// ── 文件树模型 ──────────────────────────────────────────────────────

/**
 * 目录节点：真实目录树的内部节点。
 *
 * @param children 直接子节点（已完成"文件夹优先 + 同层按名"排序，渲染层不再排序）。
 * @param fileCount 子树内的文件总数（含各级子目录）。
 * @param dirCount 子树内的子目录总数。
 */
private class DirNode(
    val name: String,
    val path: String,
    val children: List<Node>,
    val fileCount: Int,
    val dirCount: Int,
)

/** 文件节点（叶子）。 */
private class FileNode(
    val name: String,
    val path: String,
    val sizeBytes: Int,
    val isEntry: Boolean,
)

/** 树节点（目录 or 文件）。 */
private sealed interface Node {
    class Dir(val node: DirNode) : Node
    class Leaf(val node: FileNode) : Node
}

/** 根容器：只有一个"项目根"节点，便于面包屑与统计统一处理。 */
private class TreeRoot(val children: List<Node>, val fileCount: Int, val dirCount: Int)

/**
 * 扁平文件列表 → **真实目录树**。
 *
 * 入口文件视为根目录下的文件（与附加文件同层），路径 `a/b/c.ext` 逐级建目录；
 * 排序规则：**目录在前、文件在后，同级按名称**（与主流文件管理器一致）。
 */
private fun buildFileTree(entryFile: String, files: List<ProjectFile>): TreeRoot {
    // 入口文件优先取调用方传入的实体（含 HTML 内容，用于显示真实大小）；
    // 未传时补一个占位条目，保证树上一定有「入口」徽标。
    val entry = files.firstOrNull { it.name == entryFile } ?: ProjectFile(name = entryFile, content = "")
    val all = listOf(entry) + files.filter { it.name != entryFile }
    fun build(paths: List<ProjectFile>, prefix: String, depth: Int): List<Node> {
        if (paths.isEmpty()) return emptyList()
        val filesHere = mutableListOf<Node>()
        val dirNames = LinkedHashSet<String>()
        paths.forEach { file ->
            val rest = file.name.removePrefix(prefix).trimStart('/')
            if (rest.contains('/')) dirNames.add(rest.substringBefore('/'))
        }
        val dirs = dirNames.mapNotNull { dirName ->
            val dirPath = if (prefix.isEmpty()) dirName else "$prefix/$dirName"
            if (depth >= MAX_TREE_DEPTH) return@mapNotNull null
            val inside = paths.filter { it.name.startsWith("$dirPath/") }
            if (inside.isEmpty()) return@mapNotNull null
            val childNodes = build(inside, dirPath, depth + 1)
            val fileCount = childNodes.sumOf { it.fileCount() }
            val dirCount = childNodes.sumOf { it.dirCount() } + 1
            Node.Dir(DirNode(dirName, dirPath, childNodes, fileCount, dirCount))
        }
        paths.forEach { file ->
            val rest = file.name.removePrefix(prefix).trimStart('/')
            if (rest.contains('/')) return@forEach
            filesHere.add(
                Node.Leaf(
                    FileNode(
                        name = rest,
                        path = file.name,
                        sizeBytes = file.content.toByteArray(Charsets.UTF_8).size,
                        isEntry = file.name == entryFile,
                    ),
                ),
            )
        }
        return dirs.sortedBy { it.node.name.lowercase() } +
            filesHere.filterIsInstance<Node.Leaf>().sortedBy { it.node.name.lowercase() }
    }

    val children = build(all, prefix = "", depth = 0)
    return TreeRoot(
        children = children,
        fileCount = children.sumOf { it.fileCount() },
        dirCount = children.sumOf { it.dirCount() },
    )
}

private fun Node.fileCount(): Int = when (this) {
    is Node.Dir -> node.fileCount
    is Node.Leaf -> 1
}

private fun Node.dirCount(): Int = when (this) {
    is Node.Dir -> node.dirCount
    is Node.Leaf -> 0
}

/** 统计文案：`N 个文件 · M 个文件夹`。 */
private fun summarize(root: TreeRoot): String =
    "${root.fileCount} 个文件 · ${root.dirCount} 个文件夹"

/** 当前所处目录：最深的已展开目录路径（无展开即在项目根）。 */
private fun deepestExpandedPath(root: TreeRoot, expanded: Map<String, Boolean>): String {
    fun walk(nodes: List<Node>, prefix: String): String {
        var deepest = prefix
        nodes.forEach { node ->
            if (node is Node.Dir && expanded[node.node.path] == true) {
                val child = walk(node.node.children, node.node.path)
                deepest = if (child.length > deepest.length) child else deepest
            }
        }
        return deepest
    }
    return walk(root.children, "")
}

/** 收起 [target] 之后的分支（面包屑"回到该层"）：只保留 target 及其祖先为展开。 */
private fun collapseBelow(expanded: MutableMap<String, Boolean>, target: String) {
    expanded.keys.toList().forEach { key ->
        val keep = target.isEmpty() || key == target || target.startsWith("$key/")
        expanded[key] = keep
    }
}

// ── 渲染 ────────────────────────────────────────────────────────────

/**
 * 面包屑：`项目根 › css › …`，点任一段直达该层。
 *
 * 窄屏（360–412dp）上深层目录靠缩进已放不下，故用可点路径条做横向导航
 * （Material Files / Dir 的通行做法），可横向滚动、不折叠。
 */
@Composable
private fun PathBar(path: String, onNavigate: (String) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val segments = if (path.isEmpty()) emptyList() else path.split('/')
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ControlShapes.itemSingle)
            .background(colorScheme.surfaceVariant)
            .padding(horizontal = Spacing.space3, vertical = Spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PathSegment(
            label = ROOT_LABEL,
            isLast = segments.isEmpty(),
            onClick = { onNavigate("") },
        )
        segments.forEachIndexed { index, segment ->
            Text(
                text = SEPARATOR,
                style = typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
            )
            PathSegment(
                label = segment,
                isLast = index == segments.lastIndex,
                onClick = { onNavigate(segments.take(index + 1).joinToString("/")) },
            )
        }
    }
}

@Composable
private fun PathSegment(label: String, isLast: Boolean, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (isLast) colorScheme.primary else colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(ControlShapes.itemSingle)
            .then(if (isLast) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = Spacing.space1, vertical = Spacing.space1)
            .semantics { role = Role.Button },
    )
}

/** 树行（递归）：目录可折叠，文件可点击打开 + 删除（入口除外）。 */
@Composable
private fun DirRows(
    nodes: List<Node>,
    depth: Int,
    expanded: MutableMap<String, Boolean>,
    onOpenFile: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
) {
    nodes.forEach { node ->
        when (node) {
            is Node.Dir -> {
                val open = expanded[node.node.path] ?: (depth == 0)
                FolderRow(
                    node = node.node,
                    depth = depth,
                    open = open,
                    onToggle = { expanded[node.node.path] = !open },
                )
                if (open) {
                    DirRows(
                        nodes = node.node.children,
                        depth = depth + 1,
                        expanded = expanded,
                        onOpenFile = onOpenFile,
                        onRequestDelete = onRequestDelete,
                    )
                }
            }
            is Node.Leaf -> FileRow(
                node = node.node,
                depth = depth,
                onOpenFile = onOpenFile,
                onRequestDelete = onRequestDelete,
            )
        }
    }
}

/**
 * 文件夹行：**整行点击**即展开 / 折叠（箭头与整行行为一致——移动端通行约定）。
 *
 * 箭头在行首、折叠朝右、展开旋转 90° 朝下；文件夹图标随状态切换开 / 闭两态。
 */
@Composable
private fun FolderRow(node: DirNode, depth: Int, open: Boolean, onToggle: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val control = LocalMmaDesign.current.control
    val indent = Spacing.space5 * depth.coerceAtMost(MAX_INDENT_DEPTH)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ControlShapes.itemSingle)
            .height(control.menuItemHeight)
            .clickable(onClick = onToggle)
            .padding(start = indent, end = Spacing.space2)
            .semantics {
                role = Role.Button
                contentDescription = "目录 ${node.name}"
                stateDescription = if (open) STATE_EXPANDED else STATE_COLLAPSED
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space1),
    ) {
        // 折叠朝右、展开朝下：两枚方向明确的图标，不做运行时旋转
        // （旋转在纯 JVM 渲染与真机上的方向表现不一致，靠变换表达状态不可靠）。
        MmaIcon(
            iconRes = if (open) MmaIcons.Collapse else MmaIcons.ChevronRight,
            contentDescription = null,
            size = control.menuItemIcon,
            tint = colorScheme.onSurfaceVariant,
        )
        MmaIcon(
            iconRes = if (open) MmaIcons.FolderOpen else MmaIcons.Folder,
            contentDescription = null,
            size = control.menuItemIcon,
            tint = colorScheme.primary,
        )
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${node.fileCount} 项",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 文件行：点击打开编辑器 / 查看器；入口带徽标；非入口可删除；右对齐显示大小。
 */
@Composable
private fun FileRow(
    node: FileNode,
    depth: Int,
    onOpenFile: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val control = LocalMmaDesign.current.control
    val indent = Spacing.space5 * depth.coerceAtMost(MAX_INDENT_DEPTH)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(control.menuItemHeight)
            .clip(ControlShapes.itemSingle)
            .clickable { onOpenFile(node.path) }
            .padding(start = indent, end = Spacing.space2)
            .semantics {
                role = Role.Button
                contentDescription = "打开文件 ${node.name}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        Spacer(modifier = Modifier.width(control.menuItemIcon))
        MmaIcon(
            iconRes = MmaIcons.File,
            contentDescription = null,
            size = control.menuItemIcon,
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
        Text(
            text = formatSize(node.sizeBytes),
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
        )
        if (node.isEntry) {
            // 入口徽标：主色容器作底（与「选中 = 蓝」一致），窄屏也能一眼找到首屏文件。
            Text(
                text = ENTRY_BADGE,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .clip(ControlShapes.itemSingle)
                    .background(colorScheme.primaryContainer)
                    .padding(horizontal = Spacing.space2, vertical = Spacing.space1),
            )
        } else {
            // 删除用中性色：真实目录树里每行一个红色垃圾桶会形成视觉噪音，
            // 红色只在"确认删除"对话框里出现（destructive 语义不丢）。
            MmaIconAction(
                iconRes = MmaIcons.Delete,
                contentDescription = "删除文件 ${node.path}",
                onClick = { onRequestDelete(node.path) },
                tint = colorScheme.onSurfaceVariant,
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

// ── 小工具 ──────────────────────────────────────────────────────────

/** 大小格式化（1 KB 进制 1024；统一 Locale.US，避免逗号 / 点随系统语言漂移）。 */
private fun formatSize(bytes: Int): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
    else -> String.format(Locale.US, "%.1f MB", bytes / 1024f / 1024f)
}

// ── 常量 ────────────────────────────────────────────────────────────

/** 缩进封顶层数（更深层级不再右移，避免窄屏被挤出屏幕）。 */
private const val MAX_INDENT_DEPTH: Int = 4

/** 建树最大深度（防病态路径撑爆渲染；超出按文件平铺截断）。 */
private const val MAX_TREE_DEPTH: Int = 12

private const val TREE_HINT: String =
    "点击文件进入编辑器 / 查看器；点击目录展开或收起。附加文件（css / js 等）在预览与导出中直接生效。"
private const val ROOT_LABEL: String = "项目根"
private const val SEPARATOR: String = " › "
private const val ENTRY_BADGE: String = "入口"
private const val STATE_EXPANDED: String = "已展开"
private const val STATE_COLLAPSED: String = "已折叠"
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
