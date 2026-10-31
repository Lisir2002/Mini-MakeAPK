package com.make.apk.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [LogDirectoryPicker] 纯函数单测（JVM，无 Android 依赖）。
 *
 * 目录优先级是启动期唯一的**正确性决策**（写错 = 用户永远看不到日志），
 * 故三条分支必须逐条钉死，不能靠真机试出来。
 */
class LogDirectoryPickerTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun pick_prefersPublicLogDir_whenAllThreeAvailable() {
        val publicLog = File(tempFolder.newFolder("public"), MmaDir.LOG.folderName)
        val externalPrivate = File(tempFolder.newFolder("external"), "mma-logs")
        val fallback = File(tempFolder.newFolder("private"), "logs")

        assertEquals(publicLog, LogDirectoryPicker.pick(publicLog, externalPrivate, fallback))
    }

    @Test
    fun pick_usesExternalPrivate_whenPublicUnavailable() {
        val externalPrivate = File(tempFolder.newFolder("external"), "mma-logs")
        val fallback = File(tempFolder.newFolder("private"), "logs")

        assertEquals(externalPrivate, LogDirectoryPicker.pick(null, externalPrivate, fallback))
    }

    @Test
    fun pick_fallsBackToPrivate_whenNothingAvailable() {
        val fallback = File(tempFolder.newFolder("private"), "logs")

        assertEquals(fallback, LogDirectoryPicker.pick(null, null, fallback))
    }
}
