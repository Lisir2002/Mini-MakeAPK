package com.web2apk.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.web2apk.app.ui.screens.HomeScreen
import com.web2apk.app.ui.screens.WebViewScreen
import com.web2apk.app.ui.theme.Web2ApkTheme

// 应用根：绑定团队主题，首屏 → 构建预览的简单状态路由（无需 Navigation 依赖）。
@Composable
fun Web2ApkApp() {
    Web2ApkTheme {
        var built by remember { mutableStateOf<BuiltSource?>(null) }
        Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
            if (built == null) {
                HomeScreen(onBuild = { input -> built = BuiltSource.from(input) })
            } else {
                WebViewScreen(url = built?.url, html = built?.html)
            }
        }
    }
}

// 构建源：根据首屏输入判定为 URL 还是 HTML。
private data class BuiltSource(val url: String? = null, val html: String? = null) {
    companion object {
        fun from(input: String): BuiltSource =
            if (input.trim().startsWith("http", ignoreCase = true) || input.contains("://")) {
                BuiltSource(url = input.trim())
            } else {
                BuiltSource(html = input)
            }
    }
}
