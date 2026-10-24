package com.web2apk.app

import android.app.Application
import android.content.Context
import com.web2apk.app.data.local.AppDatabase
import com.web2apk.app.data.local.AppDatabaseFactory
import com.web2apk.app.data.repository.BuildHistoryRepository

/**
 * 应用入口：持有进程级单例（数据库 / 仓储）。
 *
 * 采用轻量手工注入而非引入 DI 框架：当前模块规模下单例足以覆盖，
 * 避免为 MVP 阶段引入 Hilt 及 KAPT/KSP 额外开销。后续若依赖图膨胀，
 * 再迁移到 Hilt（替换点仅限本类）。
 */
class Web2ApkApplication : Application() {

    /** 惰性打开：首次真正访问数据库时才解密并建连，不拖慢冷启动。 */
    val database: AppDatabase by lazy { AppDatabaseFactory.create(this) }

    val buildHistoryRepository: BuildHistoryRepository by lazy {
        BuildHistoryRepository(database)
    }
}

/** 从任意 Context 取得应用级依赖容器。 */
fun Context.requireWeb2ApkApplication(): Web2ApkApplication =
    applicationContext as Web2ApkApplication
