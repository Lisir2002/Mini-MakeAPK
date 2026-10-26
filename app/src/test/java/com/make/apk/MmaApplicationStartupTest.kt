package com.make.apk

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 启动路径集成测试（Robolectric，覆盖 Paparazzi 无法覆盖的生命周期盲区）。
 *
 * 背景：rc2 真机「打开就闪退」，而 Paparazzi（页面渲染）与 JVM 单测（纯逻辑）
 * 全绿——盲区正是 [MmaApplication] 的 `attachBaseContext`/`onCreate` 启动链
 * （日志安装、存储评估、DB 后台接线、权限门状态机）。本类在 JVM 上真实执行该链，
 * 任一步抛异常即在此复现。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = MmaApplication::class)
class MmaApplicationStartupTest {

    @Test
    fun applicationStartup_fullChain_doesNotCrash() {
        // Robolectric 已完成 attachBaseContext；显式驱动 onCreate 全链
        val app = RuntimeEnvironment.getApplication() as MmaApplication
        app.onCreate()
    }
}
