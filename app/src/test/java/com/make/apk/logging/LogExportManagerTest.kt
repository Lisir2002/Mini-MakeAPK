package com.make.apk.logging

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [LogExportManager.zipFiles] 纯 JVM 单测（**不启动 Android**）。
 *
 * 只测抽出来的纯文件逻辑：条目名、内容一致性、目录自动创建、空输入语义。
 * 涉及 Context / FileProvider / Intent 的函数只能真机验，逻辑分支已由实现内
 * `runCatching` 全覆盖（任何异常都收敛为 null / false）。
 */
class LogExportManagerTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun zipFiles_packsAllFiles_withFlatEntryNamesAndSameContent() {
        val sourceDir = tempFolder.newFolder("session")
        val contents = mapOf(
            "app.log" to "app line 1\napp line 2\n",
            "operations.log" to "op line\n",
            "preview.log" to "preview line\n",
        )
        contents.forEach { (name, text) -> File(sourceDir, name).writeText(text) }
        val files = sourceDir.listFiles()?.toList().orEmpty()
        val target = File(tempFolder.newFolder("out"), "mma-logs-test.zip")

        assertTrue(LogExportManager.zipFiles(files, target))
        assertTrue(target.isFile)

        ZipFile(target).use { zip ->
            val entries = zip.entries().toList().map { it.name }.sorted()
            assertEquals(contents.keys.sorted(), entries)
            contents.forEach { (name, text) ->
                zip.getInputStream(zip.getEntry(name)).bufferedReader().use { reader ->
                    assertEquals(text, reader.readText())
                }
            }
        }
    }

    @Test
    fun zipFiles_createsMissingTargetDirectory() {
        val source = File(tempFolder.newFolder("session"), "app.log").apply { writeText("hello\n") }
        // 目标父目录刻意不存在：zipFiles 必须自己 mkdirs，否则首次导出就失败。
        val target = File(
            File(tempFolder.root, "cache/log-export"),
            "mma-logs-nested.zip",
        )

        assertTrue(LogExportManager.zipFiles(listOf(source), target))
        assertTrue(target.isFile)
        assertEquals("app.log", ZipFile(target).use { it.entries().nextElement().name })
    }

    @Test
    fun zipFiles_returnsFalseForEmptyList_andCreatesNoFile() {
        val target = File(tempFolder.newFolder("out"), "empty.zip")

        // 语义钉死：空列表 = 无可导出内容，返回 false 且不留半成品 zip。
        assertFalse(LogExportManager.zipFiles(emptyList(), target))
        assertFalse(target.exists())
    }
}
