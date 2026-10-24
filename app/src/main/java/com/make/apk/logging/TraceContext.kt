package com.make.apk.logging

import java.util.concurrent.atomic.AtomicLong

/**
 * 追踪上下文：**关联，而非身份**。
 *
 * 提供两类不透明 ID：
 * - [sessionId]：进程内唯一，本次进程会话的标识（进程重启即变）。
 * - traceId：单次操作链路的标识，存于 ThreadLocal，随调用链传递。
 *
 * 硬性要求：这些 ID **不是**账号 ID、不是设备哈希、不含任何可反推用户的信息，
 * 且有界生命周期（进程级），避免日志成为第二份敏感数据库。
 */
object TraceContext {

    /** 进程内唯一的会话 ID（不透明）。 */
    val sessionId: String = Sanitizer.randomId()

    private val sequence = AtomicLong(0)

    /**
     * 用 `@PublishedApi internal`：[withNewTrace] 是 public inline 函数，
     * 而 Kotlin 禁止 public inline 访问 private 成员（inline 体会被内联到调用方）。
     */
    @PublishedApi
    internal val currentTrace = ThreadLocal<String>()

    /** 当前线程的 traceId；未显式开启时回落到 [sessionId]。 */
    fun currentTraceId(): String = currentTrace.get() ?: sessionId

    /** 生成新的 traceId（不透明、临时）。 */
    fun newTraceId(): String = Sanitizer.randomId()

    /** 进程内单调递增序号，用于排序与上报去重。 */
    fun nextSequence(): Long = sequence.incrementAndGet()

    /** 在当前线程开启一条新链路，返回其 traceId。 */
    fun beginTrace(): String = newTraceId().also { currentTrace.set(it) }

    /** 结束当前线程的链路。 */
    fun endTrace() = currentTrace.remove()

    /** 在独立 traceId 下执行 [block]，结束后恢复原值（可嵌套）。 */
    inline fun <T> withNewTrace(block: () -> T): T {
        val previous = currentTrace.get()
        currentTrace.set(newTraceId())
        return try {
            block()
        } finally {
            if (previous == null) currentTrace.remove() else currentTrace.set(previous)
        }
    }
}
