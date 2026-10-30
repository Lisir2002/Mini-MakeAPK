package com.make.apk.ui.editor

import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.ShellAction
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.VersionScheme

/**
 * 项目字段校验器（§2.6 全部规则，**纯函数**）。
 *
 * 设计约束（务必保持）：
 * - **零 `android.*` 依赖**：本文件可在纯 JVM 单元测试里直接跑；
 * - **不查库**：包名占用（"该包名已被其他项目占用"）由 ViewModel 异步查库后判定，
 *   本类只负责格式类规则；
 * - **文案集中**：面向用户的中文提示只在这里出现，ViewModel 不散落拼字符串。
 */
class ProjectValidator {

    // ── 项目类型 ───────────────────────────────────────────────────────

    /** 项目类型必须已选（初始为 null 表示用户还没确认）。 */
    fun validateProjectType(type: ProjectType?): FieldVerdict =
        if (type == null) error(MSG_TYPE_REQUIRED) else FieldVerdict()

    // ── 应用名称 ───────────────────────────────────────────────────────

    /**
     * 应用名称规则：
     * - 去空格后非空；
     * - 不含控制符 / 换行；
     * - 长度 ≤ 50（硬上限）；
     * - 长度 > 30 时给出警告。
     */
    fun validateApkName(raw: String): FieldVerdict {
        val name = raw.trim()
        if (name.isEmpty()) return error(MSG_NAME_REQUIRED)
        if (name.any { it.isISOControl() }) return error(MSG_NAME_ILLEGAL)
        if (name.length > MAX_APK_NAME_LENGTH) return error(MSG_NAME_TOO_LONG)
        if (name.length > RECOMMENDED_APK_NAME_LENGTH) {
            return warning(MSG_NAME_TOO_LONG_SOFT)
        }
        return FieldVerdict()
    }

    // ── 包名 ───────────────────────────────────────────────────────────

    /**
     * 包名规则：
     * - 形如 `^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$`（至少两段、全小写、段首非数字）；
     * - 不以 `android.` / `java.` / `com.android.` / `com.google.` 开头；
     * - 长度 ≤ 255。
     *
     * "同包名已存在"不在此处判定（需要查库，由 ViewModel 负责）。
     */
    fun validatePackageName(raw: String): FieldVerdict {
        val name = raw.trim()
        if (!PACKAGE_NAME_PATTERN.matches(name)) return error(MSG_PACKAGE_FORMAT)
        if (RESERVED_PACKAGE_PREFIXES.any { name.startsWith(it) }) return error(MSG_PACKAGE_RESERVED)
        if (name.length > MAX_PACKAGE_NAME_LENGTH) return error(MSG_PACKAGE_TOO_LONG)
        return FieldVerdict()
    }

    /** 包名已被其他项目占用（由 ViewModel 查库后调用）。 */
    fun packageNameOccupied(): FieldMessage =
        FieldMessage(MSG_PACKAGE_OCCUPIED, FieldMessage.Severity.ERROR)

    // ── 版本号 ─────────────────────────────────────────────────────────

    /**
     * 展示版本号规则（按段式区分）：
     * - 二段式 `1.0` / 三段式 `1.0.0`；
     * - 每段 0–999；
     * - 段数与所选段式不符时给出"当前为 N 段式"提示。
     */
    fun validateVersionName(raw: String, scheme: VersionScheme): FieldVerdict {
        val value = raw.trim()
        if (value.isEmpty()) return error(MSG_VERSION_FORMAT)
        val parts = value.split('.')
        // 仅接受 ASCII 数字：Char.isDigit() 对全角/其它 Unicode 数字也返回 true，
        // 而这些字符无法被 String.toIntOrNull() 解析，会造成语义歧义。
        if (parts.any { it.isEmpty() || !it.all { c -> c in '0'..'9' } }) {
            return error(MSG_VERSION_FORMAT)
        }
        val expected = if (scheme == VersionScheme.TWO_SEGMENT) 2 else 3
        if (parts.size != expected) {
            return error(
                if (scheme == VersionScheme.TWO_SEGMENT) {
                    MSG_VERSION_SCHEME_TWO
                } else {
                    MSG_VERSION_SCHEME_THREE
                },
            )
        }
        // 段值可能超出 Int 范围（如 "10000000000"），必须用 toIntOrNull() 兜底，
        // 避免 NumberFormatException 逃逸出这个纯函数校验器。
        // 解析失败（超出 Int 上限）按"超出上限"处理，返回段值越界错误。
        if (parts.any { seg -> (seg.toIntOrNull() ?: Int.MAX_VALUE) > MAX_VERSION_SEGMENT }) {
            return error(MSG_VERSION_SEGMENT_RANGE)
        }
        return FieldVerdict()
    }

