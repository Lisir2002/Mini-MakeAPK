package com.make.apk.data.mapper

import com.make.apk.data.json.RuntimeJson
import com.make.apk.data.local.entity.ProjectEntity
import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.WebSettings

/**
 * 项目持久化形态 ⇄ 领域模型映射。
 *
 * 存在的意义：Room 注解、表名、索引、JSON 存储格式等持久化细节被锁在 `data.local` 内，
 * 上层只依赖 [Project]；将来若改列或换存储格式，只需改这里，UI 无感。
 *
 * **JSON 容错策略**：反序列化失败（脏数据 / 手工改库）**不抛异常**，而是降级为安全值并保留原始
 * JSON 于日志思路中——移动端稳健性优先：一条坏数据不该让整个项目列表不可用。
 * 入口损坏降级为空白 HTML（项目仍可打开、可编辑、可重新指定入口）。
 */

/** 反序列化失败时的兜底入口：一个不依赖网络的空白页。 */
private val FallbackEntry: ProjectEntry = ProjectEntry.BundledHtml("")

fun ProjectEntity.toDomain(): Project = Project(
    id = id,
    projectType = projectType,
    apkName = apkName,
    packageName = packageName,
    versionScheme = versionScheme,
    versionName = versionName,
    versionCode = versionCode,
    iconRef = iconRef,
    themeColor = themeColor,
    entry = decodeEntryOrFallback(entryJson),
    webSettings = decodeSettingsOrFallback(webSettingsJson),
    shellSpec = decodeShellSpecOrFallback(shellSpecJson),
    isolationId = isolationId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Project.toEntity(): ProjectEntity = ProjectEntity(
    id = id,
    createdAt = createdAt,
    updatedAt = updatedAt,
    projectType = projectType,
    apkName = apkName,
    packageName = packageName,
    versionScheme = versionScheme,
    versionName = versionName,
    versionCode = versionCode,
    iconRef = iconRef,
    themeColor = themeColor,
    entryJson = RuntimeJson.encodeEntry(entry),
    webSettingsJson = RuntimeJson.encodeSettings(webSettings),
    // 默认壳（四区全关）落库为 NULL：语义即"未配置"。
    // 这样 v5 之前的既有行（NULL）与新写入的默认项目在库里**完全同形**，
    // 实体 ⇄ 领域映射往返无损，也避免给无意义的默认壳塞一坨 JSON。
    shellSpecJson = shellSpec.takeIf { it != ShellSpec.DEFAULT }?.let(RuntimeJson::encodeShellSpec),
    isolationId = isolationId,
)

private fun decodeEntryOrFallback(raw: String): ProjectEntry =
    runCatching { RuntimeJson.decodeEntry(raw) }.getOrDefault(FallbackEntry)

private fun decodeSettingsOrFallback(raw: String): WebSettings =
    runCatching { RuntimeJson.decodeSettings(raw) }.getOrDefault(WebSettings())

/**
 * 壳契约解码兜底：脏 JSON / 空列 → [ShellSpec.DEFAULT]（四区全关 = 裸 WebView）。
 *
 * 与入口 / WebSettings 同款策略：**一条坏数据不该让整个项目列表不可用**。
 */
private fun decodeShellSpecOrFallback(raw: String?): ShellSpec {
    if (raw.isNullOrBlank()) return ShellSpec.DEFAULT
    return runCatching { RuntimeJson.decodeShellSpec(raw) }.getOrDefault(ShellSpec.DEFAULT)
}
