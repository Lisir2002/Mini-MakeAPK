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
 *
 * ## 断言策略（QA 终验修正）
 *
 * 注入件是 `inject_shell_spec.py` 的**产物**，换一份演示壳条目数量就会变。故本测试
 * 只钉**契约与跨模块一致性**，不钉某次产物的条目数量/具体文案：
 * - 不得有任何 action 降级成 [ShellAction.Unknown]（= 注入 JSON 与契约对不上）；
 * - 有条目的区必须 enabled（悬空配置在预览/导出里都看不见）；
 * - 两类内容来源严格隔离：URL 槽位只放 http(s)，本地页面槽位只放项目文件名；
 * - 每个 `local_page.path` 都必须能在 `assets/mma_pages/` 下读到（否则真机「点了没反应」）。
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

    // ── 以下断言**不钉死条目数量**（QA 终验修正）─────────────────────────
    // 注入的 shell_spec.json 是 inject_shell_spec.py 的**产物**：谁用哪份示例壳做验证，
    // 条目数量就会变。旧的「每区 3 项 / 首个 tab 必须是 example.com」把测试钉在了
    // 某一次的产物形状上 —— 换一份演示壳（本次是本地页面演示壳）就从"验证契约"
    // 退化成"验证某次注入的巧合"，且失败信息完全指不到真问题。
    // 现改为钉**契约本身**：解码结果必须与 ShellSpec/ShellAction 精确对得上
    // （无 Unknown 降级）、四区配置自洽、两类内容来源互不混用。

    @Test
    fun noItemDegradesToUnknownAction() {
        // 任何一条 action 落成 Unknown，都说明注入的 JSON 与契约不一致
        // （类型判别符写错 / 用了契约里没有的 type）——这正是本测试要抓的东西。
        val degraded = spec.allItems.filter { it.action is ShellAction.Unknown }.map { it.id }
        assertTrue(
            "以下条目的 action 解码成了 Unknown（注入 JSON 与壳契约不一致）：$degraded",
            degraded.isEmpty(),
        )
    }

    @Test
    fun regionsWithItems_areEnabled() {
        // 配了条目却没启用该区 = 用户配的东西渲染不出来（且预览/导出一致地看不见）。
        val dangling = buildList {
            if (spec.topBar.actions.isNotEmpty() && !spec.topBar.enabled) add("topBar")
            if (spec.topTabs.items.isNotEmpty() && !spec.topTabs.enabled) add("topTabs")
            if (spec.bottomBar.items.isNotEmpty() && !spec.bottomBar.enabled) add("bottomBar")
            if (spec.drawer.items.isNotEmpty() && !spec.drawer.enabled) add("drawer")
        }
        assertTrue("以下区域有条目却没有 enabled（悬空配置）：$dangling", dangling.isEmpty())
    }

    @Test
    fun urlActions_areHttpAndNeverLocalFilePaths() {
        // 隔离性硬约束的一半：URL 槽位里绝不能出现项目内文件名。
        val bad = spec.allItems
            .mapNotNull { (it.action as? ShellAction.NavigateUrl)?.url }
            .filterNot { it.startsWith("http://") || it.startsWith("https://") }
        assertTrue("以下 NavigateUrl 不是 http(s) 网址（本地文件名混进了 URL 槽位？）：$bad", bad.isEmpty())
    }

    @Test
    fun localPagePaths_areFileNames_andResolveToInjectedAssets() {
        // 隔离性硬约束的另一半 + 导出可用性：
        // - path 必须是项目内文件名（不得长得像网址）；
        // - 必须真实存在于注入目录 assets/mma_pages/ 下（否则真机上就是「点了没反应」）。
        val paths = spec.allItems.mapNotNull { (it.action as? ShellAction.OpenLocalPage)?.path }
        val urlShaped = paths.filter { "://" in it || it.startsWith("www.") }
        assertTrue("以下 local_page.path 长得像网址（本地页面被当成了 URL）：$urlShaped", urlShaped.isEmpty())
        if (paths.isEmpty()) return // 本次注入的壳不含本地页面：无契约可查（不制造假失败）。

        val assets = root().resolve("template/src/main/assets/mma_pages")
        assertTrue(
            "壳引用了本地页面 $paths，但注入目录不存在：${assets.absolutePath}\n" +
                "先运行 python3 scripts/gitops/inject_shell_spec.py --in <spec> --project <entry> 注入页面",
            assets.isDirectory,
        )
        val missing = paths.filterNot { java.io.File(assets, it).isFile }
        assertTrue(
            "以下本地页面在导出壳里读不到（assets/mma_pages 下缺文件，真机上会「点了没反应」）：$missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun topTabsStyle_andDrawerSource_andDrawerHeaderMode_areWithinContractEnums() {
        // 枚举落到非法取值时，渲染层会静默回落 —— 在契约层先拦住。
        assertTrue(
            "topTabs.selectionStyle 非法：${spec.topTabs.selectionStyle}",
            spec.topTabs.selectionStyle in ShellSelectionStyle.entries,
        )
        assertTrue(
            "drawer.contentSource 非法：${spec.drawer.contentSource}",
            spec.drawer.contentSource in ShellDrawerSource.entries,
        )
        // W2 ⑨：头部模式同样受枚举白名单约束（非法值由注入管线拒绝，这里锁契约层）。
        assertTrue(
            "drawer.headerMode 非法：${spec.drawer.headerMode}",
            spec.drawer.headerMode in ShellDrawerHeaderMode.entries,
        )
        // 配了 IMAGE 却不带 ref 是悬空配置：注入管线构建期拦截，契约层兜底断言。
        if (spec.drawer.headerMode == ShellDrawerHeaderMode.IMAGE) {
            assertTrue(
                "drawer.headerMode=IMAGE 但 headerImageRef 为空（悬空配置）",
                !spec.drawer.headerImageRef.isNullOrBlank(),
            )
        }
    }

    /** 仓库根（从 user.dir 向上找 settings.gradle.kts，与 [locateInjectedSpec] 同一套定位）。 */
    private fun root(): File {
        val start = File(checkNotNull(System.getProperty("user.dir")) { "user.dir 未设置" })
        return generateSequence(start) { it.parentFile }
            .first { it.isDirectory && File(it, "settings.gradle.kts").exists() }
    }
}
