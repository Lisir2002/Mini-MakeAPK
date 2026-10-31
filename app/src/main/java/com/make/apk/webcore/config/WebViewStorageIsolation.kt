package com.make.apk.webcore.config

import android.content.Context
import android.os.Build
import android.webkit.WebView
import com.make.apk.webcore.diagnostics.WebViewEventHook

/**
 * 预览态 WebView 存储隔离（W4 主控拍板 2：first-wins + 告警埋点；导出壳真隔离）。
 *
 * ## 背景
 *
 * WebView 的本地存储（LocalStorage / IndexedDB / Cookie 落盘）默认挂在**进程级**数据目录，
 * 同一进程内多项目预览会互相读写对方站点数据。API 28+ 提供
 * [WebView.setDataDirectorySuffix] 可按后缀隔离；API 24-27 无官方隔离手段。
 *
 * ## 语义（拍板 2 原文：first-wins + 告警埋点，导出壳真隔离）
 *
 * - 进程内**第一个**调用者占用其后缀（first-wins），此后任何**不同** isolationId 的
 *   调用都回落到共享存储并上报 `storage_isolation_conflict` 告警——预览侧多项目共存，
 *   不为后到者重启内核；
 * - 导出壳每个 APK 独立进程：Activity 在 `setContent` **之前**调用本单点
 *   （[ensureApplied]），此时进程内尚无 WebView 创建 → 隔离必然生效（真隔离）；
 * - API 24-27 平台不支持后缀隔离：上报 `storage_isolation_unsupported` 后回落共享
 *   （同样 first-wins，防重复告警刷屏）；
 * - 全程**绝不抛异常**：隔离失败（罕见 ROM 限制）仅上报 `storage_isolation_failed`，
 *   回落共享存储继续加载。
 *
 * 零依赖约束：仅 android.webkit + 共享埋点钩子（template 同套源码可编译）。
 */
object WebViewStorageIsolation {

    /** 告警埋点：first-wins 命中（后到者回落共享存储）。 */
    const val EVENT_CONFLICT: String = "storage_isolation_conflict"

    /** 告警埋点：平台不支持后缀隔离（API < 28）。 */
    const val EVENT_UNSUPPORTED: String = "storage_isolation_unsupported"

    /** 告警埋点：设置后缀本身失败（罕见 ROM 限制），回落共享。 */
    const val EVENT_FAILED: String = "storage_isolation_failed"

    /** 后缀合法字符白名单之外的替换字符（后缀会拼进数据目录路径，防御路径注入）。 */
    private const val SANITIZE_REPLACEMENT: String = "_"

    /** 已占用的后缀（first-wins 标记；null = 尚未占用）。 */
    @Volatile
    private var appliedSuffix: String? = null

    /**
     * 共享回落标记：平台不支持（API < 28）或后缀设置失败后置位——本进程已接受
     * "共享存储"事实，后续任何调用直通（防告警刷屏）。
     */
    @Volatile
    private var sharedFallback: Boolean = false

    /** 预览态存储隔离单点：进程内首次调用生效，后续不同 isolationId 告警并回落共享。 */
    fun ensureApplied(context: Context, isolationId: String) {
        val suffix = sanitizeSuffix(isolationId)
        if (sharedFallback) return
        val applied = appliedSuffix
        if (applied != null) {
            // first-wins：后到者（不同后缀）回落共享 + 告警；同后缀幂等直通。
            if (applied != suffix) {
                WebViewEventHook.report(
                    EVENT_CONFLICT,
                    mapOf("first" to applied, "request" to suffix),
                )
            }
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // API 24-27：无官方隔离手段，回落共享（占位后全部直通，防重复告警）。
            sharedFallback = true
            WebViewEventHook.report(EVENT_UNSUPPORTED, mapOf("sdk" to Build.VERSION.SDK_INT))
            return
        }
        runCatching { WebView.setDataDirectorySuffix(suffix) }
            .onSuccess { appliedSuffix = suffix }
            .onFailure {
                sharedFallback = true
                WebViewEventHook.report(
                    EVENT_FAILED,
                    mapOf("suffix" to suffix, "detail" to (it.message ?: "unknown")),
                )
            }
    }

    /** 仅当前测试可见：重置 first-wins 状态（生产代码禁止调用）。 */
    internal fun resetForTest() {
        appliedSuffix = null
        sharedFallback = false
    }

    /**
     * 后缀净化：仅保留字母 / 数字 / 下划线 / 连字符，其余替换为 `_`，并限长 64。
     * isolationId（W3 新格式 `MakeApk_<yyyyMMdd_HHmmss>_item<id>` / 存量 `webapp_<id>`）
     * 天然合法；防御外部注入的路径穿越字符（`/`、`..` 等）。
     */
    internal fun sanitizeSuffix(isolationId: String): String {
        val cleaned = isolationId.map { ch ->
            if (ch.isLetterOrDigit() || ch == '_' || ch == '-') ch else SANITIZE_REPLACEMENT[0]
        }.joinToString("")
        return cleaned.take(64).ifBlank { "webapp_default" }
    }
}
