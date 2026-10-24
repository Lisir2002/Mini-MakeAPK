package com.make.apk.logging

import java.net.URI
import java.security.SecureRandom

/**
 * 日志脱敏：**在门面层强制实施**，而不是靠人自觉。
 *
 * 背景：日志属于"以电子方式记录的个人信息"（《个人信息保护法》第 4 条），
 * 落盘即产生存储与合规义务，因此：
 * - 敏感键（token / password / authorization …）**值整体掩码**；
 * - 个人标识键（phone / email / id_card / imei …）**值整体掩码**；
 * - 完整 URL 只保留“协议 + 域名”并追加掩码占位，避免查询串里的 token 与参数泄漏；
 * - 邮箱、9 位以上连续数字（卡号/手机号特征）就地掩码；
 * - 超长值截断，避免异常把整页用户输入写进日志。
 *
 * 注意"哈希不等于匿名化"：对邮箱/设备做稳定哈希仍可被字典还原并跨日志关联，
 * 因此本项目一律使用**不透明、临时**的 traceId（见 [TraceContext]）。
 */
object Sanitizer {

    internal const val MASK = "***"
    private const val MAX_VALUE_LENGTH = 512
    private const val MAX_STACK_LINES = 60

    private val SENSITIVE_KEY = Regex(
        "(?i)(token|password|passwd|secret|authorization|auth|cookie|otp|pin|credential|private[_ ]?key|session|signature|cert|salt|passphrase)",
    )
    private val PII_KEY = Regex(
        "(?i)(phone|mobile|email|mail|id[_ ]?card|real[_ ]?name|address|latitude|longitude|imei|mac|nickname)",
    )
    private val URL_KEY = Regex("(?i)(url|uri|link|href)")
    private val EMAIL_VALUE = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val URL_VALUE = Regex("(?i)\\bhttps?://\\S+")
    private val LONG_DIGITS = Regex("\\d{9,}")
    /**
     * `key=value` 形态的凭据：异常栈与第三方日志里最常见的泄漏形态
     * （如 `token=eyJhbGci...`、`Authorization=Bearer xxx`），
     * 键名本身保留以维持可排查性，值整体掩码。
     */
    private val KEY_VALUE_SECRET = Regex(
        "(?i)\\b(token|password|passwd|secret|authorization|auth|cookie|otp|pin|api[_ ]?key|access[_ ]?key|bearer)\\s*=\\s*([^\\s,;\"']+)",
    )

    /** 对整个字段表脱敏（键保留，值按规则处理）。 */
    fun sanitizeFields(fields: Map<String, Any?>): Map<String, Any?> =
        fields.entries.associate { (key, value) -> key to sanitizeValue(key, value) }

    /** 按"键名 + 值形态"双重判定脱敏。 */
    fun sanitizeValue(key: String, value: Any?): Any? = when {
        value == null -> null
        SENSITIVE_KEY.containsMatchIn(key) -> MASK
        PII_KEY.containsMatchIn(key) -> MASK
        value is Number -> value
        value is Boolean -> value
        URL_KEY.containsMatchIn(key) -> redactUrl(value.toString())
        else -> redactText(value.toString())
    }

    /** 人类可读消息脱敏。 */
    fun sanitizeMessage(message: String?): String? = message?.let { redactText(it) }

    /** 异常栈脱敏：截断行数 + 逐行清洗。栈里常带文件路径与参数。 */
    fun sanitizeStackTrace(throwable: Throwable): String =
        throwable.stackTraceToString()
            .lineSequence()
            .take(MAX_STACK_LINES)
            .joinToString("\n") { redactText(it) }

    /** 完整 URL → 只保留“协议 + 域名”并追加掩码占位（掩码常量见 [MASK]）。 */
    fun redactUrl(url: String): String = runCatching {
        val uri = URI(url)
        val host = uri.host ?: return MASK
        "${uri.scheme}://$host/$MASK"
    }.getOrDefault(MASK)

    private fun redactText(text: String): String {
        var out = EMAIL_VALUE.replace(text, MASK)
        out = URL_VALUE.replace(out) { match -> redactUrl(match.value) }
        out = KEY_VALUE_SECRET.replace(out) { match -> "${match.groupValues[1]}=$MASK" }
        out = LONG_DIGITS.replace(out, MASK)
        return if (out.length > MAX_VALUE_LENGTH) {
            out.take(MAX_VALUE_LENGTH) + "…(truncated)"
        } else {
            out
        }
    }

    /** 生成不透明随机 ID（8 字节 → 16 位十六进制）。 */
    internal fun randomId(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
