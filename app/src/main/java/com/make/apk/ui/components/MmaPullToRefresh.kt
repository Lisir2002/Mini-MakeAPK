package com.make.apk.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.make.apk.ui.icons.MmaIcon
import com.make.apk.ui.icons.MmaIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.math.roundToInt

/**
 * MMA 自研下拉刷新容器（取代 `material3.PullToRefreshBox`）。
 *
 * ## 为什么不用官方 PullToRefreshBox
 *
 * 1. **会定死**：官方件靠外部布尔 `isRefreshing` 与内部动画状态联动，刷新回调
 *    瞬间返回（本项目列表本就是 Flow 驱动，重查常在个位数毫秒内完成）时，
 *    入场动画与收起动画竞争，指示器会停在半途不再复位。
 * 2. **样式不符**：官方 Indicator 是 primaryContainer 大药丸 + 内部箭头，
 *    与本项目「黑白 + 蓝点缀、全自绘」语言不搭。
 * 3. **只在可滚动内容上生效**：官方件依赖子节点的 nested scroll 分发，
 *    空态（非 LazyColumn）下拉完全无响应。
 *
 * ## 本实现的三条硬约束
 *
 * - **不可能卡死**：刷新动作包在 `try/finally` 里，且有 [REFRESH_TIMEOUT_MS] 超时兜底；
 *   任何异常 / 挂起路径结束后都强制 `isRefreshing = false` 并回弹到 0。
 * - **一定看得见**：刷新最快也要展示 [MIN_VISIBLE_MS]，成功后还额外保持
 *   [COMPLETED_HOLD_MS] 的完成态（对勾），避免"闪一下像没发生"。
 * - **状态自持**：刷新态由本组件内部维护，调用方只提供 `suspend` 动作，
 *   无从"忘记复位"——这是官方件卡死的结构性成因。
 */
@Composable
fun MmaPullToRefreshBox(
    onRefresh: suspend () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: MmaPullToRefreshState = rememberMmaPullToRefreshState(enabled = enabled),
    content: @Composable () -> Unit,
) {
    val currentAction = rememberUpdatedState(onRefresh)
    SideEffect {
        state.enabled = enabled
        state.refreshAction = { currentAction.value() }
    }
    DisposableEffect(state) { onDispose { state.dispose() } }

    Box(
        modifier = modifier
            .nestedScroll(state)
            // 慢速下拉（松手时速度为 0）不会派发 fling，故再挂一层
            // "指针抬起" 观察作为结算兜底，两条路径都进同一个 settle（内部防重入）。
            .pointerInput(state) { observeRelease(state::settle) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, state.distance.roundToInt()) },
        ) {
            content()
        }
        MmaPullIndicator(state = state, modifier = Modifier.align(Alignment.TopCenter))
    }
}

/** 记住一个 [MmaPullToRefreshState]（几何量按当前密度换算成像素）。 */
@Composable
fun rememberMmaPullToRefreshState(enabled: Boolean = true): MmaPullToRefreshState {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val state = remember(density, scope) {
        MmaPullToRefreshState(
            triggerPx = with(density) { TRIGGER_DISTANCE.toPx() },
            maxPx = with(density) { MAX_DISTANCE.toPx() },
            dragMultiplier = DRAG_MULTIPLIER,
            scope = scope,
        )
    }
    SideEffect { state.enabled = enabled }
    return state
}

/**
 * 下拉刷新状态机（同时是 [NestedScrollConnection]）。
 *
 * 位移单位统一为**像素**，`distance` 是阻尼后的**内容位移**（不是手指位移），
 * 回弹动画与手势均直接改写它，故不存在"两套位移"打架的情况。
 */
