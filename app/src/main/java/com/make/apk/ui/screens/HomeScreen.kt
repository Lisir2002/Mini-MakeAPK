package com.make.apk.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.make.apk.ui.components.CuteButton
import com.make.apk.ui.components.MmaScreen
import com.make.apk.ui.theme.Spacing

// 首屏：单一输入流（URL/HTML 粘贴优先），契合全年龄段 + 舒适（brainstorm 决策）。
// initialInput 仅用于测试/深链预填，默认空（用户态以内部 state 为准）。
// 历史入口走底栏「历史」tab（MmaNavHost 共享底栏），本页不再单独放入口。
@Composable
fun HomeScreen(
    onBuild: (String) -> Unit,
    initialInput: String = "",
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf(initialInput) }
    MmaScreen(
        title = "MMA",
        bottomBar = bottomBar,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.space5),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.space4, Alignment.CenterVertically),
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
                shape = MaterialTheme.shapes.small,
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
        }
    }
}
