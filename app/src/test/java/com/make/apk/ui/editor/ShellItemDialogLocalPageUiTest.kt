package com.make.apk.ui.editor

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.make.apk.data.model.ShellAction
import com.make.apk.data.model.ShellIconKeys
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.ui.theme.MmaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 条目弹窗「加载内容」**双模式**的交互回归（QA 补：改造前该弹窗无任何用例）。
 *
 * 只钉**行为契约**（存在性 + 可点性），不钉像素：
 *
 * - **空态**：项目内没有页面文件时，本地 Tab 给出可见提示、下拉禁用、确定不可点；
 * - **切 Tab 清空**：网页 → 本地 会把 URL 清掉（切回网页时确定不可点即为证），
 *   本地 → 网页 会把已选页面清掉（未选页面时确定不可点即为证）；
 * - **网页 Tab 不回退**：无协议地址仍自动补 `https://`；
 * - **编辑回落**：既有本地页面条目回来时落在本地 Tab 且保留原值；
 * - **失效拦截**：已选文件被删（不在候选集）时确定不可点；
 * - **其它字段回归**：图标 / 标题在双模式改造后仍随条目一起落库。
 *
 * 屏幕取 411×891（主流机型），保证弹窗完整可布局；矮屏布局另见
 * [dialogActionRow_isLaidOutOnShortScreens]（单独指定 320×470 视口）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav")
class ShellItemDialogLocalPageUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var items: MutableState<List<ShellMenuItem>>

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
    }

    private fun setEditor(initial: List<ShellMenuItem>, pages: List<String>) {
        items = mutableStateOf(initial)
        composeRule.setContent {
            MmaTheme {
                ShellItemListEditor(
                    label = "底栏条目",
                    items = items.value,
                    onItems = { items.value = it },
                    localPageFileNames = pages,
                )
            }
        }
        settle()
    }

    /** 「确定」按钮当前是否可点（禁用态由 `confirmEnabled` 决定，禁用时不挂 click 语义）。 */
    private fun confirmClickable(): Boolean =
        composeRule.onNodeWithText("确定").fetchSemanticsNode()
            .config.contains(SemanticsActions.OnClick)

    /**
     * 打开新建弹窗：列表里的「添加条目」按钮与弹窗标题同名，
     * 故取"带点击语义"的那一个（标题只是 Text，不可点）。
     */
    private fun openNewItemDialog() {
        val candidates = composeRule.onAllNodesWithText("添加条目")
        val index = candidates.fetchSemanticsNodes()
            .indexOfFirst { it.config.contains(SemanticsActions.OnClick) }
        assertTrue("必须找得到可点的「添加条目」按钮", index >= 0)
        candidates[index].performClick()
        settle()
    }

    private fun openEditDialog() {
        composeRule.onNodeWithContentDescription("编辑").performClick()
        settle()
    }

    // ── 空态：项目内没有可加载页面 ────────────────────────────────────

    @Test
    fun newItem_localPageTabWithoutCandidates_showsHintAndBlocksConfirm() {
        setEditor(initial = emptyList(), pages = emptyList())
        openNewItemDialog()

        composeRule.onNodeWithText(LOCAL_PAGE_TAB).performClick()
        settle()

        composeRule.onNodeWithText(NO_LOCAL_PAGE_HINT).assertExists()
        composeRule.onNodeWithContentDescription("本地页面：暂无可选页面").assertExists()
        assertFalse("无候选页面时确定必须不可点", confirmClickable())
    }

    @Test
    fun editItem_localPageTabWithoutCandidates_showsHintAndBlocksConfirm() {
        val seed = ShellMenuItem(id = "itm_1", title = "首页", action = ShellAction.NavigateUrl("https://example.com"))
        setEditor(initial = listOf(seed), pages = emptyList())
        openEditDialog()

        composeRule.onNodeWithText(LOCAL_PAGE_TAB).performClick()
        settle()

        composeRule.onNodeWithText(NO_LOCAL_PAGE_HINT).assertExists()
        assertFalse("无候选页面时确定必须不可点（含已填标题）", confirmClickable())
    }

    // ── 切 Tab 清空另一模式的值 ──────────────────────────────────────

    @Test
    fun switchTabs_clearsTheOtherModesValue() {
        val seed = ShellMenuItem(id = "itm_1", title = "关于", action = ShellAction.NavigateUrl("https://example.com"))
        setEditor(initial = listOf(seed), pages = listOf("index.html", "page/about.html"))
        openEditDialog()

        // 网页模式：URL 已填 → 确定可点。
        assertTrue("网页模式下已填地址应可确认", confirmClickable())

        // 网页 → 本地：URL 被清空（未选页面 → 确定不可点）。
        composeRule.onNodeWithText(LOCAL_PAGE_TAB).performClick()
        settle()
        assertFalse("切到本地 Tab 后未选页面，确定必须不可点", confirmClickable())

        // 本地 → 网页：URL 仍是空的（切回网页也不可点，证明本地模式的值没漏回 URL）。
        composeRule.onNodeWithText(WEB_URL_TAB).performClick()
        settle()
        assertFalse("切回网页 Tab 后地址已被清空，确定必须不可点", confirmClickable())
    }

    // ── 新建：本地模式的确认门禁 ─────────────────────────────────────

    @Test
    fun newItem_localPage_blocksConfirmUntilTitleAndSelectionAreReady() {
        setEditor(initial = emptyList(), pages = listOf("index.html", "page/about.html"))
        openNewItemDialog()
        composeRule.onNodeWithText(LOCAL_PAGE_TAB).performClick()
        settle()

        assertFalse("未填标题时确定不可点", confirmClickable())
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("关于")
        settle()
        assertFalse("只填标题、未选页面时确定仍不可点", confirmClickable())

        // 下拉只给项目内的页面文件（CSS / JS / 图片不可作为加载目标——候选集由
        // ProjectEditUiState.localPageFileNames 保障，此处只钉"菜单确实按它渲染"）。
        composeRule.onNodeWithContentDescription("本地页面：请选择页面").performClick()
        settle()
        composeRule.onNodeWithText("index.html").assertExists("候选必须含入口页")
        composeRule.onNodeWithText("page/about.html").assertExists("候选必须含子页面")

        // 注：菜单项点击在 Robolectric 下会被 Popup 的 scrim 吃掉（Popup 与宿主窗口
        // 不在同一输入坐标空间），因此"选中 → 产出 OpenLocalPage"改由
        // editExistingLocalPageItem_keepsOpenLocalPageAndOtherFields 覆盖
        // （两条路径共用同一段 onConfirm 构造逻辑）。
    }

    // ── 回归：网页 Tab 在双模式改造后仍可用 ──────────────────────────

    @Test
    fun newItem_webUrlTab_stillProducesNavigateUrlWithSchemeCompletion() {
        setEditor(initial = emptyList(), pages = listOf("index.html"))
        openNewItemDialog()

        // 默认落在网页 Tab：填标题 + 无协议地址，确认时补 https://（包容性不回退）。
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("首页")
        composeRule.onAllNodes(hasSetTextAction())[1].performTextInput("example.com")
        settle()

        assertTrue("网页模式下标题 + 地址齐备应可确认", confirmClickable())
        composeRule.onNodeWithText("确定").performClick()
        settle()

        assertEquals(
            "网页模式必须产出 NavigateUrl（无协议自动补 https://）",
            ShellAction.NavigateUrl("https://example.com"),
            items.value.single().action,
        )
        assertEquals("首页", items.value.single().title)
    }

    // ── 编辑：初始 Tab 回落与原值保留 ────────────────────────────────

    @Test
    fun editExistingLocalPageItem_keepsOpenLocalPageAndOtherFields() {
        val seed = ShellMenuItem(
            id = "itm_1",
            title = "关于",
            iconRef = ShellIconKeys.HOME,
            action = ShellAction.OpenLocalPage("page/about.html"),
            visible = false,
        )
        setEditor(initial = listOf(seed), pages = listOf("index.html", "page/about.html"))
        openEditDialog()

        composeRule.onNodeWithContentDescription("本地页面：page/about.html").assertExists()
        assertTrue("原值有效时确定可点", confirmClickable())

        composeRule.onNodeWithText("确定").performClick()
        settle()

        val saved = items.value.single()
        assertEquals(
            "本地页面条目经弹窗确认后必须仍是 OpenLocalPage（绝不退化成 URL）",
            ShellAction.OpenLocalPage("page/about.html"),
            saved.action,
        )
        assertEquals("图标字段不得被双模式改造冲掉", ShellIconKeys.HOME, saved.iconRef)
        assertEquals("标题不得被冲掉", "关于", saved.title)
        assertFalse("显隐开关不得被冲掉", saved.visible)
    }

    @Test
    fun editLocalPageItem_whenFileWasDeleted_blocksConfirm() {
        val seed = ShellMenuItem(id = "itm_1", title = "幽灵页", action = ShellAction.OpenLocalPage("page/ghost.html"))
        setEditor(initial = listOf(seed), pages = listOf("index.html", "page/about.html"))
        openEditDialog()

        composeRule.onNodeWithContentDescription("本地页面：请选择页面").assertExists()
        assertFalse("已选文件被删后确定必须不可点（不留悬空引用）", confirmClickable())
    }

    private companion object {
        const val LOCAL_PAGE_TAB: String = "本地页面"
        const val WEB_URL_TAB: String = "网页链接"
        const val NO_LOCAL_PAGE_HINT: String = "暂无本地页面，请先在项目代码页添加 .html 文件"
    }
}
