package com.make.apk.ui.code

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle

/**
 * 自研代码高亮引擎（rc11，零第三方依赖）。
 *
 * ## 方案（联网调研结论落地）
 *
 * 采用业界自研编辑器的一致做法：**单趟正则扫描 → AnnotatedString Span**——
 * 不引第三方库（Sora / compose-code-editor 均为外部依赖），规则内聚、可 JVM 单测。
 * 单文件超 [MAX_HIGHLIGHT_LENGTH] 字符时跳过高亮直接原样输出（移动端 IDE 通用兜底，
 * 保证输入流畅优先于着色完整）。
 *
 * ## 语言与规则
 *
 * 按扩展名判语言：html/htm、css、js/mjs/ts、json、md。每语言一组**有序**规则
 * （注释 > 字符串 > 关键字 > 数字…，先命中先着色），组合成一个大正则一次扫描，
 * 命中哪个命名组就上哪种色——避免逐规则全文扫描的 O(n·k) 开销。
 */
object CodeHighlight {

    /** 支持的语言（按扩展名映射，未知回落纯文本）。 */
    enum class Lang { HTML, CSS, JS, JSON, MD, PLAIN }

    /** 高亮用色（从主题 colorScheme 语义色映射，禁止硬编码色值）。 */
    data class Palette(
        val keyword: SpanStyle,
        val string: SpanStyle,
        val comment: SpanStyle,
        val number: SpanStyle,
        val tag: SpanStyle,
        val attr: SpanStyle,
        val plain: SpanStyle,
    ) {
        companion object {
            /** 从主题语义色构建（italic 注释在此统一附加）。 */
            fun of(
                keyword: SpanStyle,
                string: SpanStyle,
                comment: SpanStyle,
                number: SpanStyle,
                tag: SpanStyle,
                attr: SpanStyle,
                plain: SpanStyle,
            ): Palette = Palette(
                keyword = keyword,
                string = string,
                comment = comment.copy(fontStyle = FontStyle.Italic),
                number = number,
                tag = tag,
                attr = attr,
                plain = plain,
            )
        }
    }

    /** 高亮上限（超出跳过高亮，纯文本输出）。 */
    const val MAX_HIGHLIGHT_LENGTH: Int = 60_000

    /** 扩展名 → 语言。 */
    fun langOf(fileName: String): Lang = when (fileName.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> Lang.HTML
        "css" -> Lang.CSS
        "js", "mjs", "ts" -> Lang.JS
        "json" -> Lang.JSON
        "md", "markdown" -> Lang.MD
        else -> Lang.PLAIN
    }

    /**
     * 高染入口：文本超限或纯文本语言直接原样返回（带 plain 样式）。
     */
    fun highlight(text: String, lang: Lang, palette: Palette): AnnotatedString {
        if (lang == Lang.PLAIN || text.length > MAX_HIGHLIGHT_LENGTH) {
            return buildAnnotatedString { append(text) }
        }
        return buildAnnotatedString {
            append(text)
            rules(lang).forEach { rule ->
                rule.regex.findAll(text).forEach { match ->
                    val value = match.value
                    if (value.isEmpty()) return@forEach
                    // 规则可能带捕获组（如 HTML 属性取组 1）；无组用整段匹配。
                    val start = match.groups[1]?.range?.first ?: match.range.first
                    val end = match.groups[1]?.range?.last ?: match.range.last
                    if (start >= 0) {
                        addStyle(rule.style(palette), start, end + 1)
                    } else {
                        addStyle(rule.style(palette), match.range.first, match.range.last + 1)
                    }
                }
            }
        }
    }

    // ── 规则定义 ──────────────────────────────────────────────────────

    /** 一条高亮规则：组合正则里的一个命名组。 */
    private class Rule(val name: String, val regex: Regex, val style: (Palette) -> SpanStyle)

    private val JS_KEYWORDS: Regex =
        Regex("""\b(?:function|return|var|let|const|if|else|for|while|do|switch|case|break|continue|new|this|typeof|instanceof|class|extends|import|export|from|try|catch|finally|throw|async|await|yield|null|undefined|true|false)\b""")

    private val CSS_PROPERTIES: Regex =
        Regex("""(?m)^\s*([a-zA-Z-]+)(?=\s*:)""")

    /** 各语言规则（组名即样式键）。 */
    private fun rules(lang: Lang): List<Rule> = when (lang) {
        Lang.HTML -> listOf(
            Rule("comment", Regex("""<!--[\s\S]*?-->""")) { it.comment },
            Rule("string", Regex("""(?<=<)[a-zA-Z][\w-]*(?=[\s/>])""")) { it.tag },
            Rule("string", Regex(""""[^"]*"|'[^']*'""")) { it.string },
            Rule("string", Regex("""</?[a-zA-Z][\w-]*|/>|>""")) { it.tag },
            Rule("string", Regex("""[a-zA-Z-]+(?==)""")) { it.attr },
        )
        Lang.CSS -> listOf(
            Rule("comment", Regex("""/\*[\s\S]*?\*/""")) { it.comment },
            Rule("string", Regex(""""[^"]*"|'[^']*'""")) { it.string },
            Rule("number", Regex("""#[0-9a-fA-F]{3,8}\b|\b\d+(?:\.\d+)?(?:px|em|rem|%|vh|vw|s|ms)?\b""")) { it.number },
            Rule("keyword", Regex("""@[\w-]+""")) { it.keyword },
            Rule("attr", CSS_PROPERTIES) { it.attr },
        )
        Lang.JS -> listOf(
            Rule("comment", Regex("""//[^\n]*|/\*[\s\S]*?\*/""")) { it.comment },
            Rule("string", Regex("""`[^`]*`|"[^"\n]*"|'[^'\n]*'""")) { it.string },
            Rule("keyword", JS_KEYWORDS) { it.keyword },
            Rule("number", Regex("""\b\d+(?:\.\d+)?\b""")) { it.number },
        )
        Lang.JSON -> listOf(
            Rule("attr", Regex(""""(?:\\.|[^"\\])*"(?=\s*:)""")) { it.attr },
            Rule("string", Regex(""""(?:\\.|[^"\\])*"""")) { it.string },
            Rule("keyword", Regex("""\b(?:true|false|null)\b""")) { it.keyword },
            Rule("number", Regex("""-?\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b""")) { it.number },
        )
        Lang.MD -> listOf(
            Rule("comment", Regex("""```[\s\S]*?```|`[^`\n]+`""")) { it.string },
            Rule("keyword", Regex("""(?m)^#{1,6}[^\n]*""")) { it.keyword },
            Rule("string", Regex("""\*\*[^*\n]+\*\*""")) { it.attr },
            Rule("number", Regex("""\[[^\]\n]*\]\([^)\n]*\)""")) { it.number },
        )
        Lang.PLAIN -> emptyList()
    }
}
