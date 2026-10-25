package com.make.apk.ui.permission

import androidx.compose.runtime.Composable
import app.cash.paparazzi.Paparazzi
import com.make.apk.ui.theme.MmaTheme
import org.junit.Rule
import org.junit.Test

/**
 * 权限二次确认弹窗截图回归（Paparazzi，纯 JVM）：三形态 × 亮暗 = 6 张。
 *
 * 锁定的视觉规格（DESIGN_LANGUAGE §8）：24dp 大圆角容器、64dp 圆形图标底、
 * 胶囊主按钮 + 次级文字按钮、三态文案切换不漂移。
 *
 * 注：直接测 [MmaPermissionDialogContent]（Dialog 外壳之下的纯内容）——
 * Paparazzi 无真实 Dialog window，渲染 Dialog 会崩；内容与 MmaPermissionDialog
 * 同源，像素仅差一层 scrim，视觉规格全部在内容里。
 */
class MmaPermissionDialogTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun lightFirstTime() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            dialog(PermissionDialogStage.FirstTime)
        }
    }

    @Test
    fun darkFirstTime() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) {
            dialog(PermissionDialogStage.FirstTime)
        }
    }

    @Test
    fun lightRationale() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            dialog(PermissionDialogStage.Rationale)
        }
    }

    @Test
    fun darkRationale() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) {
            dialog(PermissionDialogStage.Rationale)
        }
    }

    @Test
    fun lightGoToSettings() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            dialog(PermissionDialogStage.GoToSettings)
        }
    }

    @Test
    fun darkGoToSettings() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) {
            dialog(PermissionDialogStage.GoToSettings)
        }
    }

    /** 安装权限 spec 单独一张：验证 ic_install_mobile 图标渲染与安装文案（暗色另由单测覆盖文案）。 */
    @Test
    fun lightInstallFirstTime() = paparazzi.snapshot {
        MmaTheme(darkTheme = false) {
            MmaPermissionDialogContent(
                spec = InstallPackagesPermissionSpec,
                stage = PermissionDialogStage.FirstTime,
                onConfirm = {},
                onDismiss = {},
            )
        }
    }

    /** 通知权限 spec 覆盖三态文案（安装 spec 的文案由单测校验，不重复入图）。 */
    @Composable
    private fun dialog(stage: PermissionDialogStage) {
        MmaPermissionDialogContent(
            spec = NotificationPermissionSpec,
            stage = stage,
            onConfirm = {},
            onDismiss = {},
        )
    }
}
