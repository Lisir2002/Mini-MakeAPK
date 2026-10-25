package com.make.apk.ui.permission

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.make.apk.logging.LogFields
import com.make.apk.logging.MmaLogger
import com.make.apk.storage.MmaStorage

/**
 * 运行时权限的状态持有者（由 [rememberMmaPermissionState] 创建）。
 *
 * 设计边界（与弹窗组件分工）：
 * - 本类只管**状态机与系统交互**：check / 拉起系统弹窗 / 回调迁移 / 设置页 Intent / 审计日志；
 * - **UI 层**根据 [promptStage] 决定是否展示 [MmaPermissionDialog]，
 *   用户确认后调 [confirmPrompt]（Denied → 再请求，PermanentlyDenied → 去设置）。
 *
 * 使用规约（对齐 CODE_STYLE 权限请求模式）：
 * 1. 场景触发时才创建与请求，**不要**在启动时预申请；
 * 2. 每次执行受保护操作前重新 check（用户可随时在系统设置里撤销）；
 * 3. 拒绝是正常结果：由调用方做功能降级，不反复逼问。
 *
 * @param permission `Manifest.permission.*` 常量字符串（调用方传入，便于审计）。
 */
@Stable
class MmaPermissionState internal constructor(
    private val permission: String,
    private val onGranted: () -> Unit,
) {

    /** 当前权限状态（驱动 UI 与 [promptStage]）。 */
    var status: RuntimePermissionStatus by mutableStateOf(RuntimePermissionStatus.Idle)
        internal set

    /** 是否应弹二次确认弹窗；null = 不弹。 */
    val promptStage: PermissionDialogStage?
        get() = stageFor(status)

    val isGranted: Boolean
        get() = status is RuntimePermissionStatus.Granted

    /** 发起系统权限请求（已授予则空转）。首次说明弹窗由 UI 层先展示、确认后再调本方法。 */
    fun requestPermission() {
        if (isGranted) return
        status = RuntimePermissionStatus.Requesting
        launchRequest?.invoke()
    }

    /** 二次确认弹窗主按钮：Denied → 再发起系统请求；PermanentlyDenied → 打开设置页。 */
    fun confirmPrompt() {
        when (status) {
            is RuntimePermissionStatus.Denied -> requestPermission()
            is RuntimePermissionStatus.PermanentlyDenied -> openSettings()
            else -> Unit
        }
    }

    /** 二次确认弹窗次按钮（「以后再说」）：Denied 回到 Idle（下次触发重新走系统弹窗）。 */
    fun dismissPrompt() {
        if (status is RuntimePermissionStatus.Denied) {
            status = RuntimePermissionStatus.Idle
        }
    }

    /** 打开本应用的系统设置详情页（用户可在其中手动开启/撤销权限）。 */
    fun openSettings() {
        openSettingsIntent?.invoke()
    }

    // ── 内部接线（由 rememberMmaPermissionState 注入，API 表面保持干净） ──

    internal var launchRequest: (() -> Unit)? = null
    internal var openSettingsIntent: (() -> Unit)? = null
    internal var refreshCheck: (() -> Unit)? = null

    internal fun refreshStatus() {
        refreshCheck?.invoke()
    }

    internal fun onResult(granted: Boolean, rationaleShown: Boolean) {
        status = resolveStatusAfterResult(granted, rationaleShown)
        when (status) {
            is RuntimePermissionStatus.Granted -> {
                MmaLogger.operation(
                    event = "permission_granted",
                    fields = mapOf(LogFields.SUCCESS to true, "permission" to permission),
                )
                onGranted()
            }

            is RuntimePermissionStatus.Denied ->
                MmaLogger.operation(
                    event = "permission_denied",
                    fields = mapOf(LogFields.SUCCESS to false, "permission" to permission),
                )

            is RuntimePermissionStatus.PermanentlyDenied ->
                MmaLogger.operation(
                    event = "permission_permanently_denied",
                    message = "用户勾选不再提示或两次拒绝，只能引导去系统设置",
                    fields = mapOf(LogFields.SUCCESS to false, "permission" to permission),
                )

            else -> Unit
        }
    }
}

/**
 * 创建并持有运行时权限状态。
 *
 * @param permission `Manifest.permission.*` 字符串。
 * @param onGranted 授予成功回调（触发时机：请求成功，或从设置页返回时发现已开启）。
 */
