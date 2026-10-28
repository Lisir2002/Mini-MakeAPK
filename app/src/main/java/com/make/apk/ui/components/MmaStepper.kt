package com.make.apk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.make.apk.ui.icons.MmaIcons
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.LocalMmaDesign
import com.make.apk.ui.theme.Spacing

/**
 * 自绘数字步进器（条目上限 / 自定义宽度等**有界整数**配置）。
 *
 * ## 为什么替代文本输入框
 *
 * 原实现是 `OutlinedTextField` + `toIntOrNull()?.coerceIn(...)`：
 * ① 输入 "0" 会被悄悄改成 1、输入 "99" 被改成 8 —— 值在用户眼皮底下跳变，
 * 且没有任何提示说明为什么；② 要调出数字键盘、要删字符、要点完成，成本远高于点两下；
 * ③ 空输入 / 非数字全靠兜底，等于把校验成本转嫁给用户。
 *
 * 步进器把**取值域直接画成控件**：到边界即禁用按钮，"能填什么"一眼可见。
 *
 * @param label 字段标签（渲染在容器上方）。
 * @param value 当前值。
 * @param range 合法取值区间（闭区间）。
 * @param onValueChange 变更回调（保证恒在 [range] 内）。
 * @param unit 数值后缀单位（如 "dp" / "项"）。
 */
@Composable
fun MmaNumberStepper(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
) {
    val control = LocalMmaDesign.current.control
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space2)) {
        Text(
            text = label,
            style = typography.bodyLarge,
            color = colorScheme.onSurface,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ControlShapes.segmented)
                .background(colorScheme.surfaceVariant)
                .padding(control.menuGroupGap),
            horizontalArrangement = Arrangement.spacedBy(control.menuGroupGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MmaIconAction(
                iconRes = MmaIcons.Minus,
                contentDescription = "减少",
                onClick = { onValueChange((value - 1).coerceIn(range)) },
                enabled = value > range.first,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(control.iconButtonSize),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (unit != null) "$value $unit" else value.toString(),
                    style = typography.titleMedium,
                    color = colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
            MmaIconAction(
                iconRes = MmaIcons.Add,
                contentDescription = "增加",
                onClick = { onValueChange((value + 1).coerceIn(range)) },
                enabled = value < range.last,
            )
        }
    }
}
