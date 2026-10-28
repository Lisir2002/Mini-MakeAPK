package com.make.apk.data.model

/**
 * 导出运行（每次导出一条，见 §5 / §8.2）。
 *
 * 承接旧构建记录的运行态职责：状态机、产物路径、耗时、错误信息。
 * 与 [Project] 的关系是 **N:1**（一个项目可多次导出）。
 *
 * 相比旧模型移除了 `engine` 字段——导出统一走**本地注入**（设备端 WebView 封装 /
 * 资产注入模板壳 + 本地重签），不存在引擎分支（见 §11）。
 */
data class ExportRun(
    /** 主键，0 表示尚未落库。 */
    val id: Long = 0,
    /** 所属项目 id（外键 → `projects.id`）。 */
    val projectId: Long,
    /** 运行状态。 */
    val status: ExportStatus,
    /** 产物 APK 路径（成功时）。 */
    val outputApkPath: String? = null,
    /** 产物大小（字节，成功时）。 */
    val apkSizeBytes: Long? = null,
    /** 耗时（毫秒，终态时写入）。 */
    val durationMs: Long? = null,
    /** 失败原因（失败时）。 */
    val errorMessage: String? = null,
    /** 创建时间（epoch ms）。 */
    val createdAt: Long,
    /** 最后更新时间（epoch ms）。 */
    val updatedAt: Long,
)

/**
 * 导出运行状态。
 *
 * 命名与旧 `BuildStatus` 保持一致（`QUEUED` / `RUNNING` / `SUCCESS` / `FAILED` /
 * `CANCELLED`），因此 v2 → v3 迁移时状态文本可**原样搬运**，无需映射表。
 */
enum class ExportStatus {
    /** 已登记，等待执行。 */
    QUEUED,

    /** 执行中。 */
    RUNNING,

    /** 成功（产物已就绪）。 */
    SUCCESS,

    /** 失败（见 [ExportRun.errorMessage]）。 */
    FAILED,

    /** 已取消。 */
    CANCELLED,
    ;

    /**
     * 是否为终态。
     *
     * 终态守卫的依据：迟到的工作协程**不得**覆盖已定的终态（如已被取消）。
     */
    val isTerminal: Boolean
        get() = this == SUCCESS || this == FAILED || this == CANCELLED
}
