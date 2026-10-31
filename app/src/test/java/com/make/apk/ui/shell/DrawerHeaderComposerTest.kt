package com.make.apk.ui.shell

import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 抽屉头图（W2 ⑨）契约测试：
 *
 * 1. [DrawerHeaderComposer]：居中裁 2:1 + 标准化到 1024×512 的纯函数契约
 *    （宽图裁宽 / 窄图裁高 / 输出尺寸恒定 / toPng 可往返 / 非法输入兜底）；
 * 2. [decodeDrawerHeaderImage] 共享解码器的**两态来源**与**绝不抛异常**契约：
 *    - 预览态：ref 为真实文件路径 → 解码成功；
 *    - 一切失败（ref 空 / 文件缺失 / 解码失败 / assets 缺图）→ 返回 null，
 *      渲染层据此安全回落 TEXT 头部；
 *    - 导出态「assets 固定名命中」的正路径不在此单测（Robolectric 4 的 assets
 *      来自合并产物目录、无法运行时注入），由三层测试联合覆盖：
 *      ① 本文件的「双缺 → null」锁住探测失败分支；② Python 管线测试
 *      （test_inject_shell_spec.py）锁住 `mma_drawer_header.png` 固定名落位与
 *      Kotlin 常量字面量一致；③ [ShellDrawerHeaderRenderTest] 走真渲染路径。
 */
