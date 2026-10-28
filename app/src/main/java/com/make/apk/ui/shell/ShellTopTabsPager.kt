package com.make.apk.ui.shell

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.collect

/**
 * 顶栏 tab 滑动切页容器（阶段 6.4 · L3，阶段 6.5 QA 时收口进 `ui/shell`）。
 *
 * **只有容器，没有内容**：本组件不知道"一页是什么"——每页内容由宿主通过 [content] 注入。
 * 因此预览屏塞 WebView、导出壳塞内联资产页，两侧的内容实现可以不同，
 * 但**切页行为只有这一份**（滚动落定 → 选中、选中 → 滚动、离屏页常驻），
 * 从而实现"预览 == 导出 ⟺ 同一份 ShellSpec + 同一份 ShellRenderer"。
 *
 * ## 双向同步（每条边只走一次）
 *
 * - **外部选中 → 滚动**：[selectedIndex] 变化驱动 `animateScrollToPage`；
 *   幂等守卫（已经停在目标页就不发起动画）杜绝"选中 → 滚动 → 落定 → 再滚动"的回环。
 * - **滚动落定 → 外部选中**：只订阅 [PagerState.settledPage]（滚动过程中该值**不更新**，
 *   仅在滚动结束时才变成落点页），故滑动途经的**中间页不会被回调**——
 *   读 `currentPage` 会让宿主把中途页回写成选中态，进而反向滚回中途页
 *   （"点第 3 个 tab 却落到第 2 个"，见阶段 6.6 QA D1）。
 *
 * 两路都带幂等守卫（下标一致即跳过），"点击 → 滚动 → 再回调"的回环不成立。
 *
 * ## 离屏页常驻（内存取舍）
 *
 * `HorizontalPager` 默认 `beyondViewportPageCount = 0` → 只有可见页被组合，
 * 滑到第 3 页时第 1 页已被销毁，切回即重建（WebView 重新 `loadUrl`，
 * 滚动位置 / 表单 / 加载态全丢，与"各页保留自己的加载状态"的承诺相悖）。
 * 本组件按 [keepAllPagesBeyondViewportCount] 保留**全部**页面，让该承诺成立。
 *
 * 代价是 N 个内容实例常驻内存：顶栏 tab 条目本阶段按 ≤ 5 设计（与底栏 `maxItems` 默认一致），
 * 全部常驻 ≈ 5 个 WebView，在**预览态**（页数为个位数、内存受控）可接受；
 * 导出壳若放开条目上限，应按可用内存另定保留窗口，不要直接照抄本值。
 *
 * 这里的取舍是**刻意**的：宁可多占 4 个 WebView 的常驻内存，也不要"切回就整页 reload"——
 * 后者会丢滚动位置与表单，且让"多 tab 多实例"这个卖点在体感上退化成单实例。
 *
 * ## 页数 = 可见 tab 条目数
 *
 * [pageCount] 由 [ShellHost] 用过滤过 `visible = false` 的条目列表计算，
 * 与 [ShellTopTabsSection] 渲染的 tab **逐字同源**——隐藏条目既不成 tab，也不成页（阶段 6.6 QA D7）。
 *
 * @param pageCount 页数（≥ 1；[ShellHost] 保证只在有可见条目时才用本组件）。
 * @param selectedIndex 当前选中页下标（基于可见条目列表，与 tab 区共用同一顺序）。
 * @param onPageSettled 滚动**落定**后的回调（真的换页时才回调，至多一次）。
 * @param modifier 修饰符（由 [ShellHost] 传入 `weight(1f)` 占位）。
 * @param content 每页内容；参数 = 页下标。
 */
@Composable
internal fun ShellTopTabsPager(
    pageCount: Int,
    selectedIndex: Int,
    onPageSettled: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = selectedIndex.coerceIn(0, lastPageIndex(pageCount)),
        pageCount = { pageCount },
    )
    // 始终读最新值：落定回调的收集协程**不**随它们变化而重启
    // （重启会重复读到旧落点，把"途中态"当成"新落点"回传给宿主）。
    val latestSelectedIndex by rememberUpdatedState(selectedIndex)
    val latestOnPageSettled by rememberUpdatedState(onPageSettled)

    // 外部选中（点击 tab）→ 滚动到对应页。
    // 这里的 `currentPage` **只**用于"要不要发起滚动动画"的幂等判断（它跟着动画走，
    // 是判断"已经在路上/已到位"的正确量），**不**用于回报选中态——回报只看 settledPage，
    // 见下方收集器。
    LaunchedEffect(selectedIndex) {
        val target = selectedIndex.coerceIn(0, lastPageIndex(pageCount))
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }

    // 滑动 / 抛掷落定 → 回调落点页（订阅 settledPage：滚动途中不发射，中途页永不上报）。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            if (settled != latestSelectedIndex) latestOnPageSettled(settled)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        beyondViewportPageCount = keepAllPagesBeyondViewportCount(pageCount),
    ) { page ->
        content(page)
    }
}

/**
 * 保留全部页面所需的 `beyondViewportPageCount`。
 *
 * 该值的语义是**可见页两侧各**多保留的页数，取 `pageCount - 1` 即可保证
 * 无论停在哪一页，全部页都落在组合范围内 → 切页不再销毁任何页。
 */
internal fun keepAllPagesBeyondViewportCount(pageCount: Int): Int =
    (pageCount - 1).coerceAtLeast(0)

/** 最后一页下标（空 pager 退化为 0）。 */
private fun lastPageIndex(pageCount: Int): Int = (pageCount - 1).coerceAtLeast(0)