    /** 内部版本号规则：正整数 1..2147483647。 */
    fun validateVersionCode(raw: String): FieldVerdict {
        val code = raw.trim().toIntOrNull()
        return if (code == null || code < MIN_VERSION_CODE || code > MAX_VERSION_CODE) {
            error(MSG_VERSION_CODE_RANGE)
        } else {
            FieldVerdict()
        }
    }

    // ── 图标 ───────────────────────────────────────────────────────────

    /** 图标可为空；非空但不是 `.png` 时给出建议（警告，不阻断保存）。 */
    fun validateIconRef(raw: String?): FieldVerdict {
        if (raw.isNullOrBlank()) return FieldVerdict()
        val extension = raw.substringAfterLast('.', "").lowercase()
        return if (extension != ICON_PREFERRED_EXTENSION) warning(MSG_ICON_PNG) else FieldVerdict()
    }

    // ── 入口 ───────────────────────────────────────────────────────────

    /** 入口网址规则：`http(s)://` 开头且 host 非空；禁止 `javascript:` / `data:` / `file:`。 */
    fun validateEntryUrl(raw: String): FieldVerdict {
        val value = raw.trim()
        if (value.isEmpty()) return error(MSG_URL_REQUIRED)
        val lower = value.lowercase()
        if (BLOCKED_URL_SCHEMES.any { lower.startsWith(it) }) return error(MSG_URL_SCHEME_BLOCKED)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return error(MSG_URL_REQUIRED)
        if (hostOf(value).isNullOrBlank()) return error(MSG_URL_REQUIRED)
        return FieldVerdict()
    }

    /** 入口 HTML 规则：非空且含标签。 */
    fun validateEntryHtml(raw: String): FieldVerdict {
        val value = raw.trim()
        if (value.isEmpty()) return error(MSG_HTML_REQUIRED)
        if (!value.contains('<') || !value.contains('>')) return error(MSG_HTML_REQUIRED)
        return FieldVerdict()
    }

