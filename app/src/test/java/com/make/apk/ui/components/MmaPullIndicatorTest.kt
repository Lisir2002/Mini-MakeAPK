package com.make.apk.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import com.make.apk.ui.theme.MmaTheme
import org.junit.Rule
import org.junit.Test

/**
 * 下拉刷新**指示器**截图回归（Paparazzi，纯 JVM）。
 *
 * 钉住三个状态的外观：下拉中（半程 / 满程）、刷新中（转圈）、完成（对勾）。
 * 只钉纯内容函数 [MmaPullIndicatorContent]，与容器位移解耦——
 * 改容器布局不会让这批基线漂移。
 */
class MmaPullIndicatorTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun pullingHalfLight() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) { IndicatorBox(progress = 0.5f) }
    }

    @Test
    fun pullingArmedLight() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) { IndicatorBox(progress = 1f) }
    }

    @Test
    fun completedLight() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) { IndicatorBox(progress = 1f, completed = true) }
    }

    @Test
    fun pullingArmedDark() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) { IndicatorBox(progress = 1f) }
    }

    @Test
    fun completedDark() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) { IndicatorBox(progress = 1f, completed = true) }
    }

    private companion object {
        // 转圈态不钉：无限动画在快照里取到的是不确定帧，无法作为回归基线。
        @Suppress("TestFunctionName")
        @androidx.compose.runtime.Composable
        fun IndicatorBox(
            progress: Float,
            completed: Boolean = false,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                MmaPullIndicatorContent(
                    progress = progress,
                    isRefreshing = false,
                    completed = completed,
                )
            }
        }
    }
}
