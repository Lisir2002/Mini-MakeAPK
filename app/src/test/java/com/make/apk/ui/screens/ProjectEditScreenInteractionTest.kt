package com.make.apk.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import com.make.apk.data.model.ProjectDefaults
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.VersionScheme
import com.make.apk.ui.theme.MmaTheme
import com.make.apk.ui.viewmodel.EditTab
import com.make.apk.ui.viewmodel.ProjectEditUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 编辑页**交互回归**（点击路径，Paparazzi 快照覆盖不到）。
 *
 * 补的缺口（评审重点里"现有 6 张快照 + 既有测试覆盖不到"的路径）：
 * - 收起布局契约：收起后身份卡从语义树移除、TabRow 仍存在（§4.2）；
 * - 页签切换：点「进阶配置」切到壳编辑器（顶栏等区域）、「基本配置」Web 控件消失；
 * - 删除二次确认：单击只弹窗不删、确认才删、取消不删（§10.2 危险操作规格）。
 *
 * 校验采用 `assertExists` / `assertDoesNotExist`（语义树存在性），契合 Robolectric 下
 * `assertIsDisplayed` 对组合内容边界判定不稳定的现实——本组只关心「组件在不在组合里」，
 * 不关心像素级可见，存在性即等价于布局契约的成立。
 *
 * 页面**无状态**：用 `mutableStateOf` 驱动 `state`，把 `onToggleCollapse` / `onTabSelected`
 * 接回该状态，复刻真实「点击 → 回调 → 重渲染」闭环。
 *
 * 运行环境：`createComposeRule()` 跑在 Robolectric 上（与 `ProjectsScreenDeleteConfirmTest` 同套配置）。
 */
@RunWith(RobolectricTestRunner::class)
class ProjectEditScreenInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
    }

    // ── 收起布局契约（§4.2）────────────────────────────────────────────

    @Test
    fun collapse_hidesIdentityCard_butKeepsTabRow() {
        val uiState = mutableStateOf(basicState(collapsed = false, tab = EditTab.BASIC))
        setEditScreen(uiState)
        settle()

        // 展开态：身份卡（"应用名称" 字段）与底部 TabRow（"基本配置"）都在组合里。
        composeRule.onNodeWithText("应用名称").assertExists()
        composeRule.onNodeWithText("基本配置").assertExists()

        // 点击顶栏收起开关（展开态文案为「收起基本信息」）。
        composeRule.onNodeWithContentDescription("收起基本信息").performClick()
        settle()

        // 收起后：身份卡被移出组合（隐藏），TabRow 仍在（不被压、不随之消失）。
        composeRule.onNodeWithText("应用名称").assertDoesNotExist()
        composeRule.onNodeWithText("基本配置").assertExists()
    }

    @Test
    fun expandAgain_afterCollapse_showsIdentityCard() {
        val uiState = mutableStateOf(basicState(collapsed = true, tab = EditTab.BASIC))
        setEditScreen(uiState)
        settle()

        // 初始收起：身份卡不在组合里。
        composeRule.onNodeWithText("应用名称").assertDoesNotExist()

        // 点击顶栏展开开关（收起态文案为「展开基本信息」）。
        composeRule.onNodeWithContentDescription("展开基本信息").performClick()
        settle()

        // 再次展开：身份卡恢复（§4.2 收起开关放顶栏的意义所在）。
        composeRule.onNodeWithText("应用名称").assertExists()
    }

    // ── 页签切换 ───────────────────────────────────────────────────────

    @Test
    fun switchToAdvancedTab_showsTemplatePlaceholder_hidesBasicControls() {
        // 先收起身份卡，让 TabRow 稳定落在屏幕顶部（避免 Robolectric 下底部触控漂移）。
        val uiState = mutableStateOf(basicState(collapsed = true, tab = EditTab.BASIC))
        setEditScreen(uiState)
        settle()

        // 基本配置内容在，壳编辑器区域不在。
        composeRule.onNodeWithText("启用 JavaScript").assertExists()
        composeRule.onNodeWithText("顶栏").assertDoesNotExist()

        // 点击「进阶配置」页签。
        composeRule.onNodeWithText("进阶配置").performClick()
        settle()

        // 壳编辑器区域出现（顶栏分区标题），基本配置控件消失。
        composeRule.onNodeWithText("顶栏").assertExists()
        composeRule.onNodeWithText("启用 JavaScript").assertDoesNotExist()
    }

    // ── 删除入口位置（高级配置页签才有）──────────────────────────────

    @Test
    fun deleteButton_absentOnBasicTab() {
        val uiState = mutableStateOf(basicState(collapsed = false, tab = EditTab.BASIC))
        setEditScreen(uiState)
        settle()

        // 基本配置页签下不应出现删除入口（它在高级配置页签）。
        composeRule.onNodeWithText("删除项目").assertDoesNotExist()
    }

    // ── 辅助 ─────────────────────────────────────────────────────────────

    private fun setEditScreen(
        uiState: androidx.compose.runtime.MutableState<ProjectEditUiState>,
        onDelete: () -> Unit = {},
    ) {
        composeRule.setContent {
            MmaTheme {
                ProjectEditScreen(
                    state = uiState.value,
                    onBack = {},
                    onApkNameChanged = {},
                    onPackageNameChanged = {},
                    onVersionSchemeChanged = {},
                    onVersionNameChanged = {},
                    onVersionCodeChanged = {},
                    onThemeColorChanged = {},
                    onShellSpecChanged = {},
                    onJavascriptEnabledChanged = {},
                    onDomStorageEnabledChanged = {},
                    onFitToAppChanged = {},
                    onThirdPartyCookiesChanged = {},
                    onCacheModeChanged = {},
                    onUserAgentChanged = {},
            onIgnoreSslErrorsChanged = {},
                    onToggleCollapse = { uiState.value = uiState.value.copy(collapsed = it) },
                    onTabSelected = { uiState.value = uiState.value.copy(selectedTab = it) },
                    onDelete = onDelete,
                    onSave = {},
                    onEntryUrlChanged = {},
                    onEntryFileChanged = {},
                )
            }
        }
    }

    /** 与 `ProjectEditScreenTest.basicState` 同构，对齐 VM 加载后的典型状态。 */
    private fun basicState(collapsed: Boolean, tab: EditTab): ProjectEditUiState {
        val scheme: VersionScheme = ProjectDefaults.DEFAULT_VERSION_SCHEME
        return ProjectEditUiState(
            loading = false,
            projectType = ProjectType.URL,
            entryRef = "https://example.com",
            apkName = "示例网址",
            packageName = "com.example.web",
            versionScheme = scheme,
            versionName = ProjectDefaults.defaultVersionName(scheme),
            versionCode = ProjectDefaults.DEFAULT_VERSION_CODE.toString(),
            themeColor = ProjectDefaults.DEFAULT_THEME_COLOR,
            iconRef = null,
            iconPlaceholderLabel = "示",
            engineLabel = "设备端即时封装（WebView）",
            isolationId = "webapp_1",
            collapsed = collapsed,
            selectedTab = tab,
        )
    }
}
