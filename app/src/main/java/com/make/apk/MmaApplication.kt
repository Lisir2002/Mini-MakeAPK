package com.make.apk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import com.make.apk.data.local.AppDatabase
import com.make.apk.data.local.AppDatabaseFactory
import com.make.apk.data.repository.BuildHistoryRepository
import com.make.apk.data.repository.LogRepository
import com.make.apk.logging.LogConfig
import com.make.apk.logging.LogFields
import com.make.apk.logging.LogLevel
import com.make.apk.logging.MmaLogger
import com.make.apk.logging.StartupTracer
import java.io.File
import kotlin.concurrent.thread

/**
 * 应用入口：持有进程级单例（数据库 / 构建历史仓储 / 日志仓储）。
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

    val buildHistoryRepository: BuildHistoryRepository by lazy {
        BuildHistoryRepository(database)
    }

    val logRepository: LogRepository by lazy { LogRepository(database) }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        StartupTracer.begin()
        installLogging()
    }

    override fun onCreate() {
        super.onCreate()
        StartupTracer.mark("application_oncreate")
        registerLifecycleLogging()
        attachLogDatabaseInBackground()

        // 启动终点**不在这里**：onCreate 结束早于用户真正看到内容。
        // 真正的终点是首帧绘制完成，由 MainActivity 通过
        // reportFullyDrawn() + StartupTracer.complete() 触发（见 MainActivity）。
    }

    // ── 日志系统 ──────────────────────────────────────────────────────

    private fun installLogging() {
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        MmaLogger.install(
            LogConfig(
                minLevel = if (debuggable) LogLevel.DEBUG else LogLevel.INFO,
                logcatMinLevel = if (debuggable) LogLevel.DEBUG else null,
                // App 私有目录：免存储权限、卸载即清理
                fileDirectory = File(filesDir, LOG_DIR_NAME),
                fileMinLevel = LogLevel.INFO,
            ),
        )
        MmaLogger.security(
            event = "logging_installed",
            message = "日志系统就绪",
            fields = mapOf(LogFields.SUCCESS to true, "debuggable" to debuggable),
        )
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
            }.onFailure { throwable ->
                MmaLogger.error("log_database_ready_failed", throwable)
            }
        }
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
        private const val LOG_DIR_NAME = "logs"
    }
}

/** 从任意 Context 取得应用级依赖容器。 */
fun Context.requireMmaApplication(): MmaApplication =
    applicationContext as MmaApplication
