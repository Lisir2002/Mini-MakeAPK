package com.make.apk.data.model

import com.make.apk.data.json.RuntimeJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 壳契约（[ShellSpec]）容错与边界补充测试（QA 独立补测 · 阶段 6.1/6.2）。
 *
 * ## 补什么
 *
 * 工程师既有的 [ShellSpecContractTest] 已覆盖"主路径"（往返、未知 type 降级、脏 JSON 兜底），
 * 但以下**边界**未被钉死，任何一次重构都可能把它们悄悄改坏：
 *
 * 1. **容错降级的三档输入**（缺判别符 / 缺必需字段 / 类型不符）必须各自落到 [ShellAction.Unknown]，
 *    且**绝不抛异常**——这是"壳配置是写进 APK assets 的长期数据"这一前提的兜底保证；
 * 2. **空串 URL 可往返**：`NavigateUrl("")` 是 [ShellMenuItem.action] 的默认值，
 *    若序列化时被误判成"字段缺失"而降级为 Unknown，契约默认值就会在往返中变形；
 * 3. **[ShellSpec.specVersion] 缺省时必须回落到当前版本**——老数据没有该键时不得崩、不得写成 0；
 * 4. **[ShellTheme] 的强调色真值唯一**：默认跟随 L1 `themeColor`，
 *    显式覆写才用壳自己的色，且"覆写为 null"仍回落到 L1（不产生第二真值）；
 * 5. **[ShellIconKeys] 与 res id 分离**：契约里的 `iconRef` 只能是逻辑键，
 *    且预置子模板用到的键必须全部在逻辑键表内（防止有人把 `R.drawable.ic_x` 写进契约）。
 */
class ShellSpecContractExtraTest {

    // ── 1. 容错降级：三档坏输入 ──────────────────────────────────────────

    @Test
    fun action_missingTypeDiscriminator_degradesToUnknown() {
        val action = decodeFirstAction("""{"type":"url"}""".let { """{"specVersion":1,"topBar":{"actions":[{"id":"a","title":"t","action":$it}]}}""" })
        assertEquals(ShellAction.Unknown, action)
    }

    @Test
    fun action_missingRequiredUrlField_degradesToUnknown() {
        val action = decodeFirstAction(
            """{"specVersion":1,"topBar":{"actions":[{"id":"a","title":"t","action":{"type":"url"}}]}}""",
        )
        assertEquals(ShellAction.Unknown, action)
    }

    @Test
    fun action_missingRequiredPageIdField_degradesToUnknown() {
        val action = decodeFirstAction(
            """{"specVersion":1,"topBar":{"actions":[{"id":"a","title":"t","action":{"type":"internal_page"}}]}}""",
        )
        assertEquals(ShellAction.Unknown, action)
    }

