package com.make.apk.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.make.apk.ui.editor.FieldMessage
import com.make.apk.ui.editor.ProjectTextField
import com.make.apk.ui.fs.FsItem
import com.make.apk.ui.fs.PasteMode
import com.make.apk.ui.fs.PasteRequest
import com.make.apk.ui.fs.countDescendantFiles
import com.make.apk.ui.fs.formatSize
import com.make.apk.ui.fs.normalizePath
import com.make.apk.ui.fs.parentPathOf
import com.make.apk.ui.fs.renamePathPrefix
import com.make.apk.ui.fs.validateFsName
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.icons.MmaIcons
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.Motion
import com.make.apk.ui.theme.Spacing
import java.util.Locale

/**
 * 目录浏览器（可复用**单列缩进树**式文件管理器）。
 *
 * ## 定位
 * 与"数据来自哪"彻底解耦——只接收中性节点列表 [FsItem] + 操作回调 [DirectoryBrowserCallbacks]，
 * 不含任何业务实体；适配层（如 [com.make.apk.ui.editor.ProjectCodeTab]）负责把
 * `List<ProjectFile>` 转换成 [FsItem]。后续资产包浏览等页面可直接复用本组件。
 *
 * ## 交互模型（VS Code / IDE 文件树模式）
 * - **单列缩进树**：全部条目在同一列表内按层级缩进展示，文件夹行首 chevron
 *   （收起朝右、展开旋转 90° 朝下，[Motion.SoftSpring] 弹性过渡），叶节点留等宽空位对齐；
 * - **展开状态集中管理**：`Set<String>`（key = 目录完整路径）由组件内 state 持有，
 *   **绝不 mutate 数据模型**；默认展开第 1 层（根的直接子文件夹），更深层收起；
 * - **当前目录**：点文件夹行 = 展开/收起 + 设为"当前目录"（行背景令牌高亮），
 *   新建按钮与粘贴操作作用于当前目录，默认根目录；点 chevron 仅展开/收起；
 * - **点文件行**：打开编辑器（[DirectoryBrowserCallbacks.onOpenFile]）；
 * - **紧凑树行**：40dp 档（比逐层列表的 56dp 更紧凑），行首裸图标（无底色容器，
 *   按 IDE 文件树惯例），名称过长省略号，副信息（"N 项"/大小）弱化右置；
 * - **长按操作**：长按任意行弹出操作弹窗（打开 / 重命名 / 复制 / 剪切 / 删除，
 *   入口文件仅开放打开 / 复制 / 重命名；树模式不再需要"进入"）；
 * - **操作全部上抛**：新建 / 重命名 / 删除 / 复制 / 剪切 / 粘贴均经回调交给业务层落库；
 * - **剪贴态内部持有**：复制 / 剪切的源路径 + 模式由组件内 state holder 持有
 *   （**不跨组件全局单例**），粘贴按钮仅在剪贴板有效时出现在列表底部。
 *
 * ## 布局契约
 * 组件自身**不滚动**（无 verticalScroll），树展开后整体变高，交给外层页面滚动；
 * 空目录 / 单层 / 深层嵌套（4+ 层）均按缩进正常排布，360dp 窄屏下名称省略不换行。
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
    // 当前目录（相对根全路径；"" = 根）：新建 / 粘贴的作用目标，点文件夹行切换。组件内 state，不污染业务层。
    var currentPath by remember { mutableStateOf("") }
    // 剪贴板：源路径 + 模式（复制 / 剪切）。组件内持有，未来其他页面复用时各自持有。
    var clipboard by remember { mutableStateOf<ClipboardState?>(null) }

    // 弹窗态：统一新建弹窗（内含文件 / 文件夹分段切换）、重命名、删除、长按操作弹窗
    var showCreate by remember { mutableStateOf(false) }
    var pendingRename by remember { mutableStateOf<FsItem?>(null) }
    var pendingDelete by remember { mutableStateOf<FsItem?>(null) }
    var actionTarget by remember { mutableStateOf<FsItem?>(null) }

    // 父路径 → 已排序直接子项（目录优先、同级按名不区分大小写），一次构建供整棵树消费
    val childrenMap = remember(items) { buildChildrenMap(items) }
    // 展开状态（key = 目录完整路径）：默认展开第 1 层（根的直接子文件夹），更深层收起。
    // 初始化器不在这里取数据——首帧可能 items 为空（异步加载），改为下方 LaunchedEffect
    // 在"items 首次非空"时 seed 一次；此后增删改不再重置用户的展开 / 收起选择。
    var expandedDirs by remember { mutableStateOf(emptySet<String>()) }
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(items) {
        if (!seeded && items.isNotEmpty()) {
            expandedDirs = childrenMap[""].orEmpty().filter { it.isDirectory }.map { it.path }.toSet()
            seeded = true
        }
    }

    val visibleRows = remember(childrenMap, expandedDirs) { flattenVisibleTree(childrenMap, expandedDirs) }
    val currentChildren = childrenMap[currentPath].orEmpty()
    val clip = clipboard
    val canPaste = clip != null && clip.sources.none { s ->
        s == currentPath || currentPath.startsWith("$s/")
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 当前目录头（树模式下无"返回"：层级由树本身表达）+ 唯一的新建入口
        FsCurrentDirHeader(
            currentPath = currentPath,
            count = currentChildren.size,
            onCreateClick = { showCreate = true },
        )

        if (visibleRows.isEmpty()) {
            FsEmptyHint()
        } else {
            visibleRows.forEachIndexed { index, row ->
                val item = row.item
                FsTreeNode(
                    item = item,
                    depth = row.depth,
                    isExpanded = item.isDirectory && item.path in expandedDirs,
                    isCurrentDir = item.isDirectory && item.path == currentPath,
                    onToggleExpand = { expandedDirs = toggleExpanded(expandedDirs, item.path) },
                    onClick = {
                        if (item.isDirectory) {
                            // 文件夹行：展开/收起 + 设为当前目录（新建 / 粘贴目标随之切换）
                            expandedDirs = toggleExpanded(expandedDirs, item.path)
                            currentPath = item.path
                        } else {
                            callbacks.onOpenFile(item.path)
                        }
                    },
                    onLongPress = { actionTarget = item },
                )
                // 行间细分隔线（末行不加）：hairline 厚度 + 低透明度弱化色
                if (index < visibleRows.lastIndex) {
                    FsRowDivider()
                }
            }
        }

        // 粘贴（复制 / 剪切后出现；作用目标为当前目录，按其上抛 PasteRequest）
        if (canPaste) {
            val label = clip!!.sources.size.let { if (it > 1) FS_ACTION_PASTE_MULTI.format(it) else FS_ACTION_PASTE }
            CuteButton(
                text = label,
                onClick = {
                    val cb = clipboard ?: return@CuteButton
                    callbacks.onPaste(PasteRequest(cb.mode, cb.sources, currentPath))
                    clipboard = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.space2),
            )
        }
    }

    // ── 统一新建弹窗（SegmentedButton 切换 文件 / 文件夹）──
    if (showCreate) {
        FsCreateDialog(
            existingSiblingPaths = currentChildren.map { it.path }.toSet(),
            onDismiss = { showCreate = false },
            onConfirm = { isFolder, name ->
                val rel = if (currentPath.isEmpty()) name else "$currentPath/$name"
                // 自动展开新条目的父级（含"css/style.css"式子路径创建），使其落库后立即可见
                val parent = parentPathOf(rel)
                if (parent.isNotEmpty()) expandedDirs = expandedDirs + parent
                if (isFolder) callbacks.onAddFolder(rel) else callbacks.onAddFile(rel)
                showCreate = false
            },
        )
    }

    // ── 重命名弹窗（校验排除自身；兄弟集合取自条目真实父级而非当前目录）──
    pendingRename?.let { item ->
        val siblings = childrenMap[parentPathOf(item.path)].orEmpty().map { it.path }.toSet() - item.path
        FsNameDialog(
            title = FS_DIALOG_RENAME_TITLE,
            initial = item.name,
            confirmLabel = FS_DIALOG_CONFIRM_RENAME,
            existingSiblingPaths = siblings,
            onDismiss = { pendingRename = null },
            onConfirm = { name ->
                val parent = parentPathOf(item.path)
                val newPath = if (parent.isEmpty()) name else "$parent/$name"
                // no-op 重命名不上抛，避免向业务层发无意义事件
                if (newPath != item.path) {
                    // 展开态随路径前缀迁移（目录重命名后不回折）
                    expandedDirs = expandedDirs.map { renamePathPrefix(it, item.path, newPath) }.toSet()
                    // 当前目录若是被改名目录自身或其子孙，跟随前缀迁移而非回落根，
                    // 防止停留旧路径导致后续新建 / 粘贴以旧路径上抛、落库后复活旧目录树
                    if (currentPath == item.path || currentPath.startsWith("${item.path}/")) {
                        currentPath = renamePathPrefix(currentPath, item.path, newPath)
                    }
                    callbacks.onRename(item.path, newPath)
                }
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
                // 当前目录若为被删目录或其子孙，回落到根
                if (currentPath == item.path || currentPath.startsWith("${item.path}/")) {
                    currentPath = ""
                }
                // 清理被删子树的展开态，防止同路径目录重建后被 stale key 意外自动展开
                expandedDirs = expandedDirs.filterNot { it == item.path || it.startsWith("${item.path}/") }.toSet()
                // 剪贴板源（含其子孙路径）被删除时同步清空剪贴板，
                // 防止粘贴入口残留、粘贴时以空 content 复活已被删除的文件
                if (clipboard?.sources?.any { source ->
                        source == item.path || source.startsWith("${item.path}/")
                    } == true
                ) {
                    clipboard = null
                }
                pendingDelete = null
            },
        )
    }

    // ── 长按操作弹窗（打开 / 重命名 / 复制 / 剪切 / 删除；入口受限；树模式无"进入"）──
    actionTarget?.let { item ->
        FsActionDialog(
            item = item,
            onDismiss = { actionTarget = null },
            onOpen = {
                callbacks.onOpenFile(item.path)
                actionTarget = null
            },
            onCopy = {
                clipboard = ClipboardState(PasteMode.COPY, listOf(item.path))
                actionTarget = null
            },
            onCut = {
                if (!item.isEntry) clipboard = ClipboardState(PasteMode.CUT, listOf(item.path))
                actionTarget = null
            },
            onRename = {
                pendingRename = item
                actionTarget = null
            },
            onDelete = {
                pendingDelete = item
                actionTarget = null
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
    /** 打开文件（点文件主体 / 长按菜单"打开"触发，导航在调用方接线）。 */
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

