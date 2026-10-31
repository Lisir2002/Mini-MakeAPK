package com.make.apk.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.make.apk.data.model.ShellBottomBar
import com.make.apk.data.model.ShellDrawer
import com.make.apk.data.model.ShellDrawerHeaderMode
import com.make.apk.data.model.ShellDrawerSource
import com.make.apk.data.model.ShellDrawerWidthMode
import com.make.apk.data.model.ShellSelectionStyle
import com.make.apk.data.model.ShellTopBar
import com.make.apk.data.model.ShellTopTabs
import com.make.apk.logging.MmaLogger
import com.make.apk.storage.DrawerHeaderStore
import com.make.apk.ui.components.CuteButton
import com.make.apk.ui.components.MmaNumberStepper
import com.make.apk.ui.components.MmaOption
import com.make.apk.ui.components.MmaSegmentedChoice
import com.make.apk.ui.components.MmaSwitchRow
import com.make.apk.ui.shell.decodeDrawerHeaderImage
import com.make.apk.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * 壳四区的编辑区（顶栏 / 顶栏 tab / 底栏 / 抽屉）。
 *
 * 从 [ShellEditorTab] 拆出：原单文件 771 行，远超团队「单文件 ≤300 行」门禁，
 * 且四个区域互不依赖，拆分后各自可独立阅读。
 *
 * 全部控件自绘：开关 [MmaSwitchRow]、互斥选项 [MmaSegmentedChoice]、
 * 有界整数 [MmaNumberStepper]（替代会静默改值的文本输入框）。
 */
