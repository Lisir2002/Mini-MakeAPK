package com.make.apk.data.model

import kotlinx.serialization.Serializable

/**
 * 项目类型 / 分类（见 docs/design/project-editor-design.md §2.3）。
 *
 * 添加页横向准星选择器的四项，保存后**锁死**不可更改（更换须新建项目）。
 * 它由 [ProjectEntry] 的形态决定，并直接驱动本地注入方式与导出模板壳的选择。
 */
@Serializable
enum class ProjectType {
    /** 远程网址，设备端 WebView 即时封装（本地注入）。 */
    URL,

    /** 粘贴 / 上传的 HTML / 前端源码，资产注入后由本地 WebView 加载。 */
    HTML,

    /** Vue 等前端工程构建产物，资产注入 APK 后由本地 WebView 加载（本地注入）。 */
    VUE,

    /** PHP 项目：托管网址 或 静态化后资产注入（本地注入，不再走云端构建）。 */
    PHP,
}
