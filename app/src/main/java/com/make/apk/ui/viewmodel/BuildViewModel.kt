package com.make.apk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.make.apk.data.repository.BuildHistoryRepository
import com.make.apk.logging.LogFields
import com.make.apk.logging.MmaLogger
import com.make.apk.data.model.BuildEngine
import com.make.apk.data.model.BuildInputType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * 构建流程 ViewModel（MVVM + 单向数据流）。
 *
 * 单一数据源是**数据库**而非内存状态：构建一经登记就落加密库，
 * 因此进程被杀、页面重建后进度都能还原，不会"转圈卡死"。
 *
 * 流程：登记（OPERATION 日志）→ 执行 → 成功/失败（PERFORMANCE / ERROR 日志）。
 * Sprint-1 为设备端 WebView 即时封装，出包耗时以短延时模拟；
 * 真实云端构建（Phase 2）只需替换 [runBuild] 的实现。
 */
class BuildViewModel(
    private val repository: BuildHistoryRepository,
) : ViewModel() {

    /** 已登记、等待跳转的构建 id（UI 消费后调用 [consumePendingId]）。 */
    private val _pendingId = MutableStateFlow<Long?>(null)
    val pendingId: StateFlow<Long?> = _pendingId.asStateFlow()

    /** 是否正在提交（防止重复点击）。 */
    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    /**
     * 进行中的构建协程（recordId → Job）。
     * 取消时按 id 摘除；VM 重建后 map 为空，但仓储层终态守卫仍能兜底。
     */
    private val buildJobs = mutableMapOf<Long, Job>()

    fun startBuild(input: String) {
        if (_submitting.value) return
        val trimmed = input.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            _submitting.value = true
            val id = runCatching {
                val type = if (isUrl(trimmed)) BuildInputType.URL else BuildInputType.HTML
                val id = repository.start(
                    inputType = type,
                    sourceLabel = labelOf(trimmed),
                    sourceRef = trimmed,
                    engine = BuildEngine.DEVICE_WEBVIEW,
                )
                MmaLogger.operation(
                    event = "build_started",
                    message = "开始构建",
                    fields = mapOf(LogFields.SUCCESS to true, "input_type" to type.name),
                )
                id
            }.onFailure { throwable ->
                MmaLogger.error("build_start_failed", throwable)
            }.getOrNull()

            if (id != null) {
                _pendingId.value = id
                buildJobs[id] = coroutineContext.job
                try {
                    runBuild(id)
                } finally {
                    buildJobs.remove(id)
                }
            }
            _submitting.value = false
        }
    }

    fun consumePendingId() {
        _pendingId.value = null
    }

    /** 重试：沿用原输入重新登记一次。 */
    fun retry(input: String) = startBuild(input)

    /**
     * 取消一次进行中的构建：摘除协程 + 落库终态 + 审计日志。
     *
     * [buildJobs] 中无对应 job 时（如 VM 已重建、用户在历史页取消旧记录），
     * [com.make.apk.data.repository.BuildHistoryRepository.markCancelled] 的
     * 终态守卫与 [runBuild] 的取消传播共同保证记录不会被迟到协程改写。
     */
    fun cancelBuild(recordId: Long) {
        buildJobs.remove(recordId)?.cancel()
        viewModelScope.launch {
            repository.markCancelled(recordId)
            MmaLogger.operation(
                event = "build_cancelled",
                message = "取消构建",
                fields = mapOf(LogFields.SUCCESS to true, "record_id" to recordId),
            )
        }
    }

    private suspend fun runBuild(recordId: Long) {
        val startedAt = System.currentTimeMillis()
        try {
            // Sprint-1：设备端 WebView 即时封装（真实出包在 Phase 2）
            delay(BUILD_SIMULATED_DELAY_MS)
            repository.markSucceeded(
                id = recordId,
                outputApkPath = OUTPUT_PATH_PLACEHOLDER,
                apkSizeBytes = null,
            )
            MmaLogger.performance("build_finished", System.currentTimeMillis() - startedAt)
            MmaLogger.operation("build_succeeded", fields = mapOf(LogFields.SUCCESS to true))
        } catch (ce: CancellationException) {
            // 取消是协程协作信号：rethrow 让取消向上传播，
            // 终态由 cancelBuild 的 markCancelled 写入（仓储守卫兜底迟到写）。
            throw ce
        } catch (throwable: Throwable) {
            repository.markFailed(recordId, throwable.message ?: "未知错误")
            MmaLogger.error("build_failed", throwable)
        }
    }

    private fun isUrl(input: String): Boolean =
        input.startsWith("http", ignoreCase = true) || input.contains("://")

    private fun labelOf(input: String): String =
        if (isUrl(input)) {
            input.substringAfter("://").substringBefore("/").ifBlank { input }
        } else {
            "粘贴的 HTML"
        }

    companion object {
        private const val BUILD_SIMULATED_DELAY_MS = 1_200L
        private const val OUTPUT_PATH_PLACEHOLDER = "/data/local/tmp/mma/app.apk"
    }
}

/** 手工 DI 工厂（本项目未引入 Hilt，保持轻量）。 */
class BuildViewModelFactory(
    private val repository: BuildHistoryRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        BuildViewModel(repository) as T
}
