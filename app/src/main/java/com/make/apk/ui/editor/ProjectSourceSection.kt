package com.make.apk.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.make.apk.data.model.ProjectType
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.theme.Spacing

/**
 * 项目来源输入区（§4.1 第 1 项的下半部分）。
 *
 * 形态完全由 [projectType] 决定（§9.3 与来源输入联动）：
 * - `URL` → 单行网址输入；
 * - `HTML` → 多行 HTML 粘贴；
 * - `VUE` / `PHP` → 占位说明（本阶段不实现，需要内置运行时，见 [isPending]）；
 * - `null` → 引导先选类型。
 *
 * **校验零判断**：错误/警告消息由调用方按 [AddField] 从状态里取好传进来，
 * 本组件只负责按 [FieldMessage.Severity] 选颜色（文案唯一来源仍是 [ProjectValidator]）。
 *
 * @param urlMessage `AddField.ENTRY_URL` 的消息（无则 null）。
 * @param htmlMessage `AddField.ENTRY_HTML` 的消息（无则 null）。
 * @param archiveMessage `AddField.ENTRY_ARCHIVE` 的消息（无则 null）。本阶段恒为 null
 *        （无归档 UI 通路 ⇒ `archivePath` 恒 null），但**展示位必须先存在**：
 *        一旦后续接上 zip 选择器，该错误若无处可见就会出现「保存灰着、红字看不见」。
 */
@Composable
fun ProjectSourceSection(
    projectType: ProjectType?,
    urlInput: String,
    htmlInput: String,
    urlMessage: FieldMessage?,
    htmlMessage: FieldMessage?,
    archiveMessage: FieldMessage?,
    onUrlChanged: (String) -> Unit,
    onHtmlChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (projectType) {
        ProjectType.URL -> ProjectTextField(
            value = urlInput,
            onValueChange = onUrlChanged,
            label = URL_LABEL,
            placeholder = URL_PLACEHOLDER,
            message = urlMessage,
            keyboardType = KeyboardType.Uri,
            modifier = modifier.fillMaxWidth(),
        )

        ProjectType.HTML -> ProjectTextField(
            value = htmlInput,
            onValueChange = onHtmlChanged,
            label = HTML_LABEL,
            placeholder = HTML_PLACEHOLDER,
            message = htmlMessage,
            singleLine = false,
            minLines = HTML_MIN_LINES,
            modifier = modifier.fillMaxWidth(),
        )

        ProjectType.VUE, ProjectType.PHP -> PendingSourceCard(
            type = projectType,
            message = archiveMessage,
            modifier = modifier.fillMaxWidth(),
        )

        null -> UnselectedSourceCard(modifier = modifier.fillMaxWidth())
    }
}

/**
 * 暂未开放类型的来源占位（VUE / PHP）。
 *
 * 卡片本身就是「说明 + 引导」：说清为什么没有输入框（内置运行时 / 包体），
 * 并指一条此刻可走的路（网址或 HTML），避免用户以为界面坏了。
 *
 * 卡片同时是 `ENTRY_ARCHIVE` 的**展示位**：占位类型没有输入框可挂 `supportingText`，
 * 归档错误只能在卡片底部单独成行，否则会变成"看不见的阻断位"（保存灰着却不知为何）。
 */
@Composable
private fun PendingSourceCard(
    type: ProjectType,
    message: FieldMessage?,
    modifier: Modifier = Modifier,
) {
    CuteCard(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.space4),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.space2),
            ) {
                Text(
                    text = "${displayLabelOf(type)} 工程需要内置运行时，包体较大，将在后续版本支持。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "现在可以先用网址或 HTML。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                // 由父 Column 的 CenterHorizontally 居中，与上方两行说明对齐
                if (message != null) {
                    FieldMessageText(message = message)
                }
            }
        }
    }
}

/** 未选类型时的引导卡。 */
@Composable
private fun UnselectedSourceCard(modifier: Modifier = Modifier) {
    CuteCard(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.space4),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "先在上面选一个项目类型",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 网址输入框的标签。 */
private const val URL_LABEL: String = "网址"

/** 网址输入框的占位提示。 */
private const val URL_PLACEHOLDER: String = "https://example.com"

/** HTML 输入框的标签。 */
private const val HTML_LABEL: String = "HTML 内容"

/** HTML 输入框的占位提示。 */
private const val HTML_PLACEHOLDER: String = "<html>…</html>"

/** HTML 输入框的最小行数（多行粘贴区）。 */
private const val HTML_MIN_LINES: Int = 6
