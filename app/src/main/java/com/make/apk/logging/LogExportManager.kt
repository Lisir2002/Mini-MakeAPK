package com.make.apk.logging

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 日志外带能力（导出诊断包 / 打开日志目录）。
 *
 * 设计约束（本项目反复踩过的坑）：
 * - **全部对外函数吞异常**：导出与打开目录都是磁盘 + Intent 操作，任何失败只允许
 *   返回 `null` / `false` 让 UI 降级提示，**绝不允许把异常抛进 Compose 重组**；
 * - **UI 零 IO**：页面只发事件，磁盘与 Intent 全在本对象内完成（见 `LogViewerScreen`）；
 * - zip 落在 `cacheDir/log-export/`，已由 `res/xml/mma_file_paths.xml` 向
 *   `FileProvider` 登记，分享走 `content://` 而非 `file://`（Android 7+ 硬性要求）。
 */
object LogExportManager {

    /** FileProvider authority 后缀（与 `AndroidManifest.xml` 中的 `${applicationId}.fileprovider` 对齐）。 */
    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

    /** 导出目录名（cache 下，随卸载/清缓存清理，不污染用户可见目录）。 */
    private const val EXPORT_DIR_NAME = "log-export"

    /** 诊断包时间戳格式：**无冒号/空格**，避免任何文件系统与 Intent 转义问题。 */
    private const val TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss"

    /** 公共存储根前缀：仅该前缀下的路径才能换算成 DocumentsUI 的 `primary:` 文档 id。 */
    private const val PUBLIC_STORAGE_PREFIX = "/storage/emulated/0/"

    /** 外部存储 DocumentsProvider 的 authority（AOSP 固定值，非应用包名）。 */
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /** 导出目录（cache，FileProvider 已登记）：`cacheDir/log-export/`。 */
    fun exportDir(context: Context): File = File(context.cacheDir, EXPORT_DIR_NAME)

    /**
     * 把当前会话日志目录下的全部文件打成 zip。
     *
     * 会话目录典型内容：`app.log` / `preview.log` / `operations.log` /
     * `app.restored-<ts>.log`（会话布局见 [SessionFileSink]）。
     *
     * @return 生成的 zip；无日志可导出 / 失败返回 `null`。
     */
    fun exportDiagnosticBundle(context: Context): File? = runCatching {
        val sessionDir = MmaLogger.currentSessionLogDirectory() ?: return@runCatching null
        val files = sessionDir.listFiles()
            ?.filter { it.isFile && it.length() > 0L }
            ?.sortedBy { it.name }
            .orEmpty()
        if (files.isEmpty()) return@runCatching null

        val stamp = SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US).format(Date(System.currentTimeMillis()))
        val target = File(exportDir(context), "mma-logs-$stamp.zip")
        if (zipFiles(files, target)) target else null
    }.getOrNull()

    /**
     * 打开当前日志根目录（交给系统文件管理器 / DocumentsUI）。
     *
     * 只在日志根位于公共存储根（`/storage/emulated/0/`）下时才走 DocumentsUI 分支——
     * 私有兜底目录（`filesDir/logs`）用户本来也进不去，调用方用 Toast 显示绝对路径即可。
     *
     * @return 是否成功发出 Intent（`false` → 调用方降级为展示路径文本）。
     */
    fun openLogDirectory(context: Context): Boolean = runCatching {
        val root = MmaLogger.currentSessionLogRoot() ?: return@runCatching false
        val absolute = root.absolutePath
        if (!absolute.startsWith(PUBLIC_STORAGE_PREFIX)) return@runCatching false
        // externalstorage 的 docId 形态是 `primary:<相对路径>`（相对路径不含前导斜杠）。
        val relative = absolute.removePrefix(PUBLIC_STORAGE_PREFIX).trimEnd('/')
        // 日志根恰为外部存储根本身（理论不会发生）：无文档 id 可换算，直接降级。
        if (relative.isEmpty()) return@runCatching false
        // buildDocumentUri 内部 appendPath 会做唯一一次编码（整段作为单 path segment），
        // 此处绝不能再 Uri.encode，否则双重编码成 `primary:MMAFile%252FLog`
        // 导致 DocumentsUI 查不到该文档（QA Major）。
        val documentUri = DocumentsContract.buildDocumentUri(
            EXTERNAL_STORAGE_AUTHORITY,
            "primary:$relative",
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(documentUri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // resolveActivity 为空 → 交给调用方降级（ROM 无 DocumentsUI / 被禁用的场景）。
        if (intent.resolveActivity(context.packageManager) == null) return@runCatching false
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    /** 分享 zip（FileProvider `content://` + `ACTION_SEND`，`type=application/zip`）。 */
    fun shareZip(context: Context, zip: File): Boolean = runCatching {
        if (!zip.isFile) return@runCatching false
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + FILE_PROVIDER_SUFFIX,
            zip,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "导出诊断包").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        true
    }.getOrDefault(false)

    /**
     * 纯 JVM：把给定文件集合压成 zip（抽出来便于单测，不依赖 Context）。
     *
     * 语义（已在 [com.make.apk.logging.LogExportManagerTest] 钉死）：
     * - 条目名只取 `file.name`（不保留目录层级——日志文件本就同层）；
     * - **单个文件失败不中断整体**（逐条 try/catch，跳过继续）；
     * - 文件列表为空 → `false`；
     * - 任何异常 → `false` **且删除半成品 zip**（避免用户分享到损坏包）。
     */
    internal fun zipFiles(files: List<File>, target: File): Boolean {
        if (files.isEmpty()) return false
        return runCatching {
            target.parentFile?.mkdirs()
            ZipOutputStream(target.outputStream().buffered()).use { zip ->
                files.forEach { file -> appendEntry(zip, file) }
            }
            true
        }.getOrElse {
            runCatching { target.delete() }
            false
        }
    }

    /**
     * 追加单条条目：**失败只跳过本文件**，不影响整体导出
     * （一个损坏/被占用的日志文件不该毁掉整个诊断包）。
     */
    private fun appendEntry(zip: ZipOutputStream, file: File) {
        runCatching {
            if (!file.isFile) return
            zip.putNextEntry(ZipEntry(file.name))
            file.inputStream().buffered().use { input -> input.copyTo(zip) }
            zip.closeEntry()
        }.onFailure {
            // 条目写失败时补一次 closeEntry，避免残余状态污染后续条目。
            runCatching { zip.closeEntry() }
        }
    }
}