@Composable
fun rememberMmaPermissionState(
    permission: String,
    onGranted: () -> Unit = {},
): MmaPermissionState {
    val context = LocalContext.current
    val state = remember(permission) { MmaPermissionState(permission, onGranted) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // shouldShowRequestPermissionRationale 必须在 launcher 回调内读（系统只在这个时机给准确答案）；
        // 非 Activity 上下文（Paparazzi 渲染、未来桌面小组件）拿不到，保守视为「不可解释」。
        val activity = context as? ComponentActivity
        val rationaleShown = activity?.shouldShowRequestPermissionRationale(permission) ?: false
        state.onResult(granted = granted, rationaleShown = rationaleShown)
    }

    // 从设置页返回时重新 check：用户可能刚在系统设置里开/关了权限
    LifecycleResumeEffect(permission) {
        state.refreshStatus()
        onPauseOrDispose { }
    }

    LaunchedEffect(permission) {
        state.launchRequest = { launcher.launch(permission) }
        state.openSettingsIntent = { context.openAppSettings() }
        state.refreshCheck = {
            val granted = ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) {
                val firstTime = state.status != RuntimePermissionStatus.Granted
                state.status = RuntimePermissionStatus.Granted
                if (firstTime) onGranted()
            }
            // 未授予：保持现状（Idle 仍是 Idle；Denied/PermanentlyDenied 保留供 UI 引导）
        }
        state.refreshStatus()
    }

    return state
}

/**
 * 特殊权限状态持有者（跳系统设置页授权的那一类，如「安装未知应用」）。
 *
 * 与 [MmaPermissionState] 不同构：无系统弹窗，只有「已开 / 未开」二态，
 * 未开时 [confirmPrompt] 一律跳设置页引导。
 *
 * @param tag 审计日志标识（如 `REQUEST_INSTALL_PACKAGES`）。
 * @param isGrantedCheck 当前是否已开启。
 * @param settingsIntentFactory 跳转的系统设置页 Intent 工厂。
 */
@Stable
class SpecialPermissionState internal constructor(
    private val tag: String,
    private val isGrantedCheck: () -> Boolean,
    private val settingsIntentFactory: () -> Intent,
) {
    var status: SpecialPermissionStatus by mutableStateOf(SpecialPermissionStatus.Denied)
        internal set

    val isGranted: Boolean
        get() = status is SpecialPermissionStatus.Granted

    /** 设置引导弹窗主按钮：跳转对应系统设置页。 */
    fun confirmPrompt() {
        openSettings()
    }

    fun openSettings() {
        openSettingsIntent?.invoke()
    }

    internal var openSettingsIntent: (() -> Unit)? = null

    internal fun refresh() {
        if (isGrantedCheck()) {
            val firstTime = status != SpecialPermissionStatus.Granted
            status = SpecialPermissionStatus.Granted
            if (firstTime) {
                MmaLogger.operation(
                    event = "permission_granted",
                    fields = mapOf(LogFields.SUCCESS to true, "permission" to tag),
                )
            }
        }
    }
}

/**
 * 创建特殊权限状态（通用工厂）。
 *
 * @param tag 审计日志标识（如 `REQUEST_INSTALL_PACKAGES`）。
 * @param isGranted 检查函数（如 `packageManager.canRequestPackageInstalls`）。
 * @param settingsIntent 跳转的系统设置页 Intent 工厂。
 */
@Composable
fun rememberSpecialPermissionState(
    tag: String,
    isGranted: () -> Boolean,
    settingsIntent: () -> Intent,
): SpecialPermissionState {
    val context = LocalContext.current
    val state = remember(tag) { SpecialPermissionState(tag, isGranted, settingsIntent) }

    // 从设置页返回时重新 check：安装权限的核心流程就是「跳设置 → 打开开关 → 返回继续」
    LifecycleResumeEffect(tag) {
        state.refresh()
        onPauseOrDispose { }
    }

    LaunchedEffect(tag) {
        state.openSettingsIntent = {
            val intent = settingsIntent()
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
        state.refresh()
    }
    return state
}

/** 便捷工厂：「安装未知应用」特殊权限（API 26+；以下版本安装时即允许，视为已开启）。 */
@Composable
fun rememberInstallPackagesPermissionState(): SpecialPermissionState {
    val context = LocalContext.current
    return rememberSpecialPermissionState(
        tag = "REQUEST_INSTALL_PACKAGES",
        isGranted = {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.packageManager.canRequestPackageInstalls()
        },
        settingsIntent = {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.fromParts("package", context.packageName, null),
            )
        },
    )
}

/**
 * 便捷工厂：「所有文件访问」特殊权限（MMAFile 公共目录写入，API 30+ 跳设置页授权）。
 *
 * 授权判断委托 `MmaStorage.isAuthorized`——单一事实来源（含 API ≤28 的
 * WRITE_EXTERNAL_STORAGE 分支与 API 29 死区），Application 启动链与 UI 门共用。
 *
 * 注意：设置页 Intent（`ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`）在部分
 * 国产 ROM 上可能没有对应 Activity，调用方跳转前须 `runCatching` 兜底到
 * [openAppSettings]（不要用 `resolveActivity` 预检：包可见性过滤会误判为 null）。
 * API ≤28 走运行时权限路径（见 StoragePermissionGate），不使用本工厂。
 */
@Composable
fun rememberAllFilesAccessPermissionState(): SpecialPermissionState {
    val context = LocalContext.current
    return rememberSpecialPermissionState(
        tag = "MANAGE_EXTERNAL_STORAGE",
        isGranted = { MmaStorage.isAuthorized(context) },
        settingsIntent = {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.fromParts("package", context.packageName, null),
            )
        },
    )
}

/** 打开本应用的系统设置详情页。 */
internal fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
    )
}
