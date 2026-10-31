package com.make.apk.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.make.apk.data.model.ShellAction
import com.make.apk.data.model.ShellIconKeys
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.data.model.ShellPresets
import com.make.apk.data.model.TitleSource
import com.make.apk.ui.components.CuteButton
import com.make.apk.ui.components.IconPickerCategory
import com.make.apk.ui.components.IconPickerEntry
import com.make.apk.ui.components.MmaDialog
import com.make.apk.ui.components.MmaDropdownField
import com.make.apk.ui.components.MmaIconAction
import com.make.apk.ui.components.MmaIconPicker
import com.make.apk.ui.components.MmaOption
import com.make.apk.ui.components.MmaSegmentedChoice
import com.make.apk.ui.components.MmaSwitch
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.icons.MmaIcons
import com.make.apk.ui.shell.MmaShellIconResolver
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.Spacing
import kotlin.random.Random

/**
 * 壳条目列表编辑器：增 / 删 / 改 / 重排 / 显隐。
 *
 * ## 审计修复（进阶配置功能完善性）
 *
 * | 修复前 | 问题 | 修复后 |
 * |---|---|---|
 * | 删除直接生效 | 危险操作无二次确认，误点即丢配置 | 走自绘 [MmaDialog] 红色二次确认 |
 * | 弹窗无校验 | 标题 / 地址留空也能确定，存下不可达条目 | 标题必填、地址须 `http(s)://`，不通过则确认禁用 |
 * | 无显隐开关 | `ShellMenuItem.visible` 字段已建模但编辑器不暴露 | 条目行内 [MmaSwitch] 直接切 |
 * | 四个文字按钮挤一行 | "编辑 ↑ ↓ 删除"横向压扁，点不准 | 条目改**卡片式两行**，操作区右对齐矢量图标按钮 |
 *
 * ## rc11 强化（预置壳交互 / 扩展槽位 / 包容性）
 *
 * - **槽位计数**：标签实时显示条目数（`标签（n）`），槽位占用一目了然；
 * - **上限提示**：[maxItems] 非 null 时超限给出可见提醒（超出部分渲染层会截断）；
 * - **URL 包容性**：确认时无协议地址自动补 `https://`（输入 `example.com` 即可用），
 *   只有显式写了其它协议（如 `ftp://`）才拦；
 * - **快速填充**：空槽位提供「一键添加常用导航」模板（同源预置文案与图标键）。
 *
 * ## 「加载内容」双模式（本地页面 / 网页链接）
 *
 * 条目弹窗的"加载内容"改为 [MmaSegmentedChoice] 两段切换，**两种来源严格隔离**：
 *
 * - **本地页面**：下拉单选项目内真实存在的 `.html` / `.htm` 文件（[localPageFileNames]），
 *   产出 [ShellAction.OpenLocalPage]（项目文件名，**绝不**经过 `normalizeUrl`）；
 * - **网页链接**：沿用 `ProjectTextField` + `hasUnsupportedScheme` / `normalizeUrl`，
 *   产出 [ShellAction.NavigateUrl]；
 * - 切 Tab 即清空另一模式的值（不出现"选了本地文件但仍带着 URL"或反之），
 *   确认按钮按当前 Tab 分别判定可用性。
 */
