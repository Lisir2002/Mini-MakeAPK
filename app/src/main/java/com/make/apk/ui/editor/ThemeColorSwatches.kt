package com.make.apk.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.make.apk.ui.theme.Spacing

// token-gate: allow: 用户可选主题色预设，属用户数据而非设计令牌（设计语言允许用户自定义主题色）。
// 色板与设计语言对齐：默认项=主色蓝，废弃的旧珊瑚粉默认项已移除（黑名单门禁拦截）。
// 单一事实来源：新建项目页与编辑页 WebSettingsTab 共用，防止两处色板漂移。
val THEME_COLOR_PRESETS: List<Int> = listOf(
    0xFF2563EB.toInt(), // 蓝（默认，设计语言主色）
    0xFF0EA5E9.toInt(), // 天蓝
    0xFF16A34A.toInt(), // 绿
    0xFFD97706.toInt(), // 琥珀
    0xFFDC2626.toInt(), // 红
    0xFF14171A.toInt(), // 石墨
)

/**
 * 主题色预设色板（新建页「基本信息」与编辑页 WebSettings 共用）。
 *
 * 选中项以主色外圈标识（不引入额外图标依赖）；色块语义化播报「主题色：蓝」。
 */
@Composable
fun ThemeColorSwatches(
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space3),
    ) {
        THEME_COLOR_PRESETS.forEach { color ->
            val isSelected = color == selected
            // 选中态：外圈主色 + 内嵌色块（圆环即选中指示）。
            Box(
                modifier = Modifier
                    .size(SWATCH_SIZE)
                    .clip(CircleShape)
                    .semantics(mergeDescendants = true) {
                        contentDescription =
                            "主题色：${themeColorLabelOf(color)}" + if (isSelected) "，已选中" else ""
                        role = Role.Button
                    }
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable(onClickLabel = "选择主题色") { onSelected(color) }
                    .padding(SWATCH_RING),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color(color)),
                )
            }
        }
    }
}

/** 色板的中文标签（无障碍播报用；与 THEME_COLOR_PRESETS 顺序一一对应）。 */
private fun themeColorLabelOf(color: Int): String = when (color) {
    0xFF2563EB.toInt() -> "蓝"
    0xFF0EA5E9.toInt() -> "天蓝"
    0xFF16A34A.toInt() -> "绿"
    0xFFD97706.toInt() -> "琥珀"
    0xFFDC2626.toInt() -> "红"
    else -> "石墨"
}

/** 色块外径（含选中环）。 */
private val SWATCH_SIZE = 40.dp

/** 选中环厚度。 */
private val SWATCH_RING = 2.dp