// ── 树模型：扁平化 + 展开状态（纯函数，便于单测） ─────────────────────

/** 树的可见行：条目 + 层级深度（0 = 根的直接子项）。 */
private data class FsTreeRow(val item: FsItem, val depth: Int)

/**
 * 父路径 → 已排序直接子项映射（目录优先、同级按名不区分大小写，
 * 与 `com.make.apk.ui.fs.childItemsOf` 的排序规则一致——渲染层不再二次排序）。
 */
private fun buildChildrenMap(items: List<FsItem>): Map<String, List<FsItem>> =
    items.groupBy { parentPathOf(it.path) }
        .mapValues { (_, list) ->
            list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.US) }))
        }

/**
 * 按展开状态扁平化出可见行（深度优先，收起的目录不递归其子级）。
 * 纯读取 [FsItem] 与展开集合，绝不 mutate 数据模型。
 */
private fun flattenVisibleTree(
    childrenMap: Map<String, List<FsItem>>,
    expandedDirs: Set<String>,
): List<FsTreeRow> {
    val rows = mutableListOf<FsTreeRow>()
    fun walk(parentPath: String, depth: Int) {
        for (item in childrenMap[parentPath].orEmpty()) {
            rows.add(FsTreeRow(item, depth))
            if (item.isDirectory && item.path in expandedDirs) {
                walk(item.path, depth + 1)
            }
        }
    }
    walk(parentPath = "", depth = 0)
    return rows
}

