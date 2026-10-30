package com.make.apk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.make.apk.ui.editor.FieldMessage
import com.make.apk.ui.editor.ProjectTextField
import com.make.apk.ui.fs.FsItem
import com.make.apk.ui.fs.PasteMode
import com.make.apk.ui.fs.PasteRequest
import com.make.apk.ui.fs.childItemsOf
import com.make.apk.ui.fs.countDescendantFiles
import com.make.apk.ui.fs.formatSize
import com.make.apk.ui.fs.normalizePath
import com.make.apk.ui.fs.parentPathOf
import com.make.apk.ui.fs.validateFsName
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.icons.MmaIcons
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.Spacing

/**
 * 目录浏览器（可复用卡片列表式文件管理器）。
 *
 * ## 定位
 * 与"数据来自哪"彻底解耦——只接收中性节点列表 [FsItem] + 操作回调 [DirectoryBrowserCallbacks]，
 * 不含任何业务实体；适配层（如 [com.make.apk.ui.editor.ProjectCodeTab]）负责把
 * `List<ProjectFile>` 转换成 [FsItem]。后续资产包浏览等页面可直接复用本组件。
 *
 * ## 交互模型（参照主流网盘 / 文件 App）
 * - **逐层进入**：列表只显示当前目录的直接子项，点文件夹进入下一层；
 * - **当前目录头 + 面包屑**：显示"根目录"/当前目录名，子目录提供"返回上一级"与可点路径段；
 * - **卡片条目**：圆角卡片 + 左侧圆角图标底（按扩展名映射图标与底色 token）+ 两行文本；
 * - **操作全部上抛**：新建 / 重命名 / 删除 / 复制 / 剪切 / 粘贴均经回调交给业务层落库，组件不含业务；
 * - **剪贴态内部持有**：复制 / 剪切的源路径 + 模式由组件内 state holder 持有（**不跨组件全局单例**），
 *   进入其他目录后由"粘贴"按钮触发 [DirectoryBrowserCallbacks.onPaste]。
 *
 * 设计令牌红线：本文件所有颜色取自 `MaterialTheme.colorScheme` 或 `LocalMmaDesign`，
 * 圆角取自 `ControlShapes` / `MaterialTheme.shapes`，间距取自 `Spacing`，字号取自 `MaterialTheme.typography`，
 * 严禁任何字面量颜色 / 圆角 / 间距 / 字号（CI 门禁 `check_token_usage.py`）。
 */
