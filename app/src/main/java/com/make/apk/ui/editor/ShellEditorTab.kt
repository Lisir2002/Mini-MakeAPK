package com.make.apk.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.make.apk.data.model.ShellAction
import com.make.apk.data.model.ShellBottomBar
import com.make.apk.data.model.ShellDrawer
import com.make.apk.data.model.ShellDrawerSource
import com.make.apk.data.model.ShellDrawerWidthMode
import com.make.apk.data.model.ShellIconKeys
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.data.model.ShellPresets
import com.make.apk.data.model.ShellSelectionStyle
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.ShellTopBar
import com.make.apk.data.model.ShellTopTabs
import com.make.apk.ui.components.CuteButton
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.shell.MmaShellIconResolver
import com.make.apk.ui.theme.Spacing
import kotlin.random.Random

/**
 * 进阶配置 Tab（阶段 6.3）——壳四区增删改编辑器。
 *
 * 本 Tab 取代原 [TemplateTab] 的纯占位，把阶段 6.1/6.2 已落地的 [ShellSpec] 契约
 * 暴露给用户编辑：预置壳一键套用 + 顶栏 / 顶栏 tab / 底栏 / 抽屉 四区开关与条目 CRUD。
 *
 * **状态约定**：本组件**无内部业务状态**，所有改动通过 [onShellSpecChanged] 上抛给 VM 落库；
 * 因此可被 Paparazzi 直接喂任意 [ShellSpec] 出图，与编辑页其余 Tab 同构。
 *
 * **不变量**（对齐设计文档 §8.2）：只改 [ShellSpec] 的可配置字段，**绝不**把运行态（选中态 / 抽屉开合）
 * 写回 spec——预览与导出靠同一份 spec + 同一份渲染器保持一致。
 *
 * @param shellSpec 当前壳契约（来自 VM，只读展示 + 编辑回写）。
 * @param onShellSpecChanged 任意区域改动后回调，携带**新的整份** [ShellSpec]。
 */
@Composable
fun ShellEditorTab(
    shellSpec: ShellSpec,
    onShellSpecChanged: (ShellSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        PresetPicker(
            currentSpec = shellSpec,
            onPresetSelected = onShellSpecChanged,
            modifier = Modifier.fillMaxWidth(),
        )

        RegionCard(
            title = "顶栏",
            subtitle = "标题与右侧动作按钮",
            enabled = shellSpec.topBar.enabled,
            onEnabled = { onShellSpecChanged(shellSpec.copy(topBar = shellSpec.topBar.copy(enabled = it))) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            TopBarEditor(
                topBar = shellSpec.topBar,
                onTopBar = { onShellSpecChanged(shellSpec.copy(topBar = it)) },
            )
        }

        RegionCard(
            title = "顶栏分页 (Tab)",
            subtitle = "顶部导航标签，可点击切换",
            enabled = shellSpec.topTabs.enabled,
            onEnabled = { onShellSpecChanged(shellSpec.copy(topTabs = shellSpec.topTabs.copy(enabled = it))) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            TopTabsEditor(
                topTabs = shellSpec.topTabs,
                onTopTabs = { onShellSpecChanged(shellSpec.copy(topTabs = it)) },
            )
        }

        RegionCard(
            title = "底栏",
            subtitle = "底部导航栏",
            enabled = shellSpec.bottomBar.enabled,
            onEnabled = { onShellSpecChanged(shellSpec.copy(bottomBar = shellSpec.bottomBar.copy(enabled = it))) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            BottomBarEditor(
                bottomBar = shellSpec.bottomBar,
                onBottomBar = { onShellSpecChanged(shellSpec.copy(bottomBar = it)) },
            )
        }

        RegionCard(
            title = "侧边栏 (抽屉)",
            subtitle = "侧边菜单（原生列表已渲染；Web 覆盖层仅登记地址）",
            enabled = shellSpec.drawer.enabled,
            onEnabled = { onShellSpecChanged(shellSpec.copy(drawer = shellSpec.drawer.copy(enabled = it))) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            DrawerEditor(
                drawer = shellSpec.drawer,
                onDrawer = { onShellSpecChanged(shellSpec.copy(drawer = it)) },
            )
        }
    }
}

/** 预置壳选择区：默认 + 三套出厂预置，选中项高亮（与渲染侧反查同源）。 */
@Composable
private fun PresetPicker(
    currentSpec: ShellSpec,
    onPresetSelected: (ShellSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    CuteCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            Text(
                text = "预置壳",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
            ) {
                PresetChip(
                    label = "默认壳",
                    selected = currentSpec == ShellSpec.DEFAULT,
                    onClick = { onPresetSelected(ShellSpec.DEFAULT) },
                )
                ShellPresets.ALL.forEach { preset ->
                    PresetChip(
                        label = preset.displayName,
                        selected = currentSpec == preset.spec,
                        onClick = { onPresetSelected(preset.spec) },
                    )
                }
            }
            val matched = ShellPresets.matchingBySpec(currentSpec)
            Text(
                text = matched?.description
                    ?: "自定义壳：基于预置调整后形成，仅作用于当前项目。点「预览」可实时查看效果。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 预置壳切换药丸（复用设计语言的 `FilterChip` 选中态）。 */
@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = modifier,
    )
}

/** 区域卡：标题 + 右侧启用开关；开启后才渲染内部编辑区。 */
@Composable
private fun RegionCard(
    title: String,
    subtitle: String? = null,
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    CuteCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(checked = enabled, onCheckedChange = onEnabled)
            }
            if (enabled) content()
        }
    }
}

/** 顶栏编辑区。 */
@Composable
private fun TopBarEditor(topBar: ShellTopBar, onTopBar: (ShellTopBar) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space3)) {
        SettingsSwitchRow(
            label = "显示标题",
            checked = topBar.showTitle,
            onCheckedChange = { onTopBar(topBar.copy(showTitle = it)) },
        )
        ProjectTextField(
            value = topBar.titleOverride ?: "",
            onValueChange = { onTopBar(topBar.copy(titleOverride = it.takeIf { v -> v.isNotBlank() })) },
            label = "标题文案（留空用项目名）",
            placeholder = "项目名",
            modifier = Modifier.fillMaxWidth(),
        )
        SettingsSwitchRow(
            label = "显示返回按钮",
            checked = topBar.showBackButton,
            onCheckedChange = { onTopBar(topBar.copy(showBackButton = it)) },
        )
        ShellItemListEditor(
            label = "右侧动作按钮",
            items = topBar.actions,
            onItems = { onTopBar(topBar.copy(actions = it)) },
        )
    }
}

