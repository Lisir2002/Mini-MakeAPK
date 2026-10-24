package com.make.apk.ui.components

import app.cash.paparazzi.Paparazzi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.make.apk.ui.theme.MmaTheme
import org.junit.Rule
import org.junit.Test

/**
 * 核心可爱组件截图回归测试（Paparazzi，纯 JVM）。
 * 锁定：珊瑚粉胶囊按钮（亮/暗）、柔和卡片、打包脉冲 的视觉规范不漂移。
 */
class CuteComponentsTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun buttonLight() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            CuteButton(text = "变成 App ↘", onClick = {})
        }
    }

    @Test
    fun buttonDark() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) {
            CuteButton(text = "变成 App ↘", onClick = {})
        }
    }

    @Test
    fun cardLight() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            CuteCard(Modifier.padding(16.dp)) {
                Text("可爱的卡片内容", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    @Test
    fun buildingPulseLight() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            BuildingPulse()
        }
    }
}