@Composable
fun DirectoryBrowser(
    items: List<FsItem>,
    callbacks: DirectoryBrowserCallbacks,
    modifier: Modifier = Modifier,
) {
    // 当前所处目录（相对根全路径；"" = 根）。组件内 state，不污染业务层。
    var currentPath by remember { mutableStateOf("") }
    // 剪贴板：源路径 + 模式（复制 / 剪切）。组件内持有，未来其他页面复用时各自持有。
    var clipboard by remember { mutableStateOf<ClipboardState?>(null) }

    // 弹窗 / 菜单态
    var showNewFile by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var pendingRename by remember { mutableStateOf<FsItem?>(null) }
    var pendingDelete by remember { mutableStateOf<FsItem?>(null) }

    val children = remember(items, currentPath) { childItemsOf(items, currentPath) }
    val clip = clipboard
    val canPaste = clip != null && clip.sources.none { s ->
        s == currentPath || currentPath.startsWith("$s/")
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 当前目录头 + 返回上一级
        FsCurrentDirHeader(
            currentPath = currentPath,
            onBack = { currentPath = parentPathOf(currentPath) },
            count = children.size,
        )
        // 面包屑（深层目录快速跳转）
        FsBreadcrumb(
            currentPath = currentPath,
            onNavigate = { currentPath = it },
        )

        if (children.isEmpty()) {
            FsEmptyHint()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.space3)) {
                children.forEach { item ->
                    FsEntryCard(
                        item = item,
                        onOpen = {
                            if (item.isDirectory) currentPath = item.path else callbacks.onOpenFile(item.path)
                        },
                        onCopy = { clipboard = ClipboardState(PasteMode.COPY, listOf(item.path)) },
                        onCut = { if (!item.isEntry) clipboard = ClipboardState(PasteMode.CUT, listOf(item.path)) },
                        onRename = { pendingRename = item },
                        onDelete = { pendingDelete = item },
                    )
                }
            }
        }

        // 底部操作区：新建文件 / 文件夹 / 粘贴
        FsActionRow(
            canPaste = canPaste,
            clipCount = clip?.sources?.size ?: 0,
            onNewFile = { showNewFile = true },
            onNewFolder = { showNewFolder = true },
            onPaste = {
                val cb = clipboard ?: return@FsActionRow
                callbacks.onPaste(PasteRequest(cb.mode, cb.sources, currentPath))
                clipboard = null
            },
        )
    }

    // ── 新建文件弹窗 ──
    if (showNewFile) {
        FsNameDialog(
            title = FS_DIALOG_NEW_FILE_TITLE,
            initial = "",
            confirmLabel = FS_DIALOG_CONFIRM_CREATE,
            existingSiblingPaths = childItemsOf(items, currentPath).map { it.path }.toSet(),
            onDismiss = { showNewFile = false },
            onConfirm = { name ->
                val rel = if (currentPath.isEmpty()) name else "$currentPath/$name"
                callbacks.onAddFile(rel)
                showNewFile = false
            },
        )
    }

    // ── 新建文件夹弹窗（落库为 `name/` 空目录条目）──
    if (showNewFolder) {
        FsNameDialog(
            title = FS_DIALOG_NEW_FOLDER_TITLE,
            initial = "",
            confirmLabel = FS_DIALOG_CONFIRM_CREATE,
            existingSiblingPaths = childItemsOf(items, currentPath).map { it.path }.toSet(),
            onDismiss = { showNewFolder = false },
            onConfirm = { name ->
                val rel = if (currentPath.isEmpty()) name else "$currentPath/$name"
                callbacks.onAddFolder(rel)
                showNewFolder = false
            },
        )
    }

    // ── 重命名弹窗（校验排除自身）──
    pendingRename?.let { item ->
        val siblings = childItemsOf(items, currentPath).map { it.path }.toSet() - item.path
        FsNameDialog(
            title = FS_DIALOG_RENAME_TITLE,
            initial = item.name,
            confirmLabel = FS_DIALOG_CONFIRM_RENAME,
            existingSiblingPaths = siblings,
            onDismiss = { pendingRename = null },
            onConfirm = { name ->
                val parent = parentPathOf(item.path)
                val newPath = if (parent.isEmpty()) name else "$parent/$name"
                callbacks.onRename(item.path, newPath)
                pendingRename = null
            },
        )
    }

    // ── 删除确认弹窗（目录明示递归文件数）──
    pendingDelete?.let { item ->
        FsDeleteDialog(
            item = item,
            descendantFileCount = countDescendantFiles(items, item.path),
            onDismiss = { pendingDelete = null },
            onConfirm = {
                callbacks.onDelete(item.path)
                pendingDelete = null
            },
        )
    }
}

// ── 回调契约 ──────────────────────────────────────────────────────────

/**
 * 目录浏览器操作回调（全部上抛业务层落库，组件不含业务）。
 *
 * 路径均为相对根的全路径（目录不带尾 "/"）；新建 / 重命名时组件已折算成相对根路径。
 * 复制 / 剪切不在此列——其源与模式由组件内 [ClipboardState] 暂存，粘贴时以 [PasteRequest] 上抛。
 */
data class DirectoryBrowserCallbacks(
    /** 打开文件（点文件主体触发，导航在调用方接线）。 */
    val onOpenFile: (String) -> Unit = {},
    /** 新建文件（path 相对根，可含子路径如 `css/style.css`）。 */
    val onAddFile: (String) -> Unit = {},
    /** 新建文件夹（path 相对根；业务层落库为 `path/` 空目录条目）。 */
    val onAddFolder: (String) -> Unit = {},
    /** 重命名 / 移动（前缀替换）：(旧全路径, 新全路径)。 */
    val onRename: (String, String) -> Unit = { _, _ -> },
    /** 删除（文件或目录；目录递归由业务层统一处理）。 */
    val onDelete: (String) -> Unit = {},
    /** 粘贴（复制 / 剪切落库）。 */
    val onPaste: (PasteRequest) -> Unit = {},
)

/** 组件内剪贴态（不跨组件全局单例）。 */
private data class ClipboardState(val mode: PasteMode, val sources: List<String>)

// ── 渲染：当前目录头 ──────────────────────────────────────────────────

@Composable
private fun FsCurrentDirHeader(
    currentPath: String,
    onBack: () -> Unit,
    count: Int,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        if (currentPath.isNotEmpty()) {
            MmaIconAction(
                iconRes = MmaIcons.ArrowBack,
                contentDescription = FS_BACK_CONTENT_DESCRIPTION,
                onClick = onBack,
                tint = colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (currentPath.isEmpty()) FS_ROOT_LABEL else currentPath.substringAfterLast('/'),
            style = typography.titleMedium,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count ${FS_COUNT_UNIT}",
            style = typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
        )
    }
}

