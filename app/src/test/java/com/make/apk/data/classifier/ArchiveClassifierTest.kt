package com.make.apk.data.classifier

import com.make.apk.data.model.ArchiveKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 归档嗅探器测试（§8.5 D-M2 迁移对账第二阶段）。
 *
 * 覆盖：Vue / PHP 判据、GENERIC 兜底、依赖目录跳过、损坏 zip 容错。
 */
class ArchiveClassifierTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun directory_withVuePackageJson_classifiedAsVue() {
        val root = temp.newFolder("vue-project")
        File(root, "package.json").writeText("""{"dependencies":{"vue":"^3.4.0"}}""")

        val verdict = ArchiveClassifier.classifyDirectory(root)

        assertEquals(ArchiveKind.VUE, verdict.kind)
        assertEquals("package.json(vue)", verdict.reason)
    }

    @Test
    fun directory_withReactPackageJson_classifiedAsVue() {
        // React 工程归入 VUE（前端框架归并，见 §8.5：不新增第五个 ProjectType）
        val root = temp.newFolder("react-project")
        File(root, "package.json").writeText("""{"dependencies":{"react":"^18.0.0"}}""")

        assertEquals(ArchiveKind.VUE, ArchiveClassifier.classifyDirectory(root).kind)
    }

    @Test
    fun directory_withPhpFile_classifiedAsPhp() {
        val root = temp.newFolder("php-project")
        File(root, "index.php").writeText("<?php echo 1;")

        val verdict = ArchiveClassifier.classifyDirectory(root)

        assertEquals(ArchiveKind.PHP, verdict.kind)
        assertEquals(".php", verdict.reason)
    }

    @Test
    fun directory_withComposerJson_classifiedAsPhp() {
        val root = temp.newFolder("composer-project")
        File(root, "composer.json").writeText("""{"require":{"php":"^8.2"}}""")

        assertEquals(ArchiveKind.PHP, ArchiveClassifier.classifyDirectory(root).kind)
    }

    @Test
    fun directory_withoutAnyMarker_classifiedAsGeneric() {
        val root = temp.newFolder("plain-project")
        File(root, "readme.txt").writeText("hello")

        val verdict = ArchiveClassifier.classifyDirectory(root)

        assertEquals(ArchiveKind.GENERIC, verdict.kind)
        // 无判据命中 → 不猜（reason 为 null）
        assertEquals(null, verdict.reason)
    }

    @Test
    fun nodeModulesDirectory_isSkipped() {
        // 依赖目录里不该被当作工程根标记
        val root = temp.newFolder("project-with-deps")
        val nodeModules = File(root, "node_modules").apply { mkdirs() }
        File(nodeModules, "package.json").writeText("""{"dependencies":{"vue":"^3.0.0"}}""")

        // 根目录无 package.json → 应为 GENERIC（依赖目录被跳过）
        assertEquals(ArchiveKind.GENERIC, ArchiveClassifier.classifyDirectory(root).kind)
    }

    @Test
    fun missingDirectory_classifiedAsGeneric() {
        val missing = File(temp.root, "does-not-exist")
        val verdict = ArchiveClassifier.classifyDirectory(missing)

        assertEquals(ArchiveKind.GENERIC, verdict.kind)
        assertEquals(null, verdict.reason)
    }

    @Test
    fun zip_withVuePackageJson_classifiedAsVue() {
        val zip = temp.newFile("vue.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("package.json"))
            out.write("""{"dependencies":{"vue":"^3.4.0"}}""".toByteArray())
            out.closeEntry()
        }

        val verdict = ArchiveClassifier.classifyZip(zip)

        assertEquals(ArchiveKind.VUE, verdict.kind)
        assertEquals("package.json(vue)", verdict.reason)
    }

    @Test
    fun zip_withPhpEntry_classifiedAsPhp() {
        val zip = temp.newFile("php.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("index.php"))
            out.write("<?php".toByteArray())
            out.closeEntry()
        }

        assertEquals(ArchiveKind.PHP, ArchiveClassifier.classifyZip(zip).kind)
    }

    @Test
    fun corruptZip_classifiedAsGenericWithoutThrowing() {
        val zip = temp.newFile("corrupt.zip")
        zip.writeText("this is not a zip file at all")

        // 损坏归档不得让首启对账崩溃
        val verdict = ArchiveClassifier.classifyZip(zip)
        assertEquals(ArchiveKind.GENERIC, verdict.kind)
    }

    @Test
    fun classify_autoDetectsDirectoryOrZip() {
        val dir = temp.newFolder("auto-dir")
        File(dir, "index.php").writeText("<?php")

        val zip = temp.newFile("auto.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("composer.json"))
            out.write("{}".toByteArray())
            out.closeEntry()
        }

        assertEquals(ArchiveKind.PHP, ArchiveClassifier.classify(dir.absolutePath).kind)
        assertEquals(ArchiveKind.PHP, ArchiveClassifier.classify(zip.absolutePath).kind)
        assertEquals(ArchiveKind.GENERIC, ArchiveClassifier.classify("/no/such/path").kind)
    }
}
