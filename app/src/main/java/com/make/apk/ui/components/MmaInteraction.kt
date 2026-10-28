package com.make.apk.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

/**
 * 自绘控件的**无涟漪点击**。
 *
 * ## 为什么不用默认 clickable
 *
 * 原生 `clickable` 会套一层 Material 水波纹（`indication`）。本项目所有控件自绘，
 * 按压反馈一律由控件**自己的状态色 / 形状 morph** 表达（选中填充强调容器色、
 * 字段圆角变化、菜单项脱离 run），再叠一层系统涟漪会变成"两套反馈打架"，
 * 且涟漪的形状来自 Material 主题，绕过了我们的形状令牌。
 *
 * 无障碍不受影响：`semantics` 的 role / contentDescription 由各控件自行声明。
 */
internal fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = null,
        indication = null,
        onClick = onClick,
    )