// ── 渲染：面包屑 ──────────────────────────────────────────────────────

@Composable
private fun FsBreadcrumb(currentPath: String, onNavigate: (String) -> Unit) {
    if (currentPath.isEmpty()) return
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val segments = currentPath.split('/')
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ControlShapes.itemSingle)
            .background(colorScheme.surfaceVariant)
            .padding(horizontal = Spacing.space3, vertical = Spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FsCrumb(label = FS_ROOT_LABEL, isLast = false, onClick = { onNavigate("") })
        segments.forEachIndexed { index, segment ->
            Text(text = FS_SEPARATOR, style = typography.labelSmall, color = colorScheme.onSurfaceVariant)
            FsCrumb(
                label = segment,
                isLast = index == segments.lastIndex,
                onClick = { onNavigate(segments.take(index + 1).joinToString("/")) },
            )
        }
    }
}

@Composable
private fun FsCrumb(label: String, isLast: Boolean, onClick: () -> Unit) {
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

// ── 渲染：条目卡片 ────────────────────────────────────────────────────

@Composable
private fun FsEntryCard(
    item: FsItem,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val control = LocalMmaDesign.current.control
    val iconSpec = iconForItem(item)

    var menuExpanded by remember { mutableStateOf(false) }
    var anchorPos by remember { mutableStateOf(Offset.Zero) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }

    CuteCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.space3)
                .semantics {
                    role = Role.Button
                    contentDescription = if (item.isDirectory) "目录 ${item.name}" else "文件 ${item.name}"
                    // 目录用"含 N 项"表达状态（取代旧版的展开 / 收起态）
                    if (item.isDirectory) stateDescription = FS_STATE_CONTAINS.format(item.childCount)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            // 主点击区（除去溢出按钮的整行）：文件夹进入、文件打开
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(ControlShapes.itemSingle)
                    .clickableNoRipple(onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.space3),
            ) {
                // 左侧圆角图标底：令牌圆角 + 令牌底色，图标按扩展名映射
                Box(
                    modifier = Modifier
                        .size(control.iconButtonSize)
                        .clip(ControlShapes.segmentPiece)
                        .background(containerColorOf(iconSpec.containerRole)),
                    contentAlignment = Alignment.Center,
                ) {
                    MmaIcon(
                        iconRes = iconSpec.iconRes,
                        contentDescription = null,
                        size = control.menuItemIcon,
                        tint = contentColorOf(iconSpec.contentRole),
                    )
                }
                // 两行文本：名称 + 元数据（目录"N 项" / 文件大小）
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = typography.bodyLarge,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (item.isDirectory) "${item.childCount} ${FS_COUNT_UNIT}" else formatSize(item.sizeBytes),
                        style = typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 入口徽标（仅入口文件）
                if (item.isEntry) {
                    Text(
                        text = FS_ENTRY_BADGE,
                        style = typography.labelSmall,
                        color = colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .clip(ControlShapes.itemSingle)
                            .background(colorScheme.primaryContainer)
                            .padding(horizontal = Spacing.space2, vertical = Spacing.space1),
                    )
                }
            }
            // 溢出菜单按钮（锚点，记录窗口坐标供 Popup 定位）
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopEnd)
                    .onGloballyPositioned { coords: LayoutCoordinates ->
                        anchorPos = coords.positionInWindow()
                        anchorSize = coords.size
                    },
            ) {
                MmaIconAction(
                    iconRes = MmaIcons.MoreVert,
                    contentDescription = FS_MORE_CONTENT_DESCRIPTION.format(item.name),
                    onClick = { menuExpanded = true },
                    tint = colorScheme.onSurfaceVariant,
                )
            }
        }

        // 弹出菜单（全屏 scrim + 锚定到溢出按钮下方）
        if (menuExpanded) {
            Popup(
                popupPositionProvider = FsMenuOriginProvider,
                onDismissRequest = { menuExpanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickableNoRipple { menuExpanded = false },
                ) {
                    FsMenuPanel(
                        item = item,
                        onCopy = onCopy,
                        onCut = onCut,
                        onRename = onRename,
                        onDelete = onDelete,
                        modifier = Modifier.offset {
                            IntOffset(
                                x = anchorPos.x.roundToInt(),
                                y = (anchorPos.y + anchorSize.height).roundToInt(),
                            )
                        },
                    )
                }
            }
        }
    }
}

