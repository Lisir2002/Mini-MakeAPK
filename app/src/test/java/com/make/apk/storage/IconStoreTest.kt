package com.make.apk.storage

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [IconStore] 单测（Robolectric，覆盖 contentResolver 读取路径）。
 *
 * 覆盖：正常拷贝（png 落盘 + 内容一致 + 路径在 icons/ 下）、
 * 源不存在（null）、空文件（null，不留脏文件）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IconStoreTest {

    private lateinit var context: Context
    private lateinit var iconStore: IconStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        iconStore = IconStore(context)
    }

    @Test
    fun copyToIconsDir_copiesBytesIntoIconsDirWithPngName() = runBlocking {
        // 准备源文件（真字节），经 file:// URI 走 contentResolver 读取路径
        val source = File(context.cacheDir, "source-icon.png").apply {
            writeBytes("MMICON-TEST-Bytes-0123456789".toByteArray())
        }

        val path = iconStore.copyToIconsDir(Uri.fromFile(source))

        assertNotNull("拷贝应成功", path)
        val copied = File(path!!)
        assertTrue("应落在 icons/ 目录", copied.parentFile?.name == IconStore.DIR_NAME)
        assertEquals("扩展名应为 png", "png", copied.extension)
        assertTrue("内容应与源一致", copied.readBytes().contentEquals(source.readBytes()))
    }

    @Test
    fun copyToIconsDir_returnsNullWhenSourceMissing() = runBlocking {
        val missing = File(context.cacheDir, "no-such-icon.png")

        assertNull(iconStore.copyToIconsDir(Uri.fromFile(missing)))
    }

    @Test
    fun copyToIconsDir_returnsNullAndCleansUpForEmptySource() = runBlocking {
        val empty = File(context.cacheDir, "empty-icon.png").apply { createNewFile() }

        val path = iconStore.copyToIconsDir(Uri.fromFile(empty))

        assertNull("空文件应视为失败", path)
        assertTrue("不应残留空目标文件", File(context.filesDir, IconStore.DIR_NAME)
            .listFiles().isNullOrEmpty())
    }
}
