package com.make.apk.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import com.make.apk.data.model.ShellBottomBar
import com.make.apk.data.model.ShellMenuItem
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.ShellTopBar
import com.make.apk.ui.theme.MmaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 壳四区开关的**渲染组合**补测（QA 独立补测 · 阶段 6.2 · Robolectric 交互层）。
 *
 * ## 补什么
 *
 * 工程师既有的 [ShellHostTest] 是 Paparazzi 快照（有像素基线，但**不表达"某个元素不该存在"**这件事）；
 * 本测试用语义树断言把"四区 `enabled = false` 时对应 UI **完全不渲染**"钉成可执行断言——
 * 这正是评审要求"四区开关的四种组合（有/无顶栏 × 有/无底栏）"的可测形态。
 *
 * 组合矩阵：
 *
 * | 顶栏 | 底栏 | 期望顶栏标题 | 期望底栏条目 |
 * |---|---|---|---|
 * | 关 | 关 | 不存在 | 不存在 |
 * | 开 | 关 | 存在 | 不存在 |
 * | 关 | 开 | 不存在 | 存在 |
 * | 开 | 开 | 存在 | 存在 |
 *
 * 顺带覆盖"顶栏返回按钮只在 `onBack != null` 时出现"与"顶栏标题 `titleOverride` 优先于项目名"。
 */
@RunWith(RobolectricTestRunner::class)
class ShellHostZoneCombinationTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── 四区组合矩阵 ────────────────────────────────────────────────────

    @Test
    fun allZonesOff_rendersNeitherTopBarNorBottomBar() {
        setShell(spec = ShellSpec.DEFAULT)

        composeRule.onAllNodesWithText("示例应用").assertCountEquals(0)
        composeRule.onAllNodesWithText("底栏条目").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("返回").assertDoesNotExist()
    }

    @Test
    fun topBarOnly_rendersTopBarButNoBottomBar() {
        setShell(spec = specWith(topBar = true, bottomBar = false))

        composeRule.onNodeWithText("示例应用").assertIsDisplayed()
        composeRule.onAllNodesWithText("底栏条目").assertCountEquals(0)
    }

    @Test
    fun bottomBarOnly_rendersBottomBarButNoTopBar() {
        setShell(spec = specWith(topBar = false, bottomBar = true))

        composeRule.onAllNodesWithText("示例应用").assertCountEquals(0)
        composeRule.onNodeWithText("底栏条目").assertIsDisplayed()
    }

    @Test
    fun topBarAndBottomBarBoth_renderBoth() {
        setShell(spec = specWith(topBar = true, bottomBar = true))

        composeRule.onNodeWithText("示例应用").assertIsDisplayed()
        composeRule.onNodeWithText("底栏条目").assertIsDisplayed()
    }

    // ── 顶栏细节 ────────────────────────────────────────────────────────

    @Test
    fun topBar_backButtonAbsent_whenNoBackCallback() {
        composeRule.setContent {
            MmaTheme { shellBody(spec = specWith(topBar = true, bottomBar = false), onBack = null) }
        }
        composeRule.onNodeWithContentDescription("返回").assertDoesNotExist()
    }

    @Test
    fun topBar_backButtonShown_whenBackCallbackProvided() {
        composeRule.setContent {
            MmaTheme { shellBody(spec = specWith(topBar = true, bottomBar = false), onBack = {}) }
        }
        composeRule.onNodeWithContentDescription("返回").assertIsDisplayed()
    }

    @Test
    fun topBar_titleOverrideTakesPrecedenceOverProjectName() {
        val spec = specWith(topBar = true, bottomBar = false)
            .copy(topBar = specWith(topBar = true, bottomBar = false).topBar.copy(titleOverride = "自定义标题"))

        composeRule.setContent {
            MmaTheme { shellBody(spec = spec, onBack = {}) }
        }

        composeRule.onNodeWithText("自定义标题").assertIsDisplayed()
        composeRule.onAllNodesWithText("示例应用").assertCountEquals(0)
    }

    // ── 脚手架 ──────────────────────────────────────────────────────────

    private fun setShell(spec: ShellSpec) {
        composeRule.setContent {
            MmaTheme { shellBody(spec = spec, onBack = {}) }
        }
    }

    @Composable
    private fun shellBody(spec: ShellSpec, onBack: (() -> Unit)?) {
        ShellHost(
            spec = spec,
            title = "示例应用",
            onBack = onBack,
        ) { contentModifier ->
            Box(modifier = contentModifier.fillMaxSize())
        }
    }

    private fun specWith(topBar: Boolean, bottomBar: Boolean): ShellSpec = ShellSpec(
        topBar = ShellTopBar(
            enabled = topBar,
            showTitle = true,
            showBackButton = true,
            actions = emptyList(),
        ),
        bottomBar = ShellBottomBar(
            enabled = bottomBar,
            items = listOf(
                ShellMenuItem(id = "b1", title = "底栏条目"),
            ),
        ),
    )
}
