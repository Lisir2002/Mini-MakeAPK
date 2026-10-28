package com.make.apk.ui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.make.apk.ui.components.CuteCard
import com.make.apk.data.model.ProjectType
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.icons.MmaIcons
import com.make.apk.ui.theme.Motion
import com.make.apk.ui.theme.Spacing

/**
 * 项目类型准星选择器（§9 · 路线 A：`HorizontalPager`）。
 *
 * 交互契约：
 * - **准星恒在容器正中**：`PageSize.Fill` + **左右对称** `contentPadding` ⇒
 *   当前页恒占 `[padding, W - padding]`，其中心即容器中心，
 *   准星画在 `Box` 的 `Alignment.Center` 天然重合（**不手算偏移**，改 padding 也不漂）。
 * - **Pager → 状态**：`LaunchedEffect(currentPage)` 把"停在哪页"回传给调用方，
 *   首帧即回传第 0 项（URL），因此 `projectType` 在真实页面里不会停留在 null。
 * - **状态 → Pager**：`LaunchedEffect(selected)` 兜住**外部**重置（如页面重建恢复表单），
 *   内部滑动到此已是同一页，不会重复滚动。
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

    // Pager → 状态：滑动停止（或首帧）即回传准星下的项。
    LaunchedEffect(pagerState.currentPage) {
        val type = types.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        onSelected(type)
    }

    // 状态 → Pager：外部重置选中项时滚动对齐（内部滑动到此已是同一页，不重复滚动）。
    LaunchedEffect(selected) {
        val index = types.indexOf(selected)
        if (index >= 0 && index != pagerState.currentPage) {
            pagerState.animateScrollToPage(index)
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalPager(
            state = pagerState,
            // 准星居中的**几何前提**：必显式声明 Fill（见类 KDoc）。
            // 依赖脚手架默认值也能跑，但谁改成 PageSize.Fixed(...) 做实验，
            // 「当前页中心 == 容器中心」就不再成立，准星会静默跑偏而快照仍通过。
            pageSize = PageSize.Fill,
            // 对称 padding ⇒ 居中页两侧各露出邻居一截（§9.4 D-C2 邻居预览）
            contentPadding = PaddingValues(horizontal = Spacing.space7),
            pageSpacing = Spacing.space3,
            beyondViewportPageCount = 1,
            // 准星比卡片高 [RETICLE_OVERSHOOT]：靠 Pager 上下各留一半实现——
            // 容器因此比卡片高，准星 `fillMaxHeight` 自然上下各探出一半。
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
        // `matchParentSize` 让"准星图层"精确等于上面 Pager 的高度（= 卡片高 + 上下各 3dp），
        // 且**不参与**父 Box 的高度测量——否则 `fillMaxHeight` 会把容器撑到父约束上限
        // （独立渲染选择器时会变成整屏高的竖线）。
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.Center,
        ) {
            Reticle(modifier = Modifier.align(Alignment.Center))
        }
    }
}

/**
 * 准星：居中竖线（§9.1）。
 *
 * 纯装饰、无交互——点击/滑动一律交给 Pager，避免手势冲突。
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
 * 单张类型卡（§9.1：选中主色高亮 + 放大 + 加粗，其余灰暗）。
 *
 * 无障碍：整卡归并语义，`contentDescription` 播报「项目类型：网址」；
 * 暂未开放的两项在描述里补「敬请期待」，TalkBack 不会只念「Vue」让人误以为可用。
 *
 * 视觉取舍：暂未开放的两项**不做置灰**（置灰会被读成"禁用"，与 §9 四项并列设计冲突），
 * 改为卡片内小角标提示，详情交给下方来源区说明。
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
                .padding(vertical = Spacing.space4, horizontal = Spacing.space2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.space2),
        ) {
            MmaIcon(
                iconRes = iconResOf(type),
                contentDescription = null, // 整卡已有归并语义，图标保持装饰性
                size = TYPE_CARD_ICON_SIZE,
                tint = contentColor,
            )
            Text(
                text = displayLabelOf(type),
                style = if (selected) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
            )
            if (isPending(type)) {
                Text(
                    text = PENDING_HINT,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                )
            }
        }
    }
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

/** 项目类型的图标资源（准星卡片使用，登记见 [com.make.apk.ui.icons.MmaIcons]）。 */
fun iconResOf(type: ProjectType): Int = when (type) {
    ProjectType.URL -> MmaIcons.TypeUrl
    ProjectType.HTML -> MmaIcons.TypeHtml
    ProjectType.VUE -> MmaIcons.TypeVue
    ProjectType.PHP -> MmaIcons.TypePhp
}

/**
 * 类型是否**暂未开放**。
 *
 * 原因（产品决策）：VUE / PHP 工程需要内置运行时，包体会显著变大，且逻辑尚需打磨，
 * 本阶段仅做占位（来源区给说明 + 保存门禁拦截）。
 */
fun isPending(type: ProjectType): Boolean =
    type == ProjectType.VUE || type == ProjectType.PHP

/** 整卡的无障碍播报文案（含"暂未开放"语义）。 */
private fun accessibilityLabelOf(type: ProjectType): String =
    if (isPending(type)) {
        "项目类型：${displayLabelOf(type)}，$PENDING_HINT"
    } else {
        "项目类型：${displayLabelOf(type)}"
    }

/** 暂未开放卡片上的角标文案。 */
private const val PENDING_HINT: String = "敬请期待"

/** 选中卡放大倍率（§9.1）。 */
private const val SELECTED_SCALE: Float = 1.06f

/** 准星竖线宽度。 */
private val RETICLE_WIDTH: Dp = 2.dp

/** 准星比卡片高出的总高度（上下各一半）。 */
private val RETICLE_OVERSHOOT: Dp = 6.dp

/** 类型卡内图标尺寸。 */
private val TYPE_CARD_ICON_SIZE: Dp = 32.dp

/** 准星竖线透明度（主色柔化，避免抢过卡片内容）。 */
private const val RETICLE_ALPHA: Float = 0.38f
