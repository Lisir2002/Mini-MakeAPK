package com.make.apk.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.Spacing

/**
 * 「项目代码」页（HTML 类专属，类型隔离）——编辑项目的入口文件源码。
 *
 * ## 定位
 *
 * HTML 项目是"有文件的项目"：入口文件名（首屏加载）的**录入**只在「基本信息」卡一处进行
 * （单一事实源，避免两处漂移），本页只维护源码内容，不再重复入口选择控件。
 *
 * **校验零判断**：提示由调用方按 [EditField] 取好传入，本组件不自造校验语句。
 *
 * @param entryHtml 入口文件源码。
 * @param entryHtmlMessage 源码校验消息（null 无）。
 * @param onEntryHtmlChanged 源码变化。
 */
@Composable
fun ProjectCodeTab(
    entryHtml: String,
    entryHtmlMessage: FieldMessage?,
    onEntryHtmlChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        CodeCard(
            entryHtml = entryHtml,
            message = entryHtmlMessage,
            onEntryHtmlChanged = onEntryHtmlChanged,
        )
    }
}

/** 代码卡：入口文件的源码编辑区（等宽字体、自绘令牌）。 */
@Composable
private fun CodeCard(
    entryHtml: String,
    message: FieldMessage?,
    onEntryHtmlChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    CuteCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space2),
        ) {
            SectionTitle(text = "代码")
            Text(
                text = CODE_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
            CodeField(
                value = entryHtml,
                onValueChange = onEntryHtmlChanged,
                message = message,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 代码编辑区：多行、等宽、自绘令牌（与 [com.make.apk.ui.editor.ProjectTextField] 同一视觉语言）。 */
@Composable
private fun CodeField(
    value: String,
    onValueChange: (String) -> Unit,
    message: FieldMessage?,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val isError = message?.severity == FieldMessage.Severity.ERROR
    Column(modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .clip(ControlShapes.fieldOpen)
                .background(colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = if (isError) colorScheme.error else colorScheme.outline,
                    shape = ControlShapes.fieldOpen,
                )
                .padding(horizontal = Spacing.space4, vertical = Spacing.space3),
            singleLine = false,
            minLines = CODE_MIN_LINES,
            textStyle = typography.bodySmall.copy(
                color = colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
            ),
        )
        if (message != null) {
            FieldMessageText(message = message)
        }
    }
}

/** 区块标题（代码）。 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** 代码卡中性提示：入口文件名在基本信息中设置，本页只维护源码。 */
private const val CODE_HINT: String = "入口文件源码（首屏加载内容）；入口文件名在「基本信息」中设置"

private const val CODE_MIN_LINES: Int = 10
