package com.make.apk

import com.make.apk.logging.MmaLogger
import com.make.apk.logging.SessionLogPolicy
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
 *
 * W3 ⑦ 冒烟：会话布局安装后必须拿到合法命名的会话目录（`yyyy-MM-dd/session-HHmmss_短码`），
 * 且过期清理在启动链上执行不炸（清理判据本身由 SessionLogPolicyTest 锁定）。
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

    @Test
    fun applicationStartup_installsSessionLogLayout_withValidNames() {
        val app = RuntimeEnvironment.getApplication() as MmaApplication
        app.onCreate()

        val sessionDir: File? = MmaLogger.currentSessionLogDirectory()
        assertNotNull("会话布局安装后应有当前会话目录（私有兜底目录也算）", sessionDir)
        val dir = sessionDir!!
        val parentName = dir.parentFile?.name
        assertNotNull("会话目录应有日期父级", parentName)
        assertTrue(
            "日期父级必须是合法日期目录名（清理判据依赖）：$parentName",
            SessionLogPolicy.isValidDateDirName(parentName!!),
        )
        assertTrue(
            "会话目录名应符合 session-HHmmss_短码 形态：${dir.name}",
            dir.name.matches(Regex("""session-\d{6}_[0-9a-f]{4}""")),
        )
    }
}
