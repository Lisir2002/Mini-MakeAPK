package com.make.apk.data.strategy

import com.make.apk.data.model.ArchiveKind
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.ui.editor.AddField
import com.make.apk.ui.editor.ArchiveProbeResult
import com.make.apk.ui.editor.FieldVerdict
import com.make.apk.ui.editor.ProjectValidator

/**
 * 项目类型策略——**按类型隔离的入口逻辑唯一收口点**（§优化·第五点）。
 *
 * ## 通用 vs 隔离
 * - **通用**（URL/HTML/VUE/PHP 共享）：应用名 / 包名 / 版本 / 主题色 / 壳契约 / Web 配置，
 *   由基类与各自 ViewModel 统一维护，不在此处；
 * - **隔离**（按类型分开）：仅入口构造、入口校验、来源输入清理由本策略按 [ProjectType] 分派，
 *   杜绝"同类型多套写法"与"类型判断散落各处"。
 *
 * ## 为什么是 object 而非每个类型一个类
 * 当前各类型差异仅在入口形态（URL 串 / HTML 串 / 资产包路径），用 `when(type)` 分派最轻量；
 * 若未来出现行为差异更大的类型，再升级为每类型一个实现（[ProjectTypeStrategy] 接口）。
 */
object ProjectTypeStrategy {

    /**
     * 按类型构造入口引用；输入不完整返回 null（与 [ProjectValidator] 的判定一致）。
     *
     * HTML 类（类型隔离）：代码为空时兜底内置默认模板（开箱能跑），
     * 入口文件为空时兜底 `index.html`——新建页不再要求先写代码。
     *
     * @param type 项目类型（null 直接返回 null）。
     * @param urlInput URL 类来源输入。
     * @param htmlInput HTML 类源码输入（可空，兜底默认模板）。
     * @param archivePath 资产包路径（VUE/PHP）。
     * @param archiveKind 资产包框架类型。
     * @param entryFileInput HTML 类入口文件名输入（可空，兜底 index.html）。
     */
    fun buildEntry(
        type: ProjectType?,
        urlInput: String,
        htmlInput: String,
        archivePath: String?,
        archiveKind: ArchiveKind,
        entryFileInput: String = "",
    ): ProjectEntry? = when (type) {
        ProjectType.URL -> urlInput.trim().takeIf { it.isNotBlank() }?.let(ProjectEntry::Url)
        ProjectType.HTML -> ProjectEntry.BundledHtml(
            html = htmlInput.trim().ifBlank { ProjectEntry.BundledHtml.DEFAULT_TEMPLATE },
            entryFile = entryFileInput.trim().ifBlank { ProjectEntry.BundledHtml.DEFAULT_ENTRY_FILE },
        )
        ProjectType.VUE, ProjectType.PHP -> archivePath
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { ProjectEntry.Archive(assetRef = it, kind = archiveKind) }
        null -> null
    }

    /** 入口错误应挂在哪个字段枚举（决定提示挂在哪个输入框下方）。 */
    fun entryField(type: ProjectType): AddField = when (type) {
        ProjectType.URL -> AddField.ENTRY_URL
        ProjectType.HTML -> AddField.ENTRY_FILE
        ProjectType.VUE, ProjectType.PHP -> AddField.ENTRY_ARCHIVE
    }

    /**
     * 按类型校验入口，返回结论（错误挂到 [entryField] 对应字段）。
     *
     * HTML 类校验**入口文件名**（代码内容可空，保存时兜底默认模板）。
     *
     * @param probeResult 资产包探测结果（仅 VUE/PHP 需要，其他类型传 null）。
     * @param entryFileInput HTML 类入口文件名输入（其他类型忽略）。
     */
    fun validateEntry(
        type: ProjectType,
        validator: ProjectValidator,
        urlInput: String,
        htmlInput: String,
        archivePath: String?,
        probeResult: ArchiveProbeResult?,
        entryFileInput: String = "",
    ): FieldVerdict = when (type) {
        ProjectType.URL -> validator.validateEntryUrl(urlInput)
        ProjectType.HTML -> validator.validateEntryFile(entryFileInput)
        ProjectType.VUE, ProjectType.PHP -> validator.validateEntryArchive(archivePath, probeResult)
    }
}
