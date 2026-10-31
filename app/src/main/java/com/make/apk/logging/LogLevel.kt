package com.make.apk.logging

/**
 * 日志级别（与 android.util.Log 优先级对齐，便于 Logcat 输出）。
 *
 * 级别契约（团队必须一致，否则级别通胀会让告警失效）：
 * - [VERBOSE] / [DEBUG]：仅本地诊断。**release 构建默认关闭**。
 * - [INFO]：生命周期 / 业务结果，对运维有价值，可采样。
 * - [WARN]：降级但已自愈（重试成功、回退命中），**不告警但始终记录**。
 * - [ERROR]：操作失败且用户可感知，**必须可告警**。
 * - [FATAL]：崩溃 / ANR，进程级故障。
 *
 * 判据：如果一条 "error" 不值得人看一眼，它就是 WARN。
 *
 * **自研分级展示**（rc9，用户需求"分级一目了然"）：[label] 是用户向中文名、
 * [severityWeight] 是排序权重（越大越严重，筛选芯片与列表排序用）、
 * [tone] 是**配色的唯一依据**——展示语义**只在这一处定义**，
 * 日志查看页 / 详情弹层 / 导出格式一律引用，不允许各自再拼。
 *
 * [tone] 是「级别 → 视觉色调」的唯一映射（rc17 修复）：此前 UI 两处各自写
 * `when (severityWeight) { in 4..5 / 3 / 2 / else }`，分支用裸数字、且日志页与详情弹层
 * 各存一份逐字相同的副本；新增级别（如 TRACE）会静默落进 else 变灰，编译器毫无提示。
 * 现改为枚举字段——**新增级别不填 tone 直接编译失败**，这是把"展示契约"钉在类型上。
 */
enum class LogLevel(
    val priority: Int,
    /** 用户向级别名（日志页徽标 / 筛选芯片共用，单一事实源）。 */
    val label: String,
    /** 展示权重（越大越严重；VERBOSE=0 → FATAL=5）。 */
    val severityWeight: Int,
    /** 展示色调（UI 按此取容器/文字色；语义见 [LogTone]）。 */
    val tone: LogTone,
) {
    VERBOSE(2, "详细", 0, LogTone.Muted),
    DEBUG(3, "调试", 1, LogTone.Muted),
    INFO(4, "信息", 2, LogTone.Info),
    WARN(5, "警告", 3, LogTone.Warn),
    ERROR(6, "错误", 4, LogTone.Error),
    FATAL(7, "致命", 5, LogTone.Error),
    ;

    /** 该级别是否被 [threshold] 放行。 */
    fun isEnabledFor(threshold: LogLevel): Boolean = priority >= threshold.priority
}

/**
 * 日志级别的**展示色调**（纯语义，无色值——色值在 `ui/theme/` 由 [LogLevelBadge] 消费）。
 *
 * 为什么单独一个枚举而不是直接在 UI 里 `when`：
 * 1. 颜色是"令牌"，映射决策属于领域语义，UI 只做"取色"；
 * 2. 四档必须互不撞色，否则分级展示形同虚设（历史事故：INFO 与 DEBUG 曾取到**同一个
 *    `md_surface2` 常量**，徽标底色字节级相同）；
 * 3. 枚举穷举在编译期强制覆盖，`when` 裸数字分支做不到。
 *
 * 色调语义与应用（见 `ui/theme/LogToneColors.kt`）：
 * - [Muted]：详细/调试——灰容器，只在需要时细看，不该抢视线；
 * - [Info]：信息——主色蓝容器（`--info` 复用 `--primary`，design-tokens 既定）；
 * - [Warn]：警告——琥珀容器（`--warning` #D97706 派生）。**警告不能是蓝的**：
 *   蓝色是本项目的"正常/品牌"色，用它表达降级等于把告警伪装成常规；
 * - [Error]：错误/致命——红容器，最高优先级。
 */
enum class LogTone {
    /** 中性灰（VERBOSE / DEBUG）。 */
    Muted,

    /** 主色蓝（INFO）。 */
    Info,

    /** 琥珀（WARN）。 */
    Warn,

    /** 红（ERROR / FATAL）。 */
    Error,
}
