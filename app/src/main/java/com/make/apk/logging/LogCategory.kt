package com.make.apk.logging

/**
 * 日志类别（**全类别覆盖**，不只 bug）。
 *
 * 每种类别的载体与保留期不同，见 `docs/logging.md` §3：
 * 高价值结构化类别落加密数据库（可查），高频诊断类别落轮转文件，
 * 调试类别只进 Logcat 且不落盘。
 *
 * **自研分级展示**（rc9，用户需求"分级一目了然"）：[label] 是用户向类别名、
 * [group] 是四大展示分组（问题/行为/诊断/安全）——日志查看页按组分区呈现，
 * 分组语义**只在这一处定义**，UI 不允许自建分组。
 */
enum class LogCategory(
    /** 是否落加密数据库（Room + SQLCipher）。 */
    val persistToDatabase: Boolean,
    /** 是否落轮转文件。 */
    val persistToFile: Boolean,
    /** 默认保留天数；0 表示不落盘。 */
    val retentionDays: Int,
    /** 用户向类别名（日志页分区标题 / 筛选芯片共用）。 */
    val label: String,
    /** 展示分组（问题 / 行为 / 诊断 / 安全）。 */
    val group: LogGroup,
) {
    /** 崩溃 / ANR：栈、主线程状态。 */
    CRASH(true, true, 90, "崩溃", LogGroup.PROBLEM),

    /** 已捕获异常、操作失败。 */
    ERROR(true, true, 90, "错误", LogGroup.PROBLEM),

    /**
     * 操作 / 审计：用户做了什么（启动构建、导出、删除历史、权限、设置）。合规要求 ≥180 天。
     * W3 ⑦：落盘开关打开——会话布局下经 [SessionFileSink] 路由到 operations.log
     * （单文件布局时写入 mma.log），审计证据同时具备加密库与文件两份载体。
     */
    OPERATION(true, true, 180, "操作", LogGroup.BEHAVIOR),

    /** 启动：冷/温/热型 + 各阶段耗时 + 首帧。 */
    STARTUP(true, true, 90, "启动", LogGroup.BEHAVIOR),

    /** 性能 / Trace：耗时、慢查询、采样后的函数级埋点。 */
    PERFORMANCE(true, false, 30, "性能", LogGroup.DIAGNOSTIC),

    /** 网络：端点 / 状态码 / 耗时 / 重试次数。**不记 body 与 token**。 */
    NETWORK(true, false, 30, "网络", LogGroup.DIAGNOSTIC),

    /** 生命周期：前后台切换、Activity 状态。量大，不落盘。 */
    LIFECYCLE(false, false, 0, "生命周期", LogGroup.DIAGNOSTIC),

    /** 调试 / 逐帧诊断。仅 debug，不落盘。 */
    TRACE(false, false, 0, "调试", LogGroup.DIAGNOSTIC),

    /**
     * 预览诊断（W3 ⑬）：预览加载（preview_loaded）、白屏探测链（blank_confirmed /
     * self_heal / root_cause 四级根因）。落加密 DB（展示组=诊断）+ preview.log 文件，
     * 保留 90 天——白屏是"用户看到了但描述不清"的典型问题，结构化根因才能聚合定位。
     */
    PREVIEW(true, true, 90, "预览", LogGroup.DIAGNOSTIC),

    /** 安全审计：口令生成、加解密异常、**谁在何时导出了日志**。合规要求 3 年。 */
    SECURITY(true, true, 1095, "安全", LogGroup.SECURITY),
    ;

    /** 是否需要落盘（DB 或文件任一）。 */
    val needsPersistence: Boolean get() = persistToDatabase || persistToFile
}

/**
 * 类别的展示分组（rc9 自研分级，"一目了然"的顶层结构）。
 *
 * 日志查看页的分区顺序固定为 [PROBLEM] → [BEHAVIOR] → [DIAGNOSTIC] → [SECURITY]：
 * 先看有没有问题，再看用户做了什么，然后是诊断细节，最后是安全审计。
 */
enum class LogGroup(
    /** 用户向分组名（分区标题，单一事实源）。 */
    val label: String,
    /** 分区排序权重（越小越靠前）。 */
    val order: Int,
) {
    /** 问题：崩溃、错误——出事最先看这里。 */
    PROBLEM("问题", 0),

    /** 行为：用户操作、启动流水——谁在什么时候做了什么。 */
    BEHAVIOR("行为", 1),

    /** 诊断：性能、网络、生命周期、调试——排查细节用。 */
    DIAGNOSTIC("诊断", 2),

    /** 安全：口令、导出等敏感操作审计——合规证据。 */
    SECURITY("安全", 3),
}
