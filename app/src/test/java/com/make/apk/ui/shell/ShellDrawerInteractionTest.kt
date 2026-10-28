package com.make.apk.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.make.apk.data.model.ShellDrawer
import com.make.apk.data.model.ShellDrawerSource
import com.make.apk.data.model.ShellIconKeys
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.ShellTopBar
import com.make.apk.ui.theme.MmaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 抽屉开合**回环与交互**补测（QA 独立补测 · 阶段 6.5 · Robolectric 交互层）。
 *
 * ## 补什么
 *
 * [ShellHostTest] 的抽屉快照只锁了"展开后的像素"，锁不住四件事：
 * 1. **受控开合不回声**：外部 `open` 驱动 `DrawerState` 后，渲染层**不得**把同一值
 *    再回传 `onOpenChanged`（每次回声都是一次多余的 VM 状态写入，也是回环抖动的温床）；
 * 2. **点击条目后的回调顺序**：先 `onDrawerItemSelected` 再 `onDrawerOpenChanged(false)`
 *    （渲染层 KDoc 的承诺），顺序反了会让内容区"带着暂停态加载新页"；
 * 3. **选中态回退**：外部 id 已被删（悬空）时必须回落到契约 `selected = true` 的条目，
 *    且"无外部 id + 契约无默认"时**谁都不高亮**（抽屉是动作列表，不是分页导航）；
 * 4. **空态与 Web 源占位**是可见文案，不只是像素。
 */
