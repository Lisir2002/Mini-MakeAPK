package com.make.apk.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.components.MmaDialog
import com.make.apk.ui.components.MmaTextButton
import com.make.apk.ui.screens.DELETE_CONFIRM_TEXT
import com.make.apk.ui.screens.DELETE_CONFIRM_TITLE
import com.make.apk.ui.screens.DeleteConfirmContent
import com.make.apk.ui.theme.Spacing

/**
 * 高级配置 Tab（§10.2）：引擎展示（只读锁定）/ 本地隔离（只读）/ 导出格式·签名（占位）/
 * 危险操作（删除，红色二次确认）。
 *
 * 删除确认是**交互态**（非业务态）：初值恒为 false，截图基准零影响；页面重建丢失它
 * 等价于用户取消，无副作用——与 [com.make.apk.ui.screens.ProjectsScreen] 的待删 id 同构。
 *
 * 删除确认弹窗直接复用 [DeleteConfirmContent]（与项目列表的二次确认同源，保证文案与交互一致）。
 *
 * @param apkName 待删除项目名（提示里点名，降低误删近似项概率）。
 */
@Composable
fun HighSettingsTab(
    apkName: String,
    engineLabel: String,
    isolationId: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.space3),
    ) {
        ReadOnlyInfoCard(label = "渲染引擎", value = engineLabel.ifBlank { "—" })
        ReadOnlyInfoCard(label = "本地隔离目录", value = isolationId.ifBlank { "—" })

        // 导出格式 / 签名 占位
        CuteCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Spacing.space4),
                verticalArrangement = Arrangement.spacedBy(Spacing.space2),
            ) {
                Text(
                    text = "导出格式 / 签名",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "APK / AAB、调试 / 正式签名将在后续版本支持。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 危险操作：删除项目（红色 + 二次确认）
        CuteCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Spacing.space4),
                verticalArrangement = Arrangement.spacedBy(Spacing.space2),
            ) {
                Text(
                    text = "危险操作",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "删除项目会一并清除其数据与导出记录，且无法恢复。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MmaTextButton(
                    label = "删除项目",
                    onClick = { showDeleteConfirm = true },
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showDeleteConfirm) {
        MmaDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = DELETE_CONFIRM_TITLE,
            confirmLabel = DELETE_CONFIRM_TEXT,
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            destructive = true,
            content = { DeleteConfirmContent(apkName = apkName.ifBlank { "该项目" }) },
        )
    }
}

/** 只读信息卡（引擎 / 隔离目录等展示项）。 */
@Composable
private fun ReadOnlyInfoCard(label: String, value: String, modifier: Modifier = Modifier) {
    CuteCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space1),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
