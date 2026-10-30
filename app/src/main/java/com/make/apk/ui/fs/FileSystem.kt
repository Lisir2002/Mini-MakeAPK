package com.make.apk.ui.fs

import java.util.Locale

/**
 * 文件系统中性模型 + 纯函数（与持久化模型 [com.make.apk.data.model.ProjectFile] 完全解耦）。
 *
 * ## 定位
 * 目录浏览器组件 [com.make.apk.ui.components.DirectoryBrowser] 只认本文件的模型与函数，
 * 不依赖任何业务实体——后续资产包浏览、导出产物浏览等页面可直接复用。
 * 适配器（如 [com.make.apk.ui.editor.ProjectCodeTab]）负责把业务数据（ProjectFile 列表）
 * 转换成 [RawFsEntry] 喂给 [buildFsItems]。
 *
 * ## 空目录约定
 * `List<ProjectFile>` 无法表达空目录，故约定：**目录以 `name` 以 "/" 结尾、`content` 为空**
 * 的条目持久化（如 `assets/img/`）。[buildFsItems] 同时支持显式空目录条目与
 * 由文件路径隐式推导出的目录，二者共存不冲突；序列化无障碍（ProjectFile 本就 @Serializable）。
 */

/** 原始路径条目：组件对"数据来自哪"的唯一耦合点，适配器负责注入。 */
data class RawFsEntry(
    /** 相对根路径；目录以 "/" 结尾（空目录约定），文件不含尾 "/"。 */
    val name: String,
    /** 字节大小（文件用；目录传 0）。 */
    val sizeBytes: Long,
)

/**
 * 文件系统条目（中性）：既可能是目录也可能是文件。
 *
 * @param name 直接名称（不含父路径，如 `style.css`）。
 * @param path 相对根全路径（目录不带尾 "/"）。
 * @param isDirectory 是否为目录。
 * @param sizeBytes 文件字节数；目录为 0。
 * @param childCount 目录的直接子项数（含子目录与文件）；文件为 0（用于"N 项"展示）。
 * @param isEntry 是否为"入口"哨兵（业务注入；组件据此禁用删除 / 剪切，可复制 / 重命名）。
 */
data class FsItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val childCount: Int,
    val isEntry: Boolean = false,
)

/** 粘贴模式。 */
enum class PasteMode { COPY, CUT }

/** 粘贴请求（组件内部剪贴态 → 业务层落库）。 */
data class PasteRequest(
    /** 复制 / 剪切。 */
    val mode: PasteMode,
    /** 待粘贴的源全路径（文件或目录）。 */
    val sources: List<String>,
    /** 目标目录（相对根，"" = 根）。 */
    val targetDir: String,
)

// ── 路径工具（纯函数） ──────────────────────────────────────────────

/**
 * 路径归一化：去首尾空白、去前导 "/"，并去除尾随 "/"（目录内部比较统一用无尾斜杠形态）。
 * 例如 `" assets/img/ "` → `"assets/img"`。
 */
fun normalizePath(path: String): String =
    path.trim().trimStart('/').removeSuffix("/")

/** 取父目录路径：根返回 ""；`assets/img/logo.svg` → `assets/img`。 */
fun parentPathOf(path: String): String =
    path.substringBeforeLast('/', missingDelimiterValue = "")

// ── 树构建（纯函数，便于单测） ──────────────────────────────────────

/**
 * 原始路径条目 → 中性条目列表（扁平，含目录与文件）。
 *
 * 规则：
 * - 目录来自两类来源：① 显式空目录条目（`name` 以 "/" 结尾）；② 文件路径的各级前缀；
 * - 每个目录的父链也递归补成目录（保证任意深度的空目录都能正确挂接）；
 * - 目录按"文件夹优先、同级按名（不区分大小写）"排序；渲染层不再排序；
 * - 目录的 [FsItem.childCount] = 其直接子项数（便于"N 项"展示）。
 *
 * @param entries 原始路径集合（含可能的空目录条目与重复前缀，本函数去重）。
 * @param entryPath 入口文件全路径（命中则 [FsItem.isEntry] = true）；null 表示无入口。
 */
