package com.make.apk.ui.editor

/**
 * 添加页字段枚举（§2.6）。
 *
 * 存在的意义：把"哪个字段出了什么问题"从字符串猜测变成**类型化**定位，
 * UI 层据此决定把错误挂在哪个输入框下方（`supportingText`）。
 *
 * 字段划分与 [ProjectValidator] 的规则一一对应，两边新增字段必须同步。
 */
enum class AddField {
    /** 项目类型（准星选择器）。 */
    PROJECT_TYPE,

    /** 应用名称。 */
    APK_NAME,

    /** 包名（applicationId）。 */
    PACKAGE_NAME,

    /** 展示版本号。 */
    VERSION_NAME,

    /** 内部版本号（正整数）。 */
    VERSION_CODE,

    /** 图标引用。 */
    ICON,

    /** 入口：远程网址。 */
    ENTRY_URL,

    /** 入口：内置 HTML 源码（新建页不再直录代码，保留用于代码内容类错误）。 */
    ENTRY_HTML,

    /** 入口：HTML 类的入口文件名（首屏加载文件，留空默认 index.html）。 */
    ENTRY_FILE,

    /** 入口：资产包（zip）。 */
    ENTRY_ARCHIVE,
}

/**
 * 编辑页字段枚举（§2.6）。
 *
 * 与 [AddField] 的差别：编辑页的入口与项目类型**锁死**，不可修改，
 * 因此没有入口类字段（`ENTRY_*`）与 `PROJECT_TYPE`。
 */
enum class EditField {
    /** 应用名称。 */
    APK_NAME,

    /** 包名（applicationId）。 */
    PACKAGE_NAME,

    /** 展示版本号。 */
    VERSION_NAME,

    /** 内部版本号（正整数）。 */
    VERSION_CODE,

    /** 图标引用。 */
    ICON,

    /** 入口：远程网址（编辑页现在允许修改 URL 入口，见 §优化·第四点）。 */
    ENTRY_URL,

    /** 入口：内置 HTML 源码（「项目代码」页编辑，空内容保存时兜底默认模板）。 */
    ENTRY_HTML,

    /** 入口：HTML 类的入口文件名（首屏加载文件，留空默认 index.html）。 */
    ENTRY_FILE,
}

/**
 * 字段级提示消息（面向用户的中文文案）。
 *
 * [severity] 决定 UI 的呈现方式：`ERROR` 挂红色错误态并阻断保存，
 * `WARNING` 只做提醒（黄字），不阻断保存。
 */
data class FieldMessage(
    /** 面向用户的中文提示。 */
    val text: String,
    /** 级别。 */
    val severity: Severity,
) {
    /** 级别枚举。 */
    enum class Severity {
        /** 错误：阻断保存。 */
        ERROR,

        /** 警告：仅提醒。 */
        WARNING,
    }
}

/**
 * 单个字段的校验结论（可同时带一条错误与一条警告）。
 *
 * 例如应用名 45 个字：既超了"建议 30 字"的软上限（警告），
 * 又没超 50 字硬上限（无错误）。
 */
data class FieldVerdict(
    /** 错误提示（null 表示通过）。 */
    val error: FieldMessage? = null,
    /** 警告提示（null 表示无警告）。 */
    val warning: FieldMessage? = null,
) {
    /** 是否存在阻断性错误。 */
    val hasError: Boolean get() = error != null
}
