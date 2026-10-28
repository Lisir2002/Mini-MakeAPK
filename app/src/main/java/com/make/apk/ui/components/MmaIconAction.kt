package com.make.apk.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.Spacing

/**
 * 自绘图标动作按钮（条目行的编辑 / 上移 / 下移 / 删除）。
 *
 * ## 为什么自绘
 *
 * 原写法是四个 `TextButton`（"编辑 ↑ ↓ 删除"）：① 五个字挤在一行，横向被压到
 * 几乎点不准；② `TextButton` 自带 Material 内边距与涟漪，和自绘列表行不同皮肤；
 * ③ 箭头用 Unicode 字符（↑↓），不是矢量图标——违反项目"图标必须矢量"的铁律。
 * 改为图标按钮后：每个动作 40dp 见方、矢量图标、语义色（删除走 error）。
 *
 * @param iconRes 矢量图标资源（必须是 `res/drawable/ic_*.xml`）。
 * @param contentDescription 无障碍播报（图标按钮无文字标签，必须传）。
 * @param onClick 点击回调。
 * @param tint 图标色（默认中性；危险动作传 `colorScheme.error`）。
 * @param enabled 可用态；false 时降透明度且不响应点击（比"点了没反应"诚实）。
 */
@Composable
fun MmaIconAction(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
    rotationZ: Float = 0f,
) {
    val control = LocalMmaDesign.current.control
    val alpha = LocalMmaDesign.current.alpha
    val colorScheme = MaterialTheme.colorScheme
    val iconTint = if (tint == Color.Unspecified) colorScheme.onSurfaceVariant else tint

    Box(
        modifier = modifier
            .size(control.iconButtonSize)
            .clip(ControlShapes.segmentPiece)
            .then(
                if (enabled) {
                    Modifier.clickableNoRipple(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        MmaIcon(
            iconRes = iconRes,
            contentDescription = null,
            size = control.menuItemIcon,
            tint = iconTint.copy(alpha = if (enabled) alpha.a7 else alpha.a4),
            modifier = Modifier.graphicsLayer { this.rotationZ = rotationZ },
        )
    }
}
