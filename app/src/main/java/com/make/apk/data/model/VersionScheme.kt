package com.make.apk.data.model

import kotlinx.serialization.Serializable

/**
 * 导出 App 的 versionName 段式（见 §2.1）。
 *
 * 与 MMA 自身 4 段式（`MAJOR.MINOR.PATCH.BUILD[-rcN]`）**严格区分**：
 * 导出 App 的版本完全由用户自选 2/3 段式，独立于 MMA 自身版本。
 */
@Serializable
enum class VersionScheme {
    /** 二段式，如 `1.0`。 */
    TWO_SEGMENT,

    /** 三段式，如 `1.3.8`（默认，见 §6.1）。 */
    THREE_SEGMENT,
}
