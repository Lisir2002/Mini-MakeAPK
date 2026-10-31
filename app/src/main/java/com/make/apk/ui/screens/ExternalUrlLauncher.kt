package com.make.apk.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/** 外链启动诊断日志标签（预览与导出壳共用本文件，Logcat 是导出壳唯一诊断面）。 */
private const val TAG_EXTERNAL_LAUNCHER: String = "external_url_launcher"

/**
 * 放行 scheme 白名单（QA Minor-F1）：仅 `http`/`https` 交给系统浏览器。
 *
 * `file://`（导出壳内页资产必须走 WebView 而非外部处理器）、`intent://`（Chrome
 * 私有深链语法，可被用来绕过浏览器直达任意组件）等一律拒绝——三个调用点
 * （预览事件流 / TemplateActivity / WebViewScreen 页内分支）全部收口本单点。
 */
private val ALLOWED_SCHEMES: Set<String> = setOf("http", "https")

/**
 * 外链执行边缘（W2 ④，主控拍板：`Intent.ACTION_VIEW` 系统浏览器，**零新增依赖**——
 * 明确拒绝 androidx.browser，守住"轻量 APK"卖点）。
 *
 * 预览与导出壳共用本物理文件：`template/build.gradle.kts` 的 sourceSets 引入整个
 * `ui/screens` 目录（仅 exclude 指定 screen），新文件自动纳入。因此本文件**必须保持
 * 零 ViewModel / 零 app 主题依赖**（只用 android.* 基础 API）。
 *
 * @param url 目标地址（**仅 http/https**；空串 / 非白名单 scheme 直接失败）。
 * @return 是否成功拉起；地址非法 / scheme 非白名单 / 无浏览器 / startActivity
 *   抛异常一律返回 **false**，绝不崩溃——调用方（WebViewClient / 导航层）据此给出可见提示。
 */
fun openExternal(context: Context, url: String): Boolean {
    val target = url.trim()
    if (target.isEmpty()) return false
    val scheme = runCatching { Uri.parse(target).scheme?.lowercase() }.getOrNull()
    if (scheme !in ALLOWED_SCHEMES) {
        Log.w(TAG_EXTERNAL_LAUNCHER, "external_launch_rejected: scheme=$scheme ($target)")
        return false
    }
    return runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(target))
                .addCategory(Intent.CATEGORY_BROWSABLE),
        )
        true
    }.onFailure {
        Log.w(TAG_EXTERNAL_LAUNCHER, "external_launch_failed: $target (${it.javaClass.simpleName})")
    }.getOrDefault(false)
}
