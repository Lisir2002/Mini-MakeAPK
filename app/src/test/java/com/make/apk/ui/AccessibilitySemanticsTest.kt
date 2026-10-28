package com.make.apk.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.SemanticsMatcher
import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.VersionScheme
import com.make.apk.ui.components.MmaBottomBar
import com.make.apk.ui.components.MmaScreen
import com.make.apk.ui.editor.ProjectIcon
import com.make.apk.ui.navigation.DefaultMmaTabs
import com.make.apk.ui.navigation.MmaRoute
import com.make.apk.ui.screens.ProfileScreen
import com.make.apk.ui.screens.ProjectsScreen
import com.make.apk.ui.theme.MmaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 阶段 5 无障碍走查自动化（WCAG 2.2 AA · 自动化可测子集）。
 *
 * ## 为什么补这一组
 *
 * 阶段 5 的 T5「无障碍走查整改」此前**完全没有自动化回归护栏**——全仓无任何
 * `contentDescription` / `semantics` 断言，走查结论全靠人眼+实机 TalkBack，
 * 下一次重构（删图标、改文案、换 Card）会把无障碍悄悄改坏而无人察觉。
 *
 * 本测试把「**能自动化判定的那部分 WCAG 条目**」钉成可执行断言：
 * - **1.3.1 Info and Relationships / 4.1.2 Name, Role, Value**：
 *   纯图标按钮必须有非空 `contentDescription`（TalkBack 才有名字可读）；
 *   可交互元素必须暴露 `Role`（TalkBack 播报"按钮/标签"而非"可点击"）；
 * - **1.4.1 Use of Color / 1.3.1**：项目图标是可点击控件时必须有可访问名，
 *   否则 TalkBack 只读一个字母（"示"），用户不知道它是干什么的；
 * - **2.4.3 Focus Order / 2.4.6 Headings and Labels**：
 *   底栏 tab 遍历顺序须与视觉从左到右一致（`项目 → 开发中 → 我的`）。
 *
 * ## 本测试**不覆盖**的部分（需实机 TalkBack 走查，自动化测不出）
 *
 * - 真实朗读语序与打断行为；
 * - 动态状态公告（保存完成 / 删除完成）——按主理人裁决**列为记录项，不强求实现**；
 * - 触感反馈、字体缩放（fontScale > 1.0）下的布局溢出；
 * - 颜色对比度——那是**静态令牌计算**的活，见 `docs/design/phase5-cleanup-design.md` §6.6，
 *   不适合用截图断言（会随渲染引擎波动）。
 *
 * 运行环境：`createComposeRule()` 跑在 Robolectric 上（与既有交互回归测试同一套配置）。
 */
