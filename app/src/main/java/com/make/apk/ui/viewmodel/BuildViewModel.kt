package com.make.apk.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.make.apk.data.repository.BuildHistoryRepository
import com.make.apk.logging.LogFields
import com.make.apk.logging.MmaLogger
import com.make.apk.data.model.BuildEngine
import com.make.apk.data.model.BuildInputType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun startBuild(input: String) {
        if (_submitting.value) return
        val trimmed = input.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            _submitting.value = true
            runCatching {
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
                _pendingId.value = id
                runBuild(id)
            }.onFailure { throwable ->
                MmaLogger.error("build_start_failed", throwable)
            }
            _submitting.value = false
        }
    }

    fun consumePendingId() {
        _pendingId.value = null
    }

    /** 重试：沿用原输入重新登记一次。 */
    fun retry(input: String) = startBuild(input)

    private suspend fun runBuild(recordId: Long) {
        val startedAt = System.currentTimeMillis()
        runCatching {
            // Sprint-1：设备端 WebView 即时封装（真实出包在 Phase 2）
            delay(BUILD_SIMULATED_DELAY_MS)
            repository.markSucceeded(
                id = recordId,
                outputApkPath = OUTPUT_PATH_PLACEHOLDER,
                apkSizeBytes = null,
            )
            MmaLogger.performance("build_finished", System.currentTimeMillis() - startedAt)
            MmaLogger.operation("build_succeeded", fields = mapOf(LogFields.SUCCESS to true))
        }.onFailure { throwable ->
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
