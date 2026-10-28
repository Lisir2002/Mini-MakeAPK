package com.make.apk.logging

/**
 * 结构化字段的**约定词表**（全量化的词汇基础）。
 *
 * 为什么要固定词表：结构化日志的价值在于可聚合。若同一含义一会儿叫 `duration`
 * 一会儿叫 `cost_ms`，半年后的查询就会失效。新增量化维度请在此登记并写清单位。
 *
 * 其中 [DURATION_MS] 与 [SUCCESS] 会被提升为数据库独立列，便于 SQL 直接聚合。
 */
object LogFields {

    /** 耗时（毫秒）。 */
    const val DURATION_MS = "duration_ms"

    /** 是否成功。 */
    const val SUCCESS = "success"

    /** 重试/尝试次数。 */
    const val ATTEMPT = "attempt"

    /** 数据大小（字节）。 */
    const val SIZE_BYTES = "size_bytes"

    /** 条数。 */
    const val COUNT = "count"

    /** 接口端点（只记路径，不记完整 URL 与查询串）。 */
    const val ENDPOINT = "endpoint"

    /** HTTP 状态码。 */
    const val STATUS_CODE = "status_code"

    /** 业务/错误码。 */
    const val ERROR_CODE = "error_code"

    /** 失败原因（枚举式短码，不是原始异常文本）。 */
    const val REASON = "reason"

    /** 启动类型：cold / warm / hot。 */
    const val START_TYPE = "start_type"

    /** 阶段名（启动/构建分段耗时用）。 */
    const val PHASE = "phase"

    /** 采样率（0.0–1.0），用于还原真实量级。 */
    const val SAMPLE_RATE = "sample_rate"

    // ── 设备 / 应用上下文（仅设备类别信息，不含任何可识别标识）───────────

    /** 应用版本名（如 0.0.0.1-rc5）。 */
    const val APP_VERSION = "app_version"

    /** 应用版本号（versionCode，如 8）。 */
    const val APP_VERSION_CODE = "app_version_code"

    /** 构建类型（debug / release）。 */
    const val BUILD_TYPE = "build_type"

    /** 设备型号（如 Pixel_7）。 */
    const val DEVICE_MODEL = "device_model"

    /** 设备厂商（如 Google）。 */
    const val DEVICE_MANUFACTURER = "device_manufacturer"

    /** 系统版本号（如 14）。 */
    const val OS_VERSION = "os_version"

    /** 系统 SDK 级别（如 34）。 */
    const val OS_SDK = "os_sdk"
}
