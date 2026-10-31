package com.make.apk.data.model

import kotlinx.serialization.Serializable

/**
 * 统一内核共享契约（见 §2.5）。
 *
 * 它是 [Project] 去掉 DB 专属字段（`id` / `createdAt` / `updatedAt`）后的**可 JSON 序列化体**，
 * 是"本地容器 + 导出独立 APK"两者都要的**并集载体**：
 *
 * - 本地渲染器（[com.make.apk.ui.screens.WebViewScreen]）从 [Project] 构造它；
 * - 导出时写进模板壳 `assets/runtime-config.json`，壳内 WebView 代码与 App 内**同源**
 *   （本地注入：资产注入模板壳 APK + 设备端 apksigner 重签，无云端构建）。
 *
 * 全部字段均可 JSON 序列化（enum / data class 均标注 `@Serializable`），故能无损往返于
 * 内存对象与磁盘/资产中的 JSON。
 */
@Serializable
data class WebRuntimeConfig(
    /** 项目类型 / 分类（URL / HTML / VUE / PHP），保存后锁死。 */
    val projectType: ProjectType,
    /** 应用显示名（APK 的 `android:label`）。 */
    val apkName: String,
    /** 导出 APK 的 applicationId，如 `com.user.myapp`。 */
    val packageName: String,
    /** versionName 段式（2/3 段，独立于 MMA 自身 4 段式）。 */
    val versionScheme: VersionScheme,
    /** 按 scheme 生成的 versionName，如 `1.0` / `1.3.8`。 */
    val versionName: String,
    /** 单调递增整数，用于升级判定（Android 要求 int）。 */
    val versionCode: Int,
    /** 应用图标 uri / asset 引用；null 时用默认占位（themeColor 底 + apkName 首字母）。 */
    val iconRef: String? = null,
    /** ARGB 整数：导出 App 主题 / 启动页底色。 */
    val themeColor: Int,
    /** 实际入口引用（见 [ProjectEntry]）。 */
    val entry: ProjectEntry,
    /** Web 渲染配置（本地与导出共用），默认全集开启。 */
    val webSettings: WebSettings = WebSettings(),
    /** 本地多实例数据隔离目录后缀（W3 ⑬ 新格式 `MakeApk_<yyyyMMdd_HHmmss>_item<id>`；存量 `webapp_<id>` 不迁移）。 */
    val isolationId: String,
)
