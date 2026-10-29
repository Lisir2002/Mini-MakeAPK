package com.make.apk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import com.make.apk.data.local.AppDatabase
import com.make.apk.data.local.AppDatabaseFactory
import com.make.apk.data.local.SqlCipherInitializer
import com.make.apk.data.repository.ExportRunRepository
import com.make.apk.data.repository.LogRepository
import com.make.apk.data.repository.ProjectRepository
import com.make.apk.logging.LogConfig
import com.make.apk.logging.LogFields
import com.make.apk.logging.LogLevel
import com.make.apk.logging.MmaLogger
import com.make.apk.logging.StartupTracer
import com.make.apk.storage.MmaStorage
import com.make.apk.storage.ProjectWorkspaceStore
import java.io.File
import kotlin.concurrent.thread

/**
 * 应用入口：持有进程级单例（数据库 / 项目与导出运行仓储 / 日志仓储）。
 *
 * 采用轻量手工注入而非引入 DI 框架：当前模块规模下单例足以覆盖，
 * 避免为 MVP 阶段引入 Hilt 及 KAPT/KSP 额外开销。后续若依赖图膨胀，
 * 再迁移到 Hilt（替换点仅限本类）。
 *
 * 日志系统的安装时机是**正确性关键**：崩溃处理器必须在 `attachBaseContext()` 里
 * 尽可能早安装，晚于此的启动期崩溃（最该抓的一类）会全部漏掉。
 */
class MmaApplication : Application() {

    /** 惰性打开：首次真正访问数据库时才解密并建连，不拖慢冷启动。 */
    val database: AppDatabase by lazy { AppDatabaseFactory.create(this) }

    /** 项目仓储（稳定定义，§8.4）；外部 Items 镜像随建/改/删同步（rc9 用户需求）。 */
    val projectRepository: ProjectRepository by lazy {
        ProjectRepository(database, workspace = ProjectWorkspaceStore(this))
    }

    /** 导出运行仓储（运行态，§8.4）。 */
    val exportRunRepository: ExportRunRepository by lazy {
        ExportRunRepository(database)
    }

