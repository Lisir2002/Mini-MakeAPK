package com.make.apk.ui.screens

import androidx.annotation.DrawableRes

/**
 * 「我的」页条目数据模型——Phase-2 加条目即加一条数据，页面零改动。
 *
 * @param iconRes 条目图标（SVG 矢量，见 [com.make.apk.ui.icons.MmaIcons]）。
 * @param title 条目标题。
 * @param subtitle 副标题（可空；说明条目去向或当前状态）。
 * @param onClick 点击行为（调用方注入；占位条目给轻反馈，真实条目走 Intent）。
 */
data class ProfileItem(
    @DrawableRes val iconRes: Int,
    val title: String,
    val subtitle: String? = null,
    val onClick: () -> Unit,
)