/** 展开 / 收起切换（不可变集合运算，返回新集合）。 */
private fun toggleExpanded(current: Set<String>, path: String): Set<String> =
    if (path in current) current - path else current + path

// ── 树几何常量（令牌来源见各注释；值均在门禁 ALLOWED_DP 白名单内） ────

/** 树行最小高度：40dp——与 `ControlScale.iconButtonSize`（40dp 触控底线）同档，门禁 COMPONENT_SIZES 白名单值。 */
private val TreeRowMinHeight: Dp = 40.dp

/** chevron 占位槽边长：24dp——M3 Icon 默认尺寸档（`MmaIcon` 默认 size），门禁 COMPONENT_SIZES 白名单值；叶节点留同宽空位对齐。 */
private val TreeChevronSlot: Dp = 24.dp

/** chevron 展开旋转角：收起朝右（0°）→ 展开朝下（90°），与 IDE 文件树一致。 */
private const val TREE_EXPAND_ROTATION: Float = 90f

// ── 渲染：当前目录头 ──────────────────────────────────────────────────

/**
 * 当前目录头：标题（根目录 / 当前文件夹名）+ 直接子项数 + 唯一新建入口。
 * 树模式下无"返回上一级"（层级由树本身表达），也无面包屑（路径由缩进表达）。
 */
