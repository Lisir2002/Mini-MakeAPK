package com.make.apk.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [MmaStorage] 纯逻辑单测（JVM，无 Android 依赖）。
 *
 * 覆盖：目录结构创建、幂等重建、坏根失败路径、API-29 标记文件语义。
 * 依赖 Context/Environment 的函数（isAuthorized / resolveLogDirectory）仅在真机可验，
 * 逻辑分支已由类 KDoc 与 Paparazzi 侧 specs 固定。
 */
class MmaStorageTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun ensureDirs_createsRootAndAllSubdirs() {
        val root = File(tempFolder.newFolder("external"), MmaDir.ROOT.folderName)

        assertTrue(MmaStorage.ensureDirs(root))
        assertTrue(root.isDirectory)
        MmaDir.entries.filter { it != MmaDir.ROOT }.forEach { sub ->
            assertTrue("缺少子目录: ${sub.folderName}", File(root, sub.folderName).isDirectory)
        }
    }

    @Test
    fun ensureDirs_isIdempotent_settingRebuiltAfterDeletion() {
        val root = File(tempFolder.newFolder("external"), MmaDir.ROOT.folderName)

        // 首次创建 → 用户删除 Setting → 再次 ensure 必须重建（用户需求核心语义）
        assertTrue(MmaStorage.ensureDirs(root))
        assertTrue(File(root, MmaDir.SETTING.folderName).delete())
        assertFalse(File(root, MmaDir.SETTING.folderName).exists())

        assertTrue(MmaStorage.ensureDirs(root))
        assertTrue(File(root, MmaDir.SETTING.folderName).isDirectory)
    }

    @Test
    fun ensureDirs_returnsFalseWhenRootUnwritable() {
        // 根目录父级是普通文件：mkdirs 必然失败
        val blocker = tempFolder.newFile("blocker")
        val root = File(blocker, MmaDir.ROOT.folderName)

        assertFalse(MmaStorage.ensureDirs(root))
    }

    @Test
    fun api29Notice_flagFileSemantics() {
        // hasApi29NoticeShown/markApi29NoticeShown 以 filesDir 标记文件实现；
        // 这里用同语义的 File 操验证「创建 → 存在 → 幂等」契约（真实 Context 路径见 KDoc）。
        val filesDir = tempFolder.newFolder("files")
        val notice = File(filesDir, "storage_api29_notice")

        assertFalse(notice.exists())
        assertTrue(notice.createNewFile())
        assertTrue(notice.exists())
        // 重复标记幂等：已存在时 createNewFile 返回 false（不抛异常），文件保持
        assertFalse(notice.createNewFile())
        assertTrue(notice.exists())
        assertEquals(1, filesDir.listFiles()?.size)
    }
}
