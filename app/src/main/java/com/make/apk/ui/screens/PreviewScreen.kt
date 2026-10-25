package com.make.apk.ui.screens

import android.content.Context
import android.view.View
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.make.apk.data.model.BuildInputType
import com.make.apk.data.model.BuildRecord
import com.make.apk.ui.components.MmaScreen

/**
 * 预览页：把已封装内容用本地 WebView 渲染（设备端即时封装路径）。
 *
 * 无状态 + 可注入 [webViewFactory]：截图测试注入轻量 View 桩即可在 JVM 渲染外壳，
 * 无需真实 WebView（真实网页视觉验证走 Roborazzi / 真机，见
 * `docs/qa/screenshot-testing.md`）。
 * 子页面：顶栏带返回按钮，无底栏；WebView 区消费 Scaffold 的 innerPadding。
 */
@Composable
fun PreviewScreen(
    record: BuildRecord?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    webViewFactory: (Context) -> View = { WebView(it) },
) {
    val source = record?.sourceRef
    val isUrl = record?.inputType == BuildInputType.URL

    MmaScreen(
        title = record?.sourceLabel ?: "预览",
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        WebViewScreen(
            url = if (isUrl) source else null,
            html = if (isUrl) null else source,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            webViewFactory = webViewFactory,
        )
    }
}
