package com.make.apk.logging

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log

/**
 * 剪贴板写入（日志详情页"复制全部"的落地点）。
 *
 * 分层原因：Screen 层必须零 IO/系统服务调用（否则 Paparazzi 截图测试要为每张图准备
 * ClipData 桩，且页面会依赖真实系统状态）。故复制动作由路由层调用本函数完成。
 *
 * 刻意**全函数吞异常**：复制失败最多是用户手动长按选择，绝不能因为剪贴板不可用
 * （后台进程 / 受限设备 / ROM 裁剪）而崩溃。
 *
 * 标签通过 [ClipData.newPlainText] 的 label 承载（部分 ROM 的剪贴板管理器中可见）；
 * 不使用 API 33 才有的 `setPrimaryClip(clip, label)` 重载，以兼容低版本编译目标。
 *
 * @param label 剪贴板条目标签。
 * @param text 要复制的文本。
 * @return 是否成功写入。
 */
fun copyToClipboard(context: Context, label: String, text: String): Boolean = runCatching {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return@runCatching false
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
    true
}.getOrElse { error ->
    runCatching { Log.w(TAG, "复制日志到剪贴板失败", error) }
    false
}

/** 日志标签（复制失败时留痕，便于排查 ROM 兼容问题）。 */
private const val TAG: String = "MmaClipboard"
