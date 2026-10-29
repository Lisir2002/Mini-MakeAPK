package com.make.apk.ui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.components.clickableNoRipple
import com.make.apk.data.model.ProjectType
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.icons.MmaIcons
import com.make.apk.ui.theme.ControlShapes
import com.make.apk.ui.theme.Motion
import com.make.apk.ui.theme.Spacing

/**
 * 项目类型选择器（§9 · 路线 A：`HorizontalPager` + rc9 深度重构）。
 *
 * rc9 重构对照（联网调研 2026-10：卡片轮播最佳实践）：
 * - **首卡即完整价值**：URL 是最高频选择，排在第一位，卡片自含图标/标题/描述，无需滑动即完整可用；
 * - **一致的卡片尺寸**：四卡同宽同高，滑动中不跳变；
 * - **可见性提示不只靠小圆点**：两侧邻居露出一段（peek）+ 底部**可点击**指示条（双输入通道）；
 * - **每卡一个决策**：图标徽标 + 标题 + 一句话描述 + 待开放徽标，无多余链接；
 * - **触感反馈**：滑动停稳时轻震（HapticFeedback.TextHandleMove，尊重系统触感设置）；
 * - **无障碍**：整卡归并语义播报、指示条 liveRegion 播报当前项。
 *
 * 交互契约（不变）：
 * - **准星恒在容器正中**：`PageSize.Fill` + 左右对称 `contentPadding`；
 * - **Pager → 状态**：`LaunchedEffect(currentPage)` 停稳回传；**状态 → Pager**：外部重置对齐。
 *
 * @param selected 当前选中类型；null 表示用户尚未确认（仅无 UI 的单测初值语义）。
 * @param onSelected 选中回调（滑动停止 / 点击卡片均触发）。
 */
@Composable
fun ProjectTypeSelector(
    selected: ProjectType?,
    onSelected: (ProjectType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val types = ProjectType.entries
    val pagerState = rememberPagerState(
        initialPage = types.indexOf(selected).coerceAtLeast(0),
    ) { types.size }
    val haptics = LocalHapticFeedback.current

    // Pager → 状态：滑动停稳（或首帧）即回传准星下的项 + 触感确认。
    LaunchedEffect(pagerState.currentPage) {
        val type = types.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onSelected(type)
    }

    // 状态 → Pager：外部重置选中项时滚动对齐（内部滑动到此已是同一页，不重复滚动）。
    LaunchedEffect(selected) {
        val index = types.indexOf(selected)
        if (index >= 0 && index != pagerState.currentPage) {
            pagerState.animateScrollToPage(index)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            HorizontalPager(
                state = pagerState,
                // 准星居中的**几何前提**：必显式声明 Fill（见类 KDoc）。
                pageSize = PageSize.Fill,
                // 对称 padding ⇒ 居中页两侧各露出邻居一截（可见性提示 + 下一卡预告）
                contentPadding = PaddingValues(horizontal = Spacing.space7),
                pageSpacing = Spacing.space3,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = RETICLE_OVERSHOOT / 2),
            ) { page ->
                val type = types[page]
                ProjectTypeCard(
                    type = type,
                    selected = type == selected,
                    onSelected = onSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 准星**后画**：叠在卡片之上；不可点击（否则拦截 Pager 的滑动手势）。
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.Center,
            ) {
                Reticle(modifier = Modifier.align(Alignment.Center))
            }
        }

        // 底部指示条：可点击直达对应页（调研要点：小圆点不该是唯一的"还有内容"信号，
        // 但有可点的指示条 + 两侧 peek 双通道后，它从"装饰"升级为"第二输入方式"）。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.space3)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "项目类型：${
                        types.getOrNull(pagerState.currentPage)?.let { displayLabelOf(it) } ?: ""
                    }"
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            types.forEachIndexed { index, type ->
                val active = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = INDICATOR_GAP / 2)
                        .size(
                            width = if (active) INDICATOR_ACTIVE_WIDTH else INDICATOR_DOT,
                            height = INDICATOR_DOT,
                        )
                        .clip(ControlShapes.pill)
                        .background(
                            if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        // 指示点可点：直达该类型（双输入通道，调研要点）。
                        .clickableNoRipple { onSelected(type) },
                )
            }
        }
    }
}

/**
 * 准星：居中竖线（§9.1）。纯装饰、无交互——点击/滑动一律交给 Pager，避免手势冲突。
 */
@Composable
private fun Reticle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(RETICLE_WIDTH)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = RETICLE_ALPHA)),
    )
}

