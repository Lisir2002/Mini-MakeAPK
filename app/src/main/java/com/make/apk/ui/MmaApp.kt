package com.make.apk.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.make.apk.ui.navigation.MmaNavHost

/**
 * 应用根：页面骨架导航。
 *
 * 只做三件事：持有 NavController、铺页面级底色（`background` 令牌）、在首帧后回调
 * [onFirstFrameDrawn]（供 `Activity.reportFullyDrawn()` 与 `StartupTracer` 使用）。
 * 主题由 `MainActivity` 唯一持有（本函数不再重复包裹 `MmaTheme`）；页面 chrome
 * （顶栏/底栏）由各页面的 `MmaScreen` 负责。
 * 业务与取数都在 `MmaNavHost` 与各页面里，本函数保持极薄，便于整体截图测试。
 */
@Composable
fun MmaApp(
    modifier: Modifier = Modifier,
    onFirstFrameDrawn: () -> Unit = {},
) {
    val navController = rememberNavController()

    // 首帧合成完成后回调：这才是用户真正"看到内容"的时刻
    LaunchedEffect(Unit) { onFirstFrameDrawn() }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        MmaNavHost(navController = navController)
    }
}
