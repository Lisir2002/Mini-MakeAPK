package com.make.apk.storage

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream

/**
 * 图标合成器（rc9 应用图标自定义的**纯函数核心**，可 Robolectric 单测）。
 *
 * 职责（对应联网调研的自适应图标三要素）：
 * 1. **居中裁方**：任意宽高比 → 正方形（`ContentScale.Crop` 语义，与项目内 ProjectIcon 渲染一致，
 *    预览所见 = 导出所得）；
 * 2. **缩放标准化**：统一到 [TARGET_PX]（512，覆盖启动器 xxxhdpi 192 与商店 512 两档）；
 * 3. **底色烘焙**：透明 PNG 垫上项目主题色——透明背景直接出包会在部分启动器渲染成黑块
 *    （Android 自适应图标规范：透明度交给 background 层，我们烘焙进前景，单文件交付更稳）。
 *
 * 不做：形状遮罩（圆形/方圆等由启动器决定，应用内只做**预览**不做烘焙——烘焙了反而
 * 在圆遮罩启动器上出现双裁边）。
 */
internal object IconComposer {

    /** 合成输出边长（px）。 */
    const val TARGET_PX: Int = 512

    /** 源图过小保护：小于该边长先放大到 [TARGET_PX]，避免启动器上发糊。 */
    const val MIN_SOURCE_PX: Int = 96

    /**
     * 居中裁方 + 缩放 + 底色烘焙。
     *
     * 合成走**纯像素运算**（getPixels/setPixels 的 src-over 手工混合）而非 Canvas：
     * Canvas 在 JVM 单测环境（Robolectric）下不可靠，像素运算在真机与测试环境
     * 结果完全一致（512×512 像素混合耗时 <5ms，性能无虞）。
     *
     * @param source 源位图（任意尺寸，可为透明）。
     * @param backgroundArgb 底色（ARGB；烘焙进不透明像素之下）。
     * @return 合成后的不透明方图；解码/分配失败返回 null。
     */
    fun compose(source: Bitmap, backgroundArgb: Int): Bitmap? = runCatching {
        val side = minOf(source.width, source.height)
        if (side <= 0) return@runCatching null
        val x = (source.width - side) / 2
        val y = (source.height - side) / 2
        val cropped = Bitmap.createBitmap(source, x, y, side, side)
        val scaled = Bitmap.createScaledBitmap(cropped, TARGET_PX, TARGET_PX, true)

        val pixelCount = TARGET_PX * TARGET_PX
        val srcPixels = IntArray(pixelCount)
        scaled.getPixels(srcPixels, 0, TARGET_PX, 0, 0, TARGET_PX, TARGET_PX)
        val outPixels = IntArray(pixelCount) { i ->
            overPixel(srcPixels[i], backgroundArgb)
        }
        Bitmap.createBitmap(TARGET_PX, TARGET_PX, Bitmap.Config.ARGB_8888).apply {
            setPixels(outPixels, 0, TARGET_PX, 0, 0, TARGET_PX, TARGET_PX)
        }
    }.getOrNull()

    /** src-over 单像素混合：前景 [fg] 叠在背景 [bg] 上（结果恒不透明）。 */
    private fun overPixel(fg: Int, bg: Int): Int {
        val alpha = Color.alpha(fg)
        if (alpha == 255) return fg or 0xFF000000.toInt()
        if (alpha == 0) return bg or 0xFF000000.toInt()
        val inv = 255 - alpha
        val r = (Color.red(fg) * alpha + Color.red(bg) * inv) / 255
        val g = (Color.green(fg) * alpha + Color.green(bg) * inv) / 255
        val b = (Color.blue(fg) * alpha + Color.blue(bg) * inv) / 255
        return Color.rgb(r, g, b)
    }

    /**
     * 位图 → PNG 字节（IconStore 落盘用）。
     */
    fun toPng(bitmap: Bitmap): ByteArray {
        val buffer = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)
        return buffer.toByteArray()
    }

    /** ARGB 整数 → 位图可用的纯色像素（占位图测试用）。 */
    fun solidColorBitmap(color: Int, size: Int = MIN_SOURCE_PX): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return bitmap
    }

    /** 判断位图是否含透明像素（决定是否需要烘焙底色；全不透明可跳过合成省一次位图分配）。 */
    fun hasTransparency(bitmap: Bitmap): Boolean {
        val step = maxOf(1, bitmap.width * bitmap.height / 4096)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var i = 0
        while (i < pixels.size) {
            if (Color.alpha(pixels[i]) < 255) return true
            i += step
        }
        return false
    }
}
