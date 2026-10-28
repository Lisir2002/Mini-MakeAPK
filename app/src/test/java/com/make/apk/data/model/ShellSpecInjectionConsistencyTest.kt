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
 * 编译期注入的那一份物理 JSON（从 user.dir 向上定位仓库根，不硬编码本地路径），不另造样例。
 *
 * 若 [Json.decodeFromString] 抛 [kotlinx.serialization.SerializationException]，
 * 说明注入 JSON 与契约不匹配——需定位是 JSON 笔误还是契约层面 bug（本测试不改契约）。
 */
class ShellSpecInjectionConsistencyTest {

    /** 与 TemplateActivity 运行时完全一致的宽容解码器。 */
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var injectedText: String
    private lateinit var spec: ShellSpec

    @Before
    fun setUp() {
        val injectedJsonFile = locateInjectedSpec()
        assertTrue(
            "注入 JSON 必须存在: ${injectedJsonFile.absolutePath}",
            injectedJsonFile.exists(),
        )
        injectedText = injectedJsonFile.readText()
        // 关键断言：模板在设备上读 raw 解码时不得崩。
        spec = json.decodeFromString<ShellSpec>(injectedText)
    }

    /**
     * 跨环境定位编译期注入的壳契约：从 `user.dir` 向上找到含 `settings.gradle.kts`
     * 的仓库根，再定位 template 模块的注入文件。避免硬编码 `/workspace` 等本地路径
     * 导致 CI runner 绝对路径不符而失败。
     */
    private fun locateInjectedSpec(): File {
        val start = File(checkNotNull(System.getProperty("user.dir")) { "user.dir 未设置" })
        val root = generateSequence(start) { it.parentFile }
            .first { it.isDirectory && File(it, "settings.gradle.kts").exists() }
        return root.resolve("template/src/main/res/raw/shell_spec.json")
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