/** 弹出菜单面板（复制 / 剪切 / 重命名 / 删除；入口禁用剪切与删除）。 */
@Composable
private fun FsMenuPanel(
    item: FsItem,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val control = LocalMmaDesign.current.control
    Column(
        modifier = modifier
            .widthIn(min = control.menuMinWidth, max = control.menuMaxWidth)
            .shadow(elevation = LocalMmaDesign.current.elevation.elevation1, shape = ControlShapes.group)
            .clip(ControlShapes.group)
            .background(colorScheme.surfaceContainerLow)
            .padding(control.menuGroupGap),
        verticalArrangement = Arrangement.spacedBy(control.menuGroupGap),
    ) {
        FsMenuItem(MmaIcons.ContentCopy, FS_MENU_COPY, colorScheme.onSurface, onCopy)
        if (!item.isEntry) {
            FsMenuItem(MmaIcons.ContentCut, FS_MENU_CUT, colorScheme.onSurface, onCut)
        }
        FsMenuItem(MmaIcons.Edit, FS_MENU_RENAME, colorScheme.onSurface, onRename)
        if (!item.isEntry) {
            FsMenuItem(MmaIcons.Delete, FS_MENU_DELETE, colorScheme.error, onDelete)
        }
    }
}

@Composable
private fun FsMenuItem(
    iconRes: Int,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val typography = MaterialTheme.typography
    val control = LocalMmaDesign.current.control
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(control.menuItemHeight)
            .clip(ControlShapes.itemSingle)
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = control.menuItemPaddingH)
            .semantics { role = Role.Button; contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        MmaIcon(iconRes = iconRes, contentDescription = null, size = control.menuItemIcon, tint = tint)
        Text(text = label, style = typography.bodyLarge, color = tint, modifier = Modifier.weight(1f))
    }
}

// ── 渲染：空态 / 操作区 ───────────────────────────────────────────────

@Composable
private fun FsEmptyHint() {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Text(
        text = FS_EMPTY_HINT,
        style = typography.bodySmall,
        color = colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.space4),
    )
}

@Composable
private fun FsActionRow(
    canPaste: Boolean,
    clipCount: Int,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onPaste: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
        ) {
            CuteButton(text = FS_ACTION_NEW_FILE, onClick = onNewFile, modifier = Modifier.weight(1f))
            CuteButton(text = FS_ACTION_NEW_FOLDER, onClick = onNewFolder, modifier = Modifier.weight(1f))
        }
        if (canPaste) {
            val label = if (clipCount > 1) FS_ACTION_PASTE_MULTI.format(clipCount) else FS_ACTION_PASTE
            CuteButton(text = label, onClick = onPaste, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ── 渲染：对话框 ──────────────────────────────────────────────────────

/** 命名弹窗（新建文件 / 新建文件夹 / 重命名共用）：结构性校验 + 实时错误。 */
@Composable
private fun FsNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    existingSiblingPaths: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val trimmed = normalizePath(name)
    val error = validateFsName(name, existingSiblingPaths)
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    MmaDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmLabel = confirmLabel,
        onConfirm = { if (error == null) onConfirm(trimmed) },
        confirmEnabled = error == null,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.space2)) {
                ProjectTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = FS_DIALOG_NAME_LABEL,
                    placeholder = FS_DIALOG_NAME_PLACEHOLDER,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    message = error?.let { FieldMessage(it, FieldMessage.Severity.ERROR) },
                )
                Text(
                    text = FS_DIALOG_NAME_HINT,
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/** 删除确认弹窗：目录明示"将删除其下全部 N 个文件"。 */
@Composable
private fun FsDeleteDialog(
    item: FsItem,
    descendantFileCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    MmaDialog(
        onDismissRequest = onDismiss,
        title = FS_DIALOG_DELETE_TITLE,
        confirmLabel = FS_DIALOG_DELETE_CONFIRM,
        onConfirm = onConfirm,
        destructive = true,
        content = {
            Text(
                text = if (item.isDirectory) {
                    FS_DELETE_DIR_MESSAGE.format(item.name, descendantFileCount)
                } else {
                    FS_DELETE_FILE_MESSAGE.format(item.name)
                },
                style = typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )
        },
    )
}

// ── 图标映射（组件内私有纯函数，图标用 token 色）──────────────────────

/** 图标底色 / 内容色使用的颜色角色（全部映射自 colorScheme token，无字面量）。 */
private enum class FsColorRole { PRIMARY, SURFACE, TERTIARY }

/** 图标规格：图标资源 + 底色角色 + 内容色角色。 */
private data class FsIconSpec(
    val iconRes: Int,
    val containerRole: FsColorRole,
    val contentRole: FsColorRole,
)

/** 按扩展名映射图标与底色（文档类走 tertiary 系、代码 / 配置走 surface 系、文件夹走 primary 系）。 */
private fun iconForItem(item: FsItem): FsIconSpec {
    if (item.isDirectory) {
        return FsIconSpec(MmaIcons.Folder, FsColorRole.PRIMARY, FsColorRole.PRIMARY)
    }
    val ext = item.name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "md", "txt" -> FsIconSpec(MmaIcons.Document, FsColorRole.TERTIARY, FsColorRole.TERTIARY)
        "kt", "kts", "java", "js", "ts", "css", "html", "php", "vue" ->
            FsIconSpec(MmaIcons.Code, FsColorRole.SURFACE, FsColorRole.SURFACE)
        "json", "xml", "properties", "toml", "yml", "yaml", "gradle" ->
            FsIconSpec(MmaIcons.Config, FsColorRole.SURFACE, FsColorRole.SURFACE)
        else -> FsIconSpec(MmaIcons.File, FsColorRole.SURFACE, FsColorRole.SURFACE)
    }
}