class MmaPullToRefreshState internal constructor(
    /** 触发阈值（阻尼后位移）：达到即刷新。 */
    private val triggerPx: Float,
    /** 位移上限：拖拽封顶，避免整屏被推走。 */
    private val maxPx: Float,
    /** 阻尼系数：手指位移 → 内容位移。 */
    private val dragMultiplier: Float,
    private val scope: CoroutineScope,
) : NestedScrollConnection {

    /** 当前内容下移距离（px，0 = 收起）。 */
    var distance: Float by mutableFloatStateOf(0f)
        private set

    /** 是否正在刷新（指示器转圈）。 */
    var isRefreshing: Boolean by mutableStateOf(false)
        private set

    /** 刷新成功后的完成态（显示对勾，短暂）。 */
    var completed: Boolean by mutableStateOf(false)
        private set

    /** 开关：关闭时容器只做普通 Box（手势层零开销）。 */
    var enabled: Boolean by mutableStateOf(true)

    /** 刷新动作（由容器在每次重组时写入，保证拿到最新的捕获值）。 */
    internal var refreshAction: (suspend () -> Unit)? = null

    /** 0..1 拉拽进度（用于指示器淡入）。 */
    val progress: Float
        get() = if (triggerPx <= 0f) 0f else (distance / triggerPx).coerceIn(0f, 1f)

    /** 是否已越过触发阈值（下拉中给用户"松手即刷新"的明确反馈）。 */
    val armed: Boolean
        get() = distance >= triggerPx

    private var animJob: Job? = null
    private var settling = false

    // ── 嵌套滚动：下拉由父消费上抛的未消费位移，上滑优先收起 ──────────
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!enabled || source != NestedScrollSource.UserInput) return Offset.Zero
        // 指示器已拉出时，向上滑先把它收回去（而不是先滚列表），
        // 这是所有下拉刷新实现的通行约定。
        if (available.y < 0f && distance > 0f) {
            val consumed = dragUp(-available.y)
            return Offset(0f, -consumed)
        }
        return Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (!enabled || source != NestedScrollSource.UserInput) return Offset.Zero
        if (available.y > 0f) {
            val consumedY = dragDown(available.y)
            return Offset(0f, consumedY)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        settle()
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        settle()
        return Velocity.Zero
    }

    /**
     * 结算一次手势：够阈值就刷新，不够就回弹。
     *
     * @return 启动的刷新任务（便于测试 `join` 等待收尾）；未满足启动条件时返回 null。
     */
    fun settle(): Job? {
        if (settling || isRefreshing || distance <= 0f) return null
        settling = true
        return scope.launch {
            try {
                if (distance >= triggerPx) {
                    isRefreshing = true
                    animateDistance(triggerPx)
                    val startedAt = System.nanoTime()
                    val ok = runCatching {
                        withTimeout(REFRESH_TIMEOUT_MS) { refreshAction?.invoke() }
                    }.isSuccess
                    val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
                    // 刷新太快会让用户以为"没反应"，补齐最小可见时长。
                    if (elapsedMs < MIN_VISIBLE_MS) delay(MIN_VISIBLE_MS - elapsedMs)
                    completed = ok
                    if (ok) delay(COMPLETED_HOLD_MS)
                }
                animateDistance(0f)
            } finally {
                // 结构性不卡死：任何路径（含超时 / 抛异常 / 取消）都必须复位。
                isRefreshing = false
                completed = false
                settling = false
                if (distance != 0f) animateDistance(0f)
            }
        }
    }

    /** 组件离场：取消在途动画与刷新任务。 */
    fun dispose() {
        animJob?.cancel()
        animJob = null
    }

    internal fun dragDown(delta: Float): Float {
        if (isRefreshing || settling) return 0f
        val before = distance
        distance = (distance + delta * dragMultiplier).coerceIn(0f, maxPx)
        return if (dragMultiplier == 0f) 0f else (distance - before) / dragMultiplier
    }

    internal fun dragUp(delta: Float): Float {
        val before = distance
        distance = (distance - delta * dragMultiplier).coerceAtLeast(0f)
        return if (dragMultiplier == 0f) 0f else (before - distance) / dragMultiplier
    }

    private suspend fun animateDistance(target: Float) {
        animJob?.cancel()
        val start = distance
        if (start == target) return
        val job = scope.launch {
            animate(
                start,
                target,
                0f,
                tween(durationMillis = SETTLE_ANIM_MS, easing = FastOutSlowInEasing),
            ) { value, _ -> distance = value }
        }
        animJob = job
        job.join()
    }
}

/** 观察指针抬起（慢速松手不派发 fling 的兜底路径）。 */
private suspend fun PointerInputScope.observeRelease(onRelease: () -> Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            if (event.changes.all { !it.pressed }) onRelease()
        }
    }
}

