package com.make.apk.ui.permission

import androidx.annotation.DrawableRes

/**
 * 一条权限的二次确认弹窗文案规格。
 *
 * 设计规约（对齐 DESIGN_LANGUAGE「可爱简约」语气）：
 * - **说人话**：讲这个权限能让用户得到什么，不甩 `POST_NOTIFICATIONS` 这类权限名吓人；
 * - **三态文案分立**：首次说明 / 拒绝后解释 / 永久拒绝引导，语境不同措辞不同，
 *   共用一条文案是「弹窗机械化」的主要来源；
 * - 按钮文案默认值按 M3 无障碍要求（可读标签 + 48dp 触控区）取友好措辞，可按权限覆盖。
 *
 * @param title 弹窗标题（≤12 字，动宾结构，如「打包好了告诉你」）。
 * @param iconRes 权限表意图标（MmaIcons），放 primaryContainer 图标底内。
 * @param firstTimeMessage 首次说明：系统弹窗前的用途预告。
 * @param rationaleMessage 拒绝后解释：承认拒绝、补一句错过什么。
 * @param settingsMessage 设置引导：告诉用户开关在哪、回来能干什么。
 * @param confirmText 主按钮（继续申请 / 去设置）。
 * @param settingsConfirmText 设置引导态主按钮（覆盖默认时用）。
 * @param dismissText 次按钮（暂不）。
 */
data class MmaPermissionSpec(
    val title: String,
    @DrawableRes val iconRes: Int,
    val firstTimeMessage: String,
    val rationaleMessage: String,
    val settingsMessage: String,
    val confirmText: String = "好，去开启",
    val settingsConfirmText: String = "去设置",
    val dismissText: String = "以后再说",
)
