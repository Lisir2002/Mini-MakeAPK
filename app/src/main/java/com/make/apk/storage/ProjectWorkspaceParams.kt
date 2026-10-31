package com.make.apk.storage

/**
 * 项目工作区目录结构参数（W3 ⑧ 超集扩展，**单一事实源**）。
 *
 * 设计原则（主控拍板「超集扩展」）：目录结构只**新增**子目录约定、不删不改既有布局，
 * 旧版本 / 旧工作区照常可读（manifest 对账判据不变：仅 `manifest.json` 存在性）。
 *
 * 本轮生效 vs 预留（避免「常量存在即已实现」的误读）：
 *
 * | 常量 | 状态 | 说明 |
 * |---|---|---|
 * | [STRUCTURE_VERSION] | **本轮生效** | manifest 新增 `structureVersion` 字段：读旧（缺省=1）写新（恒=2） |
 * | [MANIFEST_WARN_BYTES] | **本轮生效** | manifest 超过 1MB 时的告警阈值（HTML 全文内联可能撑大 manifest） |
 * | [EXPORT_DIR] | **预留** | `export/` 子目录约定（Phase-2 导出产物本地镜像），本轮**不自动创建** |
 * | [BACKUPS_DIR] | **预留** | `backups/` 子目录约定（Phase-2 本地备份），本轮**不自动创建** |
 * | [MAX_ASSET_COUNT] | **预留** | assets/ 副本数量上限（图标 / 头图之外的扩展），本轮仅 icon.png |
 * | [SRC_MAX_BYTES] | **预留** | src/ 单文件写入上限（HTML 项目超大源码的保护），本轮不强制 |
 */
object ProjectWorkspaceParams {

    /**
     * 目录结构版本。v2 = 本轮引入 `structureVersion` 字段与 export//backups/ 约定；
     * v1 = 历史布局（manifest 无该字段，反序列化缺省回落 1——读旧写新）。
     */
    const val STRUCTURE_VERSION: Int = 2

    /** Phase-2 导出产物镜像子目录名（**预留**：仅约定，本轮不创建、不写入）。 */
    const val EXPORT_DIR: String = "export"

    /** Phase-2 本地备份子目录名（**预留**：仅约定，本轮不创建、不写入）。 */
    const val BACKUPS_DIR: String = "backups"

    /**
     * manifest.json 大小告警阈值（**本轮生效**）：超过即记一条 WARN——
     * manifest 内联 HTML 全文（⑥ 审计已确认保持），超大意味着源码体量值得留意。
     */
    const val MANIFEST_WARN_BYTES: Long = 1L * 1024 * 1024

    /** assets/ 副本数量上限（**预留**：本轮仅 icon.png 一项，校验留待资产扩展时启用）。 */
    const val MAX_ASSET_COUNT: Int = 32

    /** src/ 单文件写入上限（**预留**：本轮不强制；HTML 源码超大时的保护线）。 */
    const val SRC_MAX_BYTES: Long = 8L * 1024 * 1024
}
