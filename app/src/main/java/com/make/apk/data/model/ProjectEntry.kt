package com.make.apk.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 项目实际入口引用（见 §2.3）。由 [ProjectType] 决定形态，保存后**锁死**不可更改。
 *
 * 这是密封接口（sealed interface），各子类型的 JSON 序列化带类判别符（默认 `"type"`），
 * 本地渲染器与导出模板壳使用同一套序列化器即可互读。
 */
@Serializable
sealed interface ProjectEntry {
    /** 原始引用串（URL / HTML 全文 / 资产路径），用于去重与预览判等。 */
    val rawRef: String

    /** 远程网址：设备端 WebView 直接 `loadUrl`。 */
    @Serializable
    @SerialName("url")
    data class Url(val url: String) : ProjectEntry {
        override val rawRef: String get() = url
    }

    /** 内置 HTML 源码：本地 `loadDataWithBaseURL` 渲染（粘贴 / 上传的 HTML）。 */
    @Serializable
    @SerialName("html")
    data class BundledHtml(val html: String) : ProjectEntry {
        override val rawRef: String get() = html
    }

    /**
     * 资产包（Vue 工程 / PHP 工程 / 通用打包物）。
     *
     * 本地注入：资产解包到 [assetRef] 目录后由本地 WebView 加载（资产注入模板壳 + 本地重签）。
     * [kind] 标明框架类型，决定解包/加载策略（见 §8.5 D-M2 的迁移对账）。
     */
    @Serializable
    @SerialName("archive")
    data class Archive(
        val assetRef: String,
        val kind: ArchiveKind,
    ) : ProjectEntry {
        override val rawRef: String get() = assetRef
    }
}

/**
 * 资产包框架类型（见 §2.3 / §8.5）。
 *
 * 注意：**不新增第五个 [ProjectType]**——迁移对账后必然落入 VUE/PHP 之一（§8.5 D-M2），
 * 故准星选择器四项 UI 不受影响；GENERIC 仅作迁移期的兜底占位。
 */
@Serializable
enum class ArchiveKind {
    /** Vue 等前端框架构建产物。 */
    VUE,

    /** PHP 工程（静态化或托管）。 */
    PHP,

    /** 未知框架兜底：保留原始信息，待首启 `ArchiveClassifier` 一次性嗅探纠正（§8.5 D-M2）。 */
    GENERIC,
}
