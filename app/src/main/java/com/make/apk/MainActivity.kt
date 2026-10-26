package com.make.apk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.make.apk.logging.StartupTracer
import com.make.apk.ui.MmaApp
import com.make.apk.ui.theme.MmaTheme

/**
 * 应用唯一载体 Activity（此前工程**没有声明任何 Activity**，页面无法运行）。
 *
 * 只做一件事：把内容交给 Compose。所有页面与状态都在 `MmaApp` 内，
 * Activity 不持有业务逻辑，便于截图测试（Paparazzi）直接渲染 Composable。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 粗分型：无 savedInstanceState = 新进程冷启动；有则为温启动（Activity 重建）
        val startType = if (savedInstanceState == null) "cold" else "warm"

        setContent {
            MmaTheme {
                MmaApp(
                    onFirstFrameDrawn = {
                        // 启动终点：首帧绘制完成（用户真正看到内容的时刻），
                        // 比 onCreate 结束更贴近真实体验，也是官方推荐口径。
                        reportFullyDrawn()
                        StartupTracer.complete(startType = startType)
                    },
                )
            }
        }
    }
}
