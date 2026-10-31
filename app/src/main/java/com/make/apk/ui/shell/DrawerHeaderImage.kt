package com.make.apk.ui.shell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 抽屉头图**固定资产名**（W2 ⑨）。
 *
 * 每次导出只服务一个项目，无命名冲突；assets 保留二进制、无 res/raw 文件名限制
 * （与 mma_pages 同理由）。注入脚本 `inject_shell_spec.py` 的 `HEADER_IMAGE_ASSET`
 * 必须与本常量一致——双侧同键，"预览 == 导出"才成立。
 */
const val DRAWER_HEADER_ASSET: String = "mma_drawer_header.png"

/**
 * 抽屉头图共享解码器（W2 ⑨，预览与导出**同一物理函数**，判定对称由代码结构保证）。
 *
 * 来源两态（对调用方透明）：
 * 1. **预览侧**：[ref] 是 [com.make.apk.storage.DrawerHeaderStore] 落盘的绝对路径
 *    （文件真实存在）→ `BitmapFactory.decodeFile` 解码；
 * 2. **导出侧**：spec 里的 ref 是 app 设备的路径，导出壳上不存在 → 探测 assets
 *    固定名 [DRAWER_HEADER_ASSET]（注入脚本写入）并解码。
 *
 * 契约：**绝不抛异常**——ref 为空 / 文件缺失 / 解码失败一律返回 null，
 * 渲染层据此安全回落 TEXT 头部（图被删绝不渲染空块）。
 */
suspend fun decodeDrawerHeaderImage(context: Context, ref: String?): ImageBitmap? =
    withContext(Dispatchers.IO) {
        // ① 预览侧：绝对路径文件直读。
        val path = ref?.trim().orEmpty()
        if (path.isNotEmpty()) {
            val file = java.io.File(path)
            if (file.isFile) {
                val fromFile = runCatching {
                    BitmapFactory.decodeFile(path)?.asImageBitmap()
                }.getOrNull()
                if (fromFile != null) return@withContext fromFile
            }
        }
        // ② 导出侧：assets 固定名探测（ref 缺失 / 文件不存在时也尝试——导出壳的图
        //    不靠 ref 定位，靠注入管线的固定名）。
        runCatching {
            context.assets.open(DRAWER_HEADER_ASSET).use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
        }.getOrNull()
    }

/**
 * 头图合成器（W2 ⑨ 的**纯函数核心**，仿 [com.make.apk.storage.IconComposer]，可 Robolectric 单测）。
 *
 * 职责：**居中裁 2:1** + 缩放标准化到 [TARGET_WIDTH_PX]×[TARGET_HEIGHT_PX]（1024×512）。
 * 比例取 2:1：抽屉宽 240–400dp → 头图高 120–200dp，与 M3 drawer header 观感相称
 * （16:9 在窄抽屉偏矮）。裁剪走 Bitmap.createBitmap/createScaledBitmap，
 * 与 IconComposer 的纯像素混合不同——头图无需烘焙底色，Canvas 系 API 在
 * Robolectric 下仅用于尺寸断言，可靠性足够（测试只断言尺寸与成功与否，不比对像素内容）。
 */
object DrawerHeaderComposer {

    /** 标准化输出宽（px）。 */
    const val TARGET_WIDTH_PX: Int = 1024

    /** 标准化输出高（px，2:1）。 */
    const val TARGET_HEIGHT_PX: Int = 512

    /**
     * 居中裁 2:1 + 缩放标准化。
     *
     * 裁剪策略：源图比 2:1 更宽 → 保高、居中裁宽；更窄（或等于）→ 保宽、居中裁高。
     * 输出恒为 [TARGET_WIDTH_PX]×[TARGET_HEIGHT_PX]（小图放大会发糊，与图标策略一致：
     * 上传 UI 提示"建议横版图片"引导用户给足分辨率）。
     *
     * @return 裁剪后的 2:1 位图；源图非法 / 分配失败返回 null。
     */
    fun crop(source: Bitmap): Bitmap? = runCatching {
        val srcW = source.width
        val srcH = source.height
        if (srcW <= 0 || srcH <= 0) return@runCatching null
        val cropW: Int
        val cropH: Int
        if (srcW * TARGET_HEIGHT_PX >= srcH * TARGET_WIDTH_PX) {
            // 源比 2:1 更宽：保满高，裁宽。
            cropH = srcH
            cropW = srcH * TARGET_WIDTH_PX / TARGET_HEIGHT_PX
        } else {
            // 源比 2:1 更窄：保满宽，裁高。
            cropW = srcW
            cropH = srcW * TARGET_HEIGHT_PX / TARGET_WIDTH_PX
        }
        val x = (srcW - cropW) / 2
        val y = (srcH - cropH) / 2
        val cropped = Bitmap.createBitmap(source, x, y, cropW, cropH)
        Bitmap.createScaledBitmap(cropped, TARGET_WIDTH_PX, TARGET_HEIGHT_PX, true)
    }.getOrNull()

    /** 位图 → PNG 字节（落盘用）。 */
    fun toPng(bitmap: Bitmap): ByteArray {
        val buffer = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)
        return buffer.toByteArray()
    }

    /** 纯色位图（测试辅助）。 */
    fun solidColorBitmap(color: Int, width: Int = 64, height: Int = 32): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
}
