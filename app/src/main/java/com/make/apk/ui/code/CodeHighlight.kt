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
            rules(lang).forEach rule@{ rule ->
                rule.regex.findAll(text).forEach match@{ match ->
                    // 取该规则声明的捕获组区间（组不存在 / 未参与匹配时回落整段匹配）。
                    val range = match.rangeOf(rule.group)
                    val start = range.first
                    val end = range.last + 1
                    // 零宽匹配（如纯断言规则）与越界区间一律跳过：不产生空 span、不越界着色。
                    if (start < 0 || start >= end || end > text.length) return@match
                    addStyle(rule.style(palette), start, end)
                }
            }
        }
    }

    // ── 规则定义 ──────────────────────────────────────────────────────

    /**
     * 安全取组区间。
     *
     * 直接写 `match.groups[1]` 会在该正则**没有捕获组**时抛
     * `IndexOutOfBoundsException("No group 1")`（Kotlin `MatchGroupCollection.get`
     * 先校验 `index > matcher.groupCount()`）——rc11 的线上崩溃即源于此：绝大多数规则
     * 是零捕获组的整段匹配，只有少数规则（如 CSS 属性名）才需要取组 1。
     * 故：[group] 由规则**显式声明**，取组前先判组数，绝不抛异常。
     */
    private fun MatchResult.rangeOf(group: Int): IntRange {
        if (group <= 0) return range
        if (group >= groups.size) return range
        return groups[group]?.range ?: range
    }

    /** 一条高亮规则：[group] 为需要着色的捕获组下标（0 = 整段匹配）。 */
    private class Rule(
        val name: String,
        val regex: Regex,
        val group: Int = 0,
        val style: (Palette) -> SpanStyle,
    )

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
            // CSS 属性名：正则带一个捕获组（后接零宽断言），只给"属性名"上色，不含冒号。
            Rule("attr", CSS_PROPERTIES, group = 1) { it.attr },
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
