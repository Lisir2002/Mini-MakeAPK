package com.make.apk.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.make.apk.data.model.ShellPresets
import com.make.apk.data.model.ShellSpec
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.components.MmaChip
import com.make.apk.ui.components.MmaSwitch
import com.make.apk.ui.theme.Spacing

/**
 * 进阶配置 Tab（阶段 6.3）——壳四区增删改编辑器。
 *
 * 本 Tab 把 [ShellSpec] 契约暴露给用户编辑：预置壳一键套用 + 顶栏 / 顶栏 tab /
 * 底栏 / 抽屉 四区开关与条目 CRUD。
 *
 * **状态约定**：本组件**无内部业务状态**，所有改动通过 [onShellSpecChanged] 上抛给
 * VM 落库；因此可被 Paparazzi 直接喂任意 [ShellSpec] 出图，与编辑页其余 Tab 同构。
 *
 * **不变量**（对齐设计文档 §8.2）：只改 [ShellSpec] 的可配置字段，**绝不**把运行态
 * （选中态 / 抽屉开合）写回 spec——预览与导出靠同一份 spec + 同一份渲染器保持一致。
 *
 * **文件拆分**：四个区域的编辑区在 [ShellRegionEditors]，条目 CRUD 在
 * [ShellItemEditor] —— 本文件只做装配，避免再次膨胀成 700+ 行的巨文件。
 *
 * @param shellSpec 当前壳契约（来自 VM，只读展示 + 编辑回写）。
 * @param localPageFileNames 项目内可加载的本地页面文件名（入口在前；URL 项目传空集）。
 *   逐级透传给四个区域的条目弹窗，供「本地页面」模式单选；**没有默认值**——
 *   漏传会让"本地页面"静默不可用，故由调用方显式给（数据来源：
 *   [com.make.apk.ui.viewmodel.ProjectEditUiState.localPageFileNames]）。
 * @param onShellSpecChanged 任意区域改动后回调，携带**新的整份** [ShellSpec]。
 */
@Composable
fun ShellEditorTab(
    shellSpec: ShellSpec,
    localPageFileNames: List<String>,
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
                localPageFileNames = localPageFileNames,
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
                localPageFileNames = localPageFileNames,
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
                localPageFileNames = localPageFileNames,
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
                localPageFileNames = localPageFileNames,
                onDrawer = { onShellSpecChanged(shellSpec.copy(drawer = it)) },
            )
        }
    }
}

/**
 * 预置壳选择区：默认 + 三套出厂预置，选中项高亮（与渲染侧反查同源）。
 *
 * 芯片横排用 [FlowRow] 自动换行 —— 预置数量增加时不会挤成一行或溢出屏幕。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetPicker(
    currentSpec: ShellSpec,
    onPresetSelected: (ShellSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    CuteCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            Text(
                text = "预置壳",
                style = typography.titleMedium,
                color = colorScheme.onSurface,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
                verticalArrangement = Arrangement.spacedBy(Spacing.space2),
            ) {
                MmaChip(
                    label = "默认壳",
                    selected = currentSpec == ShellSpec.DEFAULT,
                    onClick = { onPresetSelected(ShellSpec.DEFAULT) },
                )
                ShellPresets.ALL.forEach { preset ->
                    MmaChip(
                        label = preset.displayName,
                        selected = currentSpec == preset.spec,
                        onClick = { onPresetSelected(preset.spec) },
                    )
                }
            }
            Text(
                text = ShellPresets.matchingBySpec(currentSpec)?.description
                    ?: CUSTOM_SHELL_HINT,
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
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
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

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
                        style = typography.titleMedium,
                        color = colorScheme.onSurface,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }
                MmaSwitch(
                    checked = enabled,
                    onCheckedChange = onEnabled,
                    contentDescription = title,
                )
            }
            if (enabled) content()
        }
    }
}

/** 自定义壳说明：与任何预置都不逐字段相等时的提示。 */
private const val CUSTOM_SHELL_HINT: String =
    "自定义壳：基于预置调整后形成，仅作用于当前项目。点「预览」可实时查看效果。"
