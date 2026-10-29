package com.make.apk.ui.code

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自研高亮引擎单测（纯 JVM）——**崩溃回归防线**。
 *
 * ## 为什么要这组测试
 *
 * rc11 线上崩溃 `IndexOutOfBoundsException: No group 1`：高亮实现里直接取
 * `match.groups[1]`，而 Kotlin 的 `MatchGroupCollection.get(i)` 会在
 * `i > matcher.groupCount()` 时抛异常——**零捕获组规则**（HTML/CSS/JS/JSON/MD 的
 * 绝大多数规则都是整段匹配）必然触发。故本组测试的第一职责是：
 * **任何语言、任何输入都不许抛异常**。
 *
 * ## 覆盖
 *
 * 1. 全语言样例不抛异常，且输出文本与输入**逐字一致**（着色只改视觉，不改文本）；
 * 2. 带捕获组的规则（CSS 属性名）只给"属性名"上色，不含冒号——验证 [CodeHighlight]
 *    取组逻辑与回退逻辑都正确；
 * 3. 退化输入（空串 / 纯文本 / 超限文本）走快速通道；
 * 4. 语言判定按扩展名映射。
 */
class CodeHighlightTest {

    private val palette = CodeHighlight.Palette.of(
        keyword = SpanStyle(color = Color.Blue),
        string = SpanStyle(color = Color.Red),
        comment = SpanStyle(color = Color.Gray),
        number = SpanStyle(color = Color.Green),
        tag = SpanStyle(color = Color.Cyan),
        attr = SpanStyle(color = Color.Magenta),
        plain = SpanStyle(color = Color.Black),
    )

    @Test
    fun `全语言样例不抛异常且文本逐字保留`() {
        val samples = mapOf(
            CodeHighlight.Lang.HTML to """
                <!doctype html>
                <!-- 入口页面 -->
                <html lang="zh">
                  <head><link rel="stylesheet" href="css/style.css"></head>
                  <body class="app" data-id='7'><h1>Hi</h1></body>
                </html>
            """.trimIndent(),
            CodeHighlight.Lang.CSS to """
                /* 主题 */
                :root { --brand: #2563EB; }
                body { margin: 0 auto; padding: 12px; color: #14171A; }
                @media (max-width: 600px) { body { font-size: 14px; } }
            """.trimIndent(),
            CodeHighlight.Lang.JS to """
                // 入口
                async function boot(opts = {}) {
                  const el = document.querySelector('#app');
                  if (!el) return null;
                  for (let i = 0; i < 3; i++) { console.log(`hit ${"$"}{i}`); }
                  /* 块注释 */
                  return true;
                }
            """.trimIndent(),
            CodeHighlight.Lang.JSON to """
                {"name": "mma", "version": 14, "nested": {"ok": true, "nil": null}, "rate": -1.5e3}
            """.trimIndent(),
            CodeHighlight.Lang.MD to """
                # 标题一
                ## 标题二
                正文 **加粗** 与 `code` 与 [链接](https://example.com)
                ```
                fenced
                ```
            """.trimIndent(),
        )

        samples.forEach { (lang, source) ->
            val result = CodeHighlight.highlight(source, lang, palette)
            assertEquals("语言 $lang 的文本被着色逻辑改写", source, result.text)
        }
    }

    @Test
    fun `零捕获组规则不再抛 No group 1`() {
        // 崩溃回归：最简单的零捕获组规则（HTML 注释）曾在此抛出 IndexOutOfBoundsException。
        val result = CodeHighlight.highlight("<!-- note -->", CodeHighlight.Lang.HTML, palette)
        assertEquals("<!-- note -->", result.text)
        assertTrue("注释应被着色", result.spanStyles.isNotEmpty())
    }

    @Test
    fun `CSS 属性名只给属性名上色不含冒号`() {
        val source = "body {\n  color: #fff;\n}"
        val result = CodeHighlight.highlight(source, CodeHighlight.Lang.CSS, palette)
        val colorSpan = result.spanStyles.first { span ->
            source.substring(span.start, span.end) == "color"
        }
        assertEquals("着色区间应恰为属性名", "color", source.substring(colorSpan.start, colorSpan.end))
        assertEquals("属性名后不应吃进冒号", ':', source[colorSpan.end])
    }

    @Test
    fun `退化输入走快速通道`() {
        assertEquals("", CodeHighlight.highlight("", CodeHighlight.Lang.JS, palette).text)
        val plain = "just text"
        assertEquals(
            "纯文本语言不应着色",
            0,
            CodeHighlight.highlight(plain, CodeHighlight.Lang.PLAIN, palette).spanStyles.size,
        )
        val huge = "a".repeat(CodeHighlight.MAX_HIGHLIGHT_LENGTH + 1)
        val hugeResult = CodeHighlight.highlight(huge, CodeHighlight.Lang.HTML, palette)
        assertEquals("超限文本应原样返回", huge, hugeResult.text)
        assertEquals("超限文本应跳过高亮", 0, hugeResult.spanStyles.size)
    }

    @Test
    fun `着色区间不越界`() {
        val source = "<p class='x'>tail</p>"
        val result = CodeHighlight.highlight(source, CodeHighlight.Lang.HTML, palette)
        result.spanStyles.forEach { span ->
            assertTrue("区间起点非负", span.start >= 0)
            assertTrue("区间终点不越界", span.end <= source.length)
            assertTrue("区间非空", span.start < span.end)
        }
    }

    @Test
    fun `扩展名映射到语言`() {
        assertEquals(CodeHighlight.Lang.HTML, CodeHighlight.langOf("index.html"))
        assertEquals(CodeHighlight.Lang.HTML, CodeHighlight.langOf("page.HTM"))
        assertEquals(CodeHighlight.Lang.CSS, CodeHighlight.langOf("css/style.css"))
        assertEquals(CodeHighlight.Lang.JS, CodeHighlight.langOf("js/app.js"))
        assertEquals(CodeHighlight.Lang.JSON, CodeHighlight.langOf("a/b/manifest.json"))
        assertEquals(CodeHighlight.Lang.MD, CodeHighlight.langOf("README.md"))
        assertEquals(CodeHighlight.Lang.PLAIN, CodeHighlight.langOf("logo.png"))
        assertEquals(CodeHighlight.Lang.PLAIN, CodeHighlight.langOf("Makefile"))
    }

    @Test
    fun `空输入不产生空 span`() {
        val result: AnnotatedString = CodeHighlight.highlight("<>", CodeHighlight.Lang.HTML, palette)
        result.spanStyles.forEach { span -> assertTrue(span.start < span.end) }
    }
}