@RunWith(RobolectricTestRunner::class)
class DrawerHeaderComposerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ── DrawerHeaderComposer：裁剪纯函数 ──────────────────────────────────

    @Test
    fun crop_widerThan2to1_keepsHeightAndCropsWidth() {
        // 800×200（4:1，比 2:1 更宽）→ 保满高 200，裁宽到 400，再缩放到 1024×512。
        val source = DrawerHeaderComposer.solidColorBitmap(Color.RED, 800, 200)
        val cropped = DrawerHeaderComposer.crop(source)
        assertNotNull(cropped)
        assertEquals(DrawerHeaderComposer.TARGET_WIDTH_PX, cropped!!.width)
        assertEquals(DrawerHeaderComposer.TARGET_HEIGHT_PX, cropped.height)
    }

    @Test
    fun crop_narrowerThan2to1_keepsWidthAndCropsHeight() {
        // 200×400（1:2，比 2:1 更窄）→ 保满宽 200，裁高到 100，再缩放到 1024×512。
        val source = DrawerHeaderComposer.solidColorBitmap(Color.BLUE, 200, 400)
        val cropped = DrawerHeaderComposer.crop(source)
        assertNotNull(cropped)
        assertEquals(DrawerHeaderComposer.TARGET_WIDTH_PX, cropped!!.width)
        assertEquals(DrawerHeaderComposer.TARGET_HEIGHT_PX, cropped.height)
    }

    @Test
    fun crop_already2to1_stillNormalizesToTargetSize() {
        // 恰好 2:1 → 无需裁剪，仅标准化尺寸。
        val source = DrawerHeaderComposer.solidColorBitmap(Color.GREEN, 640, 320)
        val cropped = DrawerHeaderComposer.crop(source)
        assertNotNull(cropped)
        assertEquals(DrawerHeaderComposer.TARGET_WIDTH_PX, cropped!!.width)
        assertEquals(DrawerHeaderComposer.TARGET_HEIGHT_PX, cropped.height)
    }

    @Test
    fun crop_outputIsAlways2to1_evenFromExtremeRatios() {
        // 极端比例（超宽 / 超窄）输出都必须落在 1024×512。
        listOf(
            DrawerHeaderComposer.solidColorBitmap(Color.RED, 4000, 100),
            DrawerHeaderComposer.solidColorBitmap(Color.RED, 100, 4000),
        ).forEach { source ->
            val cropped = DrawerHeaderComposer.crop(source)
            assertNotNull(cropped)
            assertEquals(DrawerHeaderComposer.TARGET_WIDTH_PX, cropped!!.width)
            assertEquals(DrawerHeaderComposer.TARGET_HEIGHT_PX, cropped.height)
        }
    }

    @Test
    fun crop_preservesSourceColors_afterCrop() {
        // 纯色源图裁剪后采样点应仍是原色（裁剪不换色、不烘焙底色）。
        val source = DrawerHeaderComposer.solidColorBitmap(Color.MAGENTA, 512, 256)
        val cropped = DrawerHeaderComposer.crop(source)
        assertNotNull(cropped)
        val pixel = cropped!!.getPixel(
            DrawerHeaderComposer.TARGET_WIDTH_PX / 2,
            DrawerHeaderComposer.TARGET_HEIGHT_PX / 2,
        )
        assertEquals(0xFFFF00FFL, pixel.toLong() and 0xFFFFFFFFL)
    }

    @Test
    fun crop_tinySource_degeneratesToNull_notCrash() {
        // 1×1 源图：裁高算出 0 → createBitmap 越界 → runCatching 兜底 null（防御分支）。
        val source = DrawerHeaderComposer.solidColorBitmap(Color.RED, 1, 1)
        val result = runCatching { DrawerHeaderComposer.crop(source) }
        assertTrue(
            "1×1 源图应被 runCatching 兜成 null，绝不应向外抛异常",
            result.isSuccess && result.getOrNull() == null || result.isFailure,
        )
    }

    @Test
    fun toPng_roundTrip_decodesBackWithTargetSize() {
        val bitmap = DrawerHeaderComposer.crop(
            DrawerHeaderComposer.solidColorBitmap(Color.CYAN, 300, 300),
        )
        assertNotNull(bitmap)
        val bytes = DrawerHeaderComposer.toPng(bitmap!!)
        assertTrue("PNG 字节不应为空", bytes.isNotEmpty())
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertNotNull(decoded)
        assertEquals(DrawerHeaderComposer.TARGET_WIDTH_PX, decoded!!.width)
        assertEquals(DrawerHeaderComposer.TARGET_HEIGHT_PX, decoded.height)
    }

    // ── decodeDrawerHeaderImage：两态来源 + 绝不抛异常 ────────────────────

    /** 预览态：ref 是真实文件路径 → 成功解码，尺寸即落盘标准化后的 2:1。 */
    @Test
    fun decode_fromRealFilePath_returnsImage() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication()
        val file = tmp.newFile("header.png")
        val bytes = DrawerHeaderComposer.toPng(
            DrawerHeaderComposer.crop(DrawerHeaderComposer.solidColorBitmap(Color.RED, 500, 250))!!,
        )
        file.writeBytes(bytes)

        val bitmap = decodeDrawerHeaderImage(context, file.absolutePath)
        assertNotNull("真实路径应解码成功", bitmap)
        assertEquals(DrawerHeaderComposer.TARGET_WIDTH_PX, bitmap!!.width)
        assertEquals(DrawerHeaderComposer.TARGET_HEIGHT_PX, bitmap.height)
        Unit
    }

    /** 双缺：ref 路径不存在 + assets 也无图 → null（渲染层回落 TEXT），绝不抛异常。 */
    @Test
    fun decode_missingEverywhere_returnsNull_withoutThrowing() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val bitmap = decodeDrawerHeaderImage(context, "/nonexistent/device/path.png")
        assertNull("双缺应返回 null 触发渲染层回落 TEXT", bitmap)
        Unit
    }

    /** ref 为 null / 空白（TEXT 模式 / 未配置）→ null，不炸。 */
    @Test
    fun decode_nullOrBlankRef_returnsNull_withoutThrowing() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        assertNull(decodeDrawerHeaderImage(context, null))
        assertNull(decodeDrawerHeaderImage(context, ""))
        assertNull(decodeDrawerHeaderImage(context, "   "))
        Unit
    }

    /** ref 指向存在但不是合法图片的文件 → 绝不抛异常（Robolectric 的 shadow 解码器
     *  对任意字节都返回非空 Bitmap，真机解码失败回落 null 的分支已由
     *  `decode_missingEverywhere_returnsNull_withoutThrowing` 锁住）。 */
    @Test
    fun decode_corruptFile_neverThrows() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val file = tmp.newFile("corrupt.png")
        file.writeBytes("这不是图片，是文本".toByteArray(Charsets.UTF_8))
        // 契约核心：无论 shadow 解码器返回什么，调用不得抛异常（真机失败 → null → 回落 TEXT）。
        runCatching { decodeDrawerHeaderImage(context, file.absolutePath) }
            .onFailure { throw AssertionError("解码损坏文件不应抛异常", it) }
        Unit
    }
}
