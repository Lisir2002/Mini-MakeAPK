package com.make.apk.logging

/**
 * 文件轮转策略（**纯逻辑，无 Android 依赖，可在 JVM 上单测**）。
 *
 * 双策略：
 * - **按大小**：单文件超过阈值即滚动，避免单文件过大拖慢写入与分析。
 * - **按数量**：只保留最近 N 个历史文件，超出删除最旧的（存储有界）。
 */
internal object RotationPolicy {

    private const val SUFFIX_SEPARATOR = "."

    /** 追加 [incomingBytes] 后是否会超过 [maxFileSizeBytes]。 */
    fun shouldRotate(currentSizeBytes: Long, incomingBytes: Long, maxFileSizeBytes: Long): Boolean =
        currentSizeBytes + incomingBytes > maxFileSizeBytes

    /** 历史文件名：`mma.log.1`、`mma.log.2`…… */
    fun rotatedName(baseName: String, index: Int): String = "$baseName$SUFFIX_SEPARATOR$index"

    /** 从历史文件名解析轮转序号；解析失败视为最旧（排在前面，会被优先裁剪）。 */
    fun indexOf(name: String): Int =
        name.substringAfterLast(SUFFIX_SEPARATOR).toIntOrNull() ?: Int.MIN_VALUE

    /**
     * 计算需要删除的文件名（保留最近 [maxFiles] 个）。
     *
     * @param existing 已存在的历史文件名（不含当前活跃文件）
     * @param maxFiles 保留数量上限
     * @return 应删除的文件名列表（最旧的排前面）
     */
    fun filesToPrune(existing: List<String>, maxFiles: Int): List<String> {
        if (maxFiles <= 0) return existing
        if (existing.size <= maxFiles) return emptyList()

        // 序号越大越新，按新→旧排序后跳过应保留的部分；序号解析失败的视为最旧
        val newestFirst = existing.sortedByDescending(::indexOf)
        return newestFirst.drop(maxFiles)
    }
}
