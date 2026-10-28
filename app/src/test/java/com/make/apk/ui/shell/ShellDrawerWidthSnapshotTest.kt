package com.make.apk.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.Paparazzi
import com.make.apk.data.model.ShellDrawer
import com.make.apk.data.model.ShellDrawerWidth
import com.make.apk.data.model.ShellDrawerWidthMode
import com.make.apk.data.model.ShellIconKeys
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.ShellTopBar
import com.make.apk.ui.theme.MmaTheme
import org.junit.Rule
import org.junit.Test

/**
 * 抽屉宽度档位**视觉**回归（QA 独立补测 · 阶段 6.5 · Paparazzi）。
 *
 * ## 补什么
 *
 * 既有基线只有中档（`ShellHostTest.drawerOpen_native_light`，MEDIUM 300dp）；
 * [ShellDrawerWidthTest] 锁住了"取哪一档 / 如何兜底"的**解析逻辑**，
 * 但锁不住"档位值真的传导到了 `ModalDrawerSheet`"这一步——若日后有人把宽度修饰符
 * 从 `ModalDrawerSheet` 上挪走或漏接，解析测试照样全绿而像素塌掉。
 *
 * 这里补两帧：
 * - **窄档**（240dp）：档位下限形态；
 * - **自定义越界**（customDp = 1000）：必须被钳到 [ShellDrawerWidths.maxDp]，
 *   不得撑爆屏幕把内容区挤没（resolveDrawerWidth 的钳制分支的像素级证明）。
 */
class ShellDrawerWidthSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun drawerOpen_smallWidth_light() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            drawerBody(width = ShellDrawerWidth(mode = ShellDrawerWidthMode.SMALL))
        }
    }

    @Test
    fun drawerOpen_customWidthClamped_light() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            drawerBody(width = ShellDrawerWidth(mode = ShellDrawerWidthMode.CUSTOM, customDp = 1000))
        }
    }

    @Composable
    private fun drawerBody(width: ShellDrawerWidth) {
        val spec = ShellSpec(
            topBar = ShellTopBar(enabled = true, showTitle = true, showBackButton = true),
            drawer = ShellDrawer(
                enabled = true,
                width = width,
                items = listOf(
                    ShellMenuItem(id = "itm_drawer_home", title = "首页", iconRef = ShellIconKeys.HOME, selected = true),
                    ShellMenuItem(id = "itm_drawer_discover", title = "发现", iconRef = ShellIconKeys.DISCOVER),
                ),
            ),
        )
        ShellHost(
            spec = spec,
            title = "示例应用",
            drawerOpen = true,
            selectedDrawerItemId = "itm_drawer_home",
        ) { contentModifier ->
            Box(modifier = contentModifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }
    }
}
