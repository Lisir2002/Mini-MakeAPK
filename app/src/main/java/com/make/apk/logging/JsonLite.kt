package com.make.apk.logging

/**
 * 极简 JSON 序列化（仅用于把结构化字段落成一行文本 / 存入数据库）。
 *
 * 之所以不直接用 `org.json`：`org.json` 在纯 JVM 单元测试里默认"未打桩"会抛异常，
 * 而本项目的日志层要求**可在 JVM 上测试**。自实现仅几十行，且无反射、无依赖。
 */
internal object JsonLite {

    fun encode(fields: Map<String, Any?>): String = buildString {
        append('{')
        var first = true
        for ((key, value) in fields) {
            if (!first) append(',')
            first = false
            append(escape(key))
            append(':')
            append(encodeValue(value))
        }
        append('}')
    }

    private fun encodeValue(value: Any?): String = when (value) {
        null -> "null"
        is Number -> value.toString()
        is Boolean -> value.toString()
        else -> escape(value.toString())
    }

    private fun escape(text: String): String = buildString(text.length + 8) {
        append('"')
        for (ch in text) {
            when {
                ch == '"' -> append("\\\"")
                ch == '\\' -> append("\\\\")
                ch == '\n' -> append("\\n")
                ch == '\r' -> append("\\r")
                ch == '\t' -> append("\\t")
                ch < ' ' -> append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                else -> append(ch)
            }
        }
        append('"')
    }
}
