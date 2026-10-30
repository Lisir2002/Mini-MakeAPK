package com.make.apk.ui.screens

import android.content.Context
import android.view.View
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ShellAction
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.data.model.ShellTopTabs
import com.make.apk.data.model.WebRuntimeConfig
import com.make.apk.data.model.entryTabItem
import com.make.apk.ui.shell.ShellHost
import com.make.apk.ui.shell.visibleTopTabItems
import com.make.apk.ui.viewmodel.ShellPreviewUiState

/**
 * 壳预览屏（`project/{id}/run`）——**全屏壳，只有预览**。
 *
 * ## 布局
 *
 * 预览屏本身就是"一台被注入了壳的设备"：顶栏 → 顶栏 tab → 内容区（WebView）→ 底栏，
 * 全部由 [ShellHost] 按 `ShellSpec` 渲染。
 *
 * ## 关键行为
 *
 * - **不允许切换壳**：本屏**不提供**任何壳模板切换 / 编辑入口（用户指令："预览就是预览"）。
 *   原实现在内容区顶部挂了一条"壳模板"选择条，且项目未配壳时 VM 会偷偷把渲染 spec
 *   换成经典款 —— 这违反「预览 == 导出」：用户在预览里调好的效果，导出后完全不是一回事。
 *   现在渲染的壳**恒为项目自己存的 spec**；要换壳请去进阶配置页改，改完回来即见。
 * - **非可预览项目明确拦截**：显示"当前仅支持 URL 与 HTML 项目预览"，不崩、不静默失败；
 * - **顶栏返回**：`onBack` 交给宿主 `popBackStack`。
 *
 * @param state 预览状态（VM 推送）。
 * @param onBack 返回（宿主 `popBackStack`）。
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
    onTopTabSelected: (ShellMenuItem) -> Unit,
    onBottomItemSelected: (ShellMenuItem) -> Unit,
    onDrawerItemSelected: (ShellMenuItem) -> Unit,
    onDrawerOpenChanged: (Boolean) -> Unit,
    onShellAction: (ShellMenuItem) -> Unit,
    modifier: Modifier = Modifier,
    webViewFactory: (Context) -> View = { WebView(it) },
    /** 加载失败（主帧）回调（如弹 Snackbar），透传给 WebViewScreen。 */
    onError: ((String) -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.loading -> PreviewNotice(text = PREVIEW_LOADING_TEXT)
            state.projectNotFound -> PreviewNotice(text = PREVIEW_NOT_FOUND_TEXT)
            !state.previewable -> PreviewNotice(text = state.unsupportedMessage ?: PREVIEW_UNSUPPORTED_TEXT)
            else -> {
                val config = state.runtimeConfig
                // 回入口：预览侧与模板运行时保持对称——选中入口 tab（壳内 WebView 自带页内历史）。
                val entryTabItem = state.shellSpec.topTabs.entryTabItem()
                ShellHost(
                    spec = state.shellSpec,
                    projectThemeColor = state.themeColor,
                    title = state.apkName,
                    selectedTopTabId = state.selectedTopTabId,
                    selectedBottomBarId = state.selectedBottomItemId,
                    selectedDrawerItemId = state.selectedDrawerItemId,
                    drawerOpen = state.drawerOpen,
                    onBack = onBack,
                    onResetToEntry = entryTabItem?.let { { onTopTabSelected(it) } },
                    onShellAction = onShellAction,
                    onTopTabSelected = onTopTabSelected,
                    onBottomBarSelected = onBottomItemSelected,
                    onDrawerOpenChanged = onDrawerOpenChanged,
                    onDrawerItemSelected = onDrawerItemSelected,
                    // 每页一个 WebView：页数与页序由 ShellHost 按可见 tab 条目决定，
                    // 本页只负责"第 page 页该装哪个 URL"（与导出壳共用同一入口）。
                    pagedContent = if (config == null) {
                        null
                    } else {
                        { page, pageModifier ->
                            WebViewScreen(
                                config = pagedRuntimeConfig(
                                    baseConfig = config,
                                    projectEntry = state.projectEntry,
                                    topTabs = state.shellSpec.topTabs,
                                    page = page,
                                ),
                                modifier = pageModifier,
                                paused = state.contentPaused,
                                webViewFactory = webViewFactory,
                                onError = onError,
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
                            onError = onError,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 第 [page] 页的运行时配置：与项目配置同源（主题 / WebSettings 不变），只把入口换成该 tab 的目标地址。
 *
 * 页序取自 [visibleTopTabItems] —— 与 [ShellHost] 里的 pager、与 tab 区**同一份**可见条目顺序，
 * 故隐藏条目既不会被渲染成 tab，也不会被摊成页（阶段 6.6 QA D7）。
 *
 * 入口隔离：
 * - tab 命中 [ShellAction.NavigateUrl] 且地址非空时换成该 URL；
 * - tab 命中 [ShellAction.OpenLocalPage] 且该项目文件存在时换成该本地页面
 *   （仍是 [ProjectEntry.BundledHtml] 文件形态，**不**落成 URL）；
 *   取页源是 [projectEntry]（项目自身入口，**只读**）——绝不用 [baseConfig] 的 entry，
 *   否则各页互相改写会让页面"越切越少"（pager 漂移）；
 * - 否则**保留原入口**（HTML 内联 / 资产包等不强制 URL），
 *   避免 HTML 项目在分页态被错误落成空 URL。
 */
private fun pagedRuntimeConfig(
    baseConfig: WebRuntimeConfig,
    projectEntry: ProjectEntry?,
    topTabs: ShellTopTabs,
    page: Int,
): WebRuntimeConfig {
    val item = visibleTopTabItems(topTabs).getOrNull(page) ?: return baseConfig
    return when (val action = item.action) {
        is ShellAction.NavigateUrl -> {
            val url = action.url.takeIf { it.isNotBlank() } ?: return baseConfig
            baseConfig.copy(entry = ProjectEntry.Url(url))
        }
        is ShellAction.OpenLocalPage -> {
            // 取页源是**项目自身入口**（只读），不是 baseConfig.entry：
            // baseConfig.entry 可能是上一页被改写后的展示态，从它取页会让各页互相"吃掉"文件。
            val pageEntry = (projectEntry as? ProjectEntry.BundledHtml)
                ?.pageEntryOf(action.path)
                ?: return baseConfig
            baseConfig.copy(entry = pageEntry)
        }
        else -> baseConfig
    }
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

/** 加载中文案。 */
private const val PREVIEW_LOADING_TEXT: String = "正在加载预览…"

/** 项目不存在文案。 */
private const val PREVIEW_NOT_FOUND_TEXT: String = "项目不存在或已删除"

/** 不支持预览的兜底文案。 */
private const val PREVIEW_UNSUPPORTED_TEXT: String = "当前仅支持 URL 与 HTML 项目预览"
