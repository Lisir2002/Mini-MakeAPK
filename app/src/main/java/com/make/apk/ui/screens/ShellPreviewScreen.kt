package com.make.apk.ui.screens

import android.content.Context
import android.view.View
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ShellAction
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.data.model.ShellPreset
import com.make.apk.data.model.ShellTopTabs
import com.make.apk.data.model.WebRuntimeConfig
import com.make.apk.ui.shell.ShellHost
import com.make.apk.ui.shell.visibleTopTabItems
import com.make.apk.ui.theme.Spacing
import com.make.apk.ui.viewmodel.ShellPreviewUiState

/**
 * 壳预览屏（`project/{id}/run`，阶段 6.2）——**全屏壳**，无任何表单。
 *
 * ## 布局
 *
 * 预览屏本身就是"一台被注入了壳的设备"：
 * 顶栏 → 顶栏 tab → 内容区（WebView）→ 底栏，全部由 [ShellHost] 按
 * [com.make.apk.data.model.ShellSpec] 渲染；页面自身**只**额外提供一条
 * 预置子模板选择条（预览工具，不属于壳契约）。
 *
 * ## 关键行为
 *
 * - **切换子模板不重载 WebView**：模板切换只改 `ShellSpec`，
 *   内容区的 `WebRuntimeConfig` 不变 → `WebViewScreen` 的去重判定命中 → 不 reload；
 * - **滑动切页只接线不实现**：页面只负责"每页该装什么 URL"（[pagedContent]），
 *   pager 容器与切页时序归 [ShellHost]（L3 唯一渲染入口），
 *   导出壳复用同一入口即获得同样的滑动切页与暂停行为（阶段 6.6）；
 * - **非 URL 项目明确拦截**：显示"当前仅支持 URL 项目预览"，不崩、不静默失败；
 * - **示例地址标注**：预置模板的 tab / 底栏目标地址是 `example.com` 保留域，
 *   跳过去后显式标注"示例地址"，不让用户误认成自己的站点；
 * - **顶栏返回**：`onBack` 交给宿主 `popBackStack`。
 *
 * @param state 预览状态（VM 推送）。
 * @param onBack 返回（宿主 `popBackStack`）。
 * @param onPresetSelected 预置子模板切换。
 * @param onTopTabSelected 顶栏 tab 选中。
 * @param onBottomItemSelected 底栏条目选中。
 * @param onDrawerItemSelected 抽屉菜单项点击。
 * @param onDrawerOpenChanged 抽屉开合变化（手势 / 遮罩 / 点菜单项后收起）。
 * @param onShellAction 顶栏动作点击。
 * @param webViewFactory WebView 构造工厂（截图测试注入轻量 View 桩）。
 */
