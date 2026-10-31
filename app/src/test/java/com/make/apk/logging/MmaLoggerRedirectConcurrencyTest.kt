package com.make.apk.logging

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 并发重定向回归测试（Bug：同一条 `preview_loaded` 在 `preview.log` 里连续出现 N 行）。
 *
 * 根因：`sinks` 是 CopyOnWriteArrayList，只保证单次操作原子；而
 * `redirectSessionSink` 的「读 sessionSink → remove → migrate → add newSink」序列整体无锁。
 * 两条并发路径（`MmaApplication` 的 db-init 后台线程 / `StoragePermissionGate` 的
 * Compose IO 线程）交错时各自 add 一个新 sink，remove(old) 只对第一个生效 →
 * 累积多个 SessionFileSink 指向同一 `preview.log` → `sinks.forEach { write }` 写 N 份。
 *
 * 三层防护的回归断言：
 * 1. `redirectLock` 让重定向整体原子；
 * 2. `redirectSessionSink` 的 `session !== sessionSink` 幂等守卫拦住后到线程；
 * 3. `pruneDuplicateSessionSinks` 兜底收敛（断言最终只有 1 个文件出口）。
 */
class MmaLoggerRedirectConcurrencyTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        // 单例复位：每个用例从「未安装」态出发（install 是 @Synchronized + 幂等）。
        MmaLogger.shutdown()
    }

    @After
    fun tearDown() {
        // AsyncLogDispatcher.drain 会等队列排空（<1s），shutdown 内一并停掉分发线程。
        MmaLogger.shutdown()
    }

    /** 安装会话布局（真实文件出口，PREVIEW 类落 preview.log）。 */
    private fun installSessionLayout(root: File) {
        MmaLogger.install(
            LogConfig(
                minLevel = LogLevel.DEBUG,
                logcatMinLevel = null,
                fileDirectory = root,
                fileMinLevel = LogLevel.DEBUG,
                sessionLogLayout = true,
                enableCrashHandler = false,
                enableAnrWatchdog = false,
                rateLimitMaxPerWindow = 1_000,
            ),
        )
    }

    @Test(timeout = 120_000)
    fun concurrentRedirectFileSink_keepsExactlyOneSessionSink() {
        val privateRoot = temporaryFolder.newFolder("private_root")
        val publicRoot = temporaryFolder.newFolder("mmafile_root")
        installSessionLayout(privateRoot)
        try {
            assertTrue(MmaLogger.startup("before_redirect"))
            MmaLogger.flushBlocking()
            assertEquals("安装期只应挂一个文件出口", 1, MmaLogger.sessionSinkCountForTest())

            val threadCount = 4
            val startGate = CountDownLatch(1)
            val doneGate = CountDownLatch(threadCount)
            val failures = AtomicInteger(0)

            repeat(threadCount) {
                Thread({
                    try {
                        // 同时起跑：最大化「读旧 sessionSink → 各自 add」的交错窗口。
                        startGate.await()
                        MmaLogger.redirectFileSink(publicRoot)
                    } catch (error: Throwable) {
                        failures.incrementAndGet()
                    } finally {
                        doneGate.countDown()
                    }
                }, "redirect-$it").start()
            }

            startGate.countDown()
            assertTrue(
                "并发重定向应在超时内全部返回",
                doneGate.await(90, TimeUnit.SECONDS),
            )
            assertEquals("重定向不得抛异常", 0, failures.get())

            // 防护 3：无论并发如何交错，最终只允许一个 SessionFileSink 存活。
            assertEquals(
                "同一条日志被写 N 份的根因：文件出口必须收敛为 1 个",
                1,
                MmaLogger.sessionSinkCountForTest(),
            )

            // 迁移结果正确性：会话目录已落到新根下，且只落一次。
            val sessionDir = MmaLogger.currentSessionLogDirectory()
            assertNotNull("会话目录必须存在", sessionDir)
            assertTrue(
                "会话目录应在新根下",
                sessionDir!!.canonicalPath.startsWith(publicRoot.canonicalPath),
            )

            // 症状级断言（真实 Bug 表现）：写 1 条 PREVIEW 事件，preview.log 只应有 1 行。
            assertTrue(
                MmaLogger.log(
                    level = LogLevel.INFO,
                    category = LogCategory.PREVIEW,
                    event = "preview_loaded",
                    fields = mapOf(LogFields.DURATION_MS to 10L, LogFields.SUCCESS to true),
                ),
            )
            MmaLogger.flushBlocking()

            val previewLog = File(sessionDir, SessionFileSink.PREVIEW_FILE)
            assertTrue("preview.log 应已生成", previewLog.isFile)
            val writtenLines = previewLog.readLines().count { it.contains("preview_loaded") }
            assertEquals("同一事件必须只写 1 次（此前为 3 次）", 1, writtenLines)
        } finally {
            MmaLogger.shutdown()
        }
    }

    @Test(timeout = 120_000)
    fun repeatedRedirectToSameRoot_isNoOpAndKeepsSingleSink() {
        val privateRoot = temporaryFolder.newFolder("private_root_same")
        val publicRoot = temporaryFolder.newFolder("mmafile_root_same")
        installSessionLayout(privateRoot)
        try {
            assertTrue(MmaLogger.redirectFileSink(publicRoot))
            val firstSessionDir = MmaLogger.currentSessionLogDirectory()
            assertNotNull(firstSessionDir)

            // 同根重定向必须短路（并发路径的「后到者」走的就是这条）：不再建第二个 sink。
            assertTrue("同根重定向应返回 true（空操作）", MmaLogger.redirectFileSink(publicRoot))
            assertEquals(1, MmaLogger.sessionSinkCountForTest())
            assertEquals(
                "同根短路不得重建会话目录",
                firstSessionDir!!.canonicalPath,
                MmaLogger.currentSessionLogDirectory()!!.canonicalPath,
            )
        } finally {
            MmaLogger.shutdown()
        }
    }

    @Test(timeout = 120_000)
    fun installTwice_isIdempotent() {
        val privateRoot = temporaryFolder.newFolder("private_root_twice")
        installSessionLayout(privateRoot)
        try {
            // @Synchronized + installed 检查：重复 install 不得叠加第二个文件出口。
            installSessionLayout(privateRoot)
            assertEquals(1, MmaLogger.sessionSinkCountForTest())
        } finally {
            MmaLogger.shutdown()
        }
    }
}
