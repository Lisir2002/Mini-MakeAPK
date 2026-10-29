package com.make.apk.ui.code

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.Spacing

/**
 * 自研代码编辑器核心（rc11）——文件页的编辑 / 查看共用内核。
 *
 * ## 结构（调研结论落地）
 *
 * - **高亮**：[CodeHighlight] 单趟正则 → [VisualTransformation]（只改视觉不动文本，
 *   选区 / 撤销栈零破坏——比 `TextFieldValue.copy(annotatedString)` 更稳）；
 * - **行号槽**：`onTextLayout` 取每行 top（含软换行真实偏移），行号随内容同滚；
 * - **滚动**：编辑区不设内部滚动（随内容增高），与行号槽共用外层 [verticalScroll]，
 *   从根上避免"行号与代码错位"。
 */
@Composable
fun CodeEditorCore(
    value: String,
    onValueChange: (String) -> Unit,
    fileName: String,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val lang = CodeHighlight.langOf(fileName)
    val palette = remember(colorScheme) {
        CodeHighlight.Palette.of(
            keyword = SpanStyle(color = colorScheme.primary),
            string = SpanStyle(color = colorScheme.tertiary),
            comment = SpanStyle(color = colorScheme.onSurfaceVariant),
            number = SpanStyle(color = colorScheme.secondary),
            tag = SpanStyle(color = colorScheme.primary),
            attr = SpanStyle(color = colorScheme.secondary),
            plain = SpanStyle(color = colorScheme.onSurface),
        )
    }
    val transformation = remember(lang, palette) { CodeHighlightTransformation(lang, palette) }
    // 每行 top（px，含软换行）；layout 未就绪时用 null 走等高兜底。
    var lineTops by remember { mutableStateOf<FloatArray?>(null) }
    val scrollState = rememberScrollState()
    val codeStyle = typography.bodySmall.copy(
        color = colorScheme.onSurface,
        fontFamily = FontFamily.Monospace,
    )
    val lineCount = remember(value) { value.count { it == '\n' } + 1 }

    // 滚动挂在整个 Row 上：行号与代码天然同滚（各自内滚必然错位）。
    Row(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .clip(ControlShapes.fieldOpen)
            .background(colorScheme.surface)
            .border(1.dp, colorScheme.outline, ControlShapes.fieldOpen),
    ) {
        LineGutter(
            lineCount = lineCount,
            lineTops = lineTops,
            textStyle = codeStyle,
            modifier = Modifier.width(48.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            onTextLayout = { result -> lineTops = FloatArray(result.lineCount) { result.getLineTop(it) } },
            visualTransformation = transformation,
            textStyle = codeStyle,
            cursorBrush = SolidColor(colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.space3, vertical = Spacing.space3),
        )
    }
}

/** 行号槽：按 layout 的行 top 精确对齐（软换行行号不重复显示，与主流编辑器一致取首行号）。 */
@Composable
private fun LineGutter(
    lineCount: Int,
    lineTops: FloatArray?,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme
    val defaultLineHeightPx = with(density) {
        val resolved = textStyle.lineHeight.takeIf { it != TextUnit.Unspecified }
            ?: textStyle.fontSize.times(1.4f)
        resolved.toPx()
    }
    val firstTop = lineTops?.firstOrNull() ?: 0f
    Column(
        modifier = modifier
            .background(colorScheme.surfaceVariant)
            .padding(vertical = Spacing.space3, horizontal = Spacing.space1),
    ) {
        repeat(lineCount) { index ->
            val top = lineTops?.getOrNull(index)
            Box(
                modifier = Modifier.padding(
                    // layout 就绪：按真实行 top 定位；未就绪：等高兜底。
                    top = if (top != null) {
                        with(density) { (top - firstTop).toDp() }
                    } else {
                        with(density) { (defaultLineHeightPx * index).toDp() }
                    },
                ),
            ) {
                Text(
                    text = "${index + 1}",
                    style = textStyle,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 高亮变换：text 不变，仅视觉着色（选区映射恒等）。
 *
 * 单条结果缓存：`filter` 在**每一次输入与每一帧重组**时被调用，若每次都跑全套正则，
 * 输入长文件会逐字符重复扫描（掉帧）。这里以"上一次输入文本"为键缓存着色结果——
 * 文本未变（重组 / 光标移动 / 滚动）时零成本复用。
 */
private class CodeHighlightTransformation(
    private val lang: CodeHighlight.Lang,
    private val palette: CodeHighlight.Palette,
) : VisualTransformation {
    private var lastInput: String? = null
    private var lastOutput: AnnotatedString? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val cached = lastOutput
        val source = text.text
        val result = if (cached != null && lastInput == source) {
            cached
        } else {
            CodeHighlight.highlight(source, lang, palette).also {
                lastInput = source
                lastOutput = it
            }
        }
        return TransformedText(result, OffsetMapping.Identity)
    }
}
