package com.make.apk.ui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.make.apk.ui.viewmodel.EditTab

/**
 * 编辑页底部三 Tab（§10.2）：基本配置 / 进阶配置 / 高级配置。
 *
 * Tab 的枚举沿用 VM 的 [EditTab]（`BASIC` / `TEMPLATE` / `EXPORT`），中文 label 在此映射：
 * - `BASIC`     → 基本配置（全 Web 运行时设置 + 主题色）
 * - `TEMPLATE`  → 进阶配置（模版应用自定义，本期占位）
 * - `EXPORT`    → 高级配置（引擎锁定 / 隔离 / 删除）
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
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = modifier.fillMaxWidth(),
    ) {
        tabs.forEach { (tab, label) ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            )
        }
    }
}
