package com.make.apk.storage

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.make.apk.ui.shell.DrawerHeaderComposer
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 抽屉头图本地存储（W2 ⑨，方案 B，仿 [IconStore]）。
 *
 * 职责：把用户从系统相册（Photo Picker）选中的图片**居中裁 2:1 + 标准化 1024×512**
 * 后拷入应用私有目录 `filesDir/drawer/`，返回可持久化的文件路径
 * （写入 `ShellDrawer.headerImageRef`）。
 *
 * 为什么拷贝 + 绝对路径（与 iconRef 同口径）：
 * - Photo Picker 授予的 URI 读权限在设备重启后不可靠，持久化路径才能保证
 *   预览 / 导出壳**任何时候**都能解码出头图；
 * - 拷贝即隔离：源图被用户删除不影响已有项目；
 * - 绝对路径快照跨设备不可移植——导出侧由注入管线写入固定 assets 名
 *   （`mma_drawer_header.png`）补位，见 [com.make.apk.ui.shell.decodeDrawerHeaderImage]。
 */
class DrawerHeaderStore(private val context: Context) {

    /**
     * 从 Photo Picker 保存头图：居中裁 2:1 → 1024×512 PNG。
     *
     * @return 落盘文件绝对路径；读取 / 解码 / 写入失败返回 null
     *   （调用方回落提示，不阻断编辑）。
     */
    suspend fun saveFromPicker(source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val rawBytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
                ?: return@runCatching null
            if (rawBytes.isEmpty()) return@runCatching null

            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                ?: return@runCatching null
            val composed = DrawerHeaderComposer.crop(decoded) ?: return@runCatching null

            val target = File(headerDir(), "header-${UUID.randomUUID()}.png")
                .apply { writeBytes(DrawerHeaderComposer.toPng(composed)) }
            target.absolutePath
        }.getOrNull()
    }

    /**
     * 移除头图文件（「移除图片」按钮用）。
     *
     * 静默容忍文件已不存在；只删 [path] 指向的自身文件，绝不递归清理目录
     * （目录里可能有其它项目 / 历史头图）。
     */
    fun delete(path: String?) {
        val target = path?.trim().orEmpty()
        if (target.isEmpty()) return
        runCatching { File(target).delete() }
    }

    /** 头图目录（不存在则创建）。 */
    private fun headerDir(): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    companion object {
        /** 头图目录名（filesDir 下，与注入管线的 assets 固定名无冲突——一个是私有目录一个是 APK 内）。 */
        const val DIR_NAME: String = "drawer"
    }
}