/**
 * 单张类型卡（rc9：图标徽标 + 标题 + 一句话描述；选中主色容器 + 放大）。
 *
 * 无障碍：整卡归并语义，`contentDescription` 播报「项目类型：网址」；
 * 暂未开放的两项在描述里带「即将开放」，TalkBack 不会误以为可用。
 */
@Composable
private fun ProjectTypeCard(
    type: ProjectType,
    selected: Boolean,
    onSelected: (ProjectType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) SELECTED_SCALE else 1f,
        animationSpec = Motion.SoftSpring,
    )
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val descriptor = descriptorOf(type)

    CuteCard(
        modifier = modifier
            .scale(scale)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabelOf(type)
                role = Role.Button
            },
        onClick = { onSelected(type) },
        containerColor = containerColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.space4, vertical = Spacing.space4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.space2),
        ) {
            // 图标徽标：选中时容器色圆底 + 内容色图标（同一视觉语言的两档）
            Box(
                modifier = Modifier
                    .size(TYPE_BADGE_SIZE)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                MmaIcon(
                    iconRes = descriptor.iconRes,
                    contentDescription = null, // 整卡已有归并语义，图标保持装饰性
                    size = TYPE_BADGE_ICON_SIZE,
                    tint = contentColor,
                )
            }
            Text(
                text = displayLabelOf(type),
                style = if (selected) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = descriptor.description,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = contentColor,
            )
        }
    }
}

/** 类型呈现描述（rc9 单一事实源：标题共用 [displayLabelOf]，描述/图标/待开放只在此定义）。 */
private data class TypeDescriptor(
    val iconRes: Int,
    val description: String,
    val pending: Boolean,
)

private fun descriptorOf(type: ProjectType): TypeDescriptor = when (type) {
    ProjectType.URL -> TypeDescriptor(MmaIcons.TypeUrl, "输入网址，一键封装成应用", false)
    ProjectType.HTML -> TypeDescriptor(MmaIcons.TypeHtml, "内置网页源码，离线运行", false)
    ProjectType.VUE -> TypeDescriptor(MmaIcons.TypeVue, "Vue 工程运行时，即将开放", true)
    ProjectType.PHP -> TypeDescriptor(MmaIcons.TypePhp, "PHP 运行环境，即将开放", true)
}

/**
 * 项目类型的中文短标签（准星卡片 / 列表副行**共用**，避免同一概念两处文案漂移）。
 */
fun displayLabelOf(type: ProjectType): String = when (type) {
    ProjectType.URL -> "网址"
    ProjectType.HTML -> "HTML"
    ProjectType.VUE -> "Vue"
    ProjectType.PHP -> "PHP"
}

/** 项目类型的图标资源（对外兼容保留：选择器内部改走 [descriptorOf]）。 */
fun iconResOf(type: ProjectType): Int = descriptorOf(type).iconRes

/**
 * 类型是否**暂未开放**。
 *
 * 原因（产品决策）：VUE / PHP 工程需要内置运行时，包体会显著变大，且逻辑尚需打磨，
 * 本阶段仅做占位（来源区给说明 + 保存门禁拦截）。
 */
fun isPending(type: ProjectType): Boolean = descriptorOf(type).pending

/** 整卡的无障碍播报文案（含"即将开放"语义）。 */
private fun accessibilityLabelOf(type: ProjectType): String =
    if (isPending(type)) {
        "项目类型：${displayLabelOf(type)}，即将开放"
    } else {
        "项目类型：${displayLabelOf(type)}"
    }

/** 选中卡放大倍率（§9.1）。 */
private const val SELECTED_SCALE: Float = 1.06f

/** 准星竖线宽度。 */
private val RETICLE_WIDTH: Dp = 2.dp

/** 准星比卡片高出的总高度（上下各一半）。 */
private val RETICLE_OVERSHOOT: Dp = 6.dp

/** 图标徽标（圆底）边长。 */
private val TYPE_BADGE_SIZE: Dp = 48.dp

/** 徽标内图标尺寸。 */
private val TYPE_BADGE_ICON_SIZE: Dp = 24.dp

/** 指示点直径（视觉）。 */
private val INDICATOR_DOT: Dp = 6.dp

/** 激活指示条的加长宽度（M3E 惯例：形状表达选中）。 */
private val INDICATOR_ACTIVE_WIDTH: Dp = 20.dp

/** 指示点间距的一半（点间净距 = GAP；6 为间距令牌允许集内的最小正档）。 */
private val INDICATOR_GAP: Dp = 6.dp

/** 准星竖线透明度（主色柔化，避免抢过卡片内容）。 */
private const val RETICLE_ALPHA: Float = 0.38f
