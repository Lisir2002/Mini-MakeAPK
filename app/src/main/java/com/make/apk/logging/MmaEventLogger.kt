package com.make.apk.logging

/**
 * 业务事件埋点门面（W3）：**语义化、零阻断、未安装时空操作**。
 *
 * 与 [MmaLogger] 的分工：MmaLogger 是"怎么记"（级别 / 类别 / 脱敏 / 分发），
 * 本对象是"记什么"——把各业务域的事件名、类别、字段拼装约定**收口在一处**，
 * 调用方不再手写 `mapOf(...)` 与事件名字符串（改一处漏一处是聚合失效的根源）。
 *
 * 三条硬约束：
 * 1. **永不阻断业务**：内部全部走 [MmaLogger.log]，任何失败只返回 false，绝不抛异常；
 * 2. **未安装时空操作**：`MmaLogger.install` 之前所有调用直接返回 false，零副作用；
 * 3. **字段全量化**：可聚合维度走 [LogFields] 约定词表（target_id / success / duration_ms / reason）。
 */
object MmaEventLogger {

    /** 白屏根因四级判定（W3 ⑬，与 WebViewScreen 探测链一一对应）。 */
    enum class BlankCause {
        /** 未触发白屏（占位，保证枚举序稳定）。 */
        NONE,

        /** 探测链路自身失败（evaluateJavascript 回调 null / 抛异常）——不误诊。 */
        PROBE_FAILED,

        /** 语法能力探测不过 = 内核过旧（< Chrome 80），现代 SPA 硬门槛。 */
        ENGINE_TOO_OLD,

        /** 采集到 JS 错误（页面侧 `__mmaLastError` 或原生 console 缓存）。 */
        JS_ERROR,

        /** 有错误但归类不出（脚本被拦截 / 需登录等）。 */
        UNKNOWN,
    }

    // ── 通用 CRUD 审计 ────────────────────────────────────────────────

    /**
     * 通用操作审计：事件名 = `<domain>_<action>`（如 `project_create` / `workspace_restored`）。
     *
     * @param domain   领域短码（snake_case，如 `project` / `project_file` / `workspace`）。
     * @param action   动作短码（如 `create` / `delete` / `restored`）。
     * @param targetId 操作对象 id（项目 id 等；null 表示无明确对象）。
     * @param success  成败；false 时级别自动升为 WARN。
     * @param extra    领域私有字段（如 `file` / `section`），并入 [LogFields] 词表之外的命名空间。
     */
    fun crud(
        domain: String,
        action: String,
        targetId: Any? = null,
        success: Boolean? = null,
        extra: Map<String, Any?> = emptyMap(),
    ): Boolean {
        if (domain.isBlank() || action.isBlank()) return false
        return MmaLogger.log(
            level = if (success == false) LogLevel.WARN else LogLevel.INFO,
            category = LogCategory.OPERATION,
            event = "${domain.trim()}_${action.trim()}",
            fields = buildMap {
                targetId?.let { put("target_id", it) }
                success?.let { put(LogFields.SUCCESS, it) }
                putAll(extra)
            },
        )
    }

    /** 直发一条操作审计（事件名已由调用方按 `<domain>_<action>` 拼好的既有迁移场景）。 */
    fun operation(
        event: String,
        message: String? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): Boolean = MmaLogger.log(LogLevel.INFO, LogCategory.OPERATION, event, message, fields)

    // ── 预览 / 白屏诊断（PREVIEW，preview.log）────────────────────────

    /** 预览页加载完成（`preview_loaded`）：duration + success 双量化。 */
    fun previewLoaded(durationMs: Long, success: Boolean, extra: Map<String, Any?> = emptyMap()): Boolean =
        MmaLogger.log(
            level = LogLevel.INFO,
            category = LogCategory.PREVIEW,
            event = "preview_loaded",
            fields = extra + mapOf(
                LogFields.DURATION_MS to durationMs,
                LogFields.SUCCESS to success,
            ),
        )

