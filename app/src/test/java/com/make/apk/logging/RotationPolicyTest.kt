package com.make.apk.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 文件轮转策略测试（纯逻辑，无 Android 依赖）。 */
class RotationPolicyTest {

    @Test
    fun shouldRotate_isFalseWhenThereIsRoom() {
        assertFalse(RotationPolicy.shouldRotate(currentSizeBytes = 100, incomingBytes = 100, maxFileSizeBytes = 1_000))
    }

    @Test
    fun shouldRotate_isTrueWhenIncomingExceedsLimit() {
        assertTrue(RotationPolicy.shouldRotate(currentSizeBytes = 950, incomingBytes = 100, maxFileSizeBytes = 1_000))
    }

    @Test
    fun shouldRotate_isTrueWhenAlreadyAtLimit() {
        assertTrue(RotationPolicy.shouldRotate(currentSizeBytes = 1_000, incomingBytes = 1, maxFileSizeBytes = 1_000))
    }

    @Test
    fun rotatedName_appendsIndex() {
        assertEquals("mma.log.1", RotationPolicy.rotatedName("mma.log", 1))
        assertEquals("mma.log.12", RotationPolicy.rotatedName("mma.log", 12))
    }

    @Test
    fun filesToPrune_keepsNothingWhenUnderLimit() {
        val existing = listOf("mma.log.1", "mma.log.2", "mma.log.3")
        assertEquals(emptyList<String>(), RotationPolicy.filesToPrune(existing, 5))
    }

    @Test
    fun filesToPrune_dropsOldestBeyondLimit() {
        val existing = listOf("mma.log.1", "mma.log.2", "mma.log.3")
        // 保留最近 2 个（3、2），删除最旧的 1
        assertEquals(listOf("mma.log.1"), RotationPolicy.filesToPrune(existing, 2))
    }

    @Test
    fun filesToPrune_withMaxFilesZero_removesEverything() {
        val existing = listOf("mma.log.1", "mma.log.2")
        assertEquals(existing, RotationPolicy.filesToPrune(existing, 0))
    }

    @Test
    fun filesToPrune_treatsUnparseableAsOldest() {
        val existing = listOf("mma.log.abc", "mma.log.1", "mma.log.2")
        assertEquals(listOf("mma.log.abc"), RotationPolicy.filesToPrune(existing, 2))
    }

    @Test
    fun indexOf_parsesTrailingNumber() {
        assertEquals(7, RotationPolicy.indexOf("mma.log.7"))
        assertEquals(Int.MIN_VALUE, RotationPolicy.indexOf("mma.log.abc"))
    }
}
