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

    /**
     * 内置 HTML 项目：本地 `loadDataWithBaseURL` 渲染（新建时生成的网页应用）。
     *
     * ## 文件形态（rc9 起，类型隔离）
     *
     * HTML 项目是"有文件的项目"：[entryFile] 是该网页应用的**入口文件**（首屏加载的文件），
     * 默认 `index.html`；[html] 是入口文件的源码内容。二者共同构成 HTML 项目的
     * 专属数据形态，与 URL（纯网址）互不混用（类型隔离，见 docs/design/project-editor-design.md）。
     *
     * ## 多文件（rc11 起）
     *
     * [files] 是项目的**附加文件集**（CSS / JS / 图片 / 文本等），文件名支持
     * `css/style.css` 形式的路径（按 `/` 分层渲染为目录树）；入口文件内容仍以
     * [html] 为单一事实源（[files] 中不重复存入口文件）。WebView 加载入口时以
     * 虚拟域名 baseURL 解析相对路径，附加文件经 `shouldInterceptRequest` 就地供给，
     * 网页里 `<link href="css/style.css">` 即可生效。
     *
     * **向后兼容**：[entryFile] / [files] 带默认值，老数据 JSON 缺字段时反序列化
     * 自动落默认值，不炸、行为不变。
     */
    @Serializable
    @SerialName("html")
    data class BundledHtml(
        /** 入口文件的源码内容。 */
        val html: String,
        /** 入口文件名（首屏加载的文件；留空语义由构造方兜底为 [DEFAULT_ENTRY_FILE]）。 */
        val entryFile: String = DEFAULT_ENTRY_FILE,
        /** 附加文件集（不含入口文件；rc11 起支持，老数据为空集）。 */
        val files: List<ProjectFile> = emptyList(),
    ) : ProjectEntry {
        override val rawRef: String get() = html

        /** 取指定文件内容：入口文件回落 [html]；不存在返回 null。 */
        fun contentOf(name: String): String? = when (name) {
            entryFile -> html
            else -> files.firstOrNull { it.name == name }?.content
        }

        /** 全部文件名（入口在前，其余按存储序）。 */
        val fileNames: List<String> = listOf(entryFile) + files.map { it.name }

        /** 是否已存在同名文件（与入口同名也算存在）。 */
        fun containsFile(name: String): Boolean = fileNames.any { it.equals(name, ignoreCase = false) }

        /** 新增 / 覆盖一个文件：入口名写回 [html]，其余 upsert 进 [files]。 */
        fun withFile(name: String, content: String): BundledHtml = when (name) {
            entryFile -> copy(html = content)
            else -> {
                val filtered = files.filterNot { it.name == name }
                copy(files = filtered + ProjectFile(name, content))
            }
        }

        /** 移除文件（入口文件不可移除，调用方拦截）。 */
        fun withoutFile(name: String): BundledHtml = copy(files = files.filterNot { it.name == name })

        companion object {
            /** 默认入口文件名（网页应用规范：首屏加载 index.html）。 */
            const val DEFAULT_ENTRY_FILE: String = "index.html"

            /**
             * 新建不写代码时的内置默认模板：最小可运行 HTML（含 viewport 与提示文案），
             * 保证项目开箱能跑、预览与导出不出空白页；用户可在「项目代码」页替换。
             */
            val DEFAULT_TEMPLATE: String = """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>我的网页</title>
                </head>
                <body>
                  <h1>你好，这是你的网页</h1>
                  <p>在项目的「项目代码」页编辑本文件，替换成你自己的内容。</p>
                </body>
                </html>
            """.trimIndent()
        }
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
 * HTML 项目的附加文件（rc11 起）。纯文本形态（源码 / 配置 / SVG 等），
 * 名字支持 `a/b.txt` 路径形式（按 `/` 分层为目录树）。
 *
 * 注意：入口文件内容不在本模型里（以 [ProjectEntry.BundledHtml.html] 为单一事实源）。
 */
@Serializable
data class ProjectFile(
    /** 文件名（含相对路径，`/` 分层；不以 `/` 开头）。 */
    val name: String,
    /** 文本内容。 */
    val content: String = "",
)

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