@Composable
internal fun TopBarEditor(
    topBar: ShellTopBar,
    localPageFileNames: List<String>,
    onTopBar: (ShellTopBar) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsColumn(modifier = modifier) {
        MmaSwitchRow(
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
        MmaSwitchRow(
            label = "显示返回按钮",
            checked = topBar.showBackButton,
            onCheckedChange = { onTopBar(topBar.copy(showBackButton = it)) },
        )
        ShellItemListEditor(
            label = "右侧动作按钮",
            items = topBar.actions,
            localPageFileNames = localPageFileNames,
            onItems = { onTopBar(topBar.copy(actions = it)) },
        )
    }
}

/** 顶栏 tab 编辑区。 */
@Composable
internal fun TopTabsEditor(
    topTabs: ShellTopTabs,
    localPageFileNames: List<String>,
    onTopTabs: (ShellTopTabs) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsColumn(modifier = modifier) {
        ShellItemListEditor(
            label = "分页标签",
            items = topTabs.items,
            localPageFileNames = localPageFileNames,
            onItems = { onTopTabs(topTabs.copy(items = it)) },
            // 空槽位快速填充（rc11 交互强化）。
            showQuickFill = true,
        )
        MmaSwitchRow(
            label = "允许左右滑动切页",
            description = "顶栏 tab 兼作分页指示器；预览当前仅点击切换，导出侧将支持滑动",
            checked = topTabs.swipeEnabled,
            onCheckedChange = { onTopTabs(topTabs.copy(swipeEnabled = it)) },
        )
        MmaSwitchRow(
            label = "显示选中指示器",
            checked = topTabs.showSelectionIndicator,
            onCheckedChange = { onTopTabs(topTabs.copy(showSelectionIndicator = it)) },
        )
        MmaSegmentedChoice(
            label = "选中态样式",
            options = SELECTION_STYLE_OPTIONS,
            selectedValue = topTabs.selectionStyle,
            onSelected = { onTopTabs(topTabs.copy(selectionStyle = it)) },
        )
    }
}

/**
 * 底栏编辑区。
 *
 * **条目上限改步进器**：原为文本输入 + `coerceIn(1, 8)`，用户输入 0 / 99 会被
 * 静默改成 1 / 8（值在眼皮底下跳变且无提示）；步进器把取值域画成控件，
 * 到边界即禁用按钮。
 */
@Composable
internal fun BottomBarEditor(
    bottomBar: ShellBottomBar,
    localPageFileNames: List<String>,
    onBottomBar: (ShellBottomBar) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsColumn(modifier = modifier) {
        ShellItemListEditor(
            label = "底栏条目",
            items = bottomBar.items,
            localPageFileNames = localPageFileNames,
            onItems = { onBottomBar(bottomBar.copy(items = it)) },
            // 槽位上限提示 + 空槽位快速填充（rc11 扩展槽位强化）。
            maxItems = bottomBar.maxItems,
            showQuickFill = true,
        )
        MmaSwitchRow(
            label = "显示文字标签",
            checked = bottomBar.showLabels,
            onCheckedChange = { onBottomBar(bottomBar.copy(showLabels = it)) },
        )
        MmaNumberStepper(
            label = "条目上限",
            value = bottomBar.maxItems,
            range = BOTTOM_BAR_ITEMS_RANGE,
            onValueChange = { onBottomBar(bottomBar.copy(maxItems = it)) },
            unit = "项",
        )
        // 选中态样式（W1 ②）：与顶栏 tab 同枚举同三档，渲染层按档呈现
        // （指示器条 / 药丸背景 / 仅强调色文字），默认 TEXT = 既有观感。
        MmaSegmentedChoice(
            label = "选中态样式",
            options = SELECTION_STYLE_OPTIONS,
            selectedValue = bottomBar.selectionStyle,
            onSelected = { onBottomBar(bottomBar.copy(selectionStyle = it)) },
        )
    }
}

/**
 * 抽屉编辑区（原生列表已渲染；Web 覆盖层仅登记地址）。
 *
 * 内容来源 / 宽度档位原本是横向罗列的 `RadioButton`，四档时会溢出屏幕；
 * 改为分段控件后等分宽度，绝不撑破。
 *
 * ## 抽屉头部（W2 ⑨）
 *
 * 标题 / 副标题可空输入（留空回落项目名 / 默认文案）+ 文字 / 图片模式分段；
 * 图片模式展开上传入口（Photo Picker → 居中裁 2:1 → 1024×512，见
 * [com.make.apk.storage.DrawerHeaderStore]）、2:1 预览、比例提示与移除按钮。
 * 全部字段落在 [ShellDrawer]（onDrawer 上抛 → onShellSpecChanged 落库，
 * 既有 update 链路自动镜像 manifest），零新增 VM 状态。
 */
@Composable
internal fun DrawerEditor(
    drawer: ShellDrawer,
    localPageFileNames: List<String>,
    onDrawer: (ShellDrawer) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsColumn(modifier = modifier) {
        // ── 抽屉头部（W2 ⑨）：头部在视觉上位于菜单最上方，编辑区也放最前 ──
        ProjectTextField(
            value = drawer.headerTitle ?: "",
            onValueChange = { onDrawer(drawer.copy(headerTitle = it.takeIf { v -> v.isNotBlank() })) },
            label = "头部标题（留空用项目名）",
            placeholder = "项目名",
            modifier = Modifier.fillMaxWidth(),
        )
        ProjectTextField(
            value = drawer.headerSubtitle ?: "",
            onValueChange = { onDrawer(drawer.copy(headerSubtitle = it.takeIf { v -> v.isNotBlank() })) },
            label = "头部副标题（留空用默认）",
            placeholder = DRAWER_HEADER_SUBTITLE_FALLBACK,
            modifier = Modifier.fillMaxWidth(),
        )
        MmaSegmentedChoice(
            label = "头部模式",
            options = DRAWER_HEADER_MODE_OPTIONS,
            selectedValue = drawer.headerMode,
            onSelected = { onDrawer(drawer.copy(headerMode = it)) },
        )
        if (drawer.headerMode == ShellDrawerHeaderMode.IMAGE) {
            DrawerHeaderImageSection(
                drawer = drawer,
                onDrawer = onDrawer,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MmaSegmentedChoice(
            label = "内容来源",
            options = DRAWER_SOURCE_OPTIONS,
            selectedValue = drawer.contentSource,
            onSelected = { onDrawer(drawer.copy(contentSource = it)) },
        )
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
            localPageFileNames = localPageFileNames,
            onItems = { onDrawer(drawer.copy(items = it)) },
        )
        MmaSegmentedChoice(
            label = "抽屉宽度",
            options = DRAWER_WIDTH_OPTIONS,
            selectedValue = drawer.width.mode,
            onSelected = { onDrawer(drawer.copy(width = drawer.width.copy(mode = it))) },
        )
        if (drawer.width.mode == ShellDrawerWidthMode.CUSTOM) {
            MmaNumberStepper(
                label = "自定义宽度",
                value = drawer.width.customDp,
                range = DRAWER_WIDTH_RANGE,
                onValueChange = { onDrawer(drawer.copy(width = drawer.width.copy(customDp = it))) },
                unit = "dp",
            )
        }
    }
}

/**
 * 头图上传 / 预览 / 移除节（[ShellDrawerHeaderMode.IMAGE] 模式展开）。
 *
 * - **上传**：Photo Picker（与项目图标同一授权交互，无需存储权限）→
 *   [DrawerHeaderStore.saveFromPicker] 居中裁 2:1 + 标准化落盘，成功即写
 *   `headerImageRef`（并自动切到图片模式，减少一次多余点击）；
 * - **预览**：与渲染层**同一解码器**（[decodeDrawerHeaderImage]），预览所见即导出所得；
 * - **移除**：删文件 + 清 `headerImageRef` + **模式切回 TEXT**（QA Minor-F2：
 *   编辑器观感与 spec 状态保持一致，绝不留下「IMAGE 缺图」悬空态）；
 * - 保存失败（解码 / 写盘）：静默回落现状 + 留痕日志，不阻断编辑（用户可重试）。
 */
@Composable
private fun DrawerHeaderImageSection(
    drawer: ShellDrawer,
    onDrawer: (ShellDrawer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { DrawerHeaderStore(context) }
    val hasImage = !drawer.headerImageRef.isNullOrBlank()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult // 用户取消，静默即可
        scope.launch {
            val path = store.saveFromPicker(uri)
            if (path != null) {
                onDrawer(drawer.copy(headerImageRef = path, headerMode = ShellDrawerHeaderMode.IMAGE))
            } else {
                MmaLogger.operation(
                    event = "drawer_header_save_failed",
                    message = "抽屉头图保存失败，已保持原状",
                )
            }
        }
    }

    // 预览：与渲染层同一解码器；首帧占位，可解码后换真图（与 ProjectIcon 同模式）。
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = drawer.headerImageRef) {
        value = decodeDrawerHeaderImage(context, drawer.headerImageRef)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "抽屉头图预览",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(DRAWER_HEADER_PREVIEW_ASPECT_RATIO)
                    .clip(MaterialTheme.shapes.large),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(DRAWER_HEADER_PREVIEW_ASPECT_RATIO)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = DRAWER_HEADER_EMPTY_HINT,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = DRAWER_HEADER_RATIO_HINT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space2)) {
            CuteButton(
                text = if (hasImage) "更换图片" else "选择图片",
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                modifier = Modifier.weight(1f),
            )
            if (hasImage) {
                CuteButton(
                    text = "移除图片",
                    onClick = {
                        store.delete(drawer.headerImageRef)
                        // QA Minor-F2：移除即整体回落文字模式——只清 ref 不切模式会留下
                        // 「编辑器回落 TEXT 观感、但 spec 仍 IMAGE 缺图」的不对称，
                        // 导出时会被注入管线按「IMAGE 缺图」构建期拒绝。
                        onDrawer(
                            drawer.copy(
                                headerImageRef = null,
                                headerMode = ShellDrawerHeaderMode.TEXT,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 各区域编辑区的统一纵向容器（间距一致，避免四个区域各写一份）。 */
@Composable
private fun SettingsColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.space3),
    ) {
        content()
    }
}

/** 底栏条目上限取值域。 */
private val BOTTOM_BAR_ITEMS_RANGE: IntRange = 1..8

/** 抽屉自定义宽度取值域（窄 240 / 中 300 / 宽 360 三档之外的合理区间）。 */
private val DRAWER_WIDTH_RANGE: IntRange = 160..400

/** 选中态样式选项（顶栏 tab 与底栏共用，三档语义一致）。 */
private val SELECTION_STYLE_OPTIONS: List<MmaOption<ShellSelectionStyle>> = listOf(
    MmaOption(ShellSelectionStyle.INDICATOR, "指示器"),
    MmaOption(ShellSelectionStyle.PILL, "药丸"),
    MmaOption(ShellSelectionStyle.TEXT, "文字"),
)

/** 抽屉头部模式选项（W2 ⑨：文字 = 现状，图片 = 2:1 头图）。 */
private val DRAWER_HEADER_MODE_OPTIONS: List<MmaOption<ShellDrawerHeaderMode>> = listOf(
    MmaOption(ShellDrawerHeaderMode.TEXT, "文字"),
    MmaOption(ShellDrawerHeaderMode.IMAGE, "图片"),
)

/** 头图预览区宽高比（与渲染层 2:1 一致，所见即所得）。 */
private const val DRAWER_HEADER_PREVIEW_ASPECT_RATIO: Float = 2f

/** 副标题输入框的占位（= 渲染层回落文案，与 ShellDrawerSection.DRAWER_SUBTITLE 同源口径）。 */
private const val DRAWER_HEADER_SUBTITLE_FALLBACK: String = "原生抽屉 · 与导出壳同源"

/** 头图未选择时的占位文案。 */
private const val DRAWER_HEADER_EMPTY_HINT: String = "尚未选择头图"

/** 头图比例提示（上传 UI，设计文档 A3-2 原文口径）。 */
private const val DRAWER_HEADER_RATIO_HINT: String = "建议横版图片，将自动居中裁剪为 2:1"

/** 抽屉内容来源选项。 */
private val DRAWER_SOURCE_OPTIONS: List<MmaOption<ShellDrawerSource>> = listOf(
    MmaOption(ShellDrawerSource.NATIVE, "原生列表"),
    MmaOption(ShellDrawerSource.WEB, "Web 覆盖层"),
)

/** 抽屉宽度档位选项。 */
private val DRAWER_WIDTH_OPTIONS: List<MmaOption<ShellDrawerWidthMode>> = listOf(
    MmaOption(ShellDrawerWidthMode.SMALL, "窄"),
    MmaOption(ShellDrawerWidthMode.MEDIUM, "中"),
    MmaOption(ShellDrawerWidthMode.LARGE, "宽"),
    MmaOption(ShellDrawerWidthMode.CUSTOM, "自定义"),
)
