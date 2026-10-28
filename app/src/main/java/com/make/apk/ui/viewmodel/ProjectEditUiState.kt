package com.make.apk.ui.viewmodel

import com.make.apk.data.model.ExportRun
import com.make.apk.data.model.ProjectDefaults
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.VersionScheme
import com.make.apk.data.model.WebSettings
import com.make.apk.ui.editor.EditField
import com.make.apk.ui.editor.FieldMessage

/**
 * 编辑页 UI 状态（§2.6）。
 *
 * 与添加页的差别：
 * - 入口 / 项目类型 / 隔离 id **只读**（保存后锁死，§2.6）；
 * - 多一个"外部推送"概念：库里的值变化时，若用户没改过表单（[dirty] 为 false）
 *   才覆盖用户输入，避免把用户正在输入的内容冲掉。
 */
data class ProjectEditUiState(
    /** 是否正在加载（首帧为 true）。 */
    val loading: Boolean = true,
    /** 项目不存在（id 无效或已删除）。 */
    val projectNotFound: Boolean = false,
    /** 项目类型（只读）。 */
    val projectType: ProjectType? = null,
    /** 入口引用（只读）。 */
    val entryRef: String = "",
    /** 应用名称输入。 */
    val apkName: String = "",
    /** 包名输入。 */
    val packageName: String = "",
    /** 版本号段式。 */
    val versionScheme: VersionScheme = ProjectDefaults.DEFAULT_VERSION_SCHEME,
    /** 展示版本号。 */
    val versionName: String = "",
    /** 内部版本号（字符串形态）。 */
    val versionCode: String = "",
    /** 图标引用。 */
    val iconRef: String? = null,
    /** 图标占位文字。 */
    val iconPlaceholderLabel: String = "?",
    /** Web 渲染配置（六字段可编辑）。 */
    val webSettings: WebSettings = ProjectDefaults.DEFAULT_WEB_SETTINGS,
    /** 主题色（ARGB）。 */
    val themeColor: Int = ProjectDefaults.DEFAULT_THEME_COLOR,
    /** 模板定制开关（阶段 2 恒为 false 占位）。 */
    val templateCustomizationEnabled: Boolean = false,
    /**
     * 壳契约（L2，阶段 6.1）——**只读展示**，本轮不提供编辑入口（6.3 才做进阶配置编辑器）。
     *
     * 取自库中项目的 `shell_spec_json`；旧数据（NULL / 脏 JSON）兜底为
     * [com.make.apk.data.model.ShellSpec.DEFAULT]（四区全关 = 裸 WebView）。
     *
     * **不引入 dirty 语义**：本轮没有壳配置的写路径，读到即展示，不参与"表单是否被改动过"。
     */
    val shellSpec: ShellSpec = ShellSpec.DEFAULT,
    /** 数据隔离目录后缀（只读）。 */
    val isolationId: String = "",
    /** 渲染引擎说明文案（只读展示）。 */
    val engineLabel: String = "",
    /** 该项目的导出运行记录（只读列表）。 */
    val exportRuns: List<ExportRun> = emptyList(),
    /** 面板是否折叠（页面态，不落库）。 */
    val collapsed: Boolean = false,
    /** 当前选中的页签（页面态，不落库）。 */
    val selectedTab: EditTab = EditTab.BASIC,
    /** 阻断性错误（key → 消息）。 */
    val errors: Map<EditField, FieldMessage> = emptyMap(),
    /** 非阻断性警告（key → 消息）。 */
    val warnings: Map<EditField, FieldMessage> = emptyMap(),
    /** 包名是否已被其他项目占用。 */
    val packageNameConflict: Boolean = false,
    /** 表单是否被改动过。 */
    val dirty: Boolean = false,
    /** 是否正在保存。 */
    val saving: Boolean = false,
) {
    /** 是否允许保存：加载完成、无错误。 */
    val canSave: Boolean
        get() = !saving && !loading && !projectNotFound && errors.isEmpty()
}

/**
 * 编辑页页签。
 */
enum class EditTab {
    /** 基本信息。 */
    BASIC,

    /** 模板与 Web 配置。 */
    TEMPLATE,

    /** 导出记录。 */
    EXPORT,
}

/**
 * 编辑页一次性事件（不重放）。
 */
sealed interface ProjectEditEvent {

    /** 保存成功。 */
    data class Saved(val projectId: Long) : ProjectEditEvent

    /** 保存失败，携带人类可读原因。 */
    data class SaveFailed(val message: String) : ProjectEditEvent

    /** 删除成功。 */
    data class Deleted(val projectId: Long) : ProjectEditEvent
}
