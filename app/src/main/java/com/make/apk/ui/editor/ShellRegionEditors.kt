package com.make.apk.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.make.apk.data.model.ShellBottomBar
import com.make.apk.data.model.ShellDrawer
import com.make.apk.data.model.ShellDrawerSource
import com.make.apk.data.model.ShellDrawerWidthMode
import com.make.apk.data.model.ShellSelectionStyle
import com.make.apk.data.model.ShellTopBar
import com.make.apk.data.model.ShellTopTabs
import com.make.apk.ui.components.MmaNumberStepper
import com.make.apk.ui.components.MmaOption
import com.make.apk.ui.components.MmaSegmentedChoice
import com.make.apk.ui.components.MmaSwitchRow
import com.make.apk.ui.theme.Spacing

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
            onItems = { onTopBar(topBar.copy(actions = it)) },
        )
    }
}

/** 顶栏 tab 编辑区。 */
@Composable
internal fun TopTabsEditor(
    topTabs: ShellTopTabs,
    onTopTabs: (ShellTopTabs) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsColumn(modifier = modifier) {
        ShellItemListEditor(
            label = "分页标签",
            items = topTabs.items,
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
    onBottomBar: (ShellBottomBar) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsColumn(modifier = modifier) {
        ShellItemListEditor(
            label = "底栏条目",
            items = bottomBar.items,
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
    }
}

/**
 * 抽屉编辑区（原生列表已渲染；Web 覆盖层仅登记地址）。
 *
 * 内容来源 / 宽度档位原本是横向罗列的 `RadioButton`，四档时会溢出屏幕；
 * 改为分段控件后等分宽度，绝不撑破。
 */
@Composable
internal fun DrawerEditor(
    drawer: ShellDrawer,
    onDrawer: (ShellDrawer) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsColumn(modifier = modifier) {
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

/** 顶栏 tab 选中态样式选项。 */
private val SELECTION_STYLE_OPTIONS: List<MmaOption<ShellSelectionStyle>> = listOf(
    MmaOption(ShellSelectionStyle.INDICATOR, "指示器"),
    MmaOption(ShellSelectionStyle.PILL, "药丸"),
    MmaOption(ShellSelectionStyle.TEXT, "文字"),
)

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
