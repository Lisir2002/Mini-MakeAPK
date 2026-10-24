package com.make.apk.logging

/**
 * 日志类别（**全类别覆盖**，不只 bug）。
 *
 * 每种类别的载体与保留期不同，见 `docs/logging.md` §3：
 * 高价值结构化类别落加密数据库（可查），高频诊断类别落轮转文件，
 * 调试类别只进 Logcat 且不落盘。
 */
enum class LogCategory(
    /** 是否落加密数据库（Room + SQLCipher）。 */
    val persistToDatabase: Boolean,
    /** 是否落轮转文件。 */
    val persistToFile: Boolean,
    /** 默认保留天数；0 表示不落盘。 */
    val retentionDays: Int,
) {
    /** 崩溃 / ANR：栈、主线程状态。 */
    CRASH(persistToDatabase = true, persistToFile = true, retentionDays = 90),

    /** 已捕获异常、操作失败。 */
    ERROR(persistToDatabase = true, persistToFile = true, retentionDays = 90),

    /** 操作 / 审计：用户做了什么（启动构建、导出、删除历史、权限、设置）。合规要求 ≥180 天。 */
    OPERATION(persistToDatabase = true, persistToFile = false, retentionDays = 180),

    /** 启动：冷/温/热型 + 各阶段耗时 + 首帧。 */
    STARTUP(persistToDatabase = true, persistToFile = true, retentionDays = 90),

    /** 性能 / Trace：耗时、慢查询、采样后的函数级埋点。 */
    PERFORMANCE(persistToDatabase = true, persistToFile = false, retentionDays = 30),

    /** 网络：端点 / 状态码 / 耗时 / 重试次数。**不记 body 与 token**。 */
    NETWORK(persistToDatabase = true, persistToFile = false, retentionDays = 30),

    /** 生命周期：前后台切换、Activity 状态。量大，不落盘。 */
    LIFECYCLE(persistToDatabase = false, persistToFile = false, retentionDays = 0),

    /** 调试 / 逐帧诊断。仅 debug，不落盘。 */
    TRACE(persistToDatabase = false, persistToFile = false, retentionDays = 0),

    /** 安全审计：口令生成、加解密异常、**谁在何时导出了日志**。合规要求 3 年。 */
    SECURITY(persistToDatabase = true, persistToFile = true, retentionDays = 1095),
    ;

    /** 是否需要落盘（DB 或文件任一）。 */
    val needsPersistence: Boolean get() = persistToDatabase || persistToFile
}