/**
 * 自绘下拉指示器：圆形小卡（surface 底 + outline 描边）内一枚刷新图标。
 *
 * 视觉随三个状态变化：下拉中转角度、刷新中转圈、完成后变对勾。
 * 位置随 `distance` 从顶栏下方滑入，`progress` 控制淡入——不做缩放弹跳
 * （P0：禁止弹性缓动）。
 */
@Composable
internal fun MmaPullIndicator(
    state: MmaPullToRefreshState,
    modifier: Modifier = Modifier,
) {
    val indicatorPx = with(LocalDensity.current) { INDICATOR_SIZE.toPx() }
    val offsetY = (state.distance - indicatorPx).roundToInt()
    Box(
        modifier = modifier
            .offset { IntOffset(0, offsetY) }
            .graphicsLayer { alpha = state.progress },
        contentAlignment = Alignment.Center,
    ) {
        MmaPullIndicatorContent(
            progress = state.progress,
            isRefreshing = state.isRefreshing,
            completed = state.completed,
        )
    }
}

/**
 * 指示器**纯内容**（无位移 / 无淡入）——截图测试直接钉本函数，
 * 三个状态各一张基线，与容器解耦（改布局不动基线）。
 */
@Composable
internal fun MmaPullIndicatorContent(
    progress: Float,
    isRefreshing: Boolean,
    completed: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(INDICATOR_SIZE)
            .background(color = colorScheme.surface, shape = CircleShape)
            .border(width = INDICATOR_BORDER, color = colorScheme.outlineVariant, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (completed) {
            MmaIcon(
                iconRes = MmaIcons.CheckCircle,
                contentDescription = "刷新完成",
                size = INDICATOR_ICON_SIZE,
                tint = colorScheme.primary,
            )
        } else if (isRefreshing) {
            SpinningRefreshIcon()
        } else {
            MmaIcon(
                iconRes = MmaIcons.Refresh,
                contentDescription = "下拉刷新",
                size = INDICATOR_ICON_SIZE,
                tint = if (progress >= 1f) colorScheme.primary else colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = progress * PULL_ROTATION_FULL },
            )
        }
    }
}

/** 刷新中转圈（仅在刷新态挂载，离场即停，不做无谓的无尽动画）。 */
@Composable
private fun BoxScope.SpinningRefreshIcon() {
    val transition: InfiniteTransition = rememberInfiniteTransition(label = "pull_refresh_spin")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SPIN_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )
    MmaIcon(
        iconRes = MmaIcons.Refresh,
        contentDescription = "正在刷新",
        size = INDICATOR_ICON_SIZE,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.graphicsLayer { rotationZ = rotation },
    )
}

/** 指示器直径（40dp：与列表行图标同级，视觉不抢内容）。 */
private val INDICATOR_SIZE: Dp = 40.dp

/** 指示器内图标尺寸。 */
private val INDICATOR_ICON_SIZE: Dp = 24.dp

/** 指示器描边宽度（1dp 磨砂容器描边，与全站描边语言一致）。 */
private val INDICATOR_BORDER: Dp = 1.dp

/** 触发阈值（阻尼后位移）：56dp。 */
private val TRIGGER_DISTANCE: Dp = 56.dp

/** 位移上限（阻尼后）：72dp。 */
private val MAX_DISTANCE: Dp = 72.dp

/** 阻尼系数：手指拉 100px，内容下移 60px（有"拽得动但不轻飘"的手感）。 */
private const val DRAG_MULTIPLIER: Float = 0.6f

/** 下拉过程中的最大转角度（到阈值转满一圈的半程，指示"松手即刷新"）。 */
private const val PULL_ROTATION_FULL: Float = 180f

/** 回弹 / 入场动画时长。 */
private const val SETTLE_ANIM_MS: Int = 220

/** 转圈一圈时长。 */
private const val SPIN_DURATION_MS: Int = 1000

/** 刷新最小可见时长：快于此时长补齐，避免"闪一下像没发生"。 */
private const val MIN_VISIBLE_MS: Long = 500L

/** 成功完成态保持时长（对勾停留）。 */
private const val COMPLETED_HOLD_MS: Long = 400L

/** 刷新超时兜底：超过即放弃等待并复位（杜绝"定死"）。 */
private const val REFRESH_TIMEOUT_MS: Long = 5_000L
