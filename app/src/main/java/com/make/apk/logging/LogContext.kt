package com.make.apk.logging

import android.os.Build
import com.make.apk.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志上下文：一次性惰性收集设备 / 应用元信息，供结构化日志携带。
 *
 * 仅含"设备类别信息"（厂商 / 型号 / 系统版本 / 应用版本 / 构建类型），
 * **不含** IMEI、序列号、账号等可识别标识，符合 [docs/logging.md] 脱敏红线。
 *
 * 所有取值均 try-catch 兜底返回 "unknown"：纯 JVM 单测 / Robolectric 环境下
 * `Build` / `BuildConfig` 可能取值异常或缺省（android.jar 桩常返回 null，非异常），
 * 用 `getOrNull() ?: 默认` 同时兜住"抛异常"与"返回 null"两种情形，
 * 绝不因此让日志或启动崩溃。
 */
object LogContext {

    /** 设备厂商（如 Google）。 */
    val deviceManufacturer: String by lazy {
        runCatching { Build.MANUFACTURER }.getOrNull() ?: "unknown"
    }

    /** 设备型号（如 Pixel_7）。 */
    val deviceModel: String by lazy {
        runCatching { Build.MODEL }.getOrNull() ?: "unknown"
    }

    /** 系统版本号（如 14）。 */
    val osVersion: String by lazy {
        runCatching { Build.VERSION.RELEASE }.getOrNull() ?: "unknown"
    }

    /** 系统 SDK 级别（如 34）。 */
    val osSdk: Int by lazy {
        runCatching { Build.VERSION.SDK_INT }.getOrNull() ?: 0
    }

    /** 应用版本名（如 0.0.0.1-rc5）。 */
    val appVersion: String by lazy {
        runCatching { BuildConfig.VERSION_NAME }.getOrNull() ?: "unknown"
    }

    /** 应用版本号（versionCode，如 8）。 */
    val appVersionCode: String by lazy {
        runCatching { BuildConfig.VERSION_CODE.toString() }.getOrNull() ?: "unknown"
    }

    /** 构建类型（debug / release）。 */
    val buildType: String by lazy {
        runCatching { BuildConfig.BUILD_TYPE }.getOrNull() ?: "unknown"
    }

    /**
     * 把 epoch 毫秒格式化为 ISO8601 时间戳（设备默认时区）。
     *
     * 用 `SimpleDateFormat`（**非 java.time**）：minSdk 24 未脱糖，java.time 在
     * 低版本会抛 NoSuchMethodError / 崩溃。返回形如 `2026-10-28T17:17:50.228+0800`，
     * 并把 `+0800` 这类时区修正为 `+08:00`（RFC3339，机器更友好，可选但推荐）。
     */
    fun isoFrom(epochMs: Long): String = runCatching {
        val raw = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
            .format(Date(epochMs))
        // 把 +0800 修正为 +08:00：对末尾的 ±HHMM 时区段插入冒号。
        Regex("([+-]\\d{2})(\\d{2})$").replace(raw) { m ->
            "${m.groupValues[1]}:${m.groupValues[2]}"
        }
    }.getOrDefault("unknown")
}
