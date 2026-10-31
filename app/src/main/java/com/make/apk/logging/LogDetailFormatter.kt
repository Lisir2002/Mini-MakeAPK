package com.make.apk.logging

import com.make.apk.data.model.LogEntry
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志详情页的**纯文本格式化**（无 Compose、无 IO，可在 JVM 上直接单测）。
 *
 * 为什么单独抽出来：
 * 1. `fieldsJson` 由 [JsonLite] 生成，而 JsonLite 只有 encode——详情页要展示字段，
 *    必须在这里补一个"读"的方向；把这段逻辑放在 UI 里会让 Compose 可组合项带业务判断。
 * 2. "复制全部"的文本内容必须与界面完全一致，两处共用同一份格式化实现才不会漂移。
 *
 * 解析用 `org.json.JSONObject`（Android 内置，运行时零依赖）。注意它在**纯 JVM 单测**里
 * 默认未打桩会抛异常，故 [LogDetailFormatterTest] 跑在 Robolectric 下。
 * 本类的所有解析路径都吞异常：格式化失败最多丢掉一段文本，绝不允许成为崩溃源。
 */
object LogDetailFormatter {

    /** 详情页展示的完整时间格式（毫秒级，排障时需要精确到 ms）。 */
    private const val TIME_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS"

    /**
     * `fieldsJson`（[JsonLite] 产物）→ 有序 key/value 列表，用于详情页逐行展示。
     *
     * 三点约定：
     * - null / 空白 → 空列表（调用方据此整块隐藏）；
     * - 解析失败 → 单条 `("raw", 原文)`，让用户仍能看到原始内容而不是"字段区凭空消失"；
     * - **按 key 字典序排序**：`JSONObject.keys()` 的顺序不稳定（底层 HashMap），
     *   不排序则界面顺序随机、快照与断言都不可预期。
     *
     * 值的呈现：`JSONObject.NULL` → "null"；嵌套对象/数组 → `toString()` 原样（已是合法 JSON）。
     */
    fun formatFields(fieldsJson: String?): List<Pair<String, String>> {
        val raw = fieldsJson?.takeIf { it.isNotBlank() } ?: return emptyList()
        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: return listOf(RAW_KEY to raw)

        val keys = runCatching { json.keys().asSequence().toList() }.getOrNull()
            ?: return listOf(RAW_KEY to raw)

        return keys.sorted().map { key -> key to renderValue(json.opt(key)) }
    }

    /**
     * 整条日志的完整文本（"复制全部"用）。
     *
     * 必须**完整**包含：完整时间戳、级别、类别、事件、消息（不截断）、全部结构化字段、
     * 异常栈（完整）、以及 traceId / sessionId / sequence / threadName / durationMs / success。
     * 为空的可选段落整段省略，不留"stackTrace=null"这类噪音。
     */
    fun fullText(entry: LogEntry): String = buildString {
        append("时间: ").append(formatTimestamp(entry.timestampMs)).append('\n')
        append("级别: ").append(entry.level)
        entry.levelEnum?.let { append("（").append(it.label).append('）') }
        append('\n')
        append("类别: ").append(entry.category)
        entry.categoryEnum?.let { category ->
            append("（").append(category.label).append(" / ").append(category.group.label).append('）')
        }
        append('\n')
        append("事件: ").append(entry.event).append('\n')

        entry.message?.takeIf { it.isNotBlank() }?.let { message ->
            append("消息: ").append(message).append('\n')
        }

        val fields = formatFields(entry.fieldsJson)
        if (fields.isNotEmpty()) {
            append("字段:").append('\n')
            fields.forEach { (key, value) -> append("  ").append(key).append(": ").append(value).append('\n') }
        }

        entry.stackTrace?.takeIf { it.isNotBlank() }?.let { stack ->
            append("异常栈:").append('\n').append(stack).append('\n')
        }

        append("traceId: ").append(entry.traceId).append('\n')
        append("sessionId: ").append(entry.sessionId).append('\n')
        append("sequence: ").append(entry.sequence).append('\n')
        append("threadName: ").append(entry.threadName).append('\n')
        entry.durationMs?.let { append("durationMs: ").append(it).append('\n') }
        entry.success?.let { append("success: ").append(it).append('\n') }
        append("id: ").append(entry.id)
    }

    /** 完整时间戳（详情页与复制文本共用同一格式）。 */
    fun formatTimestamp(timestampMs: Long): String =
        SimpleDateFormat(TIME_PATTERN, Locale.getDefault()).format(Date(timestampMs))

    /** 解析失败时的兜底 key（界面按"原文"展示）。 */
    internal const val RAW_KEY: String = "raw"

    /** `org.json` 的 value 渲染：`JSONObject.NULL` 归一为 "null"，其余 `toString()`。 */
    private fun renderValue(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        else -> value.toString()
    }
}