@Composable
private fun FsCurrentDirHeader(
    currentPath: String,
    count: Int,
    onCreateClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
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
        // 唯一新建入口：与"X 项"统计同排的图标按钮（风格与项目顶栏图标按钮一致）
        MmaIconAction(
            iconRes = MmaIcons.Add,
            contentDescription = FS_CREATE_CONTENT_DESCRIPTION,
            onClick = onCreateClick,
            tint = colorScheme.primary,
        )
    }
}

// ── 渲染：树节点行 ────────────────────────────────────────────────────

/**
 * 单列缩进树节点行（40dp 紧凑档）：
 * [层级缩进][chevron 槽 / 叶节点等宽空位][类型裸图标][名称 + 入口徽标][副信息]。
 *
 * - 点 chevron：仅展开 / 收起（消费点击，不冒泡到行）；
 * - 点行主体：文件夹 = 展开/收起 + 设为当前目录（背景令牌高亮）；文件 = 打开；
 * - 长按：弹出操作弹窗（由调用方注入 [onLongPress]）。
 *
 * 缩进实现：每层 `Spacing.space4`（12dp，紧凑面板档）× 深度，作为行首 padding，
 * 高亮背景铺满整行（VS Code 惯例，缩进仅推动内容）。
 */
@OptIn(ExperimentalFoundationApi::class) // combinedClickable（foundation 1.7 仍为实验 API）
@Composable
private fun FsTreeNode(
    item: FsItem,
    depth: Int,
    isExpanded: Boolean,
    isCurrentDir: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val control = LocalMmaDesign.current.control
    val alpha = LocalMmaDesign.current.alpha
    val iconSpec = iconForItem(item)
    // chevron 旋转：收起 0°（朝右）→ 展开 90°（朝下），spring 弹性过渡（Motion 令牌）
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) TREE_EXPAND_ROTATION else 0f,
        animationSpec = Motion.SoftSpring,
        label = "fsTreeChevronRotation",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TreeRowMinHeight)
            .clip(ControlShapes.itemSingle)
            // 当前目录高亮：secondaryContainer 低透明度（alpha 令牌 a3），铺满整行
            .then(
                if (isCurrentDir) {
                    Modifier.background(colorScheme.secondaryContainer.copy(alpha = alpha.a3))
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            )
            // 每层缩进 = Spacing.space4（12dp）× 深度：层级的主要视觉线索
            .padding(start = Spacing.space4 * depth, end = Spacing.space2)
            .semantics {
                role = Role.Button
                contentDescription = if (item.isDirectory) "目录 ${item.name}" else "文件 ${item.name}"
                if (item.isDirectory) {
                    selected = isCurrentDir
                    stateDescription = (if (isExpanded) FS_STATE_EXPANDED else FS_STATE_COLLAPSED)
                        .format(item.childCount)
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        if (item.isDirectory) {
            // chevron：收起朝右、展开旋转 90° 朝下；独立点击区（仅切换，不改当前目录）
            Box(
                modifier = Modifier
                    .size(TreeChevronSlot)
                    .clip(ControlShapes.itemMiddle)
                    .clickableNoRipple(onClick = onToggleExpand)
                    .semantics {
                        role = Role.Button
                        contentDescription = if (isExpanded) FS_CHEVRON_COLLAPSE else FS_CHEVRON_EXPAND
                    },
                contentAlignment = Alignment.Center,
            ) {
                MmaIcon(
                    iconRes = MmaIcons.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.rotate(chevronRotation),
                    size = control.menuItemIcon,
                    tint = colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // 叶节点：留等宽空位，保证名称列跨行对齐
            Spacer(modifier = Modifier.width(TreeChevronSlot))
        }
        // 类型裸图标（IDE 文件树惯例，无底色容器以保紧凑）：扩展名→图标映射沿用令牌色
        MmaIcon(
            iconRes = iconSpec.iconRes,
            contentDescription = null,
            size = control.menuItemIcon,
            tint = contentColorOf(iconSpec.contentRole),
        )
        // 名称 + 可选入口徽标（占满剩余宽度，超长省略）
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.name,
                style = typography.bodyMedium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (item.isEntry) {
                Spacer(modifier = Modifier.width(Spacing.space2))
                Text(
                    text = FS_ENTRY_BADGE,
                    style = typography.labelSmall,
                    color = colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .clip(ControlShapes.pill)
                        .background(colorScheme.primaryContainer)
                        .padding(horizontal = Spacing.space2, vertical = Spacing.space1),
                )
            }
        }
        // 副信息弱化右置：目录"N 项" / 文件大小（窄屏下名称先省略，副信息不换行）
        Text(
            text = if (item.isDirectory) "${item.childCount} ${FS_COUNT_UNIT}" else formatSize(item.sizeBytes),
            style = typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 行间细分隔线：hairline 厚度 + 低透明度弱化色（紧凑但不过度拥挤）。 */
@Composable
private fun FsRowDivider() {
    val colorScheme = MaterialTheme.colorScheme
    val alpha = LocalMmaDesign.current.alpha
    HorizontalDivider(
        thickness = LocalMmaDesign.current.control.dividerHeight,
        color = colorScheme.onSurfaceVariant.copy(alpha = alpha.a2),
    )
}

// ── 渲染：空态 ────────────────────────────────────────────────────────

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

// ── 渲染：对话框 ──────────────────────────────────────────────────────

/**
 * 统一新建弹窗：SegmentedButton（文件 / 文件夹）+ 名称输入框。
 * 类型切换时 placeholder 跟随变化；校验沿用 [validateFsName]（重名 / 非法实时报错）。
 */
@OptIn(ExperimentalMaterial3Api::class) // SegmentedButton（material3 1.3.0 仍为实验 API）
@Composable
private fun FsCreateDialog(
    existingSiblingPaths: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (isFolder: Boolean, name: String) -> Unit,
) {
    var isFolder by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val trimmed = normalizePath(name)
    val error = validateFsName(name, existingSiblingPaths)
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    MmaDialog(
        onDismissRequest = onDismiss,
        title = FS_DIALOG_CREATE_TITLE,
        confirmLabel = FS_DIALOG_CONFIRM_CREATE,
        onConfirm = { if (error == null) onConfirm(isFolder, trimmed) },
        confirmEnabled = error == null,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.space3)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !isFolder,
                        onClick = { isFolder = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) {
                        Text(text = FS_TYPE_FILE)
                    }
                    SegmentedButton(
                        selected = isFolder,
                        onClick = { isFolder = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) {
                        Text(text = FS_TYPE_FOLDER)
                    }
                }
                ProjectTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = FS_DIALOG_NAME_LABEL,
                    // placeholder 跟随类型切换
                    placeholder = if (isFolder) FS_DIALOG_FOLDER_PLACEHOLDER else FS_DIALOG_FILE_PLACEHOLDER,
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

/** 命名弹窗（重命名共用）：结构性校验 + 实时错误。 */
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

/**
 * 长按操作弹窗：列出该条目的可用操作（带图标列表项）。
 * 可用性沿既有规则：打开（仅文件）、复制、重命名始终可用；
 * 剪切、删除对入口文件不渲染（入口文件仅可打开 / 复制 / 重命名）。
 * 树模式不再提供"进入"——层级展开由 chevron / 行点击承担。
 */
@Composable
private fun FsActionDialog(
    item: FsItem,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val control = LocalMmaDesign.current.control
    MmaDialog(
        onDismissRequest = onDismiss,
        title = item.name,
        dismissLabel = null, // 操作即按钮，底部不再放取消
        confirmLabel = null,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(control.menuGroupGap)) {
                if (!item.isDirectory) {
                    FsMenuItem(MmaIcons.File, FS_MENU_OPEN, colorScheme.onSurface, onOpen)
                }
                FsMenuItem(MmaIcons.Edit, FS_MENU_RENAME, colorScheme.onSurface, onRename)
                FsMenuItem(MmaIcons.ContentCopy, FS_MENU_COPY, colorScheme.onSurface, onCopy)
                if (!item.isEntry) {
                    FsMenuItem(MmaIcons.ContentCut, FS_MENU_CUT, colorScheme.onSurface, onCut)
                }
                if (!item.isEntry) {
                    FsMenuItem(MmaIcons.Delete, FS_MENU_DELETE, colorScheme.error, onDelete)
                }
            }
        },
    )
}

/** 操作弹窗内的单条操作项（图标 + 文案，语义 role=Button 便于测试定位）。 */
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
            .heightIn(min = control.menuItemHeight)
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

// ── 图标映射（组件内私有纯函数，图标用 token 色）──────────────────────

/** 图标底色 / 内容色使用的颜色角色（全部映射自 colorScheme token，无字面量）。 */
private enum class FsColorRole { PRIMARY, SURFACE, TERTIARY }

/** 图标规格：图标资源 + 底色角色 + 内容色角色。 */
private data class FsIconSpec(
    val iconRes: Int,
    val containerRole: FsColorRole,
    val contentRole: FsColorRole,
)

/** 按扩展名映射图标与色系（文档类走 tertiary 系、代码 / 配置走 surface 系、文件夹走 primary 系）。 */
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
private fun contentColorOf(role: FsColorRole): Color {
    val c = MaterialTheme.colorScheme
    return when (role) {
        FsColorRole.PRIMARY -> c.primary
        FsColorRole.SURFACE -> c.onSurfaceVariant
        FsColorRole.TERTIARY -> c.tertiary
    }
}

// ── 常量（中文文案集中） ──────────────────────────────────────────────

private const val FS_ROOT_LABEL: String = "根目录"
private const val FS_COUNT_UNIT: String = "项"
private const val FS_ENTRY_BADGE: String = "入口"
private const val FS_CREATE_CONTENT_DESCRIPTION: String = "新建文件或文件夹"
private const val FS_STATE_EXPANDED: String = "已展开，含 %d 项"
private const val FS_STATE_COLLAPSED: String = "已收起，含 %d 项"
private const val FS_CHEVRON_EXPAND: String = "展开"
private const val FS_CHEVRON_COLLAPSE: String = "收起"
private const val FS_EMPTY_HINT: String = "项目为空，点右上角「＋」新建文件或文件夹。"
private const val FS_ACTION_PASTE: String = "粘贴"
private const val FS_ACTION_PASTE_MULTI: String = "粘贴（%d 项）"
private const val FS_MENU_OPEN: String = "打开"
private const val FS_MENU_COPY: String = "复制"
private const val FS_MENU_CUT: String = "剪切"
private const val FS_MENU_RENAME: String = "重命名"
private const val FS_MENU_DELETE: String = "删除"
private const val FS_DIALOG_CREATE_TITLE: String = "新建"
private const val FS_TYPE_FILE: String = "文件"
private const val FS_TYPE_FOLDER: String = "文件夹"
private const val FS_DIALOG_RENAME_TITLE: String = "重命名"
private const val FS_DIALOG_CONFIRM_CREATE: String = "创建"
private const val FS_DIALOG_CONFIRM_RENAME: String = "重命名"
private const val FS_DIALOG_NAME_LABEL: String = "名称"
private const val FS_DIALOG_FILE_PLACEHOLDER: String = "index.html"
private const val FS_DIALOG_FOLDER_PLACEHOLDER: String = "新建文件夹"
private const val FS_DIALOG_NAME_PLACEHOLDER: String = "css/style.css"
private const val FS_DIALOG_NAME_HINT: String = "支持子目录路径（如 css/style.css）；重名或非法名字无法创建。"
private const val FS_DIALOG_DELETE_TITLE: String = "删除"
private const val FS_DIALOG_DELETE_CONFIRM: String = "删除"
private const val FS_DELETE_FILE_MESSAGE: String = "「%s」将从项目中移除，此操作不可撤销。"
private const val FS_DELETE_DIR_MESSAGE: String = "「%s」及其下全部 %d 个文件将被删除，此操作不可撤销。"