    val logRepository: LogRepository by lazy { LogRepository(database) }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // SQLCipher 原生库显式加载（核心修复）：新版 sqlcipher-android 不再自动
        // loadLibs，必须在打开加密库前 System.loadLibrary("sqlcipher")，否则
        // nativeOpen 抛 UnsatisfiedLinkError。置于最前，早于日志与追踪器，
        // 保证任何数据库入口启动前原生符号已就绪。
        SqlCipherInitializer.initOnce()
        StartupTracer.begin()
        installLogging()
    }

    override fun onCreate() {
        super.onCreate()
        StartupTracer.mark("application_oncreate")
        registerLifecycleLogging()
        attachLogDatabaseInBackground()
        initStorageIfAuthorized()

        // 启动终点**不在这里**：onCreate 结束早于用户真正看到内容。
        // 真正的终点是首帧绘制完成，由 MainActivity 通过
        // reportFullyDrawn() + StartupTracer.complete() 触发（见 MainActivity）。
    }

    // ── 日志系统 ──────────────────────────────────────────────────────

    private fun installLogging() {
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        // 启动链防御（2026-10-26 rc2 闪退修复）：目录决策不得杀死进程。
        // resolveLogDirectory → isAuthorized → Environment.isExternalStorageManager
        // 在部分 ROM 上会抛异常；此处兜底私有目录，保证日志系统永远装得上。
        //
        // 诊断版（debuggable，2026-10-26 rc3 装机仍闪退定位期）：日志优先写
        // getExternalFilesDir/mma-logs——免权限、用户可用文件管理器查看，
        // 崩溃文件重启后由 MainActivity 的诊断对话框直接展示（见 CrashReportUI）。
        val logDirectory = if (debuggable) {
            runCatching {
                @Suppress("DEPRECATION")
                File(getExternalFilesDir(null), "mma-logs").apply { mkdirs() }
            }.getOrElse { runCatching { MmaStorage.resolveLogDirectory(this) }.getOrElse { MmaStorage.privateFallback(this) } }
        } else {
            runCatching { MmaStorage.resolveLogDirectory(this) }
                .getOrElse { MmaStorage.privateFallback(this) }
        }
        MmaLogger.install(
            LogConfig(
                minLevel = if (debuggable) LogLevel.DEBUG else LogLevel.INFO,
                logcatMinLevel = if (debuggable) LogLevel.DEBUG else null,
                // 已授权 → MMAFile/Log；未授权/不可写/决策异常 → 私有兜底（免存储权限、卸载即清理）。
                // 此处必须同步决策：attachBaseContext 阶段无 UI，绝不能等授权。
                fileDirectory = logDirectory,
                fileMinLevel = LogLevel.INFO,
            ),
        )
        MmaLogger.security(
            event = "logging_installed",
            message = "日志系统就绪",
            fields = mapOf(LogFields.SUCCESS to true, "debuggable" to debuggable),
        )
    }

    // ── MMAFile 外部存储 ──────────────────────────────────────────────

    /**
     * 存储初始化：**仅已授权时**确保目录结构（幂等）。
     *
     * Setting 等目录被用户删除后，下次启动经此自动重建（用户需求）。
     * 未授权时保持私有兜底不降级功能——授权申请由 UI 层启动门
     * （`StoragePermissionGate`）负责，本类绝不弹 UI。
     */
    private fun initStorageIfAuthorized() {
        // 启动链防御（2026-10-26 rc2 闪退修复）：授权判断与目录创建都不得杀死进程，
        // 异常一律视为「本轮不初始化」，私有兜底目录继续可用。
        val authorized = runCatching { MmaStorage.isAuthorized(this) }.getOrDefault(false)
        if (!authorized) return
        val ready = runCatching { MmaStorage.ensureAll() }.getOrDefault(false)
        if (ready) {
            MmaLogger.startup(
                event = "storage_ready",
                message = "MMAFile 目录就绪",
                fields = mapOf("root" to MmaStorage.ROOT_NAME),
            )
        } else {
            MmaLogger.error(
                event = "storage_init_failed",
                message = "MMAFile 目录创建失败，本轮继续用私有目录",
            )
        }
    }

    /**
     * 加密数据库打开较慢（Keystore + SQLCipher），放到后台线程。
     * 就绪前写入的日志由 DatabaseSink 缓冲，attach 后自动回灌，不丢。
     */
    private fun attachLogDatabaseInBackground() {
        thread(name = "mma-db-init", isDaemon = true) {
            runCatching {
                MmaLogger.attachDao(database.logEntryDao())
                MmaLogger.startup("log_database_ready", "日志库就绪")
                // 专用守护线程内 runBlocking：一次性启动对账（无 UI 生命周期语义，
                // 不值得为它建协程作用域）；阻塞只影响本线程，不影响主线程。
                kotlinx.coroutines.runBlocking { restoreExternalMirrors() }
            }.onFailure { throwable ->
                MmaLogger.error("log_database_ready_failed", throwable)
            }
        }
    }

    /**
     * 外部镜像自愈（rc9 用户需求，数据库就绪后执行一次）：
     * 1. **项目 Items**：外部目录被删 → 从库逐项目重建（[ProjectRepository.reconcileWorkspaces]）；
     * 2. **日志 Log**：外部日志目录被删 → 目录已由 FileSink 写前自愈重建，此处从加密库回灌
     *    近 3 天日志为 `restored-*.log`（数据库是日志的持久事实源，文件只是外显载体）。
     */
    private suspend fun restoreExternalMirrors() {
        runCatching {
            val restored = projectRepository.reconcileWorkspaces()
            if (restored > 0) {
                MmaLogger.operation(
                    event = "workspaces_restored",
                    message = "外部项目目录缺失，已从数据库补全",
                    fields = mapOf(LogFields.COUNT to restored, LogFields.SUCCESS to true),
                )
            }
        }.onFailure { MmaLogger.error("workspace_reconcile_failed", it) }

        runCatching { restoreLogFilesFromDatabase() }
            .onFailure { MmaLogger.error("log_restore_failed", it) }
    }

    /** 外部日志目录为空但库里有日志 → 回灌近 [RESTORE_LOG_DAYS] 天（幂等：有文件就跳过）。 */
    private suspend fun restoreLogFilesFromDatabase() {
        val dir = MmaStorage.resolveLogDirectory(this)
        if (!dir.isDirectory) return
        val hasActiveLogs = dir.listFiles()?.any { it.isFile && it.name.endsWith(".log") } == true
        if (hasActiveLogs) return
        val since = System.currentTimeMillis() - RESTORE_LOG_DAYS * 24L * 60 * 60 * 1000
        val entries = runCatching { logRepository.querySince(since) }.getOrDefault(emptyList())
        if (entries.isEmpty()) return
        val restored = File(dir, "restored-${System.currentTimeMillis()}.log")
        restored.bufferedWriter(Charsets.UTF_8).use { writer ->
            entries.forEach { entry -> writer.write(entry.formatLine()); writer.newLine() }
        }
        MmaLogger.operation(
            event = "log_directory_restored",
            message = "外部日志目录缺失，已从数据库回灌",
            fields = mapOf(LogFields.COUNT to entries.size, LogFields.SUCCESS to true),
        )
    }

    private fun registerLifecycleLogging() {
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    MmaLogger.lifecycle("app_foreground")
                }

                override fun onActivityStopped(activity: Activity) {
                    MmaLogger.lifecycle("app_background")
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    companion object {
        // 私有日志目录名已移交 MmaStorage.privateFallback 统一管理

        /** 日志回灌窗口（天）：外部日志目录被删后从库恢复最近多少天。 */
        private const val RESTORE_LOG_DAYS: Long = 3
    }
}

/** 从任意 Context 取得应用级依赖容器。 */
fun Context.requireMmaApplication(): MmaApplication =
    applicationContext as MmaApplication
