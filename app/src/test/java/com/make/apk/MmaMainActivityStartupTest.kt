package com.make.apk

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Activity 启动路径集成测试（Robolectric）。
 *
 * 背景：rc3 装机仍闪退（Application 层已防御，见 [MmaApplicationStartupTest]），
 * 怀疑面转向 Activity/Compose 层——`MainActivity.onCreate → setContent →
 * MmaTheme → MmaApp → MmaNavHost(Projects) → StoragePermissionGate` 全链。
 * Paparazzi 只渲染首帧 Composable 且不执行 LaunchedEffect，覆盖不到本链。
 * 本类在 JVM 上真实驱动 Activity 完整生命周期，任一步抛异常即复现。
 *
 * 注意：本测试会触发加密数据库初始化（MmaNavHost 组合期取 repository →
 * SqlCipherPassphrase Keystore 操作）；Robolectric 不支持 AndroidKeyStore
 * provider 与 SQLCipher native 库，若失败会体现在堆栈的 Keystore/native 段，
 * 那属于测试环境限制而非真机缺陷，需与 UI 层堆栈区分判读。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = MmaApplication::class)
class MmaMainActivityStartupTest {

    @Test
    fun mainActivity_fullStartup_doesNotCrash() {
        // setup() = create → start → resume → 可见，覆盖 onCreate/setContent/首帧组合
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
    }
}
