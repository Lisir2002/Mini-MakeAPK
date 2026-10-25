package com.make.apk.ui.permission

import com.make.apk.ui.icons.MmaIcons

/**
 * 预置权限文案（Phase 2 真出包时随接入点一同启用）。
 *
 * 新增权限时在此追加一份 spec，并在文档（ARCHITECTURE 权限小节）登记接入清单；
 * 文案须经「读一遍不膈应」测试：想象用户第一次看到时会不会困惑或被吓到。
 */

/** 通知权限（Android 13+ 运行时权限，<33 系统安装时即授予，无需申请）。 */
val NotificationPermissionSpec = MmaPermissionSpec(
    title = "打包好了告诉你",
    iconRes = MmaIcons.Notifications,
    firstTimeMessage = "打包完成的那一刻，让小通知轻轻提醒你一声，不用一直守着屏幕等。",
    rationaleMessage = "刚才错过了通知权限，开启后打包一完成就能第一时间告诉你，错过进度可不好。",
    settingsMessage = "通知开关在系统设置里合上了，去轻轻拨开它，我们就能把打包结果送到你面前。",
)

/** 安装未知应用权限（特殊权限：无系统弹窗，跳系统设置页授权，API 26+ 需要）。 */
val InstallPackagesPermissionSpec = MmaPermissionSpec(
    title = "准备把 App 装进去",
    iconRes = MmaIcons.InstallMobile,
    firstTimeMessage = "打包好的应用要住进你的手机，需要你点头允许一次「安装来自本应用的应用」。",
    rationaleMessage = "安装权限还没打开，没有它打包好的应用就进不了手机，之前生成的包还等着安家。",
    settingsMessage = "安装权限在系统设置里，去打开「允许安装未知应用」，回来后就能继续安装了。",
)
