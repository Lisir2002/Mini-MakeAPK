package com.make.apk.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.make.apk.ui.components.MmaTabRow
import com.make.apk.ui.viewmodel.EditTab

/**
 * 编辑页底部三 Tab（§10.2）：基本配置 / 进阶配置 / 高级配置。
 *
 * Tab 的枚举沿用 VM 的 [EditTab]（`BASIC` / `TEMPLATE` / `EXPORT`），中文 label 在此映射：
 * - `BASIC`     → 基本配置（全 Web 运行时设置 + 主题色）
 * - `TEMPLATE`  → 进阶配置（壳四区自定义）
 * - `EXPORT`    → 高级配置（引擎锁定 / 隔离 / 删除）
 *
 * **自绘**：原用原生 `TabRow`（指示器等宽横条 + 位移动画，切换中途会让人看不清
 * "选中了谁"）；改用 [MmaTabRow] 后指示器是**宽度生长**且只占标签宽 60%，
 * 与全站自绘控件同语言。
 *
 * 顺序即索引顺序 —— [MmaTabRow] 回传索引，此处按下标取回枚举，故新增 Tab 时
 * 两处必须同时改，不可只改一处。
 */
@Composable
fun EditTabRow(
    selectedTab: EditTab,
    onTabSelected: (EditTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        EditTab.BASIC to "基本配置",
        EditTab.TEMPLATE to "进阶配置",
        EditTab.EXPORT to "高级配置",
    )
    MmaTabRow(
        tabs = tabs.map { it.second },
        selectedIndex = selectedTab.ordinal,
        onSelected = { index -> onTabSelected(tabs[index].first) },
        modifier = modifier,
    )
}