    /**
     * 入口文件名规则（HTML 类专属，类型隔离）：
     * - 空 = 通过（保存时兜底 [com.make.apk.data.model.ProjectEntry.BundledHtml.DEFAULT_ENTRY_FILE]）；
     * - 非空须为**单段**文件名：禁路径分隔符 `/` `\` 与 `..`（防路径穿越）；
     * - 扩展名限 `.html` / `.htm`。
     */
    fun validateEntryFile(raw: String): FieldVerdict {
        val name = raw.trim()
        if (name.isEmpty()) return FieldVerdict()
        if (name.contains('/') || name.contains('\\') || name.contains("..")) {
            return error(MSG_ENTRY_FILE_PATH)
        }
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension !in ENTRY_FILE_EXTENSIONS) return error(MSG_ENTRY_FILE_EXTENSION)
        return FieldVerdict()
    }

    /**
     * 入口资产包规则：路径非空 + `.zip` + 文件存在 + 不超过 100MB。
     *
     * 文件是否存在 / 多大由 [ArchiveProbeResult] 给出（探测在 IO 层完成，
     * 校验器只做判定，保持纯函数）。
     */
    fun validateEntryArchive(raw: String?, probe: ArchiveProbeResult?): FieldVerdict {
        val path = raw?.trim().orEmpty()
        if (path.isEmpty()) return error(MSG_ARCHIVE_REQUIRED)
        if (!path.lowercase().endsWith(ARCHIVE_EXTENSION)) return error(MSG_ARCHIVE_REQUIRED)
        if (probe == null || !probe.exists) return error(MSG_ARCHIVE_REQUIRED)
        if (probe.sizeBytes > MAX_ARCHIVE_SIZE_BYTES) return error(MSG_ARCHIVE_REQUIRED)
        return FieldVerdict()
    }

    // ── 自动建议（§2.6）───────────────────────────────────────────────

    /**
     * 应用名建议：
     * - URL → host 去掉 `www.` 前缀；
     * - HTML → "我的网页"；
     * - 资产包 → 文件名去扩展名。
     */
    @Suppress("UNUSED_PARAMETER")
    fun suggestApkName(
        type: ProjectType?,
        url: String,
        html: String,
        archivePath: String?,
    ): String? = when (type) {
        ProjectType.URL -> hostOf(url)?.removePrefix("www.")?.takeIf { it.isNotBlank() }
        ProjectType.HTML -> DEFAULT_HTML_NAME
        ProjectType.VUE, ProjectType.PHP -> archivePath
            ?.let { java.io.File(it).nameWithoutExtension }
            ?.takeIf { it.isNotBlank() }
        null -> null
    }

    // ── 壳条目引用的本地页面 ─────────────────────────────────────────

    /**
     * 壳条目引用的**本地页面**是否仍存在于项目文件里（保存前体检）。
     *
     * 只**警告不阻断**：条目都是用户自己配的，页面被删常常是有意为之（也可能忘了），
     * 留一条可见提醒比拦住保存更有用——这类条目在预览 / 导出里表现为"点了没反应"，
     * 用户很难自己定位到原因。
     *
     * @param spec 项目壳契约（四区条目全部纳入）。
     * @param localPageFileNames 项目内可加载的本地页面文件名（空集 = 该项目没有本地页面）。
     */
    fun validateShellLocalPages(
        spec: ShellSpec,
        localPageFileNames: List<String>,
    ): FieldVerdict {
        val missing = spec.allItems
            .mapNotNull { (it.action as? ShellAction.OpenLocalPage)?.path }
            .map { it.trim() }
            .filter { it.isNotEmpty() && it !in localPageFileNames }
            .distinct()
        if (missing.isEmpty()) return FieldVerdict()
        val shown = missing.joinToString("、").let {
            if (it.length > MAX_MISSING_PAGES_SNIPPET) {
                it.take(MAX_MISSING_PAGES_SNIPPET) + ELLIPSIS
            } else {
                it
            }
        }
        return warning(MSG_SHELL_LOCAL_PAGE_MISSING + shown)
    }

    /**
     * 包名建议：URL 的反向域名（`example.com` → `com.example`）。
     *
     * 非 URL 类型不给建议（HTML / 资产包没有域名可依据）。
     */
    fun suggestPackageName(type: ProjectType?, url: String): String? {
        if (type != ProjectType.URL) return null
        val host = hostOf(url) ?: return null
        val labels = host.removePrefix("www.").split('.').filter { it.isNotBlank() }
        if (labels.isEmpty()) return null
        return labels
            .asReversed()
            .joinToString(".") { label -> sanitizePackageSegment(label) }
            .takeIf { it.isNotBlank() }
    }

    // ── 内部 ───────────────────────────────────────────────────────────

    /** 构造一条错误结论。 */
    private fun error(text: String): FieldVerdict =
        FieldVerdict(error = FieldMessage(text, FieldMessage.Severity.ERROR))

    /** 构造一条警告结论。 */
    private fun warning(text: String): FieldVerdict =
        FieldVerdict(warning = FieldMessage(text, FieldMessage.Severity.WARNING))

    /** 提取 URL 的 host（无 scheme 或格式异常时返回 null）。 */
    private fun hostOf(url: String): String? {
        val value = url.trim()
        val afterScheme = value.substringAfter("://", "")
        if (afterScheme == value) return null
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBefore(':')
        return host.takeIf { it.isNotBlank() }
    }

    /** 把域名标签清洗成合法包名片段（小写字母数字下划线）。 */
    private fun sanitizePackageSegment(label: String): String {
        val cleaned = label.lowercase().map { c -> if (c.isLetterOrDigit() || c == '_') c else '_' }
            .joinToString("")
            .trim('_')
        if (cleaned.isEmpty()) return ""
        return if (cleaned.first().isDigit()) "a$cleaned" else cleaned
    }

    companion object {
        /** 应用名硬上限。 */
        const val MAX_APK_NAME_LENGTH: Int = 50

        /** 应用名软上限（超过给警告）。 */
        const val RECOMMENDED_APK_NAME_LENGTH: Int = 30

        /** 包名长度上限。 */
        const val MAX_PACKAGE_NAME_LENGTH: Int = 255

        /** 版本号每段上限。 */
        const val MAX_VERSION_SEGMENT: Int = 999

        /** 内部版本号下限。 */
        const val MIN_VERSION_CODE: Int = 1

        /** 内部版本号上限（Android int 上限）。 */
        const val MAX_VERSION_CODE: Int = 2147483647

        /** 资产包扩展名。 */
        const val ARCHIVE_EXTENSION: String = ".zip"

        /** 入口文件允许的扩展名（HTML 类专属）。 */
        val ENTRY_FILE_EXTENSIONS: List<String> = listOf("html", "htm")

        /** 资产包大小上限（100MB）。 */
        const val MAX_ARCHIVE_SIZE_BYTES: Long = 100L * 1024 * 1024

        /** 建议的图标扩展名。 */
        const val ICON_PREFERRED_EXTENSION: String = "png"

        /** HTML 类型的默认应用名建议。 */
        const val DEFAULT_HTML_NAME: String = "我的网页"

        /** 合法包名正则。 */
        val PACKAGE_NAME_PATTERN: Regex = Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$")

        /** 禁止使用的包名前缀（与系统/官方包名冲突）。 */
        val RESERVED_PACKAGE_PREFIXES: List<String> =
            listOf("android.", "java.", "com.android.", "com.google.")

        /** 禁止的网址协议。 */
        val BLOCKED_URL_SCHEMES: List<String> = listOf("javascript:", "data:", "file:")

        // ── 面向用户的中文提示（唯一来源）──────────────────────────────

        /** 未选择项目类型。 */
        const val MSG_TYPE_REQUIRED: String = "请选择项目类型"

        /** 应用名为空。 */
        const val MSG_NAME_REQUIRED: String = "请填写应用名称"

        /** 应用名含非法字符。 */
        const val MSG_NAME_ILLEGAL: String = "名称不能包含换行或特殊符号"

        /** 应用名超长。 */
        const val MSG_NAME_TOO_LONG: String = "名称最多 50 个字"

        /** 应用名偏长。 */
        const val MSG_NAME_TOO_LONG_SOFT: String = "名称偏长，建议 30 字以内"

        /** 包名格式错误。 */
        const val MSG_PACKAGE_FORMAT: String = "包名格式不对，示例：com.example.app"

        /** 包名命中保留前缀。 */
        const val MSG_PACKAGE_RESERVED: String = "不能以 android./java./com.android./com.google. 开头"

        /** 包名超长。 */
        const val MSG_PACKAGE_TOO_LONG: String = "包名最长 255 个字符"

        /** 包名已被占用。 */
        const val MSG_PACKAGE_OCCUPIED: String = "该包名已被其他项目占用"

        /** 版本号格式错误。 */
        const val MSG_VERSION_FORMAT: String = "版本号格式应为 1.0 或 1.0.0"

        /** 版本号段值越界。 */
        const val MSG_VERSION_SEGMENT_RANGE: String = "版本号每段最大 999"

        /** 版本号与二段式不符。 */
        const val MSG_VERSION_SCHEME_TWO: String = "当前为二段式，请填 1.0"

        /** 版本号与三段式不符。 */
        const val MSG_VERSION_SCHEME_THREE: String = "当前为三段式，请填 1.0.0"

        /** 内部版本号越界。 */
        const val MSG_VERSION_CODE_RANGE: String = "版本号需为 1–2147483647 的整数"

        /** 图标格式建议。 */
        const val MSG_ICON_PNG: String = "建议使用 PNG 图标"

        /** 网址为空或格式错误。 */
        const val MSG_URL_REQUIRED: String = "请填写 http(s) 开头的网址"

        /** 网址协议被禁。 */
        const val MSG_URL_SCHEME_BLOCKED: String = "不支持该链接协议"

        /** HTML 为空或不含标签。 */
        const val MSG_HTML_REQUIRED: String = "请填写 HTML 内容"

        /** 入口文件名含路径成分。 */
        const val MSG_ENTRY_FILE_PATH: String = "入口文件只能填文件名，不能含路径"

        /** 入口文件扩展名不支持。 */
        const val MSG_ENTRY_FILE_EXTENSION: String = "入口文件须为 .html 或 .htm"

        /** 资产包不合规。 */
        const val MSG_ARCHIVE_REQUIRED: String = "请选择 zip 压缩包（不超过 100MB）"

        /** 壳条目引用的本地页面在项目里已不存在（警告，不阻断保存）。 */
        const val MSG_SHELL_LOCAL_PAGE_MISSING: String = "壳条目引用的本地页面已不存在："

        /** 缺失页面清单的最大展示长度（超出截断，避免提示把页面撑满）。 */
        const val MAX_MISSING_PAGES_SNIPPET: Int = 60

        /** 截断省略号。 */
        const val ELLIPSIS: String = "…"
    }
}
