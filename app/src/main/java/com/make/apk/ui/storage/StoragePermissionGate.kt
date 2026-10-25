package com.make.apk.ui.storage

import android.Manifest
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.make.apk.logging.MmaLogger
import com.make.apk.storage.MmaDir
import com.make.apk.storage.MmaStorage
import com.make.apk.ui.permission.AllFilesAccessPermissionSpec
import com.make.apk.ui.permission.Api29StorageLimitSpec
import com.make.apk.ui.permission.MmaPermissionDialog
import com.make.apk.ui.permission.PermissionDialogStage
import com.make.apk.ui.permission.openAppSettings
import com.make.apk.ui.permission.rememberAllFilesAccessPermissionState
import com.make.apk.ui.permission.rememberMmaPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 启动存储门：挂载于 MmaApp 根部（与 NavHost 同级），**无弹窗时零布局**。
 *
 * 职责：每次启动评估 MMAFile 公共目录（`/storage/emulated/0/MMAFile`）的写权限——
 * - 已授权：静默幂等确保目录结构（Setting 被删后重启即此路径重建）+ 日志切公共目录；
 * - 未授权：按 API 分级引导（见下），用户「以后再说」后应用继续可用（降级私有目录），
 *   下次启动重新评估。
 *
 * 这是 CODE_STYLE §3.1「禁止启动即申请」的**有意例外**：存储是应用基础能力，
 * 且仍走两段式（应用内说明 → 系统流程），拒绝不阻断主流程。
 *
 * API 分级（targetSdk 34，详见 MmaStorage KDoc）：
 * - ≥30：MANAGE_EXTERNAL_STORAGE 特殊权限，跳设置页，返回后自动复查；
 * - 29：系统死区，一次性说明后静默降级（标记文件防复弹）；
 * - ≤28：WRITE_EXTERNAL_STORAGE 运行时权限，系统弹窗 + 拒绝后可再解释。
 */
@Composable
fun StoragePermissionGate(modifier: Modifier = Modifier) {
    // 本次启动「以后再说」后不再弹（下次启动重新评估；权限状态可能已变）
    var dismissed by remember { mutableStateOf(false) }

    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            AllFilesAccessBranch(dismissed = dismissed, onDismissed = { dismissed = true })

        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ->
            Api29NoticeBranch(dismissed = dismissed, onDismissed = { dismissed = true })

        else ->
            LegacyWriteBranch(dismissed = dismissed, onDismissed = { dismissed = true })
    }
}

/**
 * 授权成功后的公共目录就绪动作（幂等，可重复触发）：
 * 确保全结构 + 把文件日志从私有兜底迁到 MMAFile/Log（redirectFileSink 内部同目录短路）。
 */
private suspend fun ensurePublicStorage() {
    withContext(Dispatchers.IO) {
        if (MmaStorage.ensureAll()) {
            MmaLogger.redirectFileSink(MmaStorage.dir(MmaDir.LOG))
        }
    }
}

/** API ≥30：所有文件访问（特殊权限，跳设置页；含从设置页返回的自动复查）。 */
@Composable
private fun AllFilesAccessBranch(dismissed: Boolean, onDismissed: () -> Unit) {
    val context = LocalContext.current
    val state = rememberAllFilesAccessPermissionState()

    LaunchedEffect(state.isGranted) {
        if (state.isGranted) ensurePublicStorage()
    }

    if (!dismissed && !state.isGranted) {
        MmaPermissionDialog(
            spec = AllFilesAccessPermissionSpec,
            stage = PermissionDialogStage.FirstTime,
            onConfirm = {
                // 部分国产 ROM 没有「所有文件访问」设置页：运行时兜底跳应用详情页，
                // 让用户手动找权限入口（勿用 resolveActivity 预检，包可见性过滤会误判）
                runCatching { state.openSettings() }
                    .onFailure { context.openAppSettings() }
            },
            onDismiss = onDismissed,
        )
    }
}

/**
 * API 29：系统死区一次性说明。
 *
 * 「知道了」→ 写标记文件，以后不再弹；「以后再说」→ 本次启动不再弹，下次重启再问。
 * 两个按钮都不跳任何设置页（没有可开的开关）。
 */
@Composable
private fun Api29NoticeBranch(dismissed: Boolean, onDismissed: () -> Unit) {
    val context = LocalContext.current
    var confirmed by remember { mutableStateOf(MmaStorage.hasApi29NoticeShown(context)) }

    if (!dismissed && !confirmed) {
        MmaPermissionDialog(
            spec = Api29StorageLimitSpec,
            stage = PermissionDialogStage.FirstTime,
            onConfirm = {
                MmaStorage.markApi29NoticeShown(context)
                confirmed = true
            },
            onDismiss = onDismissed,
        )
    }
}

/** API ≤28：WRITE_EXTERNAL_STORAGE 运行时权限（系统弹窗；拒绝后按规约走 Rationale）。 */
@Composable
private fun LegacyWriteBranch(dismissed: Boolean, onDismissed: () -> Unit) {
    val state = rememberMmaPermissionState(permission = Manifest.permission.WRITE_EXTERNAL_STORAGE)

    LaunchedEffect(state.isGranted) {
        if (state.isGranted) ensurePublicStorage()
    }

    val stage = state.promptStage
    if (!dismissed && stage != null) {
        MmaPermissionDialog(
            // 复用「给应用一个文件家」文案：同为 MMAFile 说明，不出现权限名
            spec = AllFilesAccessPermissionSpec,
            stage = stage,
            onConfirm = state::confirmPrompt,
            onDismiss = {
                state.dismissPrompt()
                onDismissed()
            },
        )
    }
}
