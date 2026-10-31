package com.make.apk.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * URL 打开策略（W2 ④）单测 —— [resolveUrlOpenTarget] 是预览与导出双侧共享的
 * **唯一判定事实源**，本测试锁定其全矩阵契约：
 *
 * - `fitToApp = true`（默认）：**恒内嵌**，与历史行为一致，零回归；
 * - `fitToApp = false`：仅「内容跳转」来源外开（ACTION / DRAWER_ITEM / IN_PAGE_LINK），
 *   壳骨架来源（ENTRY / TOP_TAB / BOTTOM_TAB）恒内嵌——「壳骨架留应用内，内容跳转交外部」。
 *
 * 纯 JVM 测试（无 android.* 依赖）：与该函数"可被导出壳工程直接消费"的设计约束互证。
 */
class UrlOpenPolicyTest {

    /** 默认设置（fitToApp = true）下，6 类来源全部内嵌。 */
    @Test
    fun defaultSettings_allOrigins_embedded() {
        UrlOpenOrigin.entries.forEach { origin ->
            assertEquals(
                "默认设置下 $origin 应恒内嵌（零回归）",
                UrlOpenTarget.EMBEDDED,
                resolveUrlOpenTarget(WebSettings(), origin),
            )
        }
    }

    /** fitToApp = true 显式设置时，同样 6 类来源全部内嵌。 */
    @Test
    fun fitToAppOn_allOrigins_embedded() {
        val settings = WebSettings(fitToApp = true)
        UrlOpenOrigin.entries.forEach { origin ->
            assertEquals(
                "fitToApp=true 时 $origin 应恒内嵌",
                UrlOpenTarget.EMBEDDED,
                resolveUrlOpenTarget(settings, origin),
            )
        }
    }

    /** fitToApp = false 时，壳骨架来源（ENTRY / TOP_TAB / BOTTOM_TAB）仍恒内嵌。 */
    @Test
    fun fitToAppOff_skeletonOrigins_stayEmbedded() {
        val settings = WebSettings(fitToApp = false)
        val skeletonOrigins = listOf(
            UrlOpenOrigin.ENTRY,
            UrlOpenOrigin.TOP_TAB,
            UrlOpenOrigin.BOTTOM_TAB,
        )
        skeletonOrigins.forEach { origin ->
            assertEquals(
                "壳骨架来源 $origin 在 fitToApp=false 时也应内嵌（骨架永不外开）",
                UrlOpenTarget.EMBEDDED,
                resolveUrlOpenTarget(settings, origin),
            )
        }
    }

    /** fitToApp = false 时，仅内容跳转来源（ACTION / DRAWER_ITEM / IN_PAGE_LINK）外开。 */
    @Test
    fun fitToAppOff_contentOrigins_goExternal() {
        val settings = WebSettings(fitToApp = false)
        val contentOrigins = listOf(
            UrlOpenOrigin.ACTION,
            UrlOpenOrigin.DRAWER_ITEM,
            UrlOpenOrigin.IN_PAGE_LINK,
        )
        contentOrigins.forEach { origin ->
            assertEquals(
                "内容跳转来源 $origin 在 fitToApp=false 时应外开",
                UrlOpenTarget.EXTERNAL,
                resolveUrlOpenTarget(settings, origin),
            )
        }
    }

    /** 全矩阵穷举：外开当且仅当（fitToApp = false 且 origin ∈ 内容来源）。 */
    @Test
    fun fullMatrix_externalOnlyWhenNotFitToAppAndContentOrigin() {
        UrlOpenOrigin.entries.forEach { origin ->
            val isContentOrigin = origin in listOf(
                UrlOpenOrigin.ACTION,
                UrlOpenOrigin.DRAWER_ITEM,
                UrlOpenOrigin.IN_PAGE_LINK,
            )
            listOf(true, false).forEach { fitToApp ->
                val target = resolveUrlOpenTarget(WebSettings(fitToApp = fitToApp), origin)
                val expected =
                    if (!fitToApp && isContentOrigin) UrlOpenTarget.EXTERNAL else UrlOpenTarget.EMBEDDED
                assertEquals(
                    "矩阵不一致：fitToApp=$fitToApp, origin=$origin",
                    expected,
                    target,
                )
            }
        }
    }

    /** 关闭开关本身不影响其它设置字段的判定（只读 fitToApp 一个字段）。 */
    @Test
    fun policy_onlyDependsOnFitToApp_otherFieldsIrrelevant() {
        val plain = WebSettings(fitToApp = false)
        val decorated = WebSettings(
            fitToApp = false,
            showLoadingProgress = true,
            progressColorToken = ProgressColorToken.TERTIARY,
            progressThickness = ProgressThickness.THICK,
        )
        UrlOpenOrigin.entries.forEach { origin ->
            assertEquals(
                "判定只应依赖 fitToApp：origin=$origin",
                resolveUrlOpenTarget(plain, origin),
                resolveUrlOpenTarget(decorated, origin),
            )
        }
    }
}
