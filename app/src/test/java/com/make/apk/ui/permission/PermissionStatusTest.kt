package com.make.apk.ui.permission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 权限状态机纯逻辑单测（JVM，无 Android 依赖）。
 *
 * 覆盖 [resolveStatusAfterResult] 迁移表全分支与 [stageFor] 映射——
 * 这三行判定是「假授权/弹窗不出现/无限重弹」事故的高发区，必须有基线。
 */
class PermissionStatusTest {

    @Test
    fun grantedResult_movesToGranted_noPrompt() {
        assertEquals(
            RuntimePermissionStatus.Granted,
            resolveStatusAfterResult(granted = true, rationaleShown = false),
        )
        assertNull(stageFor(RuntimePermissionStatus.Granted))
    }

    @Test
    fun deniedWithRationale_movesToDenied_promptsRationale() {
        val status = resolveStatusAfterResult(granted = false, rationaleShown = true)
        assertEquals(RuntimePermissionStatus.Denied, status)
        assertEquals(PermissionDialogStage.Rationale, stageFor(status))
    }

    @Test
    fun deniedWithoutRationale_movesToPermanentlyDenied_promptsSettings() {
        val status = resolveStatusAfterResult(granted = false, rationaleShown = false)
        assertEquals(RuntimePermissionStatus.PermanentlyDenied, status)
        assertEquals(PermissionDialogStage.GoToSettings, stageFor(status))
    }

    @Test
    fun stageFor_nonPromptingStates_returnsNull() {
        assertNull(stageFor(RuntimePermissionStatus.Idle))
        assertNull(stageFor(RuntimePermissionStatus.Requesting))
    }

    @Test
    fun presetSpecs_areUserFacingAndComplete() {
        val specs = listOf(
            NotificationPermissionSpec,
            InstallPackagesPermissionSpec,
            AllFilesAccessPermissionSpec,
            Api29StorageLimitSpec,
        )
        for (spec in specs) {
            assertTrue("标题为空：$spec", spec.title.isNotBlank())
            for (msg in listOf(
                spec.firstTimeMessage,
                spec.rationaleMessage,
                spec.settingsMessage,
            )) {
                assertTrue("文案为空：$spec", msg.isNotBlank())
                // 规约：说人话，不甩权限名（见 MmaPermissionSpec KDoc）
                assertFalse("文案含权限名：$msg", "POST_NOTIFICATIONS" in msg)
                assertFalse("文案含权限名：$msg", "REQUEST_INSTALL_PACKAGES" in msg)
                assertFalse("文案含权限名：$msg", "MANAGE_EXTERNAL_STORAGE" in msg)
                assertFalse("文案含权限名：$msg", "WRITE_EXTERNAL_STORAGE" in msg)
            }
        }
    }
}
