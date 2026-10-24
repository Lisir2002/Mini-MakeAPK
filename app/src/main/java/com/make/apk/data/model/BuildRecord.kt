package com.make.apk.data.model

/**
 * 构建来源类型：决定"输入什么"。
 */
enum class BuildInputType {
    /** 远程网址，直接以 WebView 封装。 */
    URL,

    /** 粘贴的 HTML / 前端源码。 */
    HTML,

    /** 上传的前端工程压缩包（含 PHP / Vue 等需真编译的项目）。 */
    ARCHIVE,
}

/**
 * 构建引擎：决定"怎么出包"（见 docs/ARCHITECTURE.md 混合构建路径）。
 */
enum class BuildEngine {
    /** Sprint-1：设备端 WebView 即时封装，零后端即可演示。 */
    DEVICE_WEBVIEW,

    /** Phase 2：云端真编译构建（契约见 docs/build-api-contract.md）。 */
    CLOUD,
}

/**
 * 构建状态。终态为 [SUCCESS] / [FAILED]，UI 据此停止进度动效。
 */
enum class BuildStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    ;

    /** 是否为终态（不再变化）。 */
    val isTerminal: Boolean
        get() = this == SUCCESS || this == FAILED
}

/**
 * 构建记录（**领域模型**）。
 *
 * 这是 UI / ViewModel 唯一可见的数据形态：不含任何 Room 注解，
 * 持久化细节（表名、索引、类型映射）被隔离在 `data.local.entity` 内。
 * 时间戳统一使用 epoch 毫秒（Long），避免引入 java.time 脱糖依赖（minSdk 24）。
 */
data class BuildRecord(
    val id: Long = 0,
    /** 构建创建时间（epoch ms），列表排序与清理策略的基准。 */
    val createdAt: Long,
    /** 最后一次状态变更时间（epoch ms）。 */
    val updatedAt: Long,
    val inputType: BuildInputType,
    /** 展示用来源名（如域名、文件名）。 */
    val sourceLabel: String,
    /** 真实来源引用（完整 URL / 本地路径 / 上传 ID）。 */
    val sourceRef: String,
    val engine: BuildEngine,
    val status: BuildStatus,
    val appName: String? = null,
    val packageName: String? = null,
    /** 成功后的 APK 输出路径。 */
    val outputApkPath: String? = null,
    val apkSizeBytes: Long? = null,
    /** 构建耗时（ms），成功与失败都会写入。 */
    val durationMs: Long? = null,
    val errorMessage: String? = null,
) {
    /** 是否已结束（用于 UI 决定是否继续播放进度动画）。 */
    val isFinished: Boolean
        get() = status.isTerminal
}
