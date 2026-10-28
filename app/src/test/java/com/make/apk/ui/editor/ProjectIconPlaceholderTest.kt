package com.make.apk.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图标占位文字的**单一实现**单测（[iconPlaceholderLabelOf]）。
 *
 * 背景：占位文字曾有两处实现（状态类里一个 `Char.uppercase()`、组件里一个
 * `uppercaseChar()`），且二者对 `ß` 的结果不同（`"SS"` vs `"ß"`）——典型的双实现漂移。
 * 现已收敛到 [ProjectIcon] 同文件的 [iconPlaceholderLabelOf]，本组用例把它钉住。
 */
class ProjectIconPlaceholderTest {

    @Test
    fun blankName_returnsQuestionMark() {
        assertEquals("?", iconPlaceholderLabelOf(""))
        assertEquals("?", iconPlaceholderLabelOf("   "))
        assertEquals("?", iconPlaceholderLabelOf("\n\t"))
    }

    @Test
    fun lowerCaseInitial_isUppercased() {
        assertEquals("D", iconPlaceholderLabelOf("demo"))
        assertEquals("M", iconPlaceholderLabelOf("mma 项目"))
    }

    @Test
    fun leadingWhitespace_isTrimmedBeforeTakingInitial() {
        assertEquals("示", iconPlaceholderLabelOf("  示例应用"))
    }

    @Test
    fun multiCharUppercase_staysSingleChar() {
        // `"ß".uppercase() == "SS"`（两字符）——占位格只有一字符宽，
        // 用 uppercaseChar() 才能保证结果恒为单字符，不会把图标撑变形。
        assertEquals("ß", iconPlaceholderLabelOf("ßeta"))
    }

    @Test
    fun resultIsAlwaysExactlyOneChar() {
        listOf("示例网址", "ßeta", "demo", "  x", "HTML 粘贴", "1").forEach { name ->
            val label = iconPlaceholderLabelOf(name)
            assertTrue(
                "占位文字必须是恰好一个字符（name=$name, label=$label）",
                label.length == 1,
            )
        }
    }
}
