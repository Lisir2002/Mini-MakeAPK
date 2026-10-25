package com.make.apk.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 三段式槽位嵌入布局骨架（顶栏 + 主页面 + 底栏）。
 *
 * 基于 M3 `Scaffold` 槽位机制（社区 MainTemplate/DetailTemplate 模式）：骨架管布局结构与
 * 系统栏避让，页面只管内容。全应用页面的唯一 chrome 事实来源。
 *
 * 使用约定（强制）：
 * - **content 必须消费 innerPadding**：Scaffold 已按顶栏/底栏高度算好内容区避让边距，
 *   不应用则内容会被栏遮挡（Scaffold 第一常见错误）。
 * - 一屏一 MmaScreen，嵌套页面内容不再自建 Surface/顶栏。
 * - 顶栏恒显示（五个页面全部必须有顶栏）；[bottomBar] 传了即「主页模板」（顶级导航页），
 *   不传即「详情模板」（流程子页，如构建中/结果/预览）。
 *
 * @param title 顶栏标题（用户语言，非路由名）。
 * @param onBack 非空则顶栏显示返回按钮（子页面）；null 为顶级页面。
 * @param bottomBar 底栏槽位内容，通常传共享的 [MmaBottomBar]。
 * @param containerColor 页面背景，默认 `background` 令牌，预留页面级覆盖能力。
 * @param content 主页面槽位，接收 Scaffold 计算的 [PaddingValues]。
 */
@Composable
fun MmaScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = containerColor,
        topBar = { MmaTopBar(title = title, onBack = onBack) },
        bottomBar = bottomBar,
    ) { innerPadding ->
        content(innerPadding)
    }
}