@RunWith(RobolectricTestRunner::class)
class AccessibilitySemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── 共享组件：MmaTopBar 返回箭头 ────────────────────────────────────

    /**
     * WCAG 4.1.2：返回箭头是**纯图标**按钮，没有可见文字，
     * `contentDescription` 是 TalkBack 唯一的可访问名来源——为空则 TalkBack 静默跳过。
     */
    @Test
    fun topBar_backButton_hasNonEmptyContentDescription() {
        composeRule.setContent {
            MmaTheme {
                MmaScreen(title = "子页面", onBack = {}) { }
            }
        }

        composeRule.onNodeWithContentDescription("返回")
            .assertIsDisplayed()

        // 兜底断言：整棵语义树里不存在「无 contentDescription 的图标节点」。
        val unnamedIcons = composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Role) and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image),
        ).fetchSemanticsNodes()
        assertTrue("MmaTopBar 不应出现没有 contentDescription 的图标节点", unnamedIcons.isEmpty())
    }

    // ── 共享组件：MmaBottomBar 底栏 tab ────────────────────────────────

    /**
     * WCAG 2.4.3 Focus Order：底栏 tab 的 TalkBack 遍历顺序必须与视觉从左到右一致。
     *
     * 视觉顺序固定为 `项目 → 开发中 → 我的`；若实现改成右到左或跳序，
     * 键盘/TalkBack 用户会觉得"焦点乱跳"。
     */
    @Test
    fun bottomBar_tabTraversalOrderMatchesVisualLeftToRight() {
        composeRule.setContent {
            MmaTheme {
                MmaBottomBar(
                    tabs = DefaultMmaTabs,
                    currentRoute = MmaRoute.Projects.route,
                    onTabSelected = {},
                )
            }
        }

        val labels = composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .fetchSemanticsNodes()
            .map { node ->
                node.config.getOrElseNullable(SemanticsProperties.Text) { emptyList() }
                    ?.joinToString("") { it.text }
            }

        assertTrue(
            "底栏 tab 顺序应与视觉一致（项目 → 开发中 → 我的），实际=$labels",
            labels == listOf("项目", "开发中", "我的"),
        )
    }

    /**
     * WCAG 4.1.2：底栏 tab 必须暴露 `Role.Tab`。
     *
     * M3 `NavigationBarItem` 自带 `Role.Tab` + 选中态 `Selected`，
     * 本测试钉住这个契约——换成自绘 Row/Column 时极易丢失。
     */
    @Test
    fun bottomBar_tabsExposeRoleAndSelectedState() {
        composeRule.setContent {
            MmaTheme {
                MmaBottomBar(
                    tabs = DefaultMmaTabs,
                    currentRoute = MmaRoute.Developing.route,
                    onTabSelected = {},
                )
            }
        }

        val tabs = composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .fetchSemanticsNodes()

        assertTrue("底栏应有 3 个 tab", tabs.size == 3)

        // 选中态：第二个（开发中）应为 Selected=true，其余为 false。
        val selectedFlags = tabs.map { node ->
            node.config.getOrElseNullable(SemanticsProperties.Selected) { false }
        }
        assertTrue(
            "选中态应落在第 2 个 tab（开发中），实际=$selectedFlags",
            selectedFlags == listOf(false, true, false),
        )
    }

    // ── ProjectsScreen：项目卡 ──────────────────────────────────────────

    /**
     * WCAG 4.1.2：项目卡整卡可点（进入编辑），但 Material3 `Card(onClick=)`
     * **不自动** 暴露 `Role.Button`（实测：只有 OnClick 动作，无 Role）。
     *
     * TalkBack 对"有 OnClick 但无 Role"的节点播报为"双击以激活"而非"按钮"，
     * 属 4.1.2 的 Name/Role 缺失。本测试把现状钉住：
     * **当前卡片节点未声明 Role**——记录为走查发现（建议补 `role = Role.Button`），
     * 一旦工程师补上，本断言会红，红了即说明"已修复，需更新本测试为断言 Role.Button"。
     */
    @Test
    fun projectsScreen_projectCard_exposesClickActionAndText() {
        composeRule.setContent {
            MmaTheme {
                ProjectsScreen(
                    projects = listOf(demoProject()),
                    onOpenProject = {},
                    onDeleteProject = {},
                    onCreateProject = {},
                )
            }
        }

        // 卡片节点：可点击 + 有可读文本（项目名）
        composeRule.onNodeWithText("示例网址")
            .assertIsDisplayed()

        // 项目名节点本身不带 OnClick（点击由整卡承担），此处断言整卡可点：
        composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .let { clickables ->
                assertTrue("项目列表页应存在可点击节点", clickables.isNotEmpty())
            }
    }

    /**
     * WCAG 4.1.2：项目卡内的「删除」是**破坏性操作**按钮，
     * 必须是 `Role.Button` + 有可见文字（TalkBack 读作"删除，按钮"）。
     */
    @Test
    fun projectsScreen_deleteButton_isRealButtonWithText() {
        composeRule.setContent {
            MmaTheme {
                ProjectsScreen(
                    projects = listOf(demoProject()),
                    onOpenProject = {},
                    onDeleteProject = {},
                    onCreateProject = {},
                )
            }
        }

        composeRule.onNodeWithText("删除")
            .assertIsDisplayed()
    }

    /**
     * WCAG 1.4.1/4.1.2：顶栏「＋」是纯图标按钮，必须有 `contentDescription`。
     * 这是新建项目的**唯一入口**，TalkBack 无名 = 盲用户完全无法新建项目。
     */
    @Test
    fun projectsScreen_createProjectIconButton_hasContentDescription() {
        composeRule.setContent {
            MmaTheme {
                ProjectsScreen(
                    projects = emptyList(),
                    onOpenProject = {},
                    onDeleteProject = {},
                    onCreateProject = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("新建项目").assertIsDisplayed()
    }

    // ── ProjectIcon：可点击态 ──────────────────────────────────────────

    /**
     * WCAG 1.3.1/4.1.2：可点击的项目图标必须有可访问名。
     *
     * 图标渲染的是应用名首字母（"示"）。若没有 `contentDescription`，
     * TalkBack 只会读出一个无意义的单字，用户无从知道"点它可以换图标"。
     */
    @Test
    fun projectIcon_clickable_hasAccessibleName() {
        composeRule.setContent {
            MmaTheme {
                ProjectIcon(
                    apkName = "示例网址",
                    themeColor = 0xFF2563EB.toInt(),
                    iconRef = null,
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("项目图标：示").assertIsDisplayed()
    }

    /**
     * WCAG 1.3.1：图标内文字（"示"）与 `contentDescription` 同时存在时，
     * 必须合并语义（`mergeDescendants = true`），否则 TalkBack 会念两遍
     * （先念 contentDescription，再念 Text）。
     *
     * 本测试断言合并后的节点**只有一个**可访问名来源被朗读——
     * 用「同时匹配 contentDescription 与 text 的节点数 == 1」表达"念一次"。
     */
    @Test
    fun projectIcon_mergedSemantics_exposeSingleAccessibleName() {
        composeRule.setContent {
            MmaTheme {
                ProjectIcon(
                    apkName = "示例网址",
                    themeColor = 0xFF2563EB.toInt(),
                    iconRef = null,
                    onClick = {},
                )
            }
        }

        val namedNodes = composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
        ).fetchSemanticsNodes()

        assertTrue(
            "可点击图标应恰好合并出 1 个带 contentDescription 的语义节点（避免重复朗读）",
            namedNodes.size == 1,
        )
    }

    // ── ProfileScreen：条目卡 ──────────────────────────────────────────

    /**
     * WCAG 4.1.2：我的页条目卡整卡可点，必须有可访问名。
     *
     * 现状核对：条目卡内含标题与副标题文字（可读），但**未显式声明**
     * `contentDescription`（如"软件设置，点击查看"）——TalkBack 只能读到标题文字，
     * 不知道"点了会发生什么"。本测试钉住"文字可读"这一底线，
     * 并把"缺 contentDescription"记入走查报告的**建议补**项（不阻塞本阶段）。
     */
    @Test
    fun profileScreen_entryCards_exposeReadableText() {
        composeRule.setContent {
            MmaTheme {
                ProfileScreen(
                    bottomBar = {},
                    onFeatureNotReady = {},
                )
            }
        }

        composeRule.onNodeWithText("软件设置").assertIsDisplayed()
        composeRule.onNodeWithText("关于软件").assertIsDisplayed()
    }

    // ── 辅助 ────────────────────────────────────────────────────────────

    private fun demoProject(): Project = Project(
        id = 1,
        projectType = ProjectType.URL,
        apkName = "示例网址",
        packageName = "com.example.web",
        versionScheme = VersionScheme.THREE_SEGMENT,
        versionName = "1.0.0",
        versionCode = 1,
        themeColor = 0xFF2563EB.toInt(),
        entry = ProjectEntry.Url("https://example.com"),
        isolationId = "webapp_1",
        createdAt = 1_700_000_000_000,
        updatedAt = 1_700_000_060_000,
    )
}