fun buildFsItems(entries: List<RawFsEntry>, entryPath: String? = null): List<FsItem> {
    val entryNorm = entryPath?.let { normalizePath(it) }?.takeIf { it.isNotEmpty() }

    val dirPaths = LinkedHashSet<String>()
    val fileEntries = LinkedHashMap<String, RawFsEntry>()

    fun collectDirPrefixes(path: String) {
        var p = path.substringBeforeLast('/', "")
        while (p.isNotEmpty()) {
            dirPaths.add(p)
            p = p.substringBeforeLast('/', "")
        }
    }

    for (raw in entries) {
        val n = normalizePath(raw.name)
        if (n.isEmpty()) continue
        if (raw.name.trimEnd().endsWith('/')) {
            // 显式空目录条目
            dirPaths.add(n)
            collectDirPrefixes(n)
        } else {
            fileEntries[n] = raw
            collectDirPrefixes(n)
        }
    }
    // 目录父链已由上方遍历时的 collectDirPrefixes 一并补全（显式目录自身已 add，
    // 各级父前缀逐层 add）；无需二次收集 / 重复 add。allDirs 直接复用该集合（行为不变）。
    val allDirs = dirPaths

    val children = mutableMapOf<String, MutableList<FsItem>>()
    fun bucket(parent: String): MutableList<FsItem> = children.getOrPut(parent) { mutableListOf() }

    // 先放文件（浅层）
    fileEntries.forEach { (path, raw) ->
        bucket(parentPathOf(path)).add(
            FsItem(
                name = path.substringAfterLast('/'),
                path = path,
                isDirectory = false,
                sizeBytes = raw.sizeBytes,
                childCount = 0,
                isEntry = path == entryNorm,
            ),
        )
    }
    // 再放目录（按深度降序，保证子项先于父项入桶，childCount 即直接子项数）
    allDirs.sortedByDescending { d -> d.count { it == '/' } }.forEach { d ->
        val kids = bucket(d)
        bucket(parentPathOf(d)).add(
            FsItem(
                name = d.substringAfterLast('/'),
                path = d,
                isDirectory = true,
                sizeBytes = 0,
                childCount = kids.size,
                isEntry = false,
            ),
        )
    }

    // 每个父桶内排序：目录优先，其次按名（不区分大小写）
    children.values.forEach { list ->
        list.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.US) }))
    }
    return children.values.flatten()
}

/**
 * 从原始路径条目推导出全部目录路径集合（中性模型，与 [com.make.apk.data.model.ProjectFile] 完全解耦）。
 *
 * 规则：对每个条目名先 [normalizePath] 去尾 "/"（兼容旧数据），
 * - 若为显式空目录条目（[RawFsEntry.name] 尾随 "/"）→ 自身即目录，纳入；
 * - 否则视为文件，从其父链起收集目录；
 * - 逐层 `substringBeforeLast('/')` 收集全部父前缀（支持多级嵌套）。
 *
 * 既覆盖显式空目录条目，也覆盖由文件路径隐式推导出的目录——二者都应被当作
 * "已存在的目录名"参与冲突判定。这正是 [resolveCopyName] 的目标集合与目录重命名
 * 冲突判定所需的补全项，避免"同名目录因无显式条目而被漏判"。
 *
 * @param files 原始路径条目集合（[RawFsEntry.name] 可含尾 "/" 表示为空目录，或无尾斜杠的文件 / 旧数据）。
 * @return 全部出现过的目录全路径集合（无尾 "/"）；无目录时返回空集。
 */
fun directoryPathsOf(files: List<RawFsEntry>): Set<String> {
    val dirs = mutableSetOf<String>()
    for (raw in files) {
        val n = normalizePath(raw.name)
        if (n.isEmpty()) continue
        // 显式空目录条目：自身即目录；文件：从其父链起收集（不含自身）
        var p = if (raw.name.trimEnd().endsWith('/')) n else n.substringBeforeLast('/', "")
        while (p.isNotEmpty()) {
            dirs.add(p)
            p = p.substringBeforeLast('/', "")
        }
    }
    return dirs
}

/** 取某目录的直接子项（已排序：目录优先、同级按名）。 */
fun childItemsOf(items: List<FsItem>, parentPath: String): List<FsItem> =
    items.filter { parentPathOf(it.path) == parentPath }
        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.US) }))

