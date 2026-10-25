package com.make.apk.ui.permission

import com.make.apk.ui.icons.MmaIcons

/**
 * 预置权限文案。
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

/**
 * 所有文件访问权限（特殊权限：无系统弹窗，API 30+ 跳设置页授权）。
 *
 * 用途：MMAFile 公共目录（`/storage/emulated/0/MMAFile`）的写入——
 * 安装包、日志、项目、设置、备份各就各位。授权判断委托
 * `MmaStorage.isAuthorized`（单一事实来源），见 `rememberAllFilesAccessPermissionState`。
 */
val AllFilesAccessPermissionSpec = MmaPermissionSpec(
    title = "给应用一个文件家",
    iconRes = MmaIcons.Folder,
    firstTimeMessage = "打包好的安装包、运行日志和你的项目，想有一个固定的家。授权后，我们会在手机存储里建好 MMAFile 文件夹，让它们各就各位不迷路。",
    rationaleMessage = "还差这一步授权，没有它 MMAFile 文件夹就建不起来，文件只能暂存在应用里，换手机时容易一起丢失。",
    settingsMessage = "授权在系统设置里合上了，去打开「所有文件访问权限」，回来后文件夹就能自动建好。",
)

/**
 * Android 10（API 29）存储限制说明——**只告知、不申请**。
 *
 * targetSdk 34 下 API 29 是权限死区：`requestLegacyExternalStorage` 被系统忽略、
 * `MANAGE_EXTERNAL_STORAGE` 不可授予，任何途径都拿不到公共根目录写权限。
 * 弹一次（标记文件防复弹）说明现状与升级建议，然后静默降级私有目录。
 * 三个 message 均不使用（此 spec 无申请路径），但按 data class 约定保持非空。
 */
val Api29StorageLimitSpec = MmaPermissionSpec(
    title = "这个系统版本建不了公共文件夹",
    iconRes = MmaIcons.Folder,
    firstTimeMessage = "你的手机是 Android 10，系统不允许任何应用直接在手机存储里建文件夹。文件会先寄存在应用内，不影响使用；升级到 Android 11 以上即可启用公共目录。",
    rationaleMessage = "Android 10 的系统限制不会因为再试一次而改变，文件会继续寄存在应用内，不影响打包与预览。",
    settingsMessage = "这个限制来自 Android 10 系统本身，系统设置里也没有可打开的开关，升级到 Android 11 以上即可启用公共目录。",
    confirmText = "知道了",
)