/** 顶栏 tab 编辑区。 */
@Composable
private fun TopTabsEditor(topTabs: ShellTopTabs, onTopTabs: (ShellTopTabs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space3)) {
        ShellItemListEditor(
            label = "分页标签",
            items = topTabs.items,
            onItems = { onTopTabs(topTabs.copy(items = it)) },
        )
        SettingsSwitchRow(
            label = "允许左右滑动切页",
            description = "顶栏 tab 兼作分页指示器；预览当前仅点击切换，导出侧将支持滑动",
            checked = topTabs.swipeEnabled,
            onCheckedChange = { onTopTabs(topTabs.copy(swipeEnabled = it)) },
        )
        SettingsSwitchRow(
            label = "显示选中指示器",
            checked = topTabs.showSelectionIndicator,
            onCheckedChange = { onTopTabs(topTabs.copy(showSelectionIndicator = it)) },
        )
        SelectionStyleRow(
            selected = topTabs.selectionStyle,
            onSelected = { onTopTabs(topTabs.copy(selectionStyle = it)) },
        )
    }
}

/** 底栏编辑区。 */
@Composable
private fun BottomBarEditor(bottomBar: ShellBottomBar, onBottomBar: (ShellBottomBar) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space3)) {
        ShellItemListEditor(
            label = "底栏条目",
            items = bottomBar.items,
            onItems = { onBottomBar(bottomBar.copy(items = it)) },
        )
        SettingsSwitchRow(
            label = "显示文字标签",
            checked = bottomBar.showLabels,
            onCheckedChange = { onBottomBar(bottomBar.copy(showLabels = it)) },
        )
        ProjectTextField(
            value = bottomBar.maxItems.toString(),
            onValueChange = {
                val n = it.toIntOrNull()?.coerceIn(1, 8) ?: 5
                onBottomBar(bottomBar.copy(maxItems = n))
            },
            label = "条目上限（1-8）",
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Number,
        )
    }
}

