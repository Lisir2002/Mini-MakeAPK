package com.make.apk.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * [ShellAction] 的**容错**序列化器。
 *
 * ## 存在理由
 *
 * kotlinx 的多态解码遇到未知 `type` 判别符会抛 `SerializationException`；
 * 壳配置是要写进 DB 与将来 APK assets 的**长期数据**，老壳读到新动作类型必须
 * "降级不崩"（与 [com.make.apk.data.json.RuntimeJson] 的宽容策略同源）。
 *
 * ## 策略
 *
 * - 编码：手写 JSON 对象（`type` + 载荷字段），字段名与各子类型的 `@SerialName` 对齐；
 * - 解码：读 `type` 判别符 → 命中已知类型则取对应字段；
 *   **未知 `type` / 必需字段缺失 / 字段类型不符 → 一律降级为 [ShellAction.Unknown]**，
 *   绝不把异常抛给上层。
 *
 * ## 类型判据（"类型不符 → Unknown"）
 *
 * JSON 的**类型**由字面量形态决定，与"内容长得像不像"无关：
 *
 * | 字段 | 声明类型 | 接受 | 降级 [ShellAction.Unknown] |
 * |---|---|---|---|
 * | `type` | 字符串（判别符，**必需**） | JSON 字符串 | 数字 / 布尔 / `null` / 对象 / 数组 / 缺失 |
 * | `url` | 字符串（**必需**） | JSON 字符串（含**空串**） | 数字 / 布尔 / `null` / 对象 / 数组 / 缺失 |
 * | `path` | 字符串（**必需**，`local_page` 用） | JSON 字符串（含**空串**） | 数字 / 布尔 / `null` / 对象 / 数组 / 缺失 |
 * | `openInNewTab` | 布尔（**可选**，缺省 `false`） | JSON 布尔 | 存在但类型不符（数字 / 字符串 / `null` / 对象 / 数组） |
 * | `kind` | 字符串枚举（**必需**） | JSON 字符串 | 数字 / 布尔 / `null` / 对象 / 数组 / 缺失 |
 * | `pageId` | 字符串（**必需**） | JSON 字符串 | 数字 / 布尔 / `null` / 对象 / 数组 / 缺失 |
 *
 * 三条易错边界：
 *
 * 1. **数值不得被静默转成字符串**——`{"type":"url","url":123}` 不是合法 URL，
 *    转成 `"123"` 会造出一个**可执行**的 [ShellAction.NavigateUrl]（预览时真会被加载），
 *    容错降级反而制造脏数据，故必须降级为 [ShellAction.Unknown]；
 * 2. **空串是合法值**——`""` 是 [ShellMenuItem.action] 的默认值（"合法的待填 URL"），
 *    往返不得变形，故与"类型不符"严格区分开；
 * 3. **枚举兜底位只覆盖"取值未登记"**——`kind` 是 JSON 字符串但取值不在
 *    [ShellNativeAction] 里（如 `"TELEPORT"`）→ [ShellAction.Native] +
 *    [ShellNativeAction.UNKNOWN]（枚举登记了兜底位：这是"取值不认识"，不是"契约破损"）；
 *    而 `kind` **缺失**或**类型不符**属于契约破损，降级为 [ShellAction.Unknown]。
 *
 * ## 与 kotlinx 多态序列化的差异
 *
 * 本序列化器**不依赖** `ShellAction.serializer()`（那会把"判别符由插件生成"与本文件耦合），
 * 而是自行构造 JSON 对象，故未知 `type` 的降级路径完全可控且可测。
 *
 * ## 向后兼容（新增 `local_page`）
 *
 * 新增判别符**只新增分支**，不改动 `url` 分支的任何判据：
 * 老数据 `{"type":"url","url":...}` 的解码结果逐字段不变（含空串 URL 可往返）；
 * 老壳读到 `local_page`（未知 type）走既有 `else -> Unknown` 降级，**不抛异常**。
 */
object ShellActionSurrogate : KSerializer<ShellAction> {

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.make.apk.data.model.ShellAction")

    /** 判别符：打开网页。 */
    private const val TYPE_URL = "url"

    /** 判别符：打开项目内的本地页面。 */
    private const val TYPE_LOCAL_PAGE = "local_page"

    /** 判别符：原生动作。 */
    private const val TYPE_NATIVE = "native"

    /** 判别符：壳内置原生页。 */
    private const val TYPE_INTERNAL_PAGE = "internal_page"

    /** 判别符：未知动作（兜底）。 */
    private const val TYPE_UNKNOWN = "unknown"

