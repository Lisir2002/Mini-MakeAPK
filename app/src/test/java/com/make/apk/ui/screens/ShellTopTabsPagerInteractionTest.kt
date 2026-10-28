package com.make.apk.ui.screens

import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.ShellAction
import com.make.apk.data.model.ShellIconKeys
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.data.model.ShellSelectionStyle
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.ShellTopBar
import com.make.apk.data.model.ShellTopTabs
import com.make.apk.data.model.VersionScheme
import com.make.apk.data.model.WebRuntimeConfig
import com.make.apk.data.model.WebSettings
import com.make.apk.ui.theme.MmaTheme
import com.make.apk.ui.viewmodel.ShellPreviewUiState
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 顶栏 tab 滑动切页（阶段 6.4 `ShellTopTabsPager`）**双向同步**补测
 * （QA 独立补测 · Robolectric 交互层）。
 *
 * ## 补什么
 *
 * 既有快照只锁了"渲染出 pager"这一帧，锁不住两条**时序**行为：
 *
 * 1. **跨页点击不回环**：点击第 3 个 tab → `selectedId` 变化 → `animateScrollToPage(2)`
 *    → 滚动中途 `pagerState.currentPage` 依次经过 1 → `LaunchedEffect(currentPage)`
 *    会对**中途页**回调 `onPageSelected` → 若宿主据此回写选中态，`LaunchedEffect(selectedId)`
 *    会被重启并**取消原动画、反向滚回中途页** → 用户"点 3 得 2"。
 *    这正是开发注释里宣称"各带幂等守卫"要防的回环，守卫只挡"最终一致"，
 *    挡不住"中途页"（id 与最终选中 id 不一致 → 守卫不命中）。
 * 2. **离屏页不销毁**：`HorizontalPager` 默认 `beyondViewportPageCount = 0`，
 *    非相邻页的 WebView 会被销毁，切回即整页重新 loadUrl（滚动位置/表单/加载态全丢）
 *    ——与 `ShellTopTabsPager` KDoc"各页保留自己的滚动位置与加载状态"相悖（见 QA 报告 D4）。
 *
 * ## 断言校准说明（修 D4 时同步）
 *
 * 原断言 `before + 1`（"往返一趟只多出 1 个 View"）是按**部分保留**（只留住当前页 + 相邻页）
 * 校准的；修复采用的是**保留全部页面**（`beyondViewportPageCount = 页数 - 1`），
 * 三页在首帧就全部常驻，往返切页**一个 View 都不会新建**。
 * 因此断言改为"首帧 3 个 + 往返后仍是 3 个"——比原 `before + 1` 更严
 * （原断言允许新建 1 个，新断言允许新建 0 个），属于加强而非弱化。
 */
