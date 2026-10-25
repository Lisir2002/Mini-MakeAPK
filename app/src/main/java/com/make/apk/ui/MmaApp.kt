package com.make.apk.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.make.apk.ui.navigation.MmaNavHost
import com.make.apk.ui.storage.StoragePermissionGate
import dev.chrisbanes.haze.HazeState

/**
 * 应用根：页面骨架导航。
 *
 * 只做三件事：持有 NavController、铺页面级底色（`background` 令牌）、在首帧后回调
 * [onFirstFrameDrawn]（供 `Activity.reportFullyDrawn()` 与 `StartupTracer` 使用）。
 * 主题由 `MainActivity` 唯一持有（本函数不再重复包裹 `MmaTheme`）；页面 chrome
 * （顶栏/底栏）由各页面的 `MmaScreen` 负责。
 * 业务与取数都在 `MmaNavHost` 与各页面里，本函数保持极薄，便于整体截图测试。
 *
 * Haze 磨砂源在此创建一次并经 [LocalHazeState] 下发：页面内容层挂 `Modifier.haze`，
 * 顶栏/底栏以 `Modifier.hazeChild` 消费（兄弟关系，见 [LocalHazeState] 架构说明）。
 * 磨砂总开关在 `MmaDesign.scaffold.translucentChrome`（默认关，零开销）。
 */
@Composable
fun MmaApp(
    modifier: Modifier = Modifier,
    onFirstFrameDrawn: () -> Unit = {},
) {
    val navController = rememberNavController()
    val hazeState = remember { HazeState() }

    // 首帧合成完成后回调：这才是用户真正"看到内容"的时刻
    LaunchedEffect(Unit) { onFirstFrameDrawn() }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            MmaNavHost(navController = navController)
            // 启动存储门：与 NavHost 同级叠加；无弹窗时零布局，不拦截页面交互。
            // 已授权则静默确保 MMAFile 目录；未授权弹引导弹窗（可「以后再说」降级）。
            StoragePermissionGate()
        }
    }
}
