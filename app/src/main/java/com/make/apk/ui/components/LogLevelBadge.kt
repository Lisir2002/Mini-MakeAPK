package com.make.apk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.make.apk.logging.LogLevel
import com.make.apk.logging.LogTone
import com.make.apk.ui.theme.Spacing
import com.make.apk.ui.theme.colors

/**
 * 日志级别徽标——**全项目唯一实现**（日志列表行 / 详情弹层共用）。
 *
 * ## 修复的三个设计缺陷（rc17）
 *
 * 1. **圆角令牌用错**：原先套 `ControlShapes.pill`（999dp 全圆），而该令牌的文档语义是
 *    「开关轨道 / 滑块」。项目 shape 令牌明确写着 `AppShapes.extraSmall = 6dp`（`--radius-sm`）
 *    用于「小组件/标签」。一个 12sp 双字徽标做成胶囊，在密集列表里是一颗颗"药丸"，
 *    与「圆角收紧、中性利落」的设计基调相反。现改用 [MaterialTheme.shapes.extraSmall]。
 * 2. **取色映射重复两份**：列表与弹层各持一份逐字相同的 `when (severityWeight)`。
 *    现统一走 [LogLevel.tone] → [colors] 单点映射。
 * 3. **字号跳档**：原用未定义的 `labelSmall`（回落 M3 默认 11sp），与项目
 *    `--caption 12` 字阶不一致。现用已定义的 `labelMedium`（12sp）。
 *
 * 本组件零 IO、零状态，可直接进 Paparazzi 快照。
 *
 * @param level 日志级别；null 表示解析失败（展示"未知"，取 [LogTone.Muted] 中性色）。
 */
@Composable
fun LogLevelBadge(
    level: LogLevel?,
    modifier: Modifier = Modifier,
) {
    val toneColors = (level?.tone ?: LogTone.Muted).colors()
    Text(
        text = level?.label ?: "未知",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = toneColors.content,
        modifier = modifier
            // background(color, shape) 自带裁剪，无需再 clip——顺序陷阱一并消掉。
            .background(color = toneColors.container, shape = MaterialTheme.shapes.extraSmall)
            .padding(horizontal = Spacing.space2, vertical = Spacing.space1),
    )
}
