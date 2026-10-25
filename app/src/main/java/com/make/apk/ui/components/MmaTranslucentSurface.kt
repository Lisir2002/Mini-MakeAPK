package com.make.apk.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.TranslucentSpec
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild

/**
 * 按 [TranslucentSpec] 构造 Haze 磨砂样式——真磨砂态的唯一事实来源。
 *
 * 令牌 → Haze 映射：`translucent.blurRadius → HazeStyle.blurRadius`、
 * `translucent.noise → HazeStyle.noiseFactor`；底色 tint 由调用方给
 * （顶栏取 background、底栏取 surface，保持各 chrome 的固有色感）。
 */
internal fun hazeChromeStyle(spec: TranslucentSpec, tint: Color): HazeStyle =
    HazeDefaults.style(
        tint = tint,
        blurRadius = spec.blurRadius,
        noiseFactor = spec.noise,
    )

/**
 * 磨砂容器——chrome 层（顶栏/底栏/悬浮卡）跨子树真模糊的统一封装。
 *
 * 三态降级链（`translucent` 参数 + `hazeState` 可得性驱动）：
 * 1. `translucent = false`：实色 Surface，零额外开销——**默认态**；
 *    截图测试（Paparazzi）不传 hazeState 即落此态，保证 golden 零风险。
 * 2. `translucent = true` 且 `hazeState != null`：`Modifier.hazeChild` 真磨砂，
 *    Haze 内部按 API 分级（31+ RenderNode 真模糊；< 31 近似）。
 * 3. `translucent = true` 但 `hazeState == null`（无源可捕获，如独立预览）：
 *    半透明纯色 + 1dp 细描边近似（iOS 风磨砂的关键视觉线索仍在）。
 *
 * 内容源（`Modifier.haze`）的挂载见 [com.make.apk.ui.MmaApp]（Phase-2 接线）；
 * 本组件只负责「消费侧」。
 *
 * @param translucent 是否启用磨砂（总开关；false 时完全等价于普通 Surface）。
 * @param hazeState Haze 捕获状态；null 走近似降级，截图路径应保持 null。
 * @param shape 容器形状（磨砂裁剪同形）。
 * @param color 实色态底色 / 近似态底色基准（乘 translucent.alpha）。
 * @param contentColor 内容色兜底（子组件显式设色时不受影响）。
 */
@Composable
fun MmaTranslucentSurface(
    modifier: Modifier = Modifier,
    translucent: Boolean = false,
    hazeState: HazeState? = null,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit = {},
) {
    val spec = LocalMmaDesign.current.translucent
    // blurState 非空 ⇔ 真磨砂态；局部 val 上的 null 检查可 smart cast
    val blurState = if (translucent) hazeState else null

    Surface(
        modifier = blurState?.let { state ->
            modifier.hazeChild(
                state = state,
                shape = shape,
                style = hazeChromeStyle(spec, color),
            )
        } ?: modifier,
        shape = shape,
        color = when {
            blurState != null -> Color.Transparent // 视觉由 hazeChild 的 tint 提供
            translucent -> color.copy(alpha = spec.alpha)
            else -> color
        },
        contentColor = contentColor,
        border = if (translucent && blurState == null) {
            // 近似态：细描边补足「玻璃边缘」线索（DESIGN_LANGUAGE §7 磨砂规格）
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = spec.borderAlpha))
        } else {
            null
        },
        content = content,
    )
}
