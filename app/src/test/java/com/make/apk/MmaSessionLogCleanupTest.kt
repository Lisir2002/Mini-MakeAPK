package com.make.apk

import com.make.apk.logging.MmaLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 会话日志目录清理**集成测试**（W3 收口 F1，Robolectric 走真实启动安装链）。
 *
 * 缺陷证据（QA Major-F1）：清理扫描曾用 `currentSessionLogDirectory()?.parentFile`
 * ——那是**日期目录**而非日志根，其下只有 session 目录（不匹配日期正则），
 * `expiredDateDirs` 恒空 → 14 天保留策略死代码。本测试：
 *
 * 1. 真实启动（attachBaseContext 安装会话布局 → 私有兜底根）；
 * 2. 在**日志根**下播种：15 天前合法日期目录（应删）+ `backup`（用户自建，应留）+
 *    `2026-13-99`（形似但非法，应留）+ 当前会话目录（应留）；
 * 3. 直调真实清理入口（[MmaApplication.cleanupExpiredSessionLogDirs]）；
 * 4. 断言**仅**过期合法日期目录被删，其余原样。
 *
 * 修复前：扫描错位 → 什么都不删 → 第一个断言必红；修复后：仅前者被删。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = MmaApplication::class)
class MmaSessionLogCleanupTest {

    private fun dateDirName(daysAgo: Int): String {
        val calendar = Calendar.getInstance().apply {
            time = Date()
            add(Calendar.DAY_OF_MONTH, -daysAgo)
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    }

    @Test
    fun cleanup_deletesOnlyExpiredValidDateDirs_andProtectsUserDirs() {
        val app = RuntimeEnvironment.getApplication() as MmaApplication
        app.onCreate()

        // 日志根 = 会话目录（<root>/<date>/session-x）向上一级即根（<root>）。
        val sessionDir: File = MmaLogger.currentSessionLogDirectory()
            ?: error("会话布局应已安装（见 MmaApplicationStartupTest 冒烟）")
        val root: File = sessionDir.parentFile?.parentFile
            ?: error("会话目录应有 两级 父级：<root>/<date>/session-x")
        assertTrue("日志根应真实存在：$root", root.isDirectory)

        // ── 播种 ─────────────────────────────────────────────────────
        val expired = File(root, dateDirName(daysAgo = 15)).apply { mkdirs() }
            .resolve("session-010203_dead").apply { mkdirs() }
        val userDir = File(root, "backup").apply { mkdirs() }
        val malformed = File(root, "2026-13-99").apply { mkdirs() }
        assertTrue(sessionDir.isDirectory)

        // ── 触发真实清理 ─────────────────────────────────────────────
        app.cleanupExpiredSessionLogDirs()

        // ── 断言：仅过期合法日期目录被删 ─────────────────────────────
        assertFalse("15 天前的合法日期目录应被删除", expired.exists())
        assertTrue("根目录整体不应被误删", root.isDirectory)
        assertTrue("当前会话目录应保留", sessionDir.isDirectory)
        assertTrue("用户自建目录（backup）永不该动", userDir.isDirectory)
        assertTrue("形似日期的非法名（2026-13-99）永不该动", malformed.isDirectory)

        // 清理后再跑一遍应幂等（无新删除、无异常）。
        app.cleanupExpiredSessionLogDirs()
        assertFalse(expired.exists())
        assertNotNull(MmaLogger.currentSessionLogDirectory())
    }
}
