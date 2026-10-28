package com.make.apk.data.classifier

import com.make.apk.data.model.ArchiveKind
import java.io.File
import java.util.zip.ZipFile

/**
 * 归档资产嗅探器（§8.5 D-M2 迁移对账第二阶段）。
 *
 * **为什么需要它**：v2 → v3 迁移时，旧 `ARCHIVE` 类型无法在 SQL 层判定究竟是 Vue 工程
 * 还是 PHP 工程（那需要读文件内容）。迁移阶段先把 [ArchiveKind.GENERIC] 作为安全默认，
 * 升级后由本嗅探器**一次性**纠正为 VUE / PHP。
 *
 * 判定规则（与设计 §8.5 一致）：
 * - 含 `package.json`（且依赖里出现 vue / react）→ **VUE**
 * - 含 `.php` 文件或 `composer.json` → **PHP**
 * - 都不满足 → 维持 [ArchiveKind.GENERIC]（不猜，交给用户手动确认）
 *
 * **不新增第五个 ProjectType**：对账后必然落入 VUE / PHP 之一，
 * 故准星选择器四项 UI 不受影响（§8.5 D-M2 第 3 点）。
 */
object ArchiveClassifier {

    /** 判定结果：[ArchiveKind] + 命中依据（供日志与调试）。 */
    data class Verdict(
        val kind: ArchiveKind,
        /** 命中的判据描述；未命中任何判据时为 null。 */
        val reason: String?,
    )

    /**
     * 对**已解包目录**进行嗅探。
     *
     * @param directory 资产解包后的目录（`Archive.assetRef` 指向的位置）。
     * @return 判定结果；目录不存在或不可读时返回 GENERIC（安全默认，不猜）。
     */
    fun classifyDirectory(directory: File): Verdict {
        if (!directory.isDirectory) return Verdict(ArchiveKind.GENERIC, null)

        // PHP 优先判定：PHP 工程几乎不会带 package.json，而 Vue 工程可能带零散 .php 模板。
        val composer = findFile(directory, "composer.json", maxDepth = 3)
        if (composer != null) {
            return Verdict(ArchiveKind.PHP, "composer.json")
        }
        if (findFileByExtension(directory, ".php", maxDepth = 4) != null) {
            return Verdict(ArchiveKind.PHP, ".php")
        }

        val packageJson = findFile(directory, "package.json", maxDepth = 3)
        if (packageJson != null) {
            val content = runCatching { packageJson.readText() }.getOrDefault("")
            val reason = when {
                content.contains("\"vue\"", ignoreCase = true) -> "package.json(vue)"
                content.contains("\"react\"", ignoreCase = true) -> "package.json(react)"
                else -> "package.json"
            }
            return Verdict(ArchiveKind.VUE, reason)
        }

        return Verdict(ArchiveKind.GENERIC, null)
    }

    /**
     * 对**未解包 zip** 进行嗅探（只读条目名，不解压，避免大文件 IO）。
     *
     * @param zipFile 归档 zip 文件。
     * @return 判定结果；文件不存在/损坏时返回 GENERIC。
     */
    fun classifyZip(zipFile: File): Verdict {
        if (!zipFile.isFile) return Verdict(ArchiveKind.GENERIC, null)
        return runCatching {
            ZipFile(zipFile).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toList()
                when {
                    names.any { it.endsWith("composer.json", ignoreCase = true) } ->
                        Verdict(ArchiveKind.PHP, "composer.json")

                    names.any { it.endsWith(".php", ignoreCase = true) } ->
                        Verdict(ArchiveKind.PHP, ".php")

                    names.any { it.endsWith("package.json", ignoreCase = true) } -> {
                        val entry = names.first { it.endsWith("package.json", ignoreCase = true) }
                        val content = runCatching {
                            zip.getInputStream(zip.getEntry(entry)).use { it.readBytes().decodeToString() }
                        }.getOrDefault("")
                        val reason = when {
                            content.contains("\"vue\"", ignoreCase = true) -> "package.json(vue)"
                            content.contains("\"react\"", ignoreCase = true) -> "package.json(react)"
                            else -> "package.json"
                        }
                        Verdict(ArchiveKind.VUE, reason)
                    }

                    else -> Verdict(ArchiveKind.GENERIC, null)
                }
            }
        }.getOrDefault(Verdict(ArchiveKind.GENERIC, null))
    }

    /**
     * 通用入口：按路径自动选择目录 / zip 嗅探。
     *
     * @param assetRef 资产路径（目录或 zip 文件均可）。
     */
    fun classify(assetRef: String): Verdict {
        val file = File(assetRef)
        return when {
            file.isDirectory -> classifyDirectory(file)
            file.isFile -> classifyZip(file)
            else -> Verdict(ArchiveKind.GENERIC, null)
        }
    }

    /** 在 [root] 下按名查找文件（限制深度，避免大目录树遍历过深）。 */
    private fun findFile(root: File, name: String, maxDepth: Int): File? {
        if (maxDepth < 0) return null
        val direct = root.resolve(name)
        if (direct.isFile) return direct
        val children = root.listFiles() ?: return null
        for (child in children) {
            if (!child.isDirectory) continue
            // 跳过依赖与构建产物目录：里面不会放工程根标记文件，且数量巨大。
            if (child.name in SkipDirs) continue
            findFile(child, name, maxDepth - 1)?.let { return it }
        }
        return null
    }

    /** 在 [root] 下按扩展名查找文件（限制深度）。 */
    private fun findFileByExtension(root: File, extension: String, maxDepth: Int): File? {
        if (maxDepth < 0) return null
        val children = root.listFiles() ?: return null
        for (child in children) {
            if (child.isFile && child.name.endsWith(extension, ignoreCase = true)) return child
            if (child.isDirectory && child.name !in SkipDirs) {
                findFileByExtension(child, extension, maxDepth - 1)?.let { return it }
            }
        }
        return null
    }

    /** 遍历时跳过的目录（依赖 / 构建产物 / 版本控制）。 */
    private val SkipDirs = setOf(
        "node_modules",
        "build",
        "dist",
        "out",
        "target",
        "vendor",
        ".git",
        ".svn",
        ".gradle",
        "__MACOSX",
    )
}
