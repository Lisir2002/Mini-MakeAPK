package com.make.apk.ui.viewmodel

import com.make.apk.data.model.ArchiveKind
import com.make.apk.data.model.ProjectDefaults
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.VersionScheme
import com.make.apk.ui.editor.AddField
import com.make.apk.ui.editor.FieldMessage

/**
 * 添加页 UI 状态（§2.6）。
 *
 * 单一不可变状态对象：页面只读它渲染，事件全部走 [AddProjectViewModel] 的方法。
 *
 * 字段错误用 [errors] / [warnings] 两张表承载，key 是字段枚举——
 * UI 直接取 `isError` + `supportingText` 即可，无需自己判断文案。
 */
data class AddProjectUiState(
    /** 项目类型；null 表示用户尚未确认（准星未选中）。 */
    val projectType: ProjectType? = null,
    /** 入口：远程网址输入。 */
    val urlInput: String = "",
    /** 入口：内置 HTML 源码（新建页不暴露输入；留空保存时兜底默认模板）。 */
    val htmlInput: String = "",
    /** 入口（HTML 类）：入口文件名输入；空 = 默认 index.html（首屏加载文件）。 */
    val entryFileInput: String = "",
    /** 入口：资产包路径；null 表示未选择。 */
    val archivePath: String? = null,
    /** 入口：资产包框架类型。 */
    val archiveKind: ArchiveKind = ArchiveKind.VUE,
    /** 应用名称输入。 */
    val apkName: String = "",
    /** 包名输入。 */
    val packageName: String = "",
    /** 版本号段式。 */
    val versionScheme: VersionScheme = ProjectDefaults.DEFAULT_VERSION_SCHEME,
    /** 展示版本号。 */
    val versionName: String = ProjectDefaults.defaultVersionName(ProjectDefaults.DEFAULT_VERSION_SCHEME),
    /** 内部版本号（字符串形态，便于输入与实时校验）。 */
    val versionCode: String = "1",
    /** 图标引用；null 表示使用默认占位。 */
    val iconRef: String? = null,
    /** 主题色（ARGB）。 */
    val themeColor: Int = ProjectDefaults.DEFAULT_THEME_COLOR,
    /** 应用名自动建议（可一键采用）。 */
    val apkNameSuggestion: String? = null,
    /** 包名自动建议（可一键采用）。 */
    val packageNameSuggestion: String? = null,
    /** 用户是否手动改过应用名（改过则不再跟随建议）。 */
    val apkNameEdited: Boolean = false,
    /** 用户是否手动改过包名（改过则不再跟随建议）。 */
    val packageNameEdited: Boolean = false,
    /** 阻断性错误（key → 消息）。 */
    val errors: Map<AddField, FieldMessage> = emptyMap(),
    /** 非阻断性警告（key → 消息）。 */
    val warnings: Map<AddField, FieldMessage> = emptyMap(),
    /** 包名是否已被其他项目占用。 */
    val packageNameConflict: Boolean = false,
    /** 是否正在保存。 */
    val saving: Boolean = false,
    /**
     * 表单是否被改动过（供"放弃修改？"确认）。
     *
     * **只由用户操作置 true**：准星在首帧就会回传默认类型（URL），那是渲染副产物不是用户动作，
     * 因此 `onProjectTypeSelected` 只在"此前已有选中项"时才置 true。
     */
    val dirty: Boolean = false,
    /**
     * 最近一次保存成功写入的项目 id；null 表示尚未保存或保存失败。
     *
     * 用于保存后**留驻编辑页**时给出「预览」入口（§优化：保存不再强制退出页面）。
     * 重新编辑（任意 onXxxChanged）会再次置脏，但不清空此值——已落库的项目随时可预览。
     */
    val savedProjectId: Long? = null,
) {
    /** 是否允许保存：无错误、项目类型已定、入口有效。 */
    val canSave: Boolean
        get() = !saving && projectType != null && errors.isEmpty() && entryValid()

    /** 入口是否已填写完整（与项目类型联动）。 */
    private fun entryValid(): Boolean = when (projectType) {
        ProjectType.URL -> urlInput.isNotBlank()
        // HTML 类：入口文件可选（默认 index.html）、代码兜底默认模板，恒可保存。
        ProjectType.HTML -> true
        ProjectType.VUE, ProjectType.PHP -> !archivePath.isNullOrBlank()
        null -> false
    }
}

/**
 * 添加页一次性事件（通过 `Channel.receiveAsFlow()` 派发，不重放）。
 *
 * 与状态分开的原因：保存成功这类**一次性动作**不该混进可重放的状态流里，
 * 否则页面重建会重复消费。
 */
sealed interface AddProjectEvent {

    /** 保存成功，携带新项目 id。 */
    data class Saved(val projectId: Long) : AddProjectEvent

    /** 保存失败，携带人类可读原因。 */
    data class SaveFailed(val message: String) : AddProjectEvent
}