@Composable
private fun containerColorOf(role: FsColorRole): Color {
    val c = MaterialTheme.colorScheme
    return when (role) {
        FsColorRole.PRIMARY -> c.primaryContainer
        FsColorRole.SURFACE -> c.surfaceVariant
        FsColorRole.TERTIARY -> c.tertiaryContainer
    }
}

@Composable
private fun contentColorOf(role: FsColorRole): Color {
    val c = MaterialTheme.colorScheme
    return when (role) {
        FsColorRole.PRIMARY -> c.primary
        FsColorRole.SURFACE -> c.onSurfaceVariant
        FsColorRole.TERTIARY -> c.tertiary
    }
}

/**
 * Popup 铺满整个窗口：菜单在其中用 offset 精确定位到溢出按钮下方，
 * 剩余区域即 scrim（点击关闭）。
 */
private object FsMenuOriginProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

// ── 常量（中文文案集中） ──────────────────────────────────────────────

private const val FS_ROOT_LABEL: String = "根目录"
private const val FS_COUNT_UNIT: String = "项"
private const val FS_SEPARATOR: String = " › "
private const val FS_ENTRY_BADGE: String = "入口"
private const val FS_BACK_CONTENT_DESCRIPTION: String = "返回上一级"
private const val FS_MORE_CONTENT_DESCRIPTION: String = "更多操作：%s"
private const val FS_STATE_CONTAINS: String = "含 %d 项"
private const val FS_EMPTY_HINT: String = "当前目录为空，点下方「新建文件」或「新建文件夹」开始添加。"
private const val FS_ACTION_NEW_FILE: String = "新建文件"
private const val FS_ACTION_NEW_FOLDER: String = "新建文件夹"
private const val FS_ACTION_PASTE: String = "粘贴"
private const val FS_ACTION_PASTE_MULTI: String = "粘贴（%d 项）"
private const val FS_MENU_COPY: String = "复制"
private const val FS_MENU_CUT: String = "剪切"
private const val FS_MENU_RENAME: String = "重命名"
private const val FS_MENU_DELETE: String = "删除"
private const val FS_DIALOG_NEW_FILE_TITLE: String = "新建文件"
private const val FS_DIALOG_NEW_FOLDER_TITLE: String = "新建文件夹"
private const val FS_DIALOG_RENAME_TITLE: String = "重命名"
private const val FS_DIALOG_CONFIRM_CREATE: String = "创建"
private const val FS_DIALOG_CONFIRM_RENAME: String = "重命名"
private const val FS_DIALOG_NAME_LABEL: String = "名称"
private const val FS_DIALOG_NAME_PLACEHOLDER: String = "css/style.css"
private const val FS_DIALOG_NAME_HINT: String = "支持子目录路径（如 css/style.css）；重名或非法名字无法创建。"
private const val FS_DIALOG_DELETE_TITLE: String = "删除"
private const val FS_DIALOG_DELETE_CONFIRM: String = "删除"
private const val FS_DELETE_FILE_MESSAGE: String = "「%s」将从项目中移除，此操作不可撤销。"
private const val FS_DELETE_DIR_MESSAGE: String = "「%s」及其下全部 %d 个文件将被删除，此操作不可撤销。"
