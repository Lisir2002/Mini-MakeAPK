package com.make.apk.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.make.apk.data.model.ProjectEntry
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.icons.MmaIcons
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.Spacing

/**
 * 「项目代码」页（HTML 类专属，类型隔离）——查看/编辑项目的文件与源码。
 *
 * ## 定位
 *
 * HTML 项目是"有文件的项目"：入口文件（首屏加载）+ 源码内容。本页是这类项目
 * 维护代码的唯一入口——新建页已不直录代码，URL 等纯引用类型**不显示本页**。
 *
 * ## 结构
 *
 * - 文件卡：入口文件名（可编辑）+「入口」徽标——标明这是首屏加载的文件；
 * - 代码卡：入口文件的源码编辑区（等宽字体、token 化配色）；留空保存时兜底默认模板。
 *
 * **校验零判断**：提示由调用方按 [EditField] 取好传入，本组件不自造校验语句。
 *
 * @param entryFile 入口文件名（空 = 默认 index.html）。
 * @param entryHtml 入口文件源码。
 * @param entryFileMessage 入口文件名校验消息（null 无）。
 * @param entryHtmlMessage 源码校验消息（null 无）。
 * @param onEntryFileChanged 入口文件名变化。
 * @param onEntryHtmlChanged 源码变化。
 */
@Composable
fun ProjectCodeTab(
    entryFile: String,
    entryHtml: String,
    entryFileMessage: FieldMessage?,
    entryHtmlMessage: FieldMessage?,
    onEntryFileChanged: (String) -> Unit,
    onEntryHtmlChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        EntryFileCard(
            entryFile = entryFile,
            message = entryFileMessage,
            onEntryFileChanged = onEntryFileChanged,
        )
        CodeCard(
            entryFile = entryFile,
            entryHtml = entryHtml,
            message = entryHtmlMessage,
            onEntryHtmlChanged = onEntryHtmlChanged,
        )
    }
}

/** 文件卡：入口文件名 +「入口」徽标。 */
@Composable
private fun EntryFileCard(
    entryFile: String,
    message: FieldMessage?,
    onEntryFileChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CuteCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space2),
        ) {
            SectionTitle(text = "文件")
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MmaIcon(
                    iconRes = MmaIcons.TypeHtml,
                    contentDescription = "入口文件",
                    tint = MaterialTheme.colorScheme.primary,
                )
                ProjectTextField(
                    value = entryFile,
                    onValueChange = onEntryFileChanged,
                    label = ENTRY_FILE_LABEL,
                    placeholder = ProjectEntry.BundledHtml.DEFAULT_ENTRY_FILE,
                    message = message,
                    hint = ENTRY_FILE_HINT,
                    modifier = Modifier.weight(1f),
                )
                EntryBadge()
            }
        }
    }
}

/** 代码卡：入口文件的源码编辑区（等宽字体）。 */
@Composable
private fun CodeCard(
    entryFile: String,
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
                text = codeFileLabel(entryFile),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

/** 代码编辑区：多行、等宽、token 化自绘（与 [ProjectTextField] 同一视觉语言）。 */
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
        androidx.compose.foundation.text.BasicTextField(
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

/** 「入口」徽标：标明该文件是首屏加载的入口。 */
@Composable
private fun EntryBadge(modifier: Modifier = Modifier) {
    Text(
        text = "入口",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier
            .clip(ControlShapes.pill)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = Spacing.space3, vertical = Spacing.space1),
    )
}

/** 区块标题（文件 / 代码）。 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** 代码卡文件名标签：空文件名回落默认 index.html（与保存兜底一致）。 */
private fun codeFileLabel(entryFile: String): String =
    CODE_FILE_PREFIX + entryFile.trim().ifBlank { ProjectEntry.BundledHtml.DEFAULT_ENTRY_FILE }

private const val ENTRY_FILE_LABEL: String = "入口文件"

private const val ENTRY_FILE_HINT: String = "网页应用首屏加载的文件，留空默认 index.html"

private const val CODE_FILE_PREFIX: String = "文件："

private const val CODE_MIN_LINES: Int = 10
