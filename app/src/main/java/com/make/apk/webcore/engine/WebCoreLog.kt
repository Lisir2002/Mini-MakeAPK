package com.make.apk.webcore.engine

/**
 * webcore 统一 Logcat 标签（白屏排查：重定向链 / 拦截 / SSL / 空内容探测统一在此）。
 *
 * 与旧单体 `WebViewScreen.TAG_LOG` 等值迁移（"webview_runtime"），保证既有
 * 排查习惯 / 日志过滤脚本零迁移成本。Logcat 是导出壳唯一可用的诊断面。
 */
internal const val TAG_WEBVIEW_RUNTIME: String = "webview_runtime"
