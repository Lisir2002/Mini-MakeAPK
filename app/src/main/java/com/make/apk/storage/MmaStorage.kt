package com.make.apk.storage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

/**
 * MMAFile 外部存储目录的**唯一事实来源**：授权判断、路径解析、幂等创建。
 *
 * 调用规约（强制）：
 * - 任何 MMAFile 路径**必须经本对象获取**（[dir] / [rootFile]），禁止散落 `File(...)` 拼路径；
 * - 创建前先 [isAuthorized]；未授权时 [resolveLogDirectory] 等会走私有兜底，功能不中断；
 * - [ensureAll] 幂等：目录被用户删除后，下次启动/授权成功会自动重建（Setting 目录即靠此）。
 *
 * API 分级（targetSdk 34 实测结论，勿凭直觉改）：
 * - **API ≤ 28**：`WRITE_EXTERNAL_STORAGE` 运行时权限即可写公共存储根；
 * - **API 29（Android 10）**：**死区**——`requestLegacyExternalStorage` 在 targetSdk ≥ 30
 *   时被系统忽略，`MANAGE_EXTERNAL_STORAGE` 在该版本不可授予，任何途径都拿不到
 *   根目录写权限 → [isAuthorized] 恒 false，走一次性说明 + 私有降级；
 * - **API ≥ 30**：仅 `MANAGE_EXTERNAL_STORAGE`（特殊权限，跳系统设置页授权）。
 */
object MmaStorage {

    /** 根目录名（对外唯一入口，勿在别处硬编码 "MMAFile"）。 */
    val ROOT_NAME: String = MmaDir.ROOT.folderName

    /** API 29 一次性说明的「已告知」标记文件名（私有目录，免权限、卸载即清）。 */
    private const val API29_NOTICE_FILE_NAME = "storage_api29_notice"

    /**
     * 外部根目录 `File`（**不保证可写**，调用方需先 [isAuthorized] 并容忍失败）。
     *
     * `Environment.getExternalStorageDirectory()` 自 API 29 弃用，但 targetSdk 34 下
     * 它仍是唯一权威的外部存储根路径（持有 MANAGE_EXTERNAL_STORAGE 后 File API 可用，
     * 仅有已知的轻微性能惩罚，见 docs/ARCHITECTURE.md §3.3）。
     */
    @Suppress("DEPRECATION")
    fun rootFile(): File = File(Environment.getExternalStorageDirectory(), ROOT_NAME)

    /** [dir] 对应的目录（不保证已创建/可写）。 */
    fun dir(dir: MmaDir): File = File(rootFile(), dir.folderName)

    /**
     * 当前是否已获得公共根目录写权限（同步、无 UI；Application 与权限门共用此判断）。
     *
     * API 29 恒 false：见类 KDoc 的死区说明。
     *
     * **启动链安全（2026-10-26 rc2 闪退修复）**：三个分支全部以 `runCatching` 包裹——
     * `Environment.isExternalStorageManager()` 与 `checkSelfPermission` 在部分
     * ROM/系统时序下会抛异常，而本方法在 `Application.attachBaseContext` 的日志安装
     * 路径上被同步调用，一旦抛异常进程在 Application 创建时即死（闪退且无任何日志）。
     * 异常一律保守视为「未授权」，走私有兜底，功能不中断。
     */
    fun isAuthorized(context: Context): Boolean = runCatching {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                Environment.isExternalStorageManager()

            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> false

            else -> ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }.getOrDefault(false)

    /**
     * 公共日志目录「实测可用」时返回 `MMAFile/Log`，否则 `null`（**不降级**，供调用方自行决策）。
     *
     * 与 [resolveLogDirectory] 的差别只在「不可用时返回什么」：
     * 决策类调用方（安装期目录优先级、启动后复查、热迁移目标）需要区分
     * 「公共可用」与「已降级」，若只有降级后的 File 就无从判断该不该搬。
     *
     * 「授权 ≠ 能写」：外部存储挂载异常时权限可能仍显示已授予，故必须实测
     * （`mkdirs() || (isDirectory && canWrite())`）。
     *
     * 异常一律视为不可用（返回 null）：本方法在 `attachBaseContext` 的日志安装路径上
     * 被同步调用，抛异常等于启动崩溃。
     */
    fun resolvePublicLogDirectory(context: Context): File? = runCatching {
        if (!isAuthorized(context)) return@runCatching null
        val publicLog = dir(MmaDir.LOG)
        val writable = publicLog.mkdirs() || (publicLog.isDirectory && publicLog.canWrite())
        if (writable) publicLog else null
    }.getOrNull()

    /**
     * 日志目录决策：已授权且实测可写 → `MMAFile/Log`；否则私有兜底 `filesDir/logs`。
     *
     * 行为与历史版本完全等价（探测逻辑收敛到 [resolvePublicLogDirectory]，去重）。
     * 需要「区分是否降级」的调用方请直接用 [resolvePublicLogDirectory]。
     */
    fun resolveLogDirectory(context: Context): File =
        resolvePublicLogDirectory(context) ?: privateFallback(context)

    /** 私有日志兜底目录（免存储权限、卸载即清理；未授权/不可写时的降级目标）。 */
    fun privateFallback(context: Context): File = File(context.filesDir, "logs")

    /**
     * 幂等创建根目录与全部子目录（**纯函数**：只吃目录、不碰 Context，JVM 可测）。
     *
     * @return 是否全部就绪；任一失败（如外部存储只读、空间不足）返回 false，
     *   调用方据此降级，不得静默假设成功。
     */
    fun ensureDirs(root: File): Boolean {
        if (!root.mkdirs() && !(root.isDirectory && root.canWrite())) return false
        return MmaDir.entries
            .filter { it != MmaDir.ROOT }
            .all { sub -> File(root, sub.folderName).mkdirs() || File(root, sub.folderName).isDirectory }
    }

    /** 确保 MMAFile 全结构就绪（仅已授权时调用；内部复用 [ensureDirs]）。 */
    fun ensureAll(): Boolean = ensureDirs(rootFile())

    /** API 29 一次性说明是否已展示过（进程重启后仍记得）。 */
    fun hasApi29NoticeShown(context: Context): Boolean =
        File(context.filesDir, API29_NOTICE_FILE_NAME).exists()

    /** 标记 API 29 一次性说明已展示（幂等）。 */
    fun markApi29NoticeShown(context: Context) {
        runCatching { File(context.filesDir, API29_NOTICE_FILE_NAME).createNewFile() }
    }
}
