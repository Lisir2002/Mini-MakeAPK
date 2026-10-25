package com.make.apk.ui.screens

import android.content.Context
import android.view.View
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.make.apk.data.model.BuildInputType
import com.make.apk.data.model.BuildRecord
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.icons.MmaIcons

/**
 * 预览页：把已封装内容用本地 WebView 渲染（设备端即时封装路径）。
 *
 * 无状态 + 可注入 [webViewFactory]：截图测试注入轻量 View 桩即可在 JVM 渲染外壳，
 * 无需真实 WebView（真实网页视觉验证走 Roborazzi / 真机，见
 * `docs/qa/screenshot-testing.md`）。
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

    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    MmaIcon(
                        iconRes = MmaIcons.ArrowBack,
                        contentDescription = null,
                        size = 18.dp,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("返回")
                }
                Text(
                    text = record?.sourceLabel ?: "预览",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            WebViewScreen(
                url = if (isUrl) source else null,
                html = if (isUrl) null else source,
                modifier = Modifier.weight(1f),
                webViewFactory = webViewFactory,
            )
        }
    }
}
