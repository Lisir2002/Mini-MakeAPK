package com.make.apk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.make.apk.data.model.BuildRecord
import com.make.apk.data.model.BuildStatus
import com.make.apk.ui.components.CuteButton

/**
 * 构建结果页（**无状态**）：成功庆祝 / 失败错误态。
 *
 * 两种终态共用一页，靠 `record.status` 分支——避免"成功页"与"失败页"
 * 两套骨架各自漂移。失败时必须给出**人话原因**与明确出口（重试 / 返回）。
 */
@Composable
fun ResultScreen(
    record: BuildRecord?,
    onPreview: () -> Unit,
    onAgain: () -> Unit,
    onRetry: () -> Unit,
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val succeeded = record?.status == BuildStatus.SUCCESS

    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = if (succeeded) "🎉" else "🫥",
                fontSize = 56.sp,
            )

            Text(
                text = if (succeeded) "打包完成！" else "打包失败了",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (succeeded) {
                record?.outputApkPath?.let { path ->
                    Text(
                        "已生成：$path",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                record?.durationMs?.let { duration ->
                    Text(
                        "耗时 ${duration}ms",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CuteButton(text = "预览", onClick = onPreview)
                    TextButton(onClick = onAgain) { Text("再来一个") }
                }
            } else {
                Text(
                    text = record?.errorMessage?.takeIf { it.isNotBlank() } ?: "请检查输入后重试",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CuteButton(text = "重试", onClick = onRetry)
                    TextButton(onClick = onBackHome) { Text("返回首页") }
                }
            }
        }
    }
}
