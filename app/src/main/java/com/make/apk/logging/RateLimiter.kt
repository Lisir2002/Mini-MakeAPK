package com.make.apk.logging

import java.util.concurrent.ConcurrentHashMap

/**
 * 按事件名的滑动窗口限流：**重复日志是日志系统最常见的自我 DoS**。
 *
 * 逐帧、逐列表项、重试循环里的日志必须被聚合或限流，否则既拖慢 App，
 * 又让真正的错误淹没在噪声里。超出配额的事件直接丢弃（不排队、不补偿写）。
 *
 * @param windowMs 统计窗口
 * @param maxPerWindow 窗口内同一 key 允许通过的最大条数
 * @param clock 可注入时钟，便于单元测试确定性断言
 */
internal class RateLimiter(
    private val windowMs: Long,
    private val maxPerWindow: Int,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    // 每个 key 一个槽：[窗口起始时间, 计数]
    private val slots = ConcurrentHashMap<String, LongArray>()

    /** 是否允许该 key 本次通过；通过即计数 +1。 */
    fun allow(key: String): Boolean {
        val now = clock()
        val slot = slots.computeIfAbsent(key) { longArrayOf(now, 0L) }
        synchronized(slot) {
            if (now - slot[0] >= windowMs) {
                slot[0] = now
                slot[1] = 0L
            }
            return if (slot[1] < maxPerWindow) {
                slot[1]++
                true
            } else {
                false
            }
        }
    }

    /** 已丢弃的条数（用于自检日志，避免静默丢日志）。 */
    fun reset() = slots.clear()
}
