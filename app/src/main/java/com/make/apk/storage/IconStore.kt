package com.make.apk.storage

import android.content.Context
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

    /** 图标目录（不存在则创建；与 SettingMirror 同级的应用私有存储）。 */
    private fun iconsDir(): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    companion object {
        /** 图标目录名（filesDir 下）。 */
        const val DIR_NAME: String = "icons"
    }
}
