package com.make.apk.data.model

import com.make.apk.data.json.RuntimeJson
import com.make.apk.data.mapper.toDomain
import com.make.apk.data.local.entity.ProjectEntity
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 壳契约（[ShellSpec]）与容错序列化器（[ShellActionSurrogate]）单测。
 *
 * 覆盖：
 * - 默认壳四区全关（`ShellSpec.DEFAULT` 等价于"裸 WebView"）；
 * - `specVersion` 必备且当前为 1；
 * - JSON 往返无损（含主题覆写、四区条目、动作）；
 * - **未知 `type` 降级 `Unknown` 且不抛异常**（容错序列化器的核心契约）；
 * - 新增字段带默认值 → 老数据反序列化不炸；
 * - 脏 JSON 经映射层兜底为默认壳；
 * - 预置子模板三套的形态约束。
 */
class ShellSpecContractTest {

    @Test
    fun defaultSpec_hasAllFourZonesDisabled() {
        val spec = ShellSpec.DEFAULT
        assertFalse(spec.topBar.enabled)
        assertFalse(spec.topTabs.enabled)
        assertFalse(spec.bottomBar.enabled)
        assertFalse(spec.drawer.enabled)
    }

    @Test
    fun specVersion_isPresentAndCurrent() {
        assertEquals(1, ShellSpec.CURRENT_SPEC_VERSION)
        assertEquals(ShellSpec.CURRENT_SPEC_VERSION, ShellSpec.DEFAULT.specVersion)
        // 序列化名里必须能读到（写进 DB 与未来 APK assets，导出侧要判版本）
        val obj = RuntimeJson.json.parseToJsonElement(RuntimeJson.encodeShellSpec(ShellSpec.DEFAULT)).jsonObject
        assertEquals(1, obj["specVersion"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun jsonRoundTrip_isLossless() {
        val original = ShellPresets.TABBED.spec
        val decoded = RuntimeJson.decodeShellSpec(RuntimeJson.encodeShellSpec(original))
        assertEquals(original, decoded)
    }

    @Test
    fun jsonRoundTrip_preservesThemeOverride() {
        val spec = ShellSpec(
            theme = ShellTheme(useProjectThemeColor = false, themeColorOverride = 0xFF00FF00.toInt()),
        )
        val decoded = RuntimeJson.decodeShellSpec(RuntimeJson.encodeShellSpec(spec))
        assertEquals(spec, decoded)
        assertEquals(0xFF00FF00.toInt(), decoded.theme.themeColorOverride)
        assertEquals(0xFF00FF00.toInt(), decoded.theme.resolveAccent(0xFF123456.toInt()))
    }

    @Test
    fun theme_followsProjectColorByDefault() {
        val accent = 0xFFE39E97.toInt()
        assertEquals(accent, ShellSpec.DEFAULT.theme.resolveAccent(accent))
    }

    @Test
    fun unknownActionType_degradesToUnknown_withoutThrowing() {
        val raw = """
            {
              "specVersion": 1,
              "topBar": {
                "enabled": true,
                "showTitle": true,
                "showBackButton": true,
                "actions": [
                  { "id": "a1", "title": "神秘", "iconRef": "add", "action": { "type": "teleport" }, "visible": true, "selected": false }
                ]
              }
            }
        """.trimIndent()
        val decoded = RuntimeJson.decodeShellSpec(raw)
        assertEquals(ShellAction.Unknown, decoded.topBar.actions.first().action)
    }

    @Test
    fun actionWithoutType_degradesToUnknown() {
        val raw = """{"specVersion":1,"topBar":{"actions":[{"id":"a","title":"t","action":{}}]}}"""
        val decoded = RuntimeJson.decodeShellSpec(raw)
        assertEquals(ShellAction.Unknown, decoded.topBar.actions.first().action)
    }

    @Test
    fun urlActionWithoutUrlField_degradesToUnknown() {
        val raw = """{"specVersion":1,"topBar":{"actions":[{"id":"a","title":"t","action":{"type":"url"}}]}}"""
        val decoded = RuntimeJson.decodeShellSpec(raw)
        assertEquals(ShellAction.Unknown, decoded.topBar.actions.first().action)
    }

    @Test
    fun navigateUrlAction_roundTripsAndKeepsEmptyUrl() {
        val spec = ShellSpec(
            topBar = ShellTopBar(
                enabled = true,
                actions = listOf(
                    ShellMenuItem(id = "a", title = "刷新", action = ShellAction.NavigateUrl("https://example.com")),
                    ShellMenuItem(id = "b", title = "空地址", action = ShellAction.NavigateUrl("")),
                ),
            ),
        )
        val decoded = RuntimeJson.decodeShellSpec(RuntimeJson.encodeShellSpec(spec))
        assertEquals(
            ShellAction.NavigateUrl("https://example.com"),
            decoded.topBar.actions[0].action,
        )
        assertEquals(
            ShellAction.NavigateUrl(""),
            decoded.topBar.actions[1].action,
        )
    }

    @Test
    fun missingNewFields_useDefaults() {
        // 老数据只有 specVersion + 少量字段：新增字段必须走默认值，不能炸。
        val decoded = RuntimeJson.decodeShellSpec("""{"specVersion":1}""")
        assertEquals(ShellSpec.DEFAULT.topBar, decoded.topBar)
        assertEquals(ShellSpec.DEFAULT.topTabs, decoded.topTabs)
        assertEquals(ShellSpec.DEFAULT.bottomBar, decoded.bottomBar)
        assertEquals(ShellSpec.DEFAULT.drawer, decoded.drawer)
    }

    @Test
    fun unknownTopLevelKeys_areIgnored() {
        val decoded = RuntimeJson.decodeShellSpec("""{"specVersion":1,"futureField":123}""")
        assertEquals(ShellSpec.DEFAULT, decoded)
    }

    @Test
    fun corruptJson_fallsBackToDefaultSpec_throughMapper() {
        val entity = sampleEntity(shellSpecJson = "{ 不是合法 JSON ")
        assertEquals(ShellSpec.DEFAULT, entity.toDomain().shellSpec)
    }

    @Test
    fun nullColumn_fallsBackToDefaultSpec_throughMapper() {
        val entity = sampleEntity(shellSpecJson = null)
        assertEquals(ShellSpec.DEFAULT, entity.toDomain().shellSpec)
    }

    @Test
    fun drawerContract_hasPlaceholdersButNoRenderer() {
        // 抽屉契约字段齐全（阶段 6.5 用），本阶段只占位。
        val drawer = ShellSpec.DEFAULT.drawer
        assertEquals(ShellDrawerSource.NATIVE, drawer.contentSource)
        assertEquals(ShellDrawerWidthMode.MEDIUM, drawer.width.mode)
        assertTrue(drawer.pauseContentWhileOpen)
    }

    @Test
    fun presets_coverThreeDistinctShapes() {
        assertEquals(3, ShellPresets.ALL.size)
        assertEquals(
            listOf("classic", "minimal", "tabbed"),
            ShellPresets.ALL.map { it.id },
        )
        // 经典款：顶栏 + 底栏，无顶栏 tab
        assertTrue(ShellPresets.CLASSIC.spec.topBar.enabled)
        assertFalse(ShellPresets.CLASSIC.spec.topTabs.enabled)
        assertTrue(ShellPresets.CLASSIC.spec.bottomBar.enabled)
        // 极简款：仅顶栏
        assertTrue(ShellPresets.MINIMAL.spec.topBar.enabled)
        assertFalse(ShellPresets.MINIMAL.spec.topTabs.enabled)
        assertFalse(ShellPresets.MINIMAL.spec.bottomBar.enabled)
        // 内容款：顶栏 + 顶栏 tab（3 项，带指示器）+ 底栏
        assertTrue(ShellPresets.TABBED.spec.topBar.enabled)
        assertTrue(ShellPresets.TABBED.spec.topTabs.enabled)
        assertEquals(3, ShellPresets.TABBED.spec.topTabs.items.size)
        assertTrue(ShellPresets.TABBED.spec.topTabs.showSelectionIndicator)
        assertEquals(ShellSelectionStyle.INDICATOR, ShellPresets.TABBED.spec.topTabs.selectionStyle)
        assertTrue(ShellPresets.TABBED.spec.bottomBar.enabled)
    }

    @Test
    fun presets_useLogicalIconKeys_notResourceIds() {
        val items = ShellPresets.TABBED.spec.topTabs.items
        assertEquals(
            listOf(ShellIconKeys.HOME, ShellIconKeys.DISCOVER, ShellIconKeys.PROFILE),
            items.map { it.iconRef },
        )
    }

    @Test
    fun presets_byId_fallsBackToClassic() {
        assertEquals(ShellPresets.CLASSIC, ShellPresets.byId("__不存在__"))
        assertEquals(ShellPresets.TABBED, ShellPresets.byId("tabbed"))
    }

    @Test
    fun presetActionUrls_useReservedExampleDomains() {
        val urls = ShellPresets.TABBED.spec.topTabs.items.mapNotNull { (it.action as? ShellAction.NavigateUrl)?.url }
        assertTrue(urls.isNotEmpty())
        assertTrue(urls.all { it.startsWith("https://example.") })
    }

    @Test
    fun actionSurrogate_encodesDiscriminatedTypeField() {
        val json = RuntimeJson.encodeShellSpec(
            ShellSpec(
                topBar = ShellTopBar(
                    enabled = true,
                    actions = listOf(
                        ShellMenuItem(id = "a", title = "刷新", action = ShellAction.NavigateUrl("https://example.com")),
                    ),
                ),
            ),
        )
        val obj = RuntimeJson.json.parseToJsonElement(json).jsonObject
        val actions = obj["topBar"]!!.jsonObject["actions"]!!.jsonArray
        val actionObj = actions.first().jsonObject["action"]!!.jsonObject
        assertEquals("url", actionObj["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun nativeAndInternalPageActions_roundTrip() {
        val spec = ShellSpec(
            topBar = ShellTopBar(
                enabled = true,
                actions = listOf(
                    ShellMenuItem(id = "a", title = "分享", action = ShellAction.Native(ShellNativeAction.SHARE)),
                    ShellMenuItem(id = "b", title = "内置页", action = ShellAction.InternalPage("settings")),
                ),
            ),
        )
        val decoded = RuntimeJson.decodeShellSpec(RuntimeJson.encodeShellSpec(spec))
        assertEquals(ShellAction.Native(ShellNativeAction.SHARE), decoded.topBar.actions[0].action)
        assertEquals(ShellAction.InternalPage("settings"), decoded.topBar.actions[1].action)
    }

    /** 构造一条仅用于壳字段断言的最小实体。 */
    private fun sampleEntity(shellSpecJson: String?): ProjectEntity = ProjectEntity(
        id = 1L,
        createdAt = 1L,
        updatedAt = 1L,
        projectType = ProjectType.URL,
        apkName = "示例",
        packageName = "com.example.demo",
        versionScheme = VersionScheme.THREE_SEGMENT,
        versionName = "1.0.0",
        versionCode = 1,
        iconRef = null,
        themeColor = 0xFFE39E97.toInt(),
        entryJson = """{"type":"url","url":"https://example.com"}""",
        webSettingsJson = "{}",
        isolationId = "webapp_1",
        shellSpecJson = shellSpecJson,
    )
}