    @Test
    fun action_notAnObject_degradesToUnknown_withoutThrowing() {
        // action 直接是字符串 / 数组 / 数字 —— 都不是合法动作对象，必须降级而非抛异常。
        listOf(""""url"""", """[1,2]""", """123""", """null""").forEach { rawAction ->
            val raw = """{"specVersion":1,"topBar":{"actions":[{"id":"a","title":"t","action":$rawAction}]}}"""
            val decoded = runCatching { RuntimeJson.decodeShellSpec(raw) }
            assertTrue("action=$rawAction 不应抛异常", decoded.isSuccess)
            assertEquals(
                "action=$rawAction 应降级为 Unknown",
                ShellAction.Unknown,
                decoded.getOrNull()?.topBar?.actions?.first()?.action,
            )
        }
    }

    @Test
    fun action_unknownDiscriminatorValue_degradesToUnknown() {
        val action = decodeFirstAction(
            """{"specVersion":1,"topBar":{"actions":[{"id":"a","title":"t","action":{"type":"teleport"}}]}}""",
        )
        assertEquals(ShellAction.Unknown, action)
    }

    @Test
    fun action_unknownNativeKind_degradesToUnknownEnumValue_withoutThrowing() {
        // 枚举登记了 UNKNOWN 兜底位：未知 kind 不得抛异常。
        val action = decodeFirstAction(
            """{"specVersion":1,"topBar":{"actions":[{"id":"a","title":"t","action":{"type":"native","kind":"TELEPORT"}}]}}""",
        )
        assertEquals(ShellAction.Native(ShellNativeAction.UNKNOWN), action)
    }

    @Test
    fun action_urlFieldWithWrongType_degradesToUnknown() {
        // url 必须是字符串。写成数字 / null / 对象时**不是合法动作**，
        // 必须降级为 Unknown（设计文档 §1.2.3「已知类型但字段缺失/类型不符 → 降级 Unknown」）。
        listOf("123", "null", """{"x":1}""", "true").forEach { badUrl ->
            val action = decodeFirstAction(
                """{"specVersion":1,"topBar":{"actions":[{"id":"a","title":"t","action":{"type":"url","url":$badUrl}}]}}""",
            )
            assertEquals(
                "url=$badUrl 类型不符，应降级为 Unknown（实际得到 $action）",
                ShellAction.Unknown,
                action,
            )
        }
    }

    @Test
    fun action_internalPageFieldWithWrongType_degradesToUnknown() {
        listOf("123", "null", """{"x":1}""").forEach { badPageId ->
            val action = decodeFirstAction(
                """{"specVersion":1,"topBar":{"actions":[{"id":"a","title":"t","action":{"type":"internal_page","pageId":$badPageId}}]}}""",
            )
            assertEquals(
                "pageId=$badPageId 类型不符，应降级为 Unknown（实际得到 $action）",
                ShellAction.Unknown,
                action,
            )
        }
    }

    // ── 2. 空串 URL 往返 ────────────────────────────────────────────────

    @Test
    fun navigateUrlWithEmptyUrl_roundTripsUnchanged() {
        val original = ShellMenuItem(id = "a", title = "空地址", action = ShellAction.NavigateUrl(""))
        val spec = ShellSpec(
            topBar = ShellTopBar(enabled = true, actions = listOf(original)),
        )
        val decoded = RuntimeJson.decodeShellSpec(RuntimeJson.encodeShellSpec(spec))
        assertEquals(ShellAction.NavigateUrl(""), decoded.topBar.actions.first().action)
    }

    @Test
    fun defaultMenuItemAction_isEmptyNavigateUrl_andSurvivesRoundTrip() {
        // ShellMenuItem.action 的默认值就是 NavigateUrl("")：往返后不能变成 Unknown。
        val spec = ShellSpec(topBar = ShellTopBar(enabled = true, actions = listOf(ShellMenuItem(id = "a", title = "t"))))
        val decoded = RuntimeJson.decodeShellSpec(RuntimeJson.encodeShellSpec(spec))
        assertEquals(ShellAction.NavigateUrl(""), decoded.topBar.actions.first().action)
    }

    // ── 3. specVersion 缺省回落 ─────────────────────────────────────────

    @Test
    fun specVersion_absentInJson_fallsBackToCurrentVersion() {
        val decoded = RuntimeJson.decodeShellSpec("""{"topBar":{"enabled":true}}""")
        assertEquals(ShellSpec.CURRENT_SPEC_VERSION, decoded.specVersion)
    }

    @Test
    fun specVersion_presentInJson_isPreserved() {
        val decoded = RuntimeJson.decodeShellSpec("""{"specVersion":7}""")
        assertEquals(7, decoded.specVersion)
    }

    // ── 4. 强调色真值唯一 ───────────────────────────────────────────────

    @Test
    fun theme_default_followsProjectThemeColor() {
        val projectColor = 0xFF112233.toInt()
        assertEquals(projectColor, ShellSpec.DEFAULT.theme.resolveAccent(projectColor))
    }

    @Test
    fun theme_explicitOverride_winsOverProjectThemeColor() {
        val projectColor = 0xFF112233.toInt()
        val override = 0xFF445566.toInt()
        val theme = ShellTheme(useProjectThemeColor = false, themeColorOverride = override)
        assertEquals(override, theme.resolveAccent(projectColor))
    }

    @Test
    fun theme_overrideDisabledButNoOverride_stillFallsBackToProjectThemeColor() {
        // 不允许出现"既不跟随 L1 又没有壳自己的色"这种无色状态（第二真值）。
        val projectColor = 0xFF112233.toInt()
        val theme = ShellTheme(useProjectThemeColor = false, themeColorOverride = null)
        assertEquals(projectColor, theme.resolveAccent(projectColor))
    }

    // ── 5. 逻辑键 vs res id ─────────────────────────────────────────────

    @Test
    fun presetIconRefs_areAllLogicalKeys() {
        val logicalKeys = setOf(
            ShellIconKeys.REFRESH,
            ShellIconKeys.ADD,
            ShellIconKeys.HOME,
            ShellIconKeys.DISCOVER,
            ShellIconKeys.PROFILE,
        )
        val used = ShellPresets.ALL.flatMap { preset ->
            preset.spec.topBar.actions.map { it.iconRef } +
                preset.spec.topTabs.items.map { it.iconRef } +
                preset.spec.bottomBar.items.map { it.iconRef }
        }.filterNotNull()
        assertTrue("预置子模板应至少用到若干逻辑键", used.isNotEmpty())
        assertTrue("预置子模板的 iconRef 必须是逻辑键：$used", used.all { it in logicalKeys })
    }

    @Test
    fun iconKeys_areDistinctLogicalNames_notResourceIds() {
        val keys = listOf(
            ShellIconKeys.REFRESH,
            ShellIconKeys.ADD,
            ShellIconKeys.HOME,
            ShellIconKeys.DISCOVER,
            ShellIconKeys.PROFILE,
        )
        assertEquals(keys.size, keys.toSet().size)
        // 逻辑键必须是可读短名（不是 "0x7f0a0001" 这类 res id 字面量）。
        keys.forEach { key ->
            assertTrue("逻辑键不应像 res id：$key", key.all { it.isLetter() })
        }
    }

    // ── 辅助 ────────────────────────────────────────────────────────────

    private fun decodeFirstAction(raw: String): ShellAction =
        RuntimeJson.decodeShellSpec(raw).topBar.actions.first().action
}