@Composable
internal fun ShellItemListEditor(
    label: String,
    items: List<ShellMenuItem>,
    onItems: (List<ShellMenuItem>) -> Unit,
    localPageFileNames: List<String>,
    modifier: Modifier = Modifier,
    /** 槽位上限（null = 不限；超出仅提示，不阻止编辑）。 */
    maxItems: Int? = null,
    /** 空槽位时是否显示「快速填充」模板按钮。 */
    showQuickFill: Boolean = false,
) {
    var editingItem by remember { mutableStateOf<ShellMenuItem?>(null) }
    var dialogOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ShellMenuItem?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        Text(
            text = if (items.isEmpty()) label else "$label（${items.size}）",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        items.forEachIndexed { index, item ->
            ShellItemCard(
                item = item,
                canMoveUp = index > 0,
                canMoveDown = index < items.lastIndex,
                onEdit = {
                    editingItem = item
                    dialogOpen = true
                },
                onMoveUp = { onItems(swapItems(items, index, index - 1)) },
                onMoveDown = { onItems(swapItems(items, index, index + 1)) },
                onVisibleChanged = { visible ->
                    onItems(items.map { if (it.id == item.id) it.copy(visible = visible) else it })
                },
                onDelete = { pendingDelete = item },
            )
        }
        if (items.isEmpty()) {
            Text(
                text = EMPTY_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showQuickFill) {
                CuteButton(
                    text = QUICK_FILL_LABEL,
                    onClick = { onItems(items + quickFillItems()) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (maxItems != null && items.size > maxItems) {
            Text(
                text = "$OVER_LIMIT_PREFIX${items.size - maxItems}$OVER_LIMIT_SUFFIX",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        CuteButton(
            text = "添加条目",
            onClick = {
                editingItem = null
                dialogOpen = true
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (dialogOpen) {
        ShellItemDialog(
            initial = editingItem,
            localPageFileNames = localPageFileNames,
            onDismiss = { dialogOpen = false },
            onConfirm = { newItem ->
                onItems(
                    if (editingItem != null) {
                        items.map { if (it.id == newItem.id) newItem else it }
                    } else {
                        items + newItem
                    },
                )
                dialogOpen = false
            },
        )
    }

    pendingDelete?.let { target ->
        MmaDialog(
            onDismissRequest = { pendingDelete = null },
            title = DELETE_TITLE,
            confirmLabel = DELETE_CONFIRM,
            onConfirm = {
                onItems(items.filterNot { it.id == target.id })
                pendingDelete = null
            },
            destructive = true,
            content = {
                Text(
                    text = "「${target.title.ifBlank { UNNAMED_ITEM }}」将从列表中移除，此操作不可撤销。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

/**
 * 条目卡片：第一行（图标 + 标题 + 地址 + 显隐开关），第二行（操作区右对齐）。
 */
@Composable
private fun ShellItemCard(
    item: ShellMenuItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onVisibleChanged: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val iconRes = MmaShellIconResolver.resolve(item.iconRef)
    // 目标摘要：网页显示网址，本地页面显示「本地页面 · 文件名」（两条来源各自可辨）。
    val targetLabel = when (val action = item.action) {
        is ShellAction.NavigateUrl -> action.url
        is ShellAction.OpenLocalPage -> "$LOCAL_PAGE_PREFIX${action.path}"
        else -> ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ControlShapes.itemSingle)
            .background(colorScheme.surfaceVariant)
            .padding(Spacing.space3),
        verticalArrangement = Arrangement.spacedBy(Spacing.space1),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
        ) {
            if (iconRes != null) {
                MmaIcon(
                    iconRes = iconRes,
                    contentDescription = null,
                    size = LocalMmaDesign.current.control.menuItemIcon,
                    tint = colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifBlank { UNNAMED_ITEM },
                    style = typography.bodyMedium,
                    color = if (item.visible) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                )
                if (targetLabel.isNotEmpty()) {
                    Text(
                        text = targetLabel,
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
            MmaSwitch(
                checked = item.visible,
                onCheckedChange = onVisibleChanged,
                contentDescription = "显示该条目",
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MmaIconAction(
                iconRes = MmaIcons.Edit,
                contentDescription = "编辑",
                onClick = onEdit,
            )
            MmaIconAction(
                iconRes = MmaIcons.Collapse,
                contentDescription = "上移",
                onClick = onMoveUp,
                enabled = canMoveUp,
                rotationZ = ARROW_UP_ROTATION,
            )
            MmaIconAction(
                iconRes = MmaIcons.Collapse,
                contentDescription = "下移",
                onClick = onMoveDown,
                enabled = canMoveDown,
            )
            MmaIconAction(
                iconRes = MmaIcons.Delete,
                contentDescription = "删除",
                onClick = onDelete,
                tint = colorScheme.error,
            )
        }
    }
}

/**
 * 单条目编辑弹窗（自绘）：标题 + 图标 + **加载内容（双模式）** + 显隐。
 *
 * ## 加载内容：本地页面 / 网页链接（[LoadMode] 两段切换）
 *
 * 两种来源**互斥且值隔离**，落到模型层是两个不同的 [ShellAction] 子类型：
 *
 * - [LoadMode.LOCAL_PAGE] → [ShellAction.OpenLocalPage]，值是**项目内文件名**，
 *   下拉单选，绝不经过 [normalizeUrl]（否则 `page/about.html` 会被写成
 *   `https://page/about.html`）；
 * - [LoadMode.WEB_URL] → [ShellAction.NavigateUrl]，沿用 http(s) 校验与规范化。
 *
 * 切 Tab 时**清空另一模式的脏值**，确认按钮按当前 Tab 分别判定；
 * 项目内没有可加载页面时本地下拉禁用 + 给出可见提示并阻止确认。
 *
 * **校验前置**：标题必填、地址须 http(s) 开头，不通过时确认按钮处于禁用态——
 * 比"允许确定、点了才报错"少一次无效往返。地址错误只在**非空且非法**时提示，
 * 避免弹窗刚打开就一片红。
 *
 * ## 「页面标题」（W1 ⑫）
 *
 * 开关「自定义页面标题」（[ShellMenuItem.useCustomTitle]）：
 * - **关**：显示「标题来源」分段选择（[ShellMenuItem.titleSource]，项目名称 / 网页标题）；
 * - **开**：显示标题文案输入框（[ShellMenuItem.pageTitle]，可留空）+ 下方保留来源选择——
 *   留空时运行期按 [com.make.apk.data.model.resolveDisplayTitle] 回退到该来源，绝不出现空白标题。
 * 编辑既有条目时三字段**回填初值**，确认时透传（编辑往返不丢配置，F1 回归已钉）。
 */
@Composable
private fun ShellItemDialog(
    initial: ShellMenuItem?,
    localPageFileNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (ShellMenuItem) -> Unit,
) {
    var mode by remember { mutableStateOf(initialModeOf(initial)) }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var iconKey by remember { mutableStateOf(initial?.iconRef) }
    // 已选本地页面：**保留原值**（含"文件已被删"的失效值）——失效由 [staleLocalPage]
    // 单独标出，这样提示能说清"是哪个页面没了"，而不是静默回落成"请选择页面"
    // （否则用户以为自己没选过，实际是选过的文件丢了）。
    var localPage by remember {
        mutableStateOf((initial?.action as? ShellAction.OpenLocalPage)?.path.orEmpty())
    }
    var url by remember { mutableStateOf((initial?.action as? ShellAction.NavigateUrl)?.url ?: "") }
    var visible by remember { mutableStateOf(initial?.visible ?: true) }
    // 「页面标题」三字段（W1 ⑫/F1）：编辑既有条目时**必须回填初值**，
    // 否则确认重建 ShellMenuItem 会把用户配置过的页面标题静默抹回默认（数据丢失）。
    var useCustomTitle by remember { mutableStateOf(initial?.useCustomTitle ?: false) }
    var pageTitle by remember { mutableStateOf(initial?.pageTitle ?: "") }
    var titleSource by remember { mutableStateOf(initial?.titleSource ?: TitleSource.PROJECT_NAME) }

    val titleError = title.isBlank()
    val urlError = url.isNotBlank() && hasUnsupportedScheme(url)
    val hasLocalPages = localPageFileNames.isNotEmpty()
    // 已选页面在项目里已不存在（文件被删）：**可见**提示 + 阻止确认（绝不留悬空引用）。
    val staleLocalPage = localPage.isNotBlank() && localPage !in localPageFileNames
    val confirmEnabled = if (mode == LoadMode.LOCAL_PAGE) {
        !titleError && hasLocalPages && localPage.isNotBlank() && !staleLocalPage
    } else {
        !titleError && !urlError && url.isNotBlank()
    }

    MmaDialog(
        onDismissRequest = onDismiss,
        title = if (initial == null) "添加条目" else "编辑条目",
        confirmLabel = "确定",
        onConfirm = {
            onConfirm(
                ShellMenuItem(
                    id = initial?.id ?: newItemId(),
                    title = title.trim(),
                    iconRef = iconKey,
                    action = if (mode == LoadMode.LOCAL_PAGE) {
                        // 本地模式：项目文件名原样落库（不补协议、不做任何 URL 规范化）。
                        ShellAction.OpenLocalPage(localPage.trim())
                    } else {
                        ShellAction.NavigateUrl(normalizeUrl(url.trim()))
                    },
                    visible = visible,
                    selected = initial?.selected ?: false,
                    // 「页面标题」三字段透传（F1）：编辑往返绝不静默丢配置。
                    useCustomTitle = useCustomTitle,
                    pageTitle = pageTitle.trim(),
                    titleSource = titleSource,
                ),
            )
        },
        confirmEnabled = confirmEnabled,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.space3)) {
                ProjectTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "标题",
                    modifier = Modifier.fillMaxWidth(),
                    message = if (titleError) FieldMessage(TITLE_REQUIRED, FieldMessage.Severity.ERROR) else null,
                )
                MmaIconPicker(
                    label = "图标",
                    selectedKey = iconKey,
                    catalog = ICON_PICKER_CATALOG,
                    onSelected = { iconKey = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                MmaSegmentedChoice(
                    label = "加载内容",
                    options = LOAD_MODE_OPTIONS,
                    selectedValue = mode,
                    onSelected = { next ->
                        if (next != mode) {
                            mode = next
                            // 切换即清空另一模式的值：两种来源互斥，绝不残留脏值
                            // （不会出现"选了本地文件但仍带着 URL"或反之）。
                            if (next == LoadMode.LOCAL_PAGE) url = "" else localPage = ""
                        }
                    },
                )
                if (mode == LoadMode.LOCAL_PAGE) {
                    MmaDropdownField(
                        label = "本地页面",
                        // 失效值不回显成"已选"：下拉回落为"请选择页面"（提示行点名是哪个文件没了）。
                        selectedLabel = when {
                            localPage.isNotBlank() && !staleLocalPage -> localPage
                            !hasLocalPages -> NO_LOCAL_PAGE_SELECTED
                            else -> LOCAL_PAGE_UNSELECTED
                        },
                        options = localPageFileNames.map { MmaOption(it, it) },
                        onSelected = { localPage = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = hasLocalPages,
                    )
                    if (!hasLocalPages || staleLocalPage) {
                        Text(
                            // 失效时**点名**是哪个页面没了（否则用户只能看到"请重新选择"，
                            // 不知道自己原先选的是谁）。
                            text = if (hasLocalPages) {
                                LOCAL_PAGE_MISSING_HINT + localPage
                            } else {
                                NO_LOCAL_PAGE_HINT
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    ProjectTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = "网页地址 (URL)",
                        placeholder = URL_PLACEHOLDER,
                        modifier = Modifier.fillMaxWidth(),
                        message = if (urlError) {
                            FieldMessage(URL_SCHEME_HINT, FieldMessage.Severity.ERROR)
                        } else {
                            null
                        },
                    )
                }
                // ── 页面标题（W1 ⑫）：「打开该页时」的标题，与条目自身的「标题」（菜单标签）区分 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "自定义页面标题",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    MmaSwitch(
                        checked = useCustomTitle,
                        onCheckedChange = { useCustomTitle = it },
                        contentDescription = "自定义页面标题",
                    )
                }
                if (useCustomTitle) {
                    ProjectTextField(
                        value = pageTitle,
                        onValueChange = { pageTitle = it },
                        label = "页面标题文案",
                        // 留空不阻断确认：运行期按 resolveDisplayTitle 回退到下方来源选择。
                        hint = PAGE_TITLE_BLANK_HINT,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                MmaSegmentedChoice(
                    label = if (useCustomTitle) "留空时的标题来源" else "标题来源",
                    options = TITLE_SOURCE_OPTIONS,
                    selectedValue = titleSource,
                    onSelected = { titleSource = it },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "立即显示",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    MmaSwitch(
                        checked = visible,
                        onCheckedChange = { visible = it },
                        contentDescription = "立即显示",
                    )
                }
            }
        },
    )
}

/**
 * 「加载内容」模式：两条互斥来源，落库为两个不同的 [ShellAction] 子类型。
 *
 * 值隔离的硬约束：本地页面是项目文件名（不补协议），网页是 http(s) 地址（可补协议），
 * 二者**互不借用字段**，也互不复用规范化逻辑。
 */
private enum class LoadMode {
    /** 本地页面：项目内的 `.html` / `.htm` 文件。 */
    LOCAL_PAGE,

    /** 网页链接：http(s) 网址。 */
    WEB_URL,
}

/** 弹窗初始模式：本地页面条目回来时落在本地页，其余（含空）落在网页链接。 */
private fun initialModeOf(initial: ShellMenuItem?): LoadMode =
    if (initial?.action is ShellAction.OpenLocalPage) LoadMode.LOCAL_PAGE else LoadMode.WEB_URL

/** 「加载内容」分段选项（顺序即 Tab 顺序）。 */
private val LOAD_MODE_OPTIONS: List<MmaOption<LoadMode>> = listOf(
    MmaOption(LoadMode.LOCAL_PAGE, "本地页面"),
    MmaOption(LoadMode.WEB_URL, "网页链接"),
)

/** 「标题来源」分段选项（值即 [TitleSource]，语义见 [ShellMenuItem.resolveDisplayTitle]）。 */
private val TITLE_SOURCE_OPTIONS: List<MmaOption<TitleSource>> = listOf(
    MmaOption(TitleSource.PROJECT_NAME, "项目名称"),
    MmaOption(TitleSource.PAGE_TITLE, "网页标题"),
)

/** 地址是否写了**不被支持的协议**（http/https 之外仍要拦；无协议不拦——确认时自动补 https）。 */
private fun hasUnsupportedScheme(value: String): Boolean {
    val scheme = value.substringBefore("://", missingDelimiterValue = "")
    return scheme.isNotBlank() && scheme.lowercase() !in SUPPORTED_SCHEMES
}

/** 确认前规范化地址：无协议自动补 https://（包容性：`example.com` 即可用）。 */
private fun normalizeUrl(value: String): String = when {
    value.isBlank() -> value
    hasUnsupportedScheme(value) -> value
    value.startsWith("http://") || value.startsWith("https://") -> value
    else -> "https://$value"
}

/** 渲染层当前支持的地址协议。 */
private val SUPPORTED_SCHEMES: Set<String> = setOf("http", "https")

/** 快速填充：常用导航三件套（标题 / 图标与预置经典款同源，地址用保留示例域）。 */
private fun quickFillItems(): List<ShellMenuItem> = listOf(
    ShellMenuItem(
        id = newItemId(),
        title = "首页",
        iconRef = ShellIconKeys.HOME,
        action = ShellAction.NavigateUrl(ShellPresets.EXAMPLE_URL),
    ),
    ShellMenuItem(
        id = newItemId(),
        title = "发现",
        iconRef = ShellIconKeys.DISCOVER,
        action = ShellAction.NavigateUrl(ShellPresets.EXAMPLE_URL_SECOND),
    ),
    ShellMenuItem(
        id = newItemId(),
        title = "我的",
        iconRef = ShellIconKeys.PROFILE,
        action = ShellAction.NavigateUrl(ShellPresets.EXAMPLE_URL_THIRD),
    ),
)

/**
 * 新条目 id：时间戳（36 进制）+ 4 位随机后缀。
 *
 * 原实现是 `Random.nextInt(0xFFFF)` 单随机 —— 条目一多碰撞概率不可忽略，
 * 而 id 是重排与选中态的锚定键，碰撞会直接导致"改 A 动了 B"。
 */
private fun newItemId(): String =
    "itm_${System.currentTimeMillis().toString(36)}_${
        Random.nextInt(0x10000).toString(RADIX_HEX).padStart(ID_SUFFIX_LENGTH, '0')
    }"

/** 把 [from] 处元素移动到 [to] 处（越界安全返回原列表）。 */
private fun swapItems(list: List<ShellMenuItem>, from: Int, to: Int): List<ShellMenuItem> {
    if (from < 0 || to < 0 || from >= list.size || to >= list.size) return list
    val mutable = list.toMutableList()
    val item = mutable.removeAt(from)
    mutable.add(to, item)
    return mutable
}

/**
 * 图标选择器目录（分类，与 [MmaShellIconResolver] 的逻辑键对齐）。
 *
 * 覆盖弹层按分类陈列网格；每项存**逻辑键**（null = 无图标），渲染时由解析器落具体
 * drawable，保证预览侧与导出侧同键同形（"预览 == 导出"铁律）。
 */
private val ICON_PICKER_CATALOG: List<IconPickerCategory> = listOf(
    IconPickerCategory(
        title = "通用",
        items = listOf(
            IconPickerEntry(null, "无图标"),
            IconPickerEntry(ShellIconKeys.HOME, "首页"),
            IconPickerEntry(ShellIconKeys.DISCOVER, "发现"),
            IconPickerEntry(ShellIconKeys.PROFILE, "我的"),
            IconPickerEntry(ShellIconKeys.REFRESH, "刷新"),
            IconPickerEntry(ShellIconKeys.ADD, "新增"),
        ),
    ),
    IconPickerCategory(
        title = "编辑",
        items = listOf(
            IconPickerEntry(MmaShellIconResolver.KEY_EDIT, "编辑"),
            IconPickerEntry(MmaShellIconResolver.KEY_COPY, "复制"),
            IconPickerEntry(MmaShellIconResolver.KEY_DELETE, "删除"),
            IconPickerEntry(MmaShellIconResolver.KEY_MORE, "更多"),
        ),
    ),
    IconPickerCategory(
        title = "文件",
        items = listOf(
            IconPickerEntry(MmaShellIconResolver.KEY_FILE, "文件"),
            IconPickerEntry(MmaShellIconResolver.KEY_DOCUMENT, "文档"),
            IconPickerEntry(MmaShellIconResolver.KEY_FOLDER, "文件夹"),
            IconPickerEntry(MmaShellIconResolver.KEY_FOLDER_OPEN, "打开的文件夹"),
            IconPickerEntry(MmaShellIconResolver.KEY_CODE, "代码"),
            IconPickerEntry(MmaShellIconResolver.KEY_CONFIG, "配置"),
        ),
    ),
    IconPickerCategory(
        title = "媒体",
        items = listOf(
            IconPickerEntry(MmaShellIconResolver.KEY_PLAY, "播放"),
            IconPickerEntry(MmaShellIconResolver.KEY_NOTIFICATIONS, "通知"),
        ),
    ),
)

/** 上移箭头旋转角（[MmaIcons.Collapse] 默认向下，转 180° 即向上）。 */
private const val ARROW_UP_ROTATION: Float = 180f

private const val RADIX_HEX: Int = 16
private const val ID_SUFFIX_LENGTH: Int = 4
private const val UNNAMED_ITEM: String = "(未命名)"
private const val EMPTY_HINT: String = "暂无条目，点击下方「添加条目」。"
private const val QUICK_FILL_LABEL: String = "一键添加常用导航（首页 / 发现 / 我的）"
private const val OVER_LIMIT_PREFIX: String = "已超出槽位上限 "
private const val OVER_LIMIT_SUFFIX: String = " 项，超出部分不会渲染。"
private const val DELETE_TITLE: String = "删除条目"
private const val DELETE_CONFIRM: String = "删除"
private const val TITLE_REQUIRED: String = "标题不能为空"
private const val URL_SCHEME_HINT: String = "地址需以 http:// 或 https:// 开头"
private const val URL_PLACEHOLDER: String = "https://"
/** 页面标题输入框的留空提示（留空 → 运行期按下方来源选择回退，见 resolveDisplayTitle）。 */
private const val PAGE_TITLE_BLANK_HINT: String = "留空则使用下方来源"
/** 条目卡片里本地页面的目标摘要前缀（与网址区分开）。 */
private const val LOCAL_PAGE_PREFIX: String = "本地页面 · "
/** 本地页面下拉的空态值文案（项目内无页面文件）。 */
private const val NO_LOCAL_PAGE_SELECTED: String = "暂无可选页面"
/** 本地页面下拉的未选择文案。 */
private const val LOCAL_PAGE_UNSELECTED: String = "请选择页面"
/** 项目内没有可加载页面时的提示（阻止确认）。 */
private const val NO_LOCAL_PAGE_HINT: String = "暂无本地页面，请先在项目代码页添加 .html 文件"
/** 已选页面在项目里不存在（被删除）时的提示前缀，后接待失效文件名（阻止确认）。 */
private const val LOCAL_PAGE_MISSING_HINT: String = "所选页面已不存在，请重新选择："
