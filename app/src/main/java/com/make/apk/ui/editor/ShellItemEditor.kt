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
import com.make.apk.ui.components.CuteButton
import com.make.apk.ui.components.MmaDialog
import com.make.apk.ui.components.MmaDropdownField
import com.make.apk.ui.components.MmaIconAction
import com.make.apk.ui.components.MmaOption
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
 */
@Composable
internal fun ShellItemListEditor(
    label: String,
    items: List<ShellMenuItem>,
    onItems: (List<ShellMenuItem>) -> Unit,
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
    val url = (item.action as? ShellAction.NavigateUrl)?.url ?: ""

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
                if (url.isNotEmpty()) {
                    Text(
                        text = url,
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
 * 单条目编辑弹窗（自绘）：标题 + 图标 + 目标地址 + 显隐。
 *
 * **校验前置**：标题必填、地址须 http(s) 开头，不通过时确认按钮处于禁用态——
 * 比"允许确定、点了才报错"少一次无效往返。地址错误只在**非空且非法**时提示，
 * 避免弹窗刚打开就一片红。
 */
@Composable
private fun ShellItemDialog(
    initial: ShellMenuItem?,
    onDismiss: () -> Unit,
    onConfirm: (ShellMenuItem) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var iconKey by remember { mutableStateOf(initial?.iconRef) }
    var url by remember { mutableStateOf((initial?.action as? ShellAction.NavigateUrl)?.url ?: "") }
    var visible by remember { mutableStateOf(initial?.visible ?: true) }

    val titleError = title.isBlank()
    val urlError = url.isNotBlank() && hasUnsupportedScheme(url)

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
                    action = ShellAction.NavigateUrl(normalizeUrl(url.trim())),
                    visible = visible,
                    selected = initial?.selected ?: false,
                ),
            )
        },
        confirmEnabled = !titleError && !urlError && url.isNotBlank(),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.space3)) {
                ProjectTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "标题",
                    modifier = Modifier.fillMaxWidth(),
                    message = if (titleError) FieldMessage(TITLE_REQUIRED, FieldMessage.Severity.ERROR) else null,
                )
                MmaDropdownField(
                    label = "图标",
                    selectedLabel = ICON_OPTIONS.firstOrNull { it.value == iconKey }?.label ?: "无图标",
                    options = ICON_OPTIONS,
                    onSelected = { iconKey = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                ProjectTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = "目标地址 (URL)",
                    placeholder = URL_PLACEHOLDER,
                    modifier = Modifier.fillMaxWidth(),
                    message = if (urlError) {
                        FieldMessage(URL_SCHEME_HINT, FieldMessage.Severity.ERROR)
                    } else {
                        null
                    },
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

/** 图标下拉选项（含矢量图标预览）。 */
private val ICON_OPTIONS: List<MmaOption<String?>> = listOf(
    MmaOption(null, "无图标"),
    MmaOption(ShellIconKeys.REFRESH, "刷新", MmaShellIconResolver.resolve(ShellIconKeys.REFRESH)),
    MmaOption(ShellIconKeys.ADD, "新增", MmaShellIconResolver.resolve(ShellIconKeys.ADD)),
    MmaOption(ShellIconKeys.HOME, "首页", MmaShellIconResolver.resolve(ShellIconKeys.HOME)),
    MmaOption(ShellIconKeys.DISCOVER, "发现", MmaShellIconResolver.resolve(ShellIconKeys.DISCOVER)),
    MmaOption(ShellIconKeys.PROFILE, "我的", MmaShellIconResolver.resolve(ShellIconKeys.PROFILE)),
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
