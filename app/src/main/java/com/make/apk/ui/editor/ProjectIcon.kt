package com.make.apk.ui.editor

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.make.apk.data.model.ProjectDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 项目图标（§2.6：默认占位 = 主题色底 + 应用名首字母）。
 *
 * 渲染规则：
 * - [iconRef] 非空且指向可解码的本地文件（见 [com.make.apk.storage.IconStore]）→
 *   渲染用户选择的真实图标（等比裁切铺满）；
 * - 文件不存在 / 解码失败 → 回落占位（**占位同时是解码失败的 fallback**，
 *   保证任何项目都有一个看得过去的图标）。
 *
 * 前景色按背景亮度取黑/白（WCAG 对比度兜底）：主题色可由用户在新建/编辑页改，
 * 固定一种前景色会出现"浅底白字"这类读不清的组合。
 *
 * @param apkName 应用名（取首字母；空名显示 "?"）。
 * @param themeColor ARGB 主题色（默认取 [ProjectDefaults.DEFAULT_THEME_COLOR]）。
 * @param iconRef 图标文件路径（[IconStore] 拷贝产物）；null 表示使用默认占位。
 * @param size 边长（列表行 40dp、添加页身份区 56dp）。
 * @param onClick 非空时可点击（如"选择图标"入口）。
 */
@Composable
fun ProjectIcon(
    apkName: String,
    themeColor: Int,
    iconRef: String?,
    modifier: Modifier = Modifier,
    size: Dp = PROJECT_ICON_SIZE,
    onClick: (() -> Unit)? = null,
) {
    val background = Color(themeColor)
    val label = iconPlaceholderLabelOf(apkName)

    // 异步解码：首帧占位（与快照基线一致），文件可解码后换真图。
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = iconRef) {
        value = withContext(Dispatchers.IO) { ProjectIconDecoder.decode(iconRef) }
    }
    val image = bitmap

    val semanticsModifier = Modifier.semantics(mergeDescendants = true) {
        contentDescription = "项目图标：$label"
        if (onClick != null) role = Role.Button
    }
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClickLabel = "选择图标") { onClick() }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(semanticsModifier)
            .then(clickModifier)
            .size(size)
            .clip(MaterialTheme.shapes.large)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null, // 整体语义已由外层播报
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = contentColorOn(background),
            )
        }
    }
}

/**
 * 图标文件解码器（独立对象便于单测：不挂 Compose 状态）。
 */
internal object ProjectIconDecoder {

    /** 解码本地图标文件；路径空 / 文件不存在 / 解码失败一律返回 null（回落占位）。 */
    fun decode(iconRef: String?): ImageBitmap? {
        val path = iconRef?.trim().orEmpty()
        if (path.isEmpty()) return null
        val file = java.io.File(path)
        if (!file.isFile) return null
        return runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
    }
}

/**
 * 图标占位文字：应用名首字母（大写），空名时为 `"?"`。
 *
 * **单一实现**（唯一事实来源）：组件与其它需要占位文字的地方一律走这里，
 * 不允许再各自拼一遍——两处实现必然漂移。
 *
 * 取 [Char.uppercaseChar] 而非 [Char.uppercase]：后者对 `ß` 返回**两个字符**（`"ß"`→`"SS"`），
 * 占位格只有一字符宽，撑爆后会把图标挤变形。
 */
internal fun iconPlaceholderLabelOf(apkName: String): String =
    apkName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: PLACEHOLDER_LABEL

/** 按背景亮度挑前景色（相对亮度阈值 0.5，标准 sRGB 加权系数）。 */
private fun contentColorOn(background: Color): Color {
    val luminance = 0.2126f * background.red +
        0.7152f * background.green +
        0.0722f * background.blue
    return if (luminance > LUMINANCE_THRESHOLD) {
        Color.Black // token-gate: allow: 背景是用户任意指定的主题色，须强制对比（WCAG 2.2 AA）
    } else {
        Color.White // token-gate: allow: 同上，亮/暗两极才能覆盖任意背景色
    }
}

/** 空应用名时的占位字符。 */
private const val PLACEHOLDER_LABEL: String = "?"

/** 前景色切换的相对亮度阈值。 */
private const val LUMINANCE_THRESHOLD: Float = 0.5f

/** 项目图标默认边长（添加页身份区）。 */
internal val PROJECT_ICON_SIZE: Dp = 56.dp
