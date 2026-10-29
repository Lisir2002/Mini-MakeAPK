package com.make.apk.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import com.make.apk.ui.editor.HighSettingsTab
import com.make.apk.ui.theme.MmaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 高级配置 Tab 删除的**交互回归**（§10.2 危险操作规格）。
 *
 * 为什么独立测 `HighSettingsTab` 而非整页：
 * 整页 `ProjectEditScreen` 在 Robolectric 下，EXPORT 页签的删除按钮位于底部滚动区，
 * 触屏注入易因「节点存在但零可见边界」静默落空；高级配置 Tab 单独挂载时删除按钮
 * 处于可视区，点击可靠（与 `ProjectsScreenDeleteConfirmTest` 同构——它也是把危险删除
 * 单独拎出来测）。
 *
 * 钉死四条（与列表页删除同款契约）：单击只弹窗不删 / 确认才删且只删一次 / 取消不删 /
 * 弹窗关闭。删对 id 的保证在导航层 `vm.delete(projectId)`，此处验证 UI 闸门正确性。
 *
 * 运行环境：`createComposeRule()` 跑在 Robolectric 上。
 */
@RunWith(RobolectricTestRunner::class)
class HighSettingsTabDeleteConfirmTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── 删除二次确认（破坏性操作的唯一闸门）────────────────────────────

    @Test
    fun clickDelete_showsConfirmDialog_andDoesNotDeleteYet() {
        val deleted = mutableListOf<Long>()
        setContent(onDelete = { deleted += 1L })

        // 此时弹窗未开，「删除项目」文案唯一指向触发按钮。
        composeRule.onNodeWithText("删除项目").performScrollTo().performClick()

        composeRule.onNodeWithText("确认删除").assertExists()
        composeRule.onNodeWithText("取消").assertExists()
        assertTrue("仅单击删除不得触发删除", deleted.isEmpty())
    }

    @Test
    fun confirmDelete_deletesExactlyOnce() {
        val deleted = mutableListOf<Long>()
        setContent(onDelete = { deleted += 1L })

        composeRule.onNodeWithText("删除项目").performScrollTo().performClick()
        composeRule.onNodeWithText("确认删除").performClick()

        assertEquals("确认后必须删除，且只触发一次", listOf(1L), deleted)
        composeRule.onNodeWithText("确认删除").assertDoesNotExist()
    }

    @Test
    fun cancelDelete_doesNotDelete() {
        val deleted = mutableListOf<Long>()
        setContent(onDelete = { deleted += 1L })

        composeRule.onNodeWithText("删除项目").performScrollTo().performClick()
        composeRule.onNodeWithText("取消").performClick()

        assertTrue("取消后不得删除", deleted.isEmpty())
        composeRule.onNodeWithText("确认删除").assertDoesNotExist()
    }

    // ── 辅助 ─────────────────────────────────────────────────────────────

    private fun setContent(onDelete: () -> Unit) {
        composeRule.setContent {
            MmaTheme {
            Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                // 真实 App 中 HighSettingsTab 始终处于可滚动的编辑页容器内；此处对齐该环境，
                // 使底部「删除项目」按钮可被 performClick 自动滚动入视，避免高度增长后点击落空。
                Column(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    HighSettingsTab(
                        apkName = "示例网址",
                        engineLabel = "设备端即时封装（WebView）",
                        isolationId = "webapp_1",
                        onDelete = onDelete,
                    )
                }
            }
            }
        }
    }
}
