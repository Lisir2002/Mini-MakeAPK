package com.make.apk.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.make.apk.data.model.ProjectDefaults

/**
 * 项目图标（§2.6：默认占位 = 主题色底 + 应用名首字母）。
 *
 * 阶段 3 无论 [iconRef] 是否为空都渲染占位——图标选择器尚未接入（顶栏/来源区的
 * 「选择图标」当前只给 Toast 提示），因此占位同时也是**解码失败的 fallback**：
 * 后续接入解码后，`iconRef` 非空时解码成功才换图，失败仍回落这里。
 *
 * TODO(后续阶段)：`iconRef != null` 时按 Uri / asset 解码为 `ImageBitmap` 并渲染；
 * 解码失败继续走占位，保证任何项目都有一个看得过去的图标。
 *
 * 前景色按背景亮度取黑/白（WCAG 对比度兜底）：主题色可由用户在编辑页改，
 * 固定一种前景色会出现"浅底白字"这类读不清的组合。
 *
 * @param apkName 应用名（取首字母；空名显示 "?"）。
 * @param themeColor ARGB 主题色（默认取 [ProjectDefaults.DEFAULT_THEME_COLOR]）。
 * @param iconRef 图标引用；阶段 3 不改变渲染结果。
 * @param size 边长（列表行 40dp、添加页身份区 56dp）。
 * @param onClick 非空时可点击（如"选择图标"入口）。
 */
@Composable
@Suppress("UNUSED_PARAMETER") // iconRef 本阶段只做预留：接入解码前渲染结果恒为占位
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
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = contentColorOn(background),
        )
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