/** 抽屉编辑区（阶段 6.5 起：原生列表已渲染，Web 覆盖层仅登记地址）。 */
@Composable
private fun DrawerEditor(drawer: ShellDrawer, onDrawer: (ShellDrawer) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space3)) {
        Text(
            text = "内容来源",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            RadioRow(
                selected = drawer.contentSource == ShellDrawerSource.NATIVE,
                label = "原生列表",
                onSelect = { onDrawer(drawer.copy(contentSource = ShellDrawerSource.NATIVE)) },
            )
            RadioRow(
                selected = drawer.contentSource == ShellDrawerSource.WEB,
                label = "Web 覆盖层",
                onSelect = { onDrawer(drawer.copy(contentSource = ShellDrawerSource.WEB)) },
            )
        }
        if (drawer.contentSource == ShellDrawerSource.WEB) {
            ProjectTextField(
                value = drawer.webMenuUrl ?: "",
                onValueChange = { onDrawer(drawer.copy(webMenuUrl = it.takeIf { v -> v.isNotBlank() })) },
                label = "Web 菜单地址",
                placeholder = "https://",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ShellItemListEditor(
            label = "菜单条目",
            items = drawer.items,
            onItems = { onDrawer(drawer.copy(items = it)) },
        )
        DrawerWidthRow(
            selected = drawer.width.mode,
            onSelected = { onDrawer(drawer.copy(width = drawer.width.copy(mode = it))) },
        )
        if (drawer.width.mode == ShellDrawerWidthMode.CUSTOM) {
            ProjectTextField(
                value = drawer.width.customDp.toString(),
                onValueChange = {
                    val n = it.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    onDrawer(drawer.copy(width = drawer.width.copy(customDp = n)))
                },
                label = "自定义宽度 (dp)",
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Number,
            )
        }
    }
}

/** 选中态样式单选组（指示器 / 药丸 / 文字）。 */
@Composable
private fun SelectionStyleRow(
    selected: ShellSelectionStyle,
    onSelected: (ShellSelectionStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space1)) {
        Text(
            text = "选中态样式",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            RadioRow(
                selected = selected == ShellSelectionStyle.INDICATOR,
                label = "指示器",
                onSelect = { onSelected(ShellSelectionStyle.INDICATOR) },
            )
            RadioRow(
                selected = selected == ShellSelectionStyle.PILL,
                label = "药丸",
                onSelect = { onSelected(ShellSelectionStyle.PILL) },
            )
            RadioRow(
                selected = selected == ShellSelectionStyle.TEXT,
                label = "文字",
                onSelect = { onSelected(ShellSelectionStyle.TEXT) },
            )
        }
    }
}

/** 抽屉宽度档位单选组。 */
@Composable
private fun DrawerWidthRow(
    selected: ShellDrawerWidthMode,
    onSelected: (ShellDrawerWidthMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space1)) {
        Text(
            text = "抽屉宽度",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            RadioRow(
                selected = selected == ShellDrawerWidthMode.SMALL,
                label = "窄",
                onSelect = { onSelected(ShellDrawerWidthMode.SMALL) },
            )
            RadioRow(
                selected = selected == ShellDrawerWidthMode.MEDIUM,
                label = "中",
                onSelect = { onSelected(ShellDrawerWidthMode.MEDIUM) },
            )
            RadioRow(
                selected = selected == ShellDrawerWidthMode.LARGE,
                label = "宽",
                onSelect = { onSelected(ShellDrawerWidthMode.LARGE) },
            )
            RadioRow(
                selected = selected == ShellDrawerWidthMode.CUSTOM,
                label = "自定义",
                onSelect = { onSelected(ShellDrawerWidthMode.CUSTOM) },
            )
        }
    }
}

/**
 * 壳条目列表编辑器：增 / 删 / 改 / 重排。
 *
 * 删除只从列表移除（保留不可见语义未用，直接删配置）；重排用稳定 [ShellMenuItem.id] 锚定，
 * 不依赖下标，避免重排后选中态漂移（对齐契约文档 §ShellMenuItem.id 约定）。
 */
