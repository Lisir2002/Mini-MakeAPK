package com.make.apk.diagnostic

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.make.apk.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 诊断期临时设施（2026-10-26 rc3 装机仍闪退定位专用，验证根因后随诊断版一并回滚）。
 *
 * 行为：仅 debuggable 构建生效；启动时扫描外置日志目录（`mma.log*`），
 * 发现上次进程的崩溃记录即弹对话框原文展示 + 设备信息。
 * 用户重启应用看到该对话框 → 截图/复制发给开发者，即完成真机堆栈回传，
 * 全程无需 adb、无需 root、无需文件管理器。
 *
 * 为什么需要：release 版日志落在 `/data/data/<pkg>/files/logs`（私有目录），
 * 普通用户既摸不到也读不了；闪退又让应用内界面无法自证。
 */
@Composable
fun CrashReportGate(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var crashText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        crashText = withContext(Dispatchers.IO) { readLatestCrash(context) }
    }

    val text = crashText ?: return
    AlertDialog(
        onDismissRequest = { crashText = null },
        modifier = modifier,
        title = { Text("检测到上次崩溃（诊断版）") },
        text = {
            Column {
                Text(
                    text = "设备：${Build.BRAND} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                        "版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        "日志目录：${context.getExternalFilesDir(null)}/mma-logs\n" +
                        "——请将下面内容截图或复制后发给开发者——",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    // 清除日志文件，让下次崩溃重新从零记录
                    clearLogs(context)
                    crashText = null
                }) { Text("清除日志") }
                TextButton(onClick = { crashText = null }) { Text("知道了") }
            }
        },
    )
}

/** 读外置日志目录中与崩溃相关的最后片段（倒序扫活动文件 + 轮转文件）。 */
private fun readLatestCrash(context: android.content.Context): String? {
    val dir = File(context.getExternalFilesDir(null), "mma-logs") ?: return null
    val files = dir.listFiles { f -> f.isFile && f.name.startsWith("mma.log") } ?: return null
    if (files.isEmpty()) return null
    val merged = files.sortedByDescending { it.lastModified() }
        .take(2)
        .flatMap { file ->
            runCatching { file.readLines() }.getOrElse { emptyList() }.asReversed()
        }
        .filter { line -> line.contains("crash", ignoreCase = true) || line.contains("FATAL", ignoreCase = true) }
        .take(12)
        .reversed()
    if (merged.isEmpty()) return null
    return merged.joinToString("\n").take(4000)
}

private fun clearLogs(context: android.content.Context) {
    val dir = File(context.getExternalFilesDir(null), "mma-logs") ?: return
    runCatching {
        dir.listFiles { f -> f.isFile && f.name.startsWith("mma.log") }?.forEach { it.delete() }
    }
}
