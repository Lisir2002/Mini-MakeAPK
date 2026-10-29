package com.make.apk.storage

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 项目图标本地存储。
 *
 * 职责：把用户从系统相册（Photo Picker）选中的图片**拷贝**进应用私有目录
 * `filesDir/icons/`，返回可持久化的文件路径（写入 `Project.iconRef`）。
 *
 * 为什么拷贝而不是存 content:// URI：
 * - Photo Picker 授予的 URI 读权限在设备重启后不可靠，持久化路径才能保证
 *   列表页 / 编辑页 / 壳预览**任何时候**都能解码出图标；
 * - 拷贝即隔离：源图被用户删除不影响已有项目。
 *
 * 统一落为 `.png` 扩展名（[ProjectValidator.validateIconRef] 对非 png 给警告），
 * 内容按源字节写入——Android 解码按内容而非扩展名，扩展名仅用于校验友好。
 */
class IconStore(private val context: Context) {

    /**
     * 把 [source]（任意 content:// 或 file:// URI）拷入图标目录。
     *
     * @return 拷贝后的绝对路径；读取或写入失败返回 null（调用方回落 Toast，不阻断建项目）。
     */
    suspend fun copyToIconsDir(source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(iconsDir(), "icon-${UUID.randomUUID()}.png")
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            if (target.length() == 0L) {
                target.delete()
                return@runCatching null
            }
            target.absolutePath
        }.getOrNull()
    }

    /**
     * rc9 图标自定义主入口：**原始副本 + 底色合成**双落盘。
     *
     * - `icon-raw-<uuid>.png`：原始像素（换底色重合成的原料，永不烘焙）；
     * - `icon-<uuid>.png`：经 [IconComposer] 居中裁方 + 主题色烘焙的成品（写入 `Project.iconRef`）。
     *
     * @param backgroundArgb 项目主题色（ARGB）——透明源图垫此底色，避免启动器渲染成黑块。
     * @return 成品绝对路径；失败返回 null（调用方回落提示，不阻断建项目）。
     */
    suspend fun saveFromPicker(source: Uri, backgroundArgb: Int): String? = withContext(Dispatchers.IO) {
        runCatching {
            val rawBytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
                ?: return@runCatching null
            if (rawBytes.isEmpty()) return@runCatching null

            val uuid = UUID.randomUUID().toString()
            val raw = File(iconsDir(), "icon-raw-$uuid.png").apply { writeBytes(rawBytes) }

            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                ?: run { raw.delete(); return@runCatching null }
            val composed = IconComposer.compose(decoded, backgroundArgb)
                ?: run { raw.delete(); return@runCatching null }

            val target = File(iconsDir(), "icon-$uuid.png").apply { writeBytes(IconComposer.toPng(composed)) }
            target.absolutePath
        }.getOrNull()
    }

    /**
     * 换底色重合成：按 [iconRef] 找到同 UUID 的原始副本，用新 [backgroundArgb] 重出成品。
     *
     * 命名约定是唯一关联（`icon-raw-<uuid>` ↔ `icon-<uuid>`）：不引入额外状态列，
     * 原始副本丢失（用户清理）时返回 null（调用方保持原图标，不破坏现状）。
     *
     * @return 新成品绝对路径；无原始副本或合成失败返回 null。
     */
    suspend fun recompose(iconRef: String, backgroundArgb: Int): String? = withContext(Dispatchers.IO) {
        runCatching {
            val name = File(iconRef).name
            if (!name.startsWith("icon-") || name.startsWith("icon-raw-")) return@runCatching null
            val uuid = name.removePrefix("icon-").removeSuffix(".png")
            val raw = File(iconsDir(), "icon-raw-$uuid.png")
            if (!raw.isFile) return@runCatching null

            val decoded = BitmapFactory.decodeFile(raw.absolutePath) ?: return@runCatching null
            val composed = IconComposer.compose(decoded, backgroundArgb) ?: return@runCatching null
            val target = File(iconsDir(), "icon-$uuid.png").apply { writeBytes(IconComposer.toPng(composed)) }
            target.absolutePath
        }.getOrNull()
    }

    /** 图标目录（不存在则创建；与 SettingMirror 同级的应用私有存储）。 */
    private fun iconsDir(): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    companion object {
        /** 图标目录名（filesDir 下）。 */
        const val DIR_NAME: String = "icons"
    }
}