@Composable
private fun ShellItemListEditor(
    label: String,
    items: List<ShellMenuItem>,
    onItems: (List<ShellMenuItem>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialogItem by remember { mutableStateOf<ShellMenuItem?>(null) }
    var dialogOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        items.forEachIndexed { index, item ->
            ShellItemRow(
                item = item,
                canMoveUp = index > 0,
                canMoveDown = index < items.lastIndex,
                onEdit = { dialogItem = item; dialogOpen = true },
                onMoveUp = { onItems(swapItems(items, index, index - 1)) },
                onMoveDown = { onItems(swapItems(items, index, index + 1)) },
                onDelete = { onItems(items.filterNot { it.id == item.id }) },
            )
        }
        if (items.isEmpty()) {
            Text(
                text = "暂无条目，点击下方「添加条目」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CuteButton(
            text = "添加条目",
            onClick = { dialogItem = null; dialogOpen = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (dialogOpen) {
        ShellItemDialog(
            initial = dialogItem,
            onDismiss = { dialogOpen = false },
            onConfirm = { newItem ->
                onItems(
                    if (dialogItem != null) {
                        items.map { if (it.id == newItem.id) newItem else it }
                    } else {
                        items + newItem
                    },
                )
                dialogOpen = false
            },
        )
    }
}

/** 列表内单行：图标 + 标题 + URL 摘要 + 编辑/上移/下移/删除。 */
@Composable
private fun ShellItemRow(
    item: ShellMenuItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconRes = MmaShellIconResolver.resolve(item.iconRef)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
        ) {
            if (iconRes != null) {
                MmaIcon(
                    iconRes = iconRes,
                    contentDescription = null,
                    size = 20.dp, // token-gate: allow:条目行内小图标紧凑尺寸（比 MmaIcon 默认 24dp 收一圈，保证长列表行高不膨胀）
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column {
                Text(
                    text = item.title.ifEmpty { "(未命名)" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val url = (item.action as? ShellAction.NavigateUrl)?.url ?: ""
                if (url.isNotEmpty()) {
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        TextButton(onClick = onEdit) {
            Text("编辑", style = MaterialTheme.typography.labelMedium)
        }
        TextButton(onClick = onMoveUp, enabled = canMoveUp) {
            Text("↑", style = MaterialTheme.typography.labelMedium)
        }
        TextButton(onClick = onMoveDown, enabled = canMoveDown) {
            Text("↓", style = MaterialTheme.typography.labelMedium)
        }
        TextButton(onClick = onDelete) {
            Text(
                text = "删除",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** 单条目编辑弹窗：标题 + 图标 + 目标 URL。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellItemDialog(
    initial: ShellMenuItem?,
    onDismiss: () -> Unit,
    onConfirm: (ShellMenuItem) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var iconKey by remember { mutableStateOf(initial?.iconRef) }
    var url by remember { mutableStateOf((initial?.action as? ShellAction.NavigateUrl)?.url ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加条目" else "编辑条目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.space3)) {
                ProjectTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "标题",
                    modifier = Modifier.fillMaxWidth(),
                )
                IconKeyDropdown(
                    selected = iconKey,
                    onSelected = { iconKey = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                ProjectTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = "目标地址 (URL)",
                    placeholder = "https://",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val id = initial?.id ?: "itm_%04x".format(Random.nextInt(0xFFFF))
                    onConfirm(
                        ShellMenuItem(
                            id = id,
                            title = title.trim(),
                            iconRef = iconKey,
                            action = ShellAction.NavigateUrl(url.trim()),
                            visible = true,
                            selected = initial?.selected ?: false,
                        ),
                    )
                },
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 逻辑图标键下拉（无图标 / 刷新 / 新增 / 首页 / 发现 / 我的）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconKeyDropdown(
    selected: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options: List<String?> = listOf(
        null,
        ShellIconKeys.REFRESH,
        ShellIconKeys.ADD,
        ShellIconKeys.HOME,
        ShellIconKeys.DISCOVER,
        ShellIconKeys.PROFILE,
    )
    val labelOf: (String?) -> String = { key ->
        when (key) {
            null -> "无图标"
            ShellIconKeys.REFRESH -> "刷新"
            ShellIconKeys.ADD -> "新增"
            ShellIconKeys.HOME -> "首页"
            ShellIconKeys.DISCOVER -> "发现"
            ShellIconKeys.PROFILE -> "我的"
            else -> key
        }
    }
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = labelOf(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("图标") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable, true)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { key ->
                DropdownMenuItem(
                    text = { Text(labelOf(key)) },
                    onClick = {
                        onSelected(key)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** 开关行（标签 + 说明 + 右侧 Switch）。 */
@Composable
private fun SettingsSwitchRow(
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 单选行（圆圈 + 标签，整行可点）。 */
@Composable
private fun RadioRow(
    selected: Boolean,
    label: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 把 [from] 处元素移动到 [to] 处（越界安全返回原列表）。 */
private fun swapItems(list: List<ShellMenuItem>, from: Int, to: Int): List<ShellMenuItem> {
    if (from < 0 || to < 0 || from >= list.size || to >= list.size) return list
    val mutable = list.toMutableList()
    val item = mutable.removeAt(from)
    mutable.add(to, item)
    return mutable
}
