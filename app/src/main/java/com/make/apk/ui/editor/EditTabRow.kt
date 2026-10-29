package com.make.apk.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.make.apk.data.model.ProjectType
import com.make.apk.ui.components.MmaTabRow
import com.make.apk.ui.viewmodel.EditTab

/**
 * 编辑页底部 Tab（§10.2）：基本配置 / 项目代码 / 进阶配置 / 高级配置。
 *
 * ## 按类型过滤（类型隔离）
 *
 * Tab 集合**由 [projectType] 决定**，不是所有项目同一套：
 * - `CODE`（项目代码）仅对"有文件的项目"（HTML）出现——URL 等纯引用类型
 *   **隔离隐藏**，绝不出现一个空代码页；
 * - `VUE` / `PHP` 待存档拾取实现后加入 [CODE] 可见类型（表驱动，一行扩展）；
 * - 其余 Tab 全类型通用。
 *
 * **自绘**：[MmaTabRow] 指示器宽度生长、只占标签宽 60%，与全站自绘控件同语言。
 *
 * 下标映射基于**过滤后的列表**（而非枚举 ordinal），选中项不在可见集合时回落
 * [EditTab.BASIC]——杜绝"隐藏 tab 的高亮/空内容"这类错位。
 */
@Composable
fun EditTabRow(
    selectedTab: EditTab,
    onTabSelected: (EditTab) -> Unit,
    projectType: ProjectType?,
    modifier: Modifier = Modifier,
) {
    val tabs = buildList {
        add(EditTab.BASIC to "基本配置")
        if (projectType in CODE_TAB_TYPES) add(EditTab.CODE to "项目代码")
        add(EditTab.TEMPLATE to "进阶配置")
        add(EditTab.EXPORT to "高级配置")
    }
    val selectedIndex = tabs.indexOfFirst { it.first == selectedTab }.takeIf { it >= 0 } ?: 0
    MmaTabRow(
        tabs = tabs.map { it.second },
        selectedIndex = selectedIndex,
        onSelected = { index -> onTabSelected(tabs[index].first) },
        modifier = modifier,
    )
}

/** 可见「项目代码」页的类型集合（有文件的项目；VUE/PHP 待存档拾取后加入）。 */
private val CODE_TAB_TYPES: List<ProjectType> = listOf(ProjectType.HTML)