@Composable
fun ShellPreviewScreen(
    state: ShellPreviewUiState,
    onBack: () -> Unit,
    onPresetSelected: (String) -> Unit,
    onTopTabSelected: (ShellMenuItem) -> Unit,
    onBottomItemSelected: (ShellMenuItem) -> Unit,
    onDrawerItemSelected: (ShellMenuItem) -> Unit,
    onDrawerOpenChanged: (Boolean) -> Unit,
    onShellAction: (ShellMenuItem) -> Unit,
    modifier: Modifier = Modifier,
    webViewFactory: (Context) -> View = { WebView(it) },
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.loading -> PreviewNotice(text = PREVIEW_LOADING_TEXT)
            state.projectNotFound -> PreviewNotice(text = PREVIEW_NOT_FOUND_TEXT)
            !state.previewable -> PreviewNotice(text = state.unsupportedMessage ?: PREVIEW_UNSUPPORTED_TEXT)
            else -> {
                val config = state.runtimeConfig
                ShellHost(
                    spec = state.shellSpec,
                    projectThemeColor = state.themeColor,
                    title = state.apkName,
                    selectedTopTabId = state.selectedTopTabId,
                    selectedBottomBarId = state.selectedBottomItemId,
                    selectedDrawerItemId = state.selectedDrawerItemId,
                    drawerOpen = state.drawerOpen,
                    onBack = onBack,
                    onShellAction = onShellAction,
                    onTopTabSelected = onTopTabSelected,
                    onBottomBarSelected = onBottomItemSelected,
                    onDrawerOpenChanged = onDrawerOpenChanged,
                    onDrawerItemSelected = onDrawerItemSelected,
                    // 预置子模板选择条是**预览工具**（不属于壳契约）：走 contentHeader 工具位，
                    // 固定在顶栏 tab 与内容区之间，且不随 pager 滑动。
                    contentHeader = {
                        PresetSelector(
                            presets = state.presets,
                            selectedPresetId = state.selectedPresetId,
                            isExampleContent = state.isExampleContent,
                            onPresetSelected = onPresetSelected,
                        )
                    },
                    // 每页一个 WebView：页数与页序由 ShellHost 按可见 tab 条目决定，
                    // 本页只负责"第 page 页该装哪个 URL"（与导出壳共用同一入口）。
                    pagedContent = if (config == null) {
                        null
                    } else {
                        { page, pageModifier ->
                            WebViewScreen(
                                config = pagedRuntimeConfig(
                                    baseConfig = config,
                                    topTabs = state.shellSpec.topTabs,
                                    page = page,
                                ),
                                modifier = pageModifier,
                                paused = state.contentPaused,
                                webViewFactory = webViewFactory,
                            )
                        }
                    },
                ) { contentModifier ->
                    if (config != null) {
                        WebViewScreen(
                            config = config,
                            modifier = contentModifier.fillMaxSize(),
                            paused = state.contentPaused,
                            webViewFactory = webViewFactory,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 预置子模板选择条（预览工具，不属于壳契约）。
 *
 * 放在内容区顶部而非壳顶栏内——壳顶栏的构成完全由 `ShellSpec` 决定，
 * 预览工具混进去会污染"预览 == 导出"的一致性。
 *
 * ## 高亮 == 渲染
 *
 * [selectedPresetId] 为 null 时（当前渲染的是**项目自定义壳**，与任何预置都不相等），
 * 不高亮任何预置，改显示"自定义"态标签——绝不出现
 * "高亮经典款、实际渲染的是别的壳"这种自相矛盾的指示。
 */
@Composable
private fun PresetSelector(
    presets: List<ShellPreset>,
    selectedPresetId: String?,
    isExampleContent: Boolean,
    onPresetSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space4, vertical = Spacing.space2),
        verticalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = PRESET_SELECTOR_LABEL,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            presets.forEach { preset ->
                FilterChip(
                    selected = preset.id == selectedPresetId,
                    onClick = { onPresetSelected(preset.id) },
                    label = { Text(text = preset.displayName) },
                )
            }
            if (selectedPresetId == null) {
                // 自定义态：当前渲染的壳不是任何预置 → 不高亮任何预置 chip，
                // 显式标注"自定义"，避免用户以为"没选"。
                Text(
                    text = PRESET_CUSTOM_LABEL,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isExampleContent) {
            Text(
                text = PRESET_EXAMPLE_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 第 [page] 页的运行时配置：与项目配置同源（主题 / WebSettings 不变），只把入口换成该 tab 的目标地址。
 *
 * 页序取自 [visibleTopTabItems] —— 与 [ShellHost] 里的 pager、与 tab 区**同一份**可见条目顺序，
 * 故隐藏条目既不会被渲染成 tab，也不会被摊成页（阶段 6.6 QA D7）。
 * 目标地址为空时回落项目自身入口地址。
 */
private fun pagedRuntimeConfig(
    baseConfig: WebRuntimeConfig,
    topTabs: ShellTopTabs,
    page: Int,
): WebRuntimeConfig {
    val item = visibleTopTabItems(topTabs).getOrNull(page) ?: return baseConfig
    val url = (item.action as? ShellAction.NavigateUrl)
        ?.url
        ?.takeIf { it.isNotBlank() }
        ?: (baseConfig.entry as? ProjectEntry.Url)?.url
        ?: ""
    return baseConfig.copy(entry = ProjectEntry.Url(url))
}

/** 预览屏的居中提示（加载中 / 项目不存在 / 不支持预览）。 */
@Composable
private fun PreviewNotice(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 预置子模板选择条的标题。 */
private const val PRESET_SELECTOR_LABEL: String = "壳模板"

/** 自定义态标注：当前渲染的壳与任何预置都不相等（不高亮任何预置 chip）。 */
private const val PRESET_CUSTOM_LABEL: String = "自定义"

/** 示例地址提示（`example.com` 保留域，非用户真实站点）。 */
private const val PRESET_EXAMPLE_HINT: String = "示例地址：example.com 为占位演示地址，非真实站点"

/** 加载中文案。 */
private const val PREVIEW_LOADING_TEXT: String = "正在加载预览…"

/** 项目不存在文案。 */
private const val PREVIEW_NOT_FOUND_TEXT: String = "项目不存在或已删除"

/** 不支持预览的兜底文案。 */
private const val PREVIEW_UNSUPPORTED_TEXT: String = "当前仅支持 URL 项目预览"