    /** 白屏最终确认（`preview_blank_confirmed`）：两段式探测都为空。 */
    fun previewBlankConfirmed(pageUrlPresent: Boolean): Boolean =
        MmaLogger.log(
            level = LogLevel.WARN,
            category = LogCategory.PREVIEW,
            event = "preview_blank_confirmed",
            fields = mapOf("has_url" to pageUrlPresent),
        )

    /** 坏缓存自愈触发（`preview_self_heal`）；重载指令本身失败时 success=false。 */
    fun previewSelfHeal(reason: String, success: Boolean = true): Boolean =
        MmaLogger.log(
            level = LogLevel.WARN,
            category = LogCategory.PREVIEW,
            event = "preview_self_heal",
            fields = mapOf(
                LogFields.REASON to reason,
                LogFields.SUCCESS to success,
            ),
        )

    /** 白屏根因四级判定（`preview_blank_root_cause`，自愈后仍空才上报）。 */
    fun previewBlankRootCause(cause: BlankCause, detail: String? = null): Boolean =
        MmaLogger.log(
            level = LogLevel.ERROR,
            category = LogCategory.PREVIEW,
            event = "preview_blank_root_cause",
            message = detail,
            fields = mapOf(LogFields.REASON to cause.name),
        )

    /**
     * WebViewScreen 埋点的桥接入口（模板侧经共享钩子 `WebViewEventHook` 转发到这里，
     * 模板模块不引入 logging 包——Data.local 依赖被模板约束禁止）。
     * 级别按事件语义固定：根因判定 = ERROR（需人介入），加载完成 = INFO，其余 = WARN。
     */
    fun webViewEvent(event: String, fields: Map<String, Any?> = emptyMap()): Boolean {
        if (!event.startsWith("preview_")) return false
        val level = when (event) {
            "preview_blank_root_cause" -> LogLevel.ERROR
            "preview_loaded" -> LogLevel.INFO
            else -> LogLevel.WARN
        }
        return MmaLogger.log(level, LogCategory.PREVIEW, event, fields = fields)
    }

    // ── 设置 / 导出 ───────────────────────────────────────────────────

    /** 设置项变更（`setting_changed`）：key 必填，value 可空（开关类记布尔 / 文本类记长度语义由调用方定）。 */
    fun settingChanged(key: String, value: Any? = null): Boolean {
        if (key.isBlank()) return false
        return MmaLogger.log(
            level = LogLevel.INFO,
            category = LogCategory.OPERATION,
            event = "setting_changed",
            fields = buildMap {
                put("key", key.trim())
                value?.let { put("value", it) }
            },
        )
    }

    /** 导出登记（`export_run_recorded`）。 */
    fun exportRunRecorded(runId: Long, projectId: Long, status: String): Boolean =
        MmaLogger.log(
            level = LogLevel.INFO,
            category = LogCategory.OPERATION,
            event = "export_run_recorded",
            fields = mapOf(
                "run_id" to runId,
                "project_id" to projectId,
                "status" to status,
                LogFields.SUCCESS to true,
            ),
        )

    /** 导出状态迁移（`export_run_status`）：终态带 duration；失败带原因（经门面脱敏）。 */
    fun exportRunStatus(
        runId: Long,
        status: String,
        durationMs: Long? = null,
        success: Boolean? = null,
        reason: String? = null,
    ): Boolean = MmaLogger.log(
        level = when (success) {
            false -> LogLevel.WARN
            true -> LogLevel.INFO
            null -> LogLevel.INFO
        },
        category = LogCategory.OPERATION,
        event = "export_run_status",
        message = reason,
        fields = buildMap {
            put("run_id", runId)
            put("status", status)
            durationMs?.let { put(LogFields.DURATION_MS, it) }
            success?.let { put(LogFields.SUCCESS, it) }
        },
    )
}