    override fun serialize(encoder: Encoder, value: ShellAction) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("ShellActionSurrogate 仅支持 JSON 编码器")
        val payload = when (value) {
            is ShellAction.NavigateUrl -> buildJsonObject {
                put("type", TYPE_URL)
                put("url", value.url)
                put("openInNewTab", value.openInNewTab)
            }

            is ShellAction.OpenLocalPage -> buildJsonObject {
                put("type", TYPE_LOCAL_PAGE)
                put("path", value.path)
            }

            is ShellAction.Native -> buildJsonObject {
                put("type", TYPE_NATIVE)
                put("kind", value.kind.name)
            }

            is ShellAction.InternalPage -> buildJsonObject {
                put("type", TYPE_INTERNAL_PAGE)
                put("pageId", value.pageId)
            }

            ShellAction.Unknown -> buildJsonObject {
                put("type", TYPE_UNKNOWN)
            }
        }
        jsonEncoder.encodeJsonElement(payload)
    }

    override fun deserialize(decoder: Decoder): ShellAction {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return ShellAction.Unknown
        val obj = runCatching { jsonDecoder.decodeJsonElement().jsonObject }
            .getOrNull() ?: return ShellAction.Unknown
        return obj.toShellActionOrUnknown()
    }

    /** 把 JSON 对象解析为动作；任何异常路径都降级为 [ShellAction.Unknown]。 */
    private fun JsonObject.toShellActionOrUnknown(): ShellAction {
        // 判别符必须是 JSON 字符串：数字 123 / 布尔 true / null 都不是合法判别符。
        val type = stringField(TYPE_KEY) ?: return ShellAction.Unknown
        return when (type) {
            TYPE_URL -> toNavigateUrlOrUnknown()
            TYPE_LOCAL_PAGE -> toLocalPageOrUnknown()
            TYPE_NATIVE -> toNativeOrUnknown()
            TYPE_INTERNAL_PAGE -> toInternalPageOrUnknown()
            else -> ShellAction.Unknown
        }
    }

    /**
     * `type = "url"` → [ShellAction.NavigateUrl]。
     *
     * - `url` **必需**：缺失 → Unknown；类型不符（数字 / 布尔 / `null` / 对象 / 数组）→ Unknown；
     * - `url` 为**空串**是合法的"待填 URL"，原样保留（可往返）；
     * - `openInNewTab` **可选**：缺失 → `false`；存在但类型不符 → Unknown。
     */
    private fun JsonObject.toNavigateUrlOrUnknown(): ShellAction {
        val url = stringField(URL_KEY) ?: return ShellAction.Unknown
        val openInNewTab = if (containsKey(OPEN_IN_NEW_TAB_KEY)) {
            booleanField(OPEN_IN_NEW_TAB_KEY) ?: return ShellAction.Unknown
        } else {
            false
        }
        return ShellAction.NavigateUrl(url = url, openInNewTab = openInNewTab)
    }

    /**
     * `type = "local_page"` → [ShellAction.OpenLocalPage]。
     *
     * - `path` **必需**：缺失 → Unknown；类型不符（数字 / 布尔 / `null` / 对象 / 数组）→ Unknown；
     * - `path` 原样保留（**不做任何 URL 规范化**）——本地页面是项目文件，不是网址；
     * - 空串按"类型合法"接受（与 `url` 同一判据），语义为空由消费侧忽略。
     */
    private fun JsonObject.toLocalPageOrUnknown(): ShellAction {
        val path = stringField(PATH_KEY) ?: return ShellAction.Unknown
        return ShellAction.OpenLocalPage(path = path)
    }

    /**
     * `type = "native"` → [ShellAction.Native]。
     *
     * - `kind` **必需**：缺失 → [ShellAction.Unknown]（设计文档 §1.2.3「已知类型但字段缺失 → 降级 Unknown」）；
     * - `kind` **类型不符**（数字 / 布尔 / `null` / 对象 / 数组）→ [ShellAction.Unknown]
     *   （绝不把数字静默转成字符串再交给枚举查找）；
     * - `kind` 是 JSON 字符串但**取值未登记** → [ShellNativeAction.UNKNOWN]
     *   （枚举登记了兜底位：这是"取值不认识"，不是"契约破损"，故不抛异常、也不整条丢弃）。
     */
    private fun JsonObject.toNativeOrUnknown(): ShellAction {
        val kindName = stringField(KIND_KEY) ?: return ShellAction.Unknown
        val kind = runCatching { ShellNativeAction.valueOf(kindName) }
            .getOrNull() ?: ShellNativeAction.UNKNOWN
        return ShellAction.Native(kind = kind)
    }

    /**
     * `type = "internal_page"` → [ShellAction.InternalPage]。
     *
     * - `pageId` **必需**：缺失 → Unknown；类型不符 → Unknown。
     */
    private fun JsonObject.toInternalPageOrUnknown(): ShellAction {
        val pageId = stringField(PAGE_ID_KEY) ?: return ShellAction.Unknown
        return ShellAction.InternalPage(pageId = pageId)
    }

    // ── 字段读取助手（严格按 JSON 类型判定）─────────────────────────────

    /**
     * 读取一个**必须是 JSON 字符串**的字段。
     *
     * 只有 `JsonPrimitive.isString == true`（即带引号的字符串字面量）才被接受；
     * 数字 / 布尔 / `null` / 对象 / 数组一律返回 `null`（= 类型不符），
     * **绝不**把它们的内容当成字符串取用。
     */
    private fun JsonObject.stringField(key: String): String? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (!primitive.isString) return null
        return primitive.content
    }

    /**
     * 读取一个**必须是 JSON 布尔**的字段。
     *
     * JSON 字符串 `"true"` 与 JSON 布尔 `true` 是**两种类型**，故字符串一律拒绝
     * （`booleanOrNull` 会把 `"true"` 也解析成 `true`，必须先用 [JsonPrimitive.isString] 拦掉）；
     * 数字同理（`1` 不是布尔）。
     */
    private fun JsonObject.booleanField(key: String): Boolean? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return primitive.booleanOrNull
    }

    /** 判别符字段名。 */
    private const val TYPE_KEY: String = "type"

    /** URL 字段名。 */
    private const val URL_KEY: String = "url"

    /** 本地页面字段名（项目内页面文件名）。 */
    private const val PATH_KEY: String = "path"

    /** 新标签页开关字段名。 */
    private const val OPEN_IN_NEW_TAB_KEY: String = "openInNewTab"

    /** 原生动作种类字段名。 */
    private const val KIND_KEY: String = "kind"

    /** 内置页标识字段名。 */
    private const val PAGE_ID_KEY: String = "pageId"
}