@RunWith(RobolectricTestRunner::class)
class ShellDrawerInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 受控开合（模拟 VM 状态）。 */
    private var open by mutableStateOf(false)

    /**
     * 事件流水（按发生顺序）：`open=true/false`（开合回调）与 `select=<id>`（条目点击）。
     * 注意：外部直接置 `open` 造成的同步**不应**产生事件（不回声）。
     */
    private val eventLog = mutableListOf<String>()

    // ── 汉堡入口 ────────────────────────────────────────────────────────

    @Test
    fun menuEntryShown_whenDrawerAndTopBarEnabled() {
        setContent(drawer = drawerWithItems())

        composeRule.onNodeWithContentDescription("打开菜单").assertIsDisplayed()
    }

    @Test
    fun noMenuEntry_whenDrawerDisabled() {
        setContent(drawer = ShellDrawer(enabled = false))

        composeRule.onNodeWithContentDescription("打开菜单").assertDoesNotExist()
    }

    /**
     * **当前实现记录**：抽屉区开启但顶栏区关闭时，宿主不注入汉堡入口
     * （[ShellHost] 要求 `drawer.enabled && topBar.enabled`）。
     * 此刻抽屉只能靠**边缘手势**触达——`ModalNavigationDrawer.gesturesEnabled`
     * 默认开启，功能未死，但与 `ShellHost` KDoc"抽屉区开启 ⇒ 顶栏汉堡"的表述不一致，
     * 导出壳若不同样实现手势入口就会产生"预览 / 导出"分叉（见 QA 报告 D1）。
     */
    @Test
    fun noMenuEntry_whenTopBarDisabled() {
        setContent(drawer = drawerWithItems(), topBar = false)

        composeRule.onNodeWithContentDescription("打开菜单").assertDoesNotExist()
    }

    // ── 受控开合：不回声（外部驱动不得再回传同一值） ─────────────────────

    @Test
    fun externalOpen_showsDrawer_withoutEchoBack() {
        setContent(drawer = drawerWithItems())

        composeRule.runOnIdle { open = true }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(DRAWER_ITEM_ABOUT).assertIsDisplayed()
        assertTrue("外部驱动的展开不得回声到 onOpenChanged", eventLog.isEmpty())
    }

    @Test
    fun externalClose_hidesDrawer_withoutEchoBack() {
        setContent(drawer = drawerWithItems())

        composeRule.runOnIdle { open = true }
        composeRule.waitForIdle()
        composeRule.runOnIdle { open = false }
        composeRule.waitForIdle()

        assertTextGone(DRAWER_ITEM_ABOUT)
        assertTrue("外部驱动的收起不得回声到 onOpenChanged", eventLog.isEmpty())
    }

    // ── 条目点击 ────────────────────────────────────────────────────────

    /**
     * **未自动化说明**：`ShellDrawerSection` 的"条目点击 → 先 `onDrawerItemSelected`
     * 再 `onDrawerOpenChanged(false)`"这一顺序承诺，在本环境（Robolectric +
     * `createComposeRule`）无法注入验证——M3 `ModalDrawerSheet` 的内容位于独立
     * Dialog 窗口，语义树可读（选中态断言可用）但触摸坐标无法跨窗口注入
     * （点击会落到主窗口的遮罩上，表现为"点了遮罩关抽屉"）。
     * 该顺序目前只有快照与代码评审覆盖，建议在设备端 Espresso / 手工走查补位。
     */

    // ── 选中态回退 ──────────────────────────────────────────────────────

    @Test
    fun staleSelectedId_fallsBackToContractSelected() {
        setContent(
            drawer = drawerWithItems(),
            selectedDrawerItemId = "itm_deleted_elsewhere",
        )

        composeRule.runOnIdle { open = true }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(DRAWER_ITEM_HOME).assertIsSelected()
        composeRule.onNodeWithText(DRAWER_ITEM_ABOUT).assertIsNotSelected()
    }

    @Test
    fun noExternalIdAndNoContractDefault_noHighlightAtAll() {
        val items = listOf(
            ShellMenuItem(
                id = "itm_drawer_home",
                title = DRAWER_ITEM_HOME,
                iconRef = ShellIconKeys.HOME,
            ),
            ShellMenuItem(
                id = "itm_drawer_about",
                title = DRAWER_ITEM_ABOUT,
                iconRef = ShellIconKeys.PROFILE,
            ),
        )
        setContent(
            drawer = ShellDrawer(enabled = true, items = items),
            selectedDrawerItemId = null,
        )

        composeRule.runOnIdle { open = true }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(DRAWER_ITEM_HOME).assertIsNotSelected()
        composeRule.onNodeWithText(DRAWER_ITEM_ABOUT).assertIsNotSelected()
    }

    // ── 无障碍：条目图标不自带描述（避免 TalkBack 播两遍） ───────────────

    /**
     * 抽屉条目的图标若挂 `contentDescription = item.title`，而同一行的 label 文本也是
     * `item.title`，TalkBack 会把条目名播两遍。描述应由 label 承担，图标恒为 null。
     */
    @Test
    fun drawerItemIcon_hasNoOwnContentDescription() {
        setContent(drawer = drawerWithItems())

        composeRule.runOnIdle { open = true }
        composeRule.waitForIdle()

        assertEquals(
            "抽屉条目名不得由图标二次描述（label 已承担播报）",
            0,
            composeRule.onAllNodesWithContentDescription(DRAWER_ITEM_HOME).fetchSemanticsNodes().size,
        )
        // label 仍然是可读文本：无障碍没有因此丢信息。
        composeRule.onNodeWithText(DRAWER_ITEM_HOME).assertIsDisplayed()
    }

    // ── 内容源与空态 ────────────────────────────────────────────────────

    @Test
    fun webSource_showsExplicitPlaceholderCard() {
        setContent(
            drawer = ShellDrawer(
                enabled = true,
                contentSource = ShellDrawerSource.WEB,
                webMenuUrl = "https://example.com/menu",
            ),
        )

        composeRule.runOnIdle { open = true }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Web 覆盖层菜单").assertIsDisplayed()
        composeRule.onNodeWithText("https://example.com/menu").assertIsDisplayed()
    }

    @Test
    fun emptyNativeMenu_showsPlaceholder() {
        setContent(drawer = ShellDrawer(enabled = true, items = emptyList()))

        composeRule.runOnIdle { open = true }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("暂无菜单项").assertIsDisplayed()
    }

    // ── 脚手架 ──────────────────────────────────────────────────────────

    private fun setContent(
        drawer: ShellDrawer,
        topBar: Boolean = true,
        selectedDrawerItemId: String? = null,
    ) {
        eventLog.clear()
        open = false
        val spec = ShellSpec(
            topBar = ShellTopBar(enabled = topBar, showTitle = true, showBackButton = true),
            drawer = drawer,
        )
        composeRule.setContent {
            MmaTheme(darkTheme = false) {
                ShellHost(
                    spec = spec,
                    title = "示例应用",
                    selectedDrawerItemId = selectedDrawerItemId,
                    drawerOpen = open,
                    onDrawerOpenChanged = { value ->
                        eventLog += "open=$value"
                        open = value
                    },
                    onDrawerItemSelected = { item -> eventLog += "select=${item.id}" },
                ) { contentModifier ->
                    Box(modifier = contentModifier.fillMaxSize())
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun drawerWithItems(): ShellDrawer = ShellDrawer(
        enabled = true,
        items = listOf(
            ShellMenuItem(
                id = "itm_drawer_home",
                title = DRAWER_ITEM_HOME,
                iconRef = ShellIconKeys.HOME,
                selected = true,
            ),
            ShellMenuItem(
                id = "itm_drawer_about",
                title = DRAWER_ITEM_ABOUT,
                iconRef = ShellIconKeys.PROFILE,
            ),
        ),
    )

    /**
     * 断言文本不可见：节点不存在，或存在但不显示。
     * （M3 `ModalDrawerSheet` 关闭态是否仍持有节点属库实现细节，两种情况都算"不可见"。）
     */
    private fun assertTextGone(text: String) {
        val nodes = composeRule.onAllNodesWithText(text).fetchSemanticsNodes()
        if (nodes.isNotEmpty()) {
            composeRule.onAllNodesWithText(text)[0].assertIsNotDisplayed()
        }
    }

    private companion object {
        const val DRAWER_ITEM_HOME: String = "首页"
        const val DRAWER_ITEM_ABOUT: String = "关于"
    }
}