/** 统计某目录下的文件总数（含各级子目录，不含目录自身）。 */
fun countDescendantFiles(items: List<FsItem>, dirPath: String): Int =
    items.count { !it.isDirectory && it.path.startsWith("$dirPath/") }

/** UTF-8 字节长度（文件大小统计用）。 */
fun utf8ByteLength(text: String): Long = text.toByteArray(Charsets.UTF_8).size.toLong()

/**
 * 大小格式化（1024 进制；统一 Locale.US，避免逗号 / 点随系统语言漂移）。
 * 复用于目录浏览器的文件元数据展示与业务层大小统计。
 */
fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
    else -> String.format(Locale.US, "%.1f MB", bytes / 1024f / 1024f)
}

// ── 校验 / 改名 / 前缀替换（纯函数，便于单测） ──────────────────────

/**
 * 校验新建 / 重命名的相对路径是否合法、不重名。
 *
 * @param rawName 用户输入（可能含子路径如 `css/style.css`）。
 * @param existingSiblingPaths 当前目录已有直接子项的全路径集合（用于重名 / 前缀冲突检测）。
 * @return 错误文案（常量见本文件底部）；null 表示通过。
 */
fun validateFsName(rawName: String, existingSiblingPaths: Set<String>): String? {
    val norm = normalizePath(rawName)
    if (norm.isEmpty()) return FS_NAME_EMPTY
    val segments = norm.split('/')
    if (segments.any { it == ".." || it == "." }) return FS_NAME_ILLEGAL
    if (norm.contains('\\')) return FS_NAME_ILLEGAL
    val normalizedSiblings = existingSiblingPaths.map { normalizePath(it) }.toSet()
    // 与任何兄弟精确冲突，或互为父子前缀冲突（如输入 `css/style.css` 但已存在文件 `css`）
    if (normalizedSiblings.contains(norm)) return FS_NAME_DUPLICATE
    if (normalizedSiblings.any { it.startsWith("$norm/") || norm.startsWith("$it/") }) return FS_NAME_DUPLICATE
    return null
}

/**
 * 同名冲突自动改名：依次尝试 `name`、`name 副本`、`name(2)`、`name(3)`……
 * 带扩展名时插入在扩展名之前（如 `style.css` → `style 副本.css`）。
 *
 * @param desiredName 期望的目标名（相对目标目录）。
 * @param taken 目标目录已占用的全名集合（用于冲突判定）。
 */
fun resolveCopyName(desiredName: String, taken: Set<String>): String {
    val norm = normalizePath(desiredName)
    if (norm !in taken) return norm
    val (stem, ext) = splitName(norm)
    var i = 2
    return buildList {
        add("$stem 副本$ext")
        while (true) {
            add("$stem($i)$ext")
            i++
        }
    }.first { it !in taken }
}

/**
 * 路径前缀替换（重命名 / 移动 / 目录复制核心）。
 *
 * - `css/a/b.txt` 中 from=`css/a` to=`assets/x` → `assets/x/b.txt`
 * - 空目录条目 `css/a/`（name 尾随 "/"） → `assets/x/`
 * - 同名文件 `css/a.txt` → `assets/x.txt`
 */
fun renamePathPrefix(path: String, fromPrefix: String, toPrefix: String): String {
    val from = normalizePath(fromPrefix)
    val to = normalizePath(toPrefix)
    return when {
        path == from -> to
        path == "$from/" -> "$to/"
        path.startsWith("$from/") -> to + path.substring(from.length)
        else -> path
    }
}

/** 拆名与扩展名：返回 (stem, ext)；ext 含前导点（如 ".css"），无扩展名则为 ""（`.gitignore` 视为无扩展名）。 */
private fun splitName(name: String): Pair<String, String> {
    val idx = name.lastIndexOf('.')
    if (idx <= 0) return name to ""
    return name.substring(0, idx) to name.substring(idx)
}

// ── 常量（用户可见文案集中，组件复用） ──────────────────────────────

/** 名称空。 */
const val FS_NAME_EMPTY: String = "名称不能为空"

/** 名称含非法字符（\\ 或 .. 段）。 */
const val FS_NAME_ILLEGAL: String = "名称包含非法字符（不能含 \\ 或 ..）"

/** 同名或前缀冲突。 */
const val FS_NAME_DUPLICATE: String = "同名条目已存在"
