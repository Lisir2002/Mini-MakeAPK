package com.make.apk.logging

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 会话日志目录策略（W3 ⑦，**纯 JVM 逻辑，无 Android 依赖**）。
 *
 * 目录布局（两级）：
 * ```
 * <日志根>/yyyy-MM-dd/session-HHmmss_<sessionId前4位hex>/
 * ├── operations.log   # OPERATION + SECURITY
 * ├── preview.log      # PREVIEW
 * ├── app.log          # 其余类别
 * └── app.restored-<ts>.log  # DB 回灌（见 MmaApplication）
 * ```
 *
 * 命名安全铁律（本类是唯一产出目录名的地方）：
 * - **不得含 `:` / `/`（段内）/ 空格**——时间戳用 `yyyyMMdd_HHmmss`（下划线分隔），
 *   会话短码只保留十六进制字符；
 * - 同一秒内多个进程会话（理论上可能：多进程 / 快速重启）靠 sessionId 短码去重，
 *   不依赖毫秒抖动；
 * - 会话**跨午夜归属启动日**：日期取会话启动时刻，之后不再切目录（主控拍板）。
 */
object SessionLogPolicy {

    /** 日期目录名格式（目录层级第一级）。 */
    private const val DATE_DIR_PATTERN = "yyyy-MM-dd"

    /** 会话目录名内的时刻段（无冒号——文件名安全铁律）。 */
    private const val TIME_PATTERN = "HHmmss"

    /** 日期目录名合法形态（整段匹配，防 `2026-10-28备份` 之类混入）。 */
    private val DATE_DIR_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")

    /**
     * 是否为合法日期目录名（**双重校验**）：形状必须严格 `yyyy-MM-dd`，
     * 且必须是真实存在的日历日期（`2026-13-99` 这类"形似"名判非法，
     * 配合过期清理的「非法名永不删除」——用户自建目录绝无被误删的可能）。
     */
    fun isValidDateDirName(name: String): Boolean {
        if (!DATE_DIR_REGEX.matches(name)) return false
        return runCatching {
            val format = SimpleDateFormat(DATE_DIR_PATTERN, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }
            format.parse(name) != null
        }.getOrDefault(false)
    }

    /**
     * 生成会话目录名（相对日志根，含日期层级）。
     *
     * @param timestampMs 会话启动时刻（epoch ms；**跨午夜不切目录**——启动日归属）。
     * @param sessionId   进程会话 ID（[TraceContext.sessionId]，不透明字符串）；
     *   只保留前 4 个十六进制字符作短码（不足补 `0`，无合法字符则 `0000`），
     *   保证目录名永不出现 `:` / `/` / 空格。
     */
    fun sessionDirName(timestampMs: Long, sessionId: String): String {
        val date = SimpleDateFormat(DATE_DIR_PATTERN, Locale.US).format(Date(timestampMs))
        val time = SimpleDateFormat(TIME_PATTERN, Locale.US).format(Date(timestampMs))
        val shortCode = sessionId
            .lowercase(Locale.US)
            .filter { it in '0'..'9' || it in 'a'..'f' }
            .take(4)
            .padEnd(4, '0')
            .ifEmpty { "0000" }
        return "$date/session-$time" + "_" + shortCode
    }

    /**
     * 计算过期日期目录名。
     *
     * 只返回**同时满足**两条件的目录名：
     * 1. [isValidDateDirName] 为 true（用户自建的任意目录永不入选，绝不误删）；
     * 2. 目录日期早于 `today - retentionDays`（当天与保留窗口内的都保留）。
     *
     * @param existing      候选目录名列表（不必预先过滤）
     * @param today         本地时区的今天（`yyyy-MM-dd` 字符串，由调用方计算）
     * @param retentionDays 保留天数；≤0 表示全部过期（仅限合法日期名）
     * @return 应删除的目录名（升序），**原样返回名称**，不构造 File。
     */
    fun expiredDateDirs(existing: List<String>, today: String, retentionDays: Int): List<String> {
        val threshold = shiftDate(today, days = -retentionDays.toLong()) ?: return emptyList()
        return existing
            .filter { isValidDateDirName(it) && it < threshold }
            .sorted()
    }

    /**
     * 日期字符串加减天数（UTC 语义下的纯算术：`yyyy-MM-dd` 字典序即时间序，
     * 结果仍为 `yyyy-MM-dd`）。解析失败返回 null（调用方据此放弃整批清理，绝不瞎删）。
     */
    private fun shiftDate(date: String, days: Long): String? = runCatching {
        val parser = SimpleDateFormat(DATE_DIR_PATTERN, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
        val parsed = parser.parse(date) ?: return@runCatching null
        // Calendar 必须在本函数内一次性取用（避免共享实例的 time/add 分离竞态）。
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)
        calendar.time = parsed
        calendar.add(Calendar.DAY_OF_MONTH, days.toInt())
        SimpleDateFormat(DATE_DIR_PATTERN, Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(calendar.time)
    }.getOrNull()
}
