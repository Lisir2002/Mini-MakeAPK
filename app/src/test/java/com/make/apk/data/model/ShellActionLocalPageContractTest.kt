package com.make.apk.data.model

import com.make.apk.data.json.RuntimeJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「本地页面」动作（[ShellAction.OpenLocalPage]）契约测试。
 *
 * ## 钉什么
 *
 * 1. **模式隔离**：本地页面走自己的判别符 `local_page` + 字段 `path`，
 *    相对路径（`page/about.html`）**不得**被规范化成 `https://page/about.html`，
 *    编码结果里**不得出现** `url` 字段（URL 与本地文件绝不共用字段）；
 * 2. **向后兼容**：老数据 `{"type":"url",...}` 的解码行为**逐字段不变**
 *    （含空串 URL 可往返），未知 `type` 仍降级 [ShellAction.Unknown]；
 * 3. **容错策略一致**：`path` 缺失 / 类型不符 → [ShellAction.Unknown]，不抛异常。
 */
class ShellActionLocalPageContractTest {

    // ── 1. 模式隔离 ────────────────────────────────────────────────────

    @Test
    fun localPageAction_roundTripsWithRelativePath_withoutUrlNormalization() {
        val spec = specWith(ShellAction.OpenLocalPage("page/about.html"))
        val decoded = RuntimeJson.decodeShellSpec(RuntimeJson.encodeShellSpec(spec))

        assertEquals(
            "本地页面路径必须原样往返（绝不补 https://）",
            ShellAction.OpenLocalPage("page/about.html"),
            decoded.topBar.actions.first().action,
        )
        assertFalse(
            "本地页面绝不得变成 NavigateUrl",
            decoded.topBar.actions.first().action is ShellAction.NavigateUrl,
        )
    }

    @Test
    fun localPageAction_encodesLocalPageDiscriminatorAndPath_only() {
        val json = RuntimeJson.encodeShellSpec(specWith(ShellAction.OpenLocalPage("index.html")))
        val actionObj = firstActionObject(json)

        assertEquals("local_page", actionObj["type"]?.jsonPrimitive?.content)
        assertEquals("index.html", actionObj["path"]?.jsonPrimitive?.content)
        assertFalse("本地页面不得携带 url 字段（两种来源不共用字段）", actionObj.containsKey("url"))
    }

    @Test
    fun localPageAction_entryFileIsAlsoAPlainFileName() {
        // 入口文件本身即"本地页面"的一种：值同样是项目文件名，不是网址。
        val decoded = RuntimeJson.decodeShellSpec(
            RuntimeJson.encodeShellSpec(specWith(ShellAction.OpenLocalPage("index.html"))),
        )
        assertEquals(
            ShellAction.OpenLocalPage("index.html"),
            decoded.topBar.actions.first().action,
        )
    }

    // ── 2. 向后兼容（老数据行为不变） ──────────────────────────────────

    @Test
    fun legacyUrlAction_decodingIsUnchanged() {
        val raw = """{"specVersion":1,"topBar":{"actions":[{"id":"a","title":"t","action":{"type":"url","url":"https://example.com"}}]}}"""
        assertEquals(
            ShellAction.NavigateUrl("https://example.com"),
            RuntimeJson.decodeShellSpec(raw).topBar.actions.first().action,
        )
        val emptyRaw = """{"specVersion":1,"topBar":{"actions":[{"id":"a","title":"t","action":{"type":"url","url":""}}]}}"""
        assertEquals(
            "空串 URL 仍是合法的待填地址（默认值可往返）",
            ShellAction.NavigateUrl(""),
            RuntimeJson.decodeShellSpec(emptyRaw).topBar.actions.first().action,
        )
    }

    @Test
    fun legacySpecWithoutLocalPage_decodesAsBefore() {
        val decoded = RuntimeJson.decodeShellSpec("""{"specVersion":1}""")
        assertEquals(ShellSpec.DEFAULT.topBar, decoded.topBar)
    }

    @Test
    fun unknownType_stillDegradesToUnknown() {
        val raw = """{"specVersion":1,"topBar":{"actions":[{"id":"a","title":"t","action":{"type":"teleport"}}]}}"""
        assertEquals(
            ShellAction.Unknown,
            RuntimeJson.decodeShellSpec(raw).topBar.actions.first().action,
        )
    }

    // ── 3. 容错降级（与既有字段同一判据） ──────────────────────────────

    @Test
    fun localPageAction_missingPath_degradesToUnknown() {
        assertEquals(ShellAction.Unknown, decodeFirstAction("""{"type":"local_page"}"""))
    }

    @Test
    fun localPageAction_nonStringPath_degradesToUnknown() {
        // 数字 / 布尔 / null / 对象 / 数组：与 url 字段同一判据（类型不符 → Unknown，
        // 绝不静默转成字符串——那会造出一个指向错误文件的可执行动作）。
        listOf("123", "true", "null", """{"a":1}""", "[1,2]").forEach { rawPath ->
            assertEquals(
                "path=$rawPath 应降级为 Unknown",
                ShellAction.Unknown,
                decodeFirstAction("""{"type":"local_page","path":$rawPath}"""),
            )
        }
    }

    @Test
    fun localPageAction_emptyPath_isPreservedAsTypedValue() {
        // 与 url 一致：空串是"类型合法"的值（语义为空由消费侧忽略），不降级为 Unknown。
        assertEquals(ShellAction.OpenLocalPage(""), decodeFirstAction("""{"type":"local_page","path":""}"""))
    }

    // ── 辅助 ──────────────────────────────────────────────────────────

    private fun specWith(action: ShellAction): ShellSpec = ShellSpec(
        topBar = ShellTopBar(
            enabled = true,
            actions = listOf(ShellMenuItem(id = "a", title = "关于", action = action)),
        ),
    )

    private fun decodeFirstAction(rawAction: String): ShellAction {
        val raw = """{"specVersion":1,"topBar":{"actions":[{"id":"a","title":"t","action":$rawAction}]}}"""
        return RuntimeJson.decodeShellSpec(raw).topBar.actions.first().action
    }

    private fun firstActionObject(specJson: String): kotlinx.serialization.json.JsonObject {
        val obj = RuntimeJson.json.parseToJsonElement(specJson).jsonObject
        val actions = obj["topBar"]!!.jsonObject["actions"]!!.jsonArray
        return actions.first().jsonObject["action"]!!.jsonObject.also {
            assertTrue("action 必须是对象", it.isNotEmpty())
        }
    }
}
