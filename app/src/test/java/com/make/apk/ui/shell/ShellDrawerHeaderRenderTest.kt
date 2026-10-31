package com.make.apk.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.Paparazzi
import com.make.apk.data.model.ShellDrawer
import com.make.apk.data.model.ShellDrawerHeaderMode
import com.make.apk.data.model.ShellIconKeys
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.ShellTopBar
import com.make.apk.ui.theme.MmaTheme
import java.io.File
import org.junit.Rule
import org.junit.Test

/**
 * 抽屉头部三态**视觉**回归（W2 ⑨ · Paparazzi）：
 *
 * 1. **TEXT**（默认）：标题回落链 + 副标题，与改动前观感一致（零回归基线）；
 * 2. **IMAGE**（有图）：2:1 头图铺满头部（`headerImageRef` 指向真实落盘 PNG，
 *    走 [decodeDrawerHeaderImage] 的「文件路径」预览态）；
 * 3. **IMAGE 缺图**：ref 指向不存在的路径且 assets 无图 → 渲染层**安全回落 TEXT**，
 *    绝不渲染空块（W2 ⑨ 的核心安全契约）。
 *
 * 三态共用同一份 [ShellHost]，保证测的就是预览与导出共享的那一条渲染路径。
 */
class ShellDrawerHeaderRenderTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @get:Rule
    val tmp = org.junit.rules.TemporaryFolder()

    @Test
    fun drawerHeader_textMode_light() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            drawerBody(
                drawer = ShellDrawer(
                    enabled = true,
                    headerMode = ShellDrawerHeaderMode.TEXT,
                    headerTitle = "我的应用",
                    headerSubtitle = "自定义副标题",
                ),
            )
        }
    }

    @Test
    fun drawerHeader_imageMode_light() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            // 落盘一张标准化 2:1 头图，走真实文件路径解码（预览态）。
            val headerFile: File = tmp.root.resolve("mma_drawer_header_preview.png")
            headerFile.writeBytes(
                DrawerHeaderComposer.toPng(
                    DrawerHeaderComposer.crop(
                        DrawerHeaderComposer.solidColorBitmap(
                            0xFF2563EB.toInt(), 800, 200,
                        ),
                    )!!,
                ),
            )
            drawerBody(
                drawer = ShellDrawer(
                    enabled = true,
                    headerMode = ShellDrawerHeaderMode.IMAGE,
                    headerTitle = "我的应用",
                    headerImageRef = headerFile.absolutePath,
                ),
            )
        }
    }

    @Test
    fun drawerHeader_imageModeMissingImage_fallsBackToText_light() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            // ref 指向不存在的文件 + 测试环境 assets 无固定名图 → 双缺，必须回落 TEXT。
            drawerBody(
                drawer = ShellDrawer(
                    enabled = true,
                    headerMode = ShellDrawerHeaderMode.IMAGE,
                    headerTitle = "我的应用",
                    headerImageRef = "/nonexistent/mma_drawer_header.png",
                ),
            )
        }
    }

    @Composable
    private fun drawerBody(drawer: ShellDrawer) {
        val spec = ShellSpec(
            topBar = ShellTopBar(enabled = true, showTitle = true, showBackButton = true),
            drawer = drawer.copy(
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
