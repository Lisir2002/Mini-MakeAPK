package com.make.apk.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import dev.chrisbanes.haze.HazeState

/**
 * Haze 磨砂源状态——App 壳层（[MmaApp]）创建一次并下发，全链路共享。
 *
 * 架构（Haze 官方模式）：
 * - **源**挂在页面内容层（[com.make.apk.ui.components.MmaScreen] 的 content slot 外包
 *   `Modifier.haze(state)`）；
 * - **消费方**是顶栏/底栏（`Modifier.hazeChild(state)`），与源是 Scaffold 内的兄弟 slot。
 * - 消费方**不能**是源的后代（hazeSource 包 NavHost 再让内部顶栏消费会形成
 *   自引用循环）——这是本 CompositionLocal 存在的理由：源与消费方都从同一处取状态，
 *   但挂载点按兄弟关系布置。
 *
 * 截图测试（Paparazzi）不提供本 Local → 默认 null → 磨砂走半透明近似降级，
 * 保证 golden 零风险。
 */
val LocalHazeState: ProvidableCompositionLocal<HazeState?> = compositionLocalOf { null }
