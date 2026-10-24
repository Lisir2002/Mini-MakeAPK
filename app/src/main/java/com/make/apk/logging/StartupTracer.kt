package com.make.apk.logging

import android.os.SystemClock

/**
 * 启动耗时追踪：**冷/温/热分型 + 分阶段耗时**。
 *
 * 为什么用 [SystemClock.elapsedRealtime] 而非 `currentTimeMillis`：
 * 后者会受用户改时间、NTP 校时影响，可能得到负数耗时。
 *
 * 阶段建议：attach_base_context → application_oncreate → first_activity → first_frame。
 * 终点以"首帧绘制完成"为准（`Activity.reportFullyDrawn()`），
 * 这与用户主观感受一致，也是官方推荐的测量口径。
 */
object StartupTracer {

    private val lock = Any()
    private val phases = LinkedHashMap<String, Long>()

    @Volatile
    private var startedAtElapsed = 0L

    @Volatile
    private var completed = false

    /** 记录启动起点，应在 `attachBaseContext()` 调用。 */
    fun begin() {
        synchronized(lock) {
            startedAtElapsed = SystemClock.elapsedRealtime()
            phases.clear()
            completed = false
        }
    }

    /** 记录一个阶段（起点到现在的耗时）。 */
    fun mark(phase: String) {
        val origin = startedAtElapsed
        if (origin == 0L) return
        synchronized(lock) {
            phases[phase] = SystemClock.elapsedRealtime() - origin
        }
    }

    /**
     * 启动完成，落一条 [LogCategory.STARTUP] 日志。
     *
     * @param startType cold / warm / hot；由调用方根据进程是否新建判定
     */
    fun complete(startType: String, extraFields: Map<String, Any?> = emptyMap()): Boolean {
        val origin = startedAtElapsed
        if (origin == 0L) return false
        val total: Long
        val snapshot: Map<String, Long>
        synchronized(lock) {
            if (completed) return false
            completed = true
            total = SystemClock.elapsedRealtime() - origin
            snapshot = phases.toMap()
        }

        val fields = buildMap {
            put(LogFields.START_TYPE, startType)
            put(LogFields.DURATION_MS, total)
            put(LogFields.SUCCESS, true)
            // 各阶段耗时单独成字段，便于定位是哪一段拖慢了启动
            snapshot.forEach { (phase, elapsed) -> put("phase_$phase", elapsed) }
            putAll(extraFields)
        }
        return MmaLogger.startup("app_started", "启动完成（$startType）", fields)
    }
}
