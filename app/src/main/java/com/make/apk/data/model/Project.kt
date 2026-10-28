package com.make.apk.data.model

/**
 * 项目（统一内核 · 稳定定义，见 docs/design/project-editor-design.md §2.2）。
 *
 * 与 [ExportRun] 的分工：
 * - [Project] = **稳定定义**：用户创建的项目身份与配置，一经保存即落库；
 * - [ExportRun] = **运行态**：每次导出（出包）一条，与项目 1:N。
 *
 * 关键约束（见 §2.6 / §9.4）：
 * - [projectType] 与 [entry] 保存后**锁死**——更换须新建项目；
 * - [packageName] 全局唯一（同包名强制拦截，避免导出 APK 互相覆盖）；
 * - [isolationId] 为本地多实例数据隔离目录后缀。
 */
data class Project(
    /** 主键，0 表示尚未落库。 */
    val id: Long = 0,
    /** 项目类型 / 分类（URL / HTML / VUE / PHP），保存后锁死。 */
    val projectType: ProjectType,
    /** 应用显示名（APK 的 `android:label`）。 */
    val apkName: String,
    /** 导出 APK 的 applicationId，如 `com.user.myapp`；全局唯一。 */
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
    /** 实际入口引用（见 [ProjectEntry]），保存后锁死。 */
    val entry: ProjectEntry,
    /** Web 渲染配置（本地与导出共用）。 */
    val webSettings: WebSettings = WebSettings(),
    /**
     * 壳契约（L2，阶段 6.1）——顶栏 / 顶栏 tab / 底栏 / 侧边栏 四区配置。
     *
     * 默认 [ShellSpec.DEFAULT]（四区全关 = 裸 WebView），与阶段 5 行为完全一致 → 零回归。
     * **不参与** [toRuntimeConfig]：L1 是"内容运行时"，L2 是"壳形态"，两者不混。
     */
    val shellSpec: ShellSpec = ShellSpec.DEFAULT,
    /** 本地多实例数据隔离目录后缀（`webapp_<id>`）。 */
    val isolationId: String,
    /** 创建时间（epoch ms）。 */
    val createdAt: Long,
    /** 最后更新时间（epoch ms）。 */
    val updatedAt: Long,
) {
    /**
     * 投影为统一内核共享契约 [WebRuntimeConfig]。
     *
     * 这是"本地容器 + 导出独立 APK"的**唯一交汇点**：
     * 本地 WebView 渲染与导出模板壳 `assets/runtime-config.json` 消费的是同一份结构。
     */
    fun toRuntimeConfig(): WebRuntimeConfig = WebRuntimeConfig(
        projectType = projectType,
        apkName = apkName,
        packageName = packageName,
        versionScheme = versionScheme,
        versionName = versionName,
        versionCode = versionCode,
        iconRef = iconRef,
        themeColor = themeColor,
        entry = entry,
        webSettings = webSettings,
        isolationId = isolationId,
    )
}

/**
 * 项目默认值（§2.6 / §6.1）——**单一事实源**。
 *
 * 添加页、编辑页、迁移脚本、仓储层都从这里取默认值，
 * 避免同一个魔数散落多处（改一处漏一处是典型 bug 温床）。
 */
object ProjectDefaults {

    /** 默认主题色：设计语言主色蓝 `#2563EB`（ARGB，DESIGN_LANGUAGE §2；旧默认珊瑚粉 #E39E97 已废弃）。 */
    const val DEFAULT_THEME_COLOR: Int = 0xFF2563EB.toInt()

    /** 默认版本段式：三段（§6.1 已确认）。 */
    val DEFAULT_VERSION_SCHEME: VersionScheme = VersionScheme.THREE_SEGMENT

    /** 默认 versionCode（正整数，§2.6）。 */
    const val DEFAULT_VERSION_CODE: Int = 1

    /** 默认 Web 渲染配置（§2.6 全集开启）。 */
    val DEFAULT_WEB_SETTINGS: WebSettings = WebSettings()

    /** 按段式派生默认版本号：三段 `1.0.0` / 二段 `1.0`。 */
    fun defaultVersionName(scheme: VersionScheme): String = when (scheme) {
        VersionScheme.TWO_SEGMENT -> "1.0"
        VersionScheme.THREE_SEGMENT -> "1.0.0"
    }

    /** 本地多实例数据隔离目录后缀（§2.6）：`webapp_<id>`。 */
    fun isolationIdOf(projectId: Long): String = "webapp_$projectId"
}

/**
 * 包名重复异常（§2.6 / §6.1：同包名**强制拦截**，禁止保存）。
 *
 * 放在领域层而非仓储实现里，UI 可直接捕获并转成"人话"提示。
 */
class DuplicatePackageNameException(
    /** 冲突的包名。 */
    val packageName: String,
) : IllegalStateException("包名已存在：$packageName")
