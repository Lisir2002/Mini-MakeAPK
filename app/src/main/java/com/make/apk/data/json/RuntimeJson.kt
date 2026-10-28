package com.make.apk.data.json

import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.WebRuntimeConfig
import com.make.apk.data.model.WebSettings
import kotlinx.serialization.json.Json

/**
 * 统一内核 JSON 契约编解码器（§2.5 / §11.4）。
 *
 * **同源同序列化器**是本设计的核心约束：本地 WebView 渲染与导出模板壳
 * `assets/runtime-config.json` 必须读同一份结构，故编解码器必须全局唯一、
 * 不得各处 `Json { }` 各建一份（配置漂移 = 壳读不懂配置）。
 *
 * 宽容策略（`ignoreUnknownKeys = true`）：模板壳升级后新增字段时，
 * 旧版壳读到新配置不应崩溃——向前兼容优先于严格校验。
 */
object RuntimeJson {

    /** 全局唯一 JSON 实例（线程安全，可跨线程共享）。 */
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    /**
     * 序列化入口 [ProjectEntry]（密封接口，含 `type` 类判别符）。
     *
     * 判别符取值来自各子类型的 `@SerialName`（`url` / `html` / `archive`），
     * 与包名解耦：重构包路径不会让已导出的配置文件失效。
     */
    fun encodeEntry(entry: ProjectEntry): String =
        json.encodeToString(ProjectEntry.serializer(), entry)

    /**
     * 反序列化入口 [ProjectEntry]。
     *
     * @throws IllegalStateException JSON 非法或结构不匹配时抛出——
     *   调用方（仓储 / 迁移）负责兜底，避免脏数据被静默当成有效配置。
     */
    fun decodeEntry(raw: String): ProjectEntry =
        json.decodeFromString(ProjectEntry.serializer(), raw)

    /** 序列化 [WebSettings]。 */
    fun encodeSettings(settings: WebSettings): String =
        json.encodeToString(WebSettings.serializer(), settings)

    /** 反序列化 [WebSettings]。 */
    fun decodeSettings(raw: String): WebSettings =
        json.decodeFromString(WebSettings.serializer(), raw)

    /**
     * 序列化统一内核共享契约 [WebRuntimeConfig]——导出模板壳读的就是它。
     */
    fun encodeRuntimeConfig(config: WebRuntimeConfig): String =
        json.encodeToString(WebRuntimeConfig.serializer(), config)

    /** 反序列化统一内核共享契约 [WebRuntimeConfig]。 */
    fun decodeRuntimeConfig(raw: String): WebRuntimeConfig =
        json.decodeFromString(WebRuntimeConfig.serializer(), raw)

    /**
     * 序列化壳契约 [ShellSpec]（L2）。
     *
     * 与 L1 同源同序列化器：导出侧 `assets/shell-spec.json` 与本地 DB 存的是同一份结构。
     */
    fun encodeShellSpec(spec: ShellSpec): String =
        json.encodeToString(ShellSpec.serializer(), spec)

    /**
     * 反序列化壳契约 [ShellSpec]。
     *
     * 动作模型走容错序列化器（[com.make.apk.data.model.ShellActionSurrogate]），
     * 未知 `type` 降级为 Unknown 而**不抛异常**。
     *
     * @throws IllegalStateException JSON 非法或结构不匹配时抛出——调用方负责兜底。
     */
    fun decodeShellSpec(raw: String): ShellSpec =
        json.decodeFromString(ShellSpec.serializer(), raw)
}
