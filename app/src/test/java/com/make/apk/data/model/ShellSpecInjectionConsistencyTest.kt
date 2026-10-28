package com.make.apk.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 方向 1 一致性测试（新增，不改 app 主代码）。
 *
 * 目的：证明模板注入的 `template/src/main/res/raw/shell_spec.json`
 * 能被 **app 同一份** `ShellSpec` 契约 + [ShellActionSurrogate] 容错序列化器真实反序列化，
 * 从而验证
 * 「预览 == 导出 ⟺ 同一份 ShellSpec + 同一份 ShellHost」
 * 在真实产物上成立。
 *
 * 与设备运行时同源：解码器配置 `Json { ignoreUnknownKeys = true }` 与
 * [com.make.apk.TemplateActivity.rememberInjectedShellSpec] 完全一致；读取的是
 * 编译期注入的那一份物理 JSON（绝对路径），不另造样例。
 *
 * 若 [Json.decodeFromString] 抛 [kotlinx.serialization.SerializationException]，
 * 说明注入 JSON 与契约不匹配——需定位是 JSON 笔误还是契约层面 bug（本测试不改契约）。
 */
class ShellSpecInjectionConsistencyTest {

    /** 编译期注入的真实 JSON（模板模块 raw 资源）。 */
    private val injectedJsonFile =
        File("/workspace/template/src/main/res/raw/shell_spec.json")

    /** 与 TemplateActivity 运行时完全一致的宽容解码器。 */
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var injectedText: String
    private lateinit var spec: ShellSpec

    @Before
    fun setUp() {
        assertTrue(
            "注入 JSON 必须存在: ${injectedJsonFile.absolutePath}",
            injectedJsonFile.exists(),
        )
        injectedText = injectedJsonFile.readText()
        // 关键断言：模板在设备上读 raw 解码时不得崩。
        spec = json.decodeFromString<ShellSpec>(injectedText)
    }

    @Test
    fun injectedJson_decodesWithoutException() {
        // setUp 已执行 decodeFromString；能走到这里即代表未抛异常。
        assertEquals(1, spec.specVersion)
    }

    @Test
    fun topTabs_contractMatchesExpectation() {
        assertTrue("spec.topTabs.enabled == true", spec.topTabs.enabled)
        assertEquals("spec.topTabs.items.size == 3", 3, spec.topTabs.items.size)
        assertTrue("spec.topTabs.swipeEnabled == true", spec.topTabs.swipeEnabled)
    }

    @Test
    fun bottomBar_contractMatchesExpectation() {
        assertTrue("spec.bottomBar.enabled == true", spec.bottomBar.enabled)
        assertEquals("spec.bottomBar.items.size == 3", 3, spec.bottomBar.items.size)
    }

    @Test
    fun drawer_contractMatchesExpectation() {
        assertTrue("spec.drawer.enabled == true", spec.drawer.enabled)
        assertEquals("spec.drawer.items.size == 3", 3, spec.drawer.items.size)
        assertEquals(
            "spec.drawer.contentSource == NATIVE",
            ShellDrawerSource.NATIVE,
            spec.drawer.contentSource,
        )
    }

    @Test
    fun firstTopTab_action_isNavigateUrlWithExpectedUrl() {
        val action = spec.topTabs.items.first().action
        assertTrue(
            "首个 topTab action 应为 ShellAction.NavigateUrl",
            action is ShellAction.NavigateUrl,
        )
        assertEquals(
            "url == https://example.com",
            "https://example.com",
            (action as ShellAction.NavigateUrl).url,
        )
    }
}
