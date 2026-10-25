package com.make.apk.ui.permission

/**
 * 运行时权限状态（走系统标准弹窗的那一类，如 Android 13+ 的 `POST_NOTIFICATIONS`）。
 *
 * 状态机（迁移逻辑见 [resolveStatusAfterResult]）：
 * ```
 * Idle ──requestPermission()──▶ Requesting ──launcher 回调──┬─ granted   ▶ Granted
 *                                                          ├─ 拒+可解释 ▶ Denied（promptStage=Rationale）
 *                                                          └─ 拒+不可解释 ▶ PermanentlyDenied（promptStage=GoToSettings）
 * ```
 * `Denied` 下用户确认二次弹窗后才再次请求；`PermanentlyDenied` 只能引导去系统设置页。
 */
sealed interface RuntimePermissionStatus {
    /** 尚未请求过。 */
    data object Idle : RuntimePermissionStatus

    /** 已发起请求，等待 launcher 回调。 */
    data object Requesting : RuntimePermissionStatus

    /** 已授予。 */
    data object Granted : RuntimePermissionStatus

    /**
     * 被拒绝，但系统认为**还可以解释**（用户上次是主动拒绝，没勾「不再提示」）。
     * 此时应由 UI 展示二次确认弹窗，用户确认后才再次发起系统请求。
     */
    data object Denied : RuntimePermissionStatus

    /**
     * 被永久拒绝（勾选过「不再提示」，或同一会话内被拒两次）。
     * 系统弹窗不会再出现，唯一出路是引导用户去应用设置页手动开启。
     */
    data object PermanentlyDenied : RuntimePermissionStatus
}

/**
 * 特殊权限状态（跳系统设置页授权的那一类，如「安装未知应用」）。
 *
 * 与 [RuntimePermissionStatus] 不同构：这类权限没有系统弹窗，
 * 只有「已开 / 未开」二态，未开时一律走设置页引导，故独立建模。
 */
sealed interface SpecialPermissionStatus {
    /** 已开启（或当前系统版本无需此权限，如 API 26 以下的安装场景）。 */
    data object Granted : SpecialPermissionStatus

    /** 未开启，需引导用户去系统设置页。 */
    data object Denied : SpecialPermissionStatus
}

/**
 * 二次确认弹窗的阶段（[MmaPermissionDialog] 的形态开关）。
 *
 * - [FirstTime]：首次说明——在系统弹窗前先讲清用途，提高授权率
 * - [Rationale]：拒绝后解释——用户拒过一次，系统允许再解释一次
 * - [GoToSettings]：永久拒绝后的设置引导
 */
enum class PermissionDialogStage {
    FirstTime,
    Rationale,
    GoToSettings,
}

/**
 * launcher 回调后的状态迁移（**纯函数**，便于 JVM 单测覆盖全部边界）。
 *
 * @param granted 回调的授权结果。
 * @param rationaleShown 回调时读到的 `shouldShowRequestPermissionRationale`
 *   （true = 用户上次主动拒绝且可再解释；false = 首次拒绝即勾不再提示，或已两次拒绝）。
 */
internal fun resolveStatusAfterResult(
    granted: Boolean,
    rationaleShown: Boolean,
): RuntimePermissionStatus = when {
    granted -> RuntimePermissionStatus.Granted
    rationaleShown -> RuntimePermissionStatus.Denied
    else -> RuntimePermissionStatus.PermanentlyDenied
}

/**
 * 当前状态下是否应弹出二次确认弹窗，以及弹哪一形态。
 * 返回 null 表示不需要弹窗（[RuntimePermissionStatus.Granted] 等）。
 */
internal fun stageFor(status: RuntimePermissionStatus): PermissionDialogStage? = when (status) {
    is RuntimePermissionStatus.Denied -> PermissionDialogStage.Rationale
    is RuntimePermissionStatus.PermanentlyDenied -> PermissionDialogStage.GoToSettings
    else -> null
}
