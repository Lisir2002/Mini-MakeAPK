package com.web2apk.app.ui.screens

import android.view.View
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

// WebView 封装屏（MVP=设备端即时封装）：输入 URL 或 HTML → 本地 WebView 预览/出包。
// 注：此为代码级封装路径；PHP/Vue 真编译走云端构建（见 docs/build-api-contract.md，Phase 2）。
// webViewFactory 默认构造真实 WebView；截图测试可注入轻量 View 桩，在 JVM 上渲染外壳
//（真实 WebView 无法在 JVM 渲染网页，完整网页视觉验证见 docs/qa/screenshot-testing.md 的 Roborazzi/设备路径）。
@Composable
fun WebViewScreen(
    url: String? = null,
    html: String? = null,
    modifier: Modifier = Modifier,
    webViewFactory: (android.content.Context) -> View = { WebView(it) },
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = webViewFactory,
        update = { view ->
            if (view is WebView) {
                view.settings.javaScriptEnabled = true
                view.settings.domStorageEnabled = true
                view.settings.loadWithOverviewMode = true
                view.settings.useWideViewPort = true
                when {
                    !html.isNullOrBlank() -> view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                    !url.isNullOrBlank() -> view.loadUrl(url)
                }
            }
        },
    )
}