@RunWith(RobolectricTestRunner::class)
class ShellTopTabsPagerInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 当前选中 tab id（模拟 VM 状态，点击回调原样回写）。 */
    private var selectedTabId by mutableStateOf<String?>(null)

    /** 每次创建内容 View 的流水（离屏页销毁检测用）。 */
    private val createdViews = CopyOnWriteArrayList<View>()

    // ── 跨页点击：落点与选中态必须一致 ──────────────────────────────────

    @Test
    fun clickThirdTab_landsOnAndSelectsThirdTab() {
        setContent()

        composeRule.onNodeWithText(TAB_THIRD).performClick()
        composeRule.waitForIdle()

        assertEquals("点击第 3 个 tab，选中态必须停在第 3 个 tab", TAB_THIRD_ID, selectedTabId)
    }

    // ── 离屏页：切页不得销毁重建 WebView ────────────────────────────────

    @Test
    fun goSecondTabAndBack_firstPageWebViewNotRecreated() {
        setContent()

        // 保留全部页面：三页在首帧就全部常驻（第 3 页虽不可见也已被组合）。
        assertEquals(
            "开启滑动切页后，三页的 WebView 应在首帧就全部常驻，而不是只留住可见页",
            TAB_COUNT,
            createdViews.size,
        )

        composeRule.onNodeWithText(TAB_SECOND).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(TAB_FIRST).performClick()
        composeRule.waitForIdle()

        // 任何一页被销毁重建都会让总数增加：往返后总数不变即"第 1 页还是原来那个实例"。
        assertEquals(
            "切到相邻页再切回，不得新建/销毁任何页（第 1 页的 WebView 必须仍是原实例）",
            TAB_COUNT,
            createdViews.size,
        )
    }

    // ── 隐藏条目：不成 tab、也不成页（下标基于可见列表） ────────────────

    /**
     * 修复 D7 前：pager 用全量 `topTabs.items`，隐藏条目照样生成一页且能滑到，
     * 而 tab 区只渲染可见条目 → "页比 tab 多一页"，且选中 id 的下标会整体错位一格。
     */
    @Test
    fun hiddenTab_producesNoPageAndSelectionStaysAligned() {
        setContent(spec = swipeSpecWithHiddenFirstTab())

        // 可见条目 2 个 → 只应有 2 页（隐藏条目既不渲染 tab，也不生成页）。
        assertEquals(
            "隐藏的 tab 条目不得生成 pager 页面（页数必须等于可见条目数）",
            2,
            createdViews.size,
        )

        // 点第 2 个**可见** tab：选中态必须落在它自己身上（下标基于过滤后的列表）。
        composeRule.onNodeWithText(TAB_THIRD).performClick()
        composeRule.waitForIdle()

        assertEquals("隐藏条目不得让选中态下标错位", TAB_THIRD_ID, selectedTabId)
        assertEquals("切页后仍不得凭空多出页面", 2, createdViews.size)
    }

    // ── 脚手架 ──────────────────────────────────────────────────────────

    private fun setContent(spec: ShellSpec = swipeSpec()) {
        createdViews.clear()
        selectedTabId = null
        composeRule.setContent {
            MmaTheme(darkTheme = false) {
                ShellPreviewScreen(
                    state = ShellPreviewUiState(
                        loading = false,
                        previewable = true,
                        apkName = "示例应用",
                        runtimeConfig = SAMPLE_CONFIG,
                        shellSpec = spec,
                        selectedTopTabId = selectedTabId,
                        currentUrl = "https://example.com",
                        isExampleContent = true,
                    ),
                    onBack = {},
                    onTopTabSelected = { item -> selectedTabId = item.id },
                    onBottomItemSelected = {},
                    onDrawerItemSelected = {},
                    onDrawerOpenChanged = {},
                    onShellAction = {},
                    webViewFactory = { context -> FrameLayout(context).also { createdViews += it } },
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** 三页顶栏 tab、滑动开启（内容款复制后开 `swipeEnabled`，条目 id 各不相同）。 */
    private fun swipeSpec(): ShellSpec = swipeSpecOf(visibleFirstTab = true)

    /**
     * 首条 tab **隐藏**（`visible = false`）的三条目契约：可见条目只剩「发现 / 我的」两个。
     *
     * 刻意让隐藏条目带 `selected = true`：若渲染层用全量列表算下标，
     * 选中态就会指向一个根本不存在于界面上的条目。
     */
    private fun swipeSpecWithHiddenFirstTab(): ShellSpec = swipeSpecOf(visibleFirstTab = false)

    private fun swipeSpecOf(visibleFirstTab: Boolean): ShellSpec = ShellSpec(
        topBar = ShellTopBar(enabled = true, showTitle = true, showBackButton = true),
        topTabs = ShellTopTabs(
            enabled = true,
            items = listOf(
                ShellMenuItem(
                    id = TAB_FIRST_ID,
                    title = TAB_FIRST,
                    iconRef = ShellIconKeys.HOME,
                    action = ShellAction.NavigateUrl("https://example.com/1"),
                    visible = visibleFirstTab,
                    selected = true,
                ),
                ShellMenuItem(
                    id = TAB_SECOND_ID,
                    title = TAB_SECOND,
                    iconRef = ShellIconKeys.DISCOVER,
                    action = ShellAction.NavigateUrl("https://example.com/2"),
                ),
                ShellMenuItem(
                    id = TAB_THIRD_ID,
                    title = TAB_THIRD,
                    iconRef = ShellIconKeys.PROFILE,
                    action = ShellAction.NavigateUrl("https://example.com/3"),
                ),
            ),
            swipeEnabled = true,
            selectionStyle = ShellSelectionStyle.INDICATOR,
        ),
    )

    private companion object {
        const val TAB_FIRST: String = "首页"
        const val TAB_SECOND: String = "发现"
        const val TAB_THIRD: String = "我的"
        const val TAB_FIRST_ID: String = "itm_page_first"
        const val TAB_SECOND_ID: String = "itm_page_second"
        const val TAB_THIRD_ID: String = "itm_page_third"
        /** 顶栏 tab 条目数（本用例构造的页数）。 */
        const val TAB_COUNT: Int = 3

        val SAMPLE_CONFIG: WebRuntimeConfig = WebRuntimeConfig(
            projectType = ProjectType.URL,
            apkName = "示例应用",
            packageName = "com.example.sample",
            versionScheme = VersionScheme.THREE_SEGMENT,
            versionName = "1.0.0",
            versionCode = 1,
            themeColor = 0xFF2563EB.toInt(),
            entry = ProjectEntry.Url("https://example.com"),
            webSettings = WebSettings(),
            isolationId = "webapp_pager_it",
        )
    }
}
