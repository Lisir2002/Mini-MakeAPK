package com.make.apk.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

// 动效：spring + 轻微回弹（DESIGN_LANGUAGE §6）。拒绝生硬线性过渡。
// 调用处须尊重系统"减少动效"设置（androidx.compose.foundation.gestures 或 Settings）。
object Motion {
    // 柔和弹性：stiffness≈300, dampingRatio≈0.3（轻微回弹）
    val SoftSpring = spring<Float>(stiffness = 300f, dampingRatio = 0.3f)

    val PressScaleTarget = 0.96f   // 按钮按压 scale 1 -> 0.96 -> 回弹
    val DurationFast     = 200     // ms 快
    val DurationStandard= 300      // ms 标准
    val DurationEnter   = 400      // ms 入场
    val StaggerMs       = 40L      // 列表项依次浮现间隔
}
