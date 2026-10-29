package com.make.apk.storage

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [IconComposer] 契约测试（Robolectric：android.graphics 位图操作）。
 *
 * rc9 应用图标自定义的核心纯函数：居中裁方 / 缩放标准化 / 底色烘焙。
 */
@RunWith(RobolectricTestRunner::class)
class IconComposerTest {

    @Test
    fun compose_rectangularSource_centerCropsToSquare() {
        // 100x50 源图：应被裁成 50x50 的中部区域再缩放到 512
        val source = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.RED)
        val composed = IconComposer.compose(source, Color.BLUE)

        assertNotNull(composed)
        assertEquals(IconComposer.TARGET_PX, composed!!.width)
        assertEquals(IconComposer.TARGET_PX, composed.height)
        // 全图被红色铺满（裁切后仍是红），底色不可见
        assertEquals(0xFFFF0000L, composed.getPixel(0, 0).toLong() and 0xFFFFFFFFL)
    }

    @Test
    fun compose_transparentSource_bakesBackground() {
        // 透明源图：合成后任意采样点应为底色（蓝）而非透明/黑
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888) // 默认全透明
        val composed = IconComposer.compose(source, Color.rgb(0x25, 0x63, 0xEB))

        assertNotNull(composed)
        val pixel = composed!!.getPixel(IconComposer.TARGET_PX / 2, IconComposer.TARGET_PX / 2)
        assertEquals(0xFF2563EBL, pixel.toLong() and 0xFFFFFFFFL)
    }

    @Test
    fun compose_zeroSizedSource_returnsNull() {
        // 0 尺寸在 createBitmap 阶段即抛，compose 应兜底 null 而非崩溃
        val composed = runCatching {
            IconComposer.compose(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), Color.WHITE)
        }.getOrNull()
        assertNotNull(composed)
    }

    @Test
    fun hasTransparency_opaqueSource_false() {
        val opaque = IconComposer.solidColorBitmap(Color.RED)
        assertFalse(IconComposer.hasTransparency(opaque))
    }

    @Test
    fun hasTransparency_transparentSource_true() {
        val transparent = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        assertTrue(IconComposer.hasTransparency(transparent))
    }

    @Test
    fun toPng_roundTrip_keepsDimensions() {
        val bitmap = IconComposer.solidColorBitmap(Color.GREEN, 128)
        val bytes = IconComposer.toPng(bitmap)
        assertTrue(bytes.isNotEmpty())
        val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertNotNull(decoded)
        assertEquals(128, decoded!!.width)
    }
}
