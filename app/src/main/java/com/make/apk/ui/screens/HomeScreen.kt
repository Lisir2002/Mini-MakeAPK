package com.make.apk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.make.apk.ui.components.CuteButton

// 首屏：单一输入流（URL/HTML 粘贴优先），契合全年龄段 + 舒适（brainstorm 决策）。
// initialInput 仅用于测试/深链预填，默认空（用户态以内部 state 为准）。
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onBuild: (String) -> Unit,
    initialInput: String = "",
    // 可选：历史入口。传 null 时不渲染该按钮（截图测试可保持纯净构图）。
    onOpenHistory: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf(initialInput) }
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                "把网页，变成 App",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "粘贴一个网址或 HTML，我们帮你打包成可安装的 APK。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                placeholder = {
                    Text("https://… 或 <html>…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                minLines = 3,
            )
            CuteButton(
                text = "变成 App ↘",
                onClick = { onBuild(input) },
                modifier = Modifier.fillMaxWidth(),
            )

            if (onOpenHistory != null) {
                TextButton(onClick = onOpenHistory) {
                    Text("查看构建历史")
                }
            }
        }
    }
}
