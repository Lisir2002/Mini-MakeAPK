package com.make.apk.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.make.apk.ui.LocalHazeState
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.TopBarVariant
import dev.chrisbanes.haze.haze

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
 * - 顶栏恒显示（五个页面全部必须有顶栏）：[topBar] 为 null 时用内置 [MmaTopBar]
 *   （title/onBack 驱动）；自定义顶栏传入后 title/onBack/translucent 均不再生效，
 *   由自定义内容自管（「插拔换样式」的挂载点）。
 *
 * Haze 源：content slot 外包 `Modifier.haze(state)`（[LocalHazeState]），
 * 与顶栏/底栏的 `hazeChild` 构成兄弟关系（Haze 官方模式，防自引用循环）。
 * 未提供 state（截图测试）时无源，磨砂自动走近似降级。
 *
 * 磨砂解析（「参数优先、令牌兜底」）：[translucent] 传值即显式决策；
 * null（默认）时读 [com.make.apk.ui.theme.ScaffoldStyle]——
 * `translucentChrome && topBarVariant == Translucent` 才磨砂。
 * Phase-2 自定义主题换一个 `MmaDesign(scaffold = …)` 即可全局换装，调用点零改动。
 *
 * @param title 顶栏标题（用户语言，非路由名）。
 * @param onBack 非空则内置顶栏显示返回按钮（子页面）；null 为顶级页面。
 * @param topBar 自定义顶栏槽位；null 用内置 [MmaTopBar]。
 * @param bottomBar 底栏槽位内容，通常传共享的 [MmaBottomBar]。
 * @param containerColor 页面背景，默认 `background` 令牌，预留页面级覆盖能力。
 * @param translucent 顶栏磨砂开关；null 跟随 [com.make.apk.ui.theme.ScaffoldStyle]（截图路径应保持 null 且令牌默认关）。
 * @param content 主页面槽位，接收 Scaffold 计算的 [PaddingValues]。
 */
@Composable
fun MmaScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
    translucent: Boolean? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scaffoldStyle = LocalMmaDesign.current.scaffold
    val chromeTranslucent = translucent
        ?: (scaffoldStyle.translucentChrome && scaffoldStyle.topBarVariant == TopBarVariant.Translucent)
    val hazeState = LocalHazeState.current

    Scaffold(
        modifier = modifier,
        containerColor = containerColor,
        topBar = topBar ?: {
            MmaTopBar(
                title = title,
                onBack = onBack,
                translucent = chromeTranslucent,
            )
        },
        bottomBar = bottomBar,
    ) { innerPadding ->
        // 内容层即 Haze 源（无 state 时退化为普通 Box，截图零影响）
        Box(
            modifier = if (hazeState != null) {
                Modifier.fillMaxSize().haze(hazeState)
            } else {
                Modifier.fillMaxSize()
            },
        ) {
            content(innerPadding)
        }
    }
}
