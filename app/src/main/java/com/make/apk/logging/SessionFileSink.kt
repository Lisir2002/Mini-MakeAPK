package com.make.apk.logging

import java.io.File

/**
 * 会话化多文件出口（W3 ⑦）：**按类别路由**的组合 FileSink。
 *
 * 目录布局由 [SessionLogPolicy] 定义：`<root>/<sessionDirName>/<category>.log`。
 * 相比单一大 `mma.log`：
 * - operations.log（审计，合规保留期最长）与高频诊断日志分文件，互不冲刷轮转窗口；
 * - preview.log 单独承载预览白屏诊断（W3 ⑬ 配套），排障时不用在万行日志里翻找。
 *
 * 设计继承 [FileSink] 的三条铁律：
 * - 调用线程绝不碰磁盘（FileSink 内部走 AsyncLogDispatcher）；
 * - 写前 `mkdirs` **自愈**（会话目录被用户删除后自动重建，rc9 需求）；
 * - 轮转参数（2MB × [maxFilesPerFile] 个）按文件独立生效，磁盘占用有上界。
 *
 * @param root             日志根目录（MMAFile/Log 或私有兜底）。
 * @param sessionDirName   会话目录名（相对 [root]，由 [SessionLogPolicy.sessionDirName] 生成；
 *   含 `/` 的两级路径在这里是合法的——只查首段是否合法日期名，删除治理由启动清理负责）。
 * @param maxFileSizeBytes 单文件轮转阈值。
 * @param maxFilesPerFile  单文件保留的历史轮转数上限（主控拍板：20）。
 * @param routing          类别 → 文件名映射（默认 [DEFAULT_ROUTING]）。
 * @param defaultFileName  路由未登记类别（routing 返回空白）时的兜底文件。
 * @param minLevel         文件出口最低级别。
 */
internal class SessionFileSink(
    internal val root: File,
    internal val sessionDirName: String,
    private val maxFileSizeBytes: Long = DEFAULT_MAX_FILE_SIZE_BYTES,
    private val maxFilesPerFile: Int = DEFAULT_MAX_FILES_PER_FILE,
    private val routing: (LogCategory) -> String = ::defaultRoute,
    private val defaultFileName: String = APP_FILE,
    private val minLevel: LogLevel = LogLevel.INFO,
) : LogSink {

    /** 当前会话的完整目录（供 redirectFileSink 整体搬迁、DB 回灌落位）。 */
    val sessionDirectory: File = File(root, sessionDirName)

    private val lock = Any()
    private val sinks = LinkedHashMap<String, FileSink>()

    init {
        runCatching { sessionDirectory.mkdirs() }
    }

    override fun write(event: LogEvent) {
        if (!event.level.isEnabledFor(minLevel)) return
        if (!event.category.persistToFile) return
        val fileName = routing(event.category).ifBlank { defaultFileName }
        sinkFor(fileName)?.write(event)
    }

    override fun flush() {
        allSinks().forEach { runCatching { it.flush() } }
    }

    override fun close() {
        allSinks().forEach { runCatching { it.close() } }
        synchronized(lock) { sinks.clear() }
    }

    /** 全部已路由文件（活跃 + 历史），供导出诊断包与整体迁移。 */
    fun logFiles(): List<File> = allSinks().flatMap { it.logFiles() }

    /** 当前会话目录下已产生的全部文件（含非路由产物，如 app.restored-*.log）。 */
    fun sessionFiles(): List<File> =
        sessionDirectory.listFiles()?.filter { it.isFile }?.toList() ?: emptyList()

    /** 取（或惰性创建）某个路由文件的底层 FileSink；文件名不安全时返回 null（丢弃该条）。 */
    private fun sinkFor(fileName: String): FileSink? {
        // 文件名安全铁律（W3 硬约束）：路由产物必须是纯文件名——
        // 含路径分隔符 / 盘符 / 空格的名字一律拒绝（防御 routing 被注入非法配置）。
        if (fileName.isEmpty() ||
            fileName.contains('/') ||
            fileName.contains('\\') ||
            fileName.contains(':') ||
            fileName.contains(' ')
        ) {
            return null
        }
        synchronized(lock) {
            sinks[fileName]?.let { return it }
            // mkdirs 自愈（写前发现目录不在则幂等重建；FileSink 内部还有一层兜底）
            runCatching { sessionDirectory.mkdirs() }
            val sink = FileSink(
                directory = sessionDirectory,
                maxFileSizeBytes = maxFileSizeBytes,
                maxFiles = maxFilesPerFile,
                minLevel = minLevel,
                activeFileName = fileName,
            )
            sinks[fileName] = sink
            return sink
        }
    }

    private fun allSinks(): List<FileSink> = synchronized(lock) { sinks.values.toList() }

    companion object {
        /** 操作 / 安全审计文件（OPERATION + SECURITY，合规保留期最长）。 */
        const val OPERATIONS_FILE: String = "operations.log"

        /** 预览诊断文件（PREVIEW，W3 ⑬ 白屏排查专用）。 */
        const val PREVIEW_FILE: String = "preview.log"

        /** 其余全部类别（默认兜底文件）。 */
        const val APP_FILE: String = "app.log"

        /** 默认单文件大小（与既有 FileSink 一致）。 */
        const val DEFAULT_MAX_FILE_SIZE_BYTES: Long = 2L * 1024 * 1024

        /** 默认单文件历史轮转上限（主控拍板：20）。 */
        const val DEFAULT_MAX_FILES_PER_FILE: Int = 20

        /**
         * 默认路由（主控拍板）：OPERATION + SECURITY → operations.log、
         * PREVIEW → preview.log、其余（含未来新增类别）→ app.log。
         */
        fun defaultRoute(category: LogCategory): String = when (category) {
            LogCategory.OPERATION, LogCategory.SECURITY -> OPERATIONS_FILE
            LogCategory.PREVIEW -> PREVIEW_FILE
            else -> APP_FILE
        }
    }
}
