package com.make.apk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.make.apk.data.model.BuildRecord
import com.make.apk.ui.components.BuildingPulse

/**
 * 构建中页面（**无状态**：`record` 由调用方观察数据层后传入）。
 *
 * 用可爱脉冲替代生硬转圈，契合"舒适、零恐吓感"的产品基调。
 * 进度不放在内存里——构建记录已落加密库，进程被杀后仍能还原状态。
 */
@Composable
fun BuildingScreen(
    record: BuildRecord?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            BuildingPulse()

            Text(
                "正在打包…",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            record?.sourceLabel?.let { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            TextButton(onClick = onCancel) {
                Text("取消")
            }
        }
    }
}
