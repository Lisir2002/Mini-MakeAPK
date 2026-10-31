package com.make.apk.webcore.chrome

import android.content.Context
import android.view.View
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.make.apk.data.model.ProgressPosition
import com.make.apk.data.model.WebRuntimeConfig
import com.make.apk.webcore.config.WebViewStorageIsolation
import com.make.apk.webcore.engine.EngineEvent
import com.make.apk.webcore.engine.WebViewEngine

/**
 * WebView 渲染面（W4 内核重写的 chrome 层）：消费 [WebRuntimeConfig]，统一渲染入口。
 *
 * ## 公开 API 与旧单体完全兼容
 *
 * `WebViewScreen(config, modifier, paused, webViewFactory, onError, onTitle)` 签名
 * 逐参保留（消费方 ShellPreviewScreen / TemplateActivity / 截图测试零改动语义），
 * 组合逻辑（AndroidView + rebuildKey + 进度条叠加）与旧单体 :92-195 **逐行等价迁移**；
 * 差异仅在内部：装配 / 生命周期 / 诊断全部下沉到 [WebViewEngine] 与其协作层。
 *
 * ## 事件接线
 *
 * 引擎单监听者：Progress → 进度条；Title → onTitle；MainFrameError / SslOutcome /
 * BlankConfirmed / SelfHealNotice → onError（文案已在引擎侧按
 * [com.make.apk.webcore.diagnostics.BlankRootCause] 分级，W4 拍板 5）；
 * RendererGone → rebuildKey++（AndroidView 工厂重建全新 WebView，真恢复）。
 */
@Composable
public fun WebViewSurface(
    config: WebRuntimeConfig,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    webViewFactory: (Context) -> View = { WebView(it) },
    /** 加载失败（主帧）时回调（如预览页用它弹 Snackbar）。导出壳默认不传。 */
    onError: ((String) -> Unit)? = null,
    /** 页面标题回调（onReceivedTitle）：用于「页面标题」来源。预览与导出两侧天然一致。 */
    onTitle: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    // 预览态存储隔离（W4 拍板 2：first-wins + 告警埋点；导出壳已在 Activity onCreate
    // 更早收口，此处为 app 预览侧单点；幂等，重复调用仅在冲突时告警）。
    WebViewStorageIsolation.ensureApplied(context, config.isolationId)
    // 引擎随组合创建、随组合销毁（自持模式）；引擎池模式由 registry 统一销毁。
    val engine = remember { WebViewEngine(context, config) }
    DisposableEffect(Unit) {
        onDispose { engine.destroy() }
    }
    WebViewSurface(
        engine = engine,
        config = config,
        modifier = modifier,
        paused = paused,
        webViewFactory = webViewFactory,
        onError = onError,
        onTitle = onTitle,
    )
}

/**
 * 引擎池接线版渲染面：复用外部 [WebViewEngine]（TabEngineRegistry 按 tab 保活，
 * 页内导航历史跨重组保留——W4 拍板 4：tabStacks 退役后由原生 back/forward 承担）。
 */
@Composable
public fun WebViewSurface(
    engine: WebViewEngine,
    config: WebRuntimeConfig,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    webViewFactory: (Context) -> View = { WebView(it) },
    onError: ((String) -> Unit)? = null,
    onTitle: ((String) -> Unit)? = null,
) {
    // 加载进度：由 EngineEvent.ProgressChanged 驱动；仅"功能加载进度条"开启时渲染。
    val progress = remember { mutableStateOf(0) }
    // 渲染进程死亡后的重建代次：+1 使 key() 变化 → AndroidView 工厂重建**全新** WebView 实例
    // （渲染进程一旦崩溃，原 WebView 实例已被内核标记失效，复用只会继续白屏，必须整体重建）。
    val rebuildKey = remember { mutableStateOf(0) }
    val showProgress = config.webSettings.showLoadingProgress
    val progressPosition = config.webSettings.loadingProgressPosition
    // WebView 底色：**不透明**，取主题表面色（透明底在部分 ROM 上会导致内容不绘制 → 白屏）。
    val surfaceArgb = MaterialTheme.colorScheme.surface.toArgb()

    // 引擎事件接线（单监听者；DisposableEffect 保证组合期间常驻、销毁时解绑）。
    DisposableEffect(engine) {
        val listener = WebViewEngine.Listener { event ->
            when (event) {
                is EngineEvent.ProgressChanged -> progress.value = event.progress
                is EngineEvent.TitleReceived -> onTitle?.invoke(event.title)
                is EngineEvent.MainFrameError -> reportLoadError(event.description, onError)
                is EngineEvent.SslOutcome -> reportLoadError(event.message, onError)
                is EngineEvent.BlankConfirmed -> reportLoadError(event.message, onError)
                is EngineEvent.SelfHealNotice -> reportLoadError(event.message, onError)
                is EngineEvent.RendererGone -> {
                    reportLoadError("渲染进程已自动重载，如页面仍异常请重新进入", onError)
                    rebuildKey.value++
                }
                // ConsoleError 已在引擎侧 Logcat 留痕；PageLoaded 埋点已在客户端上报。
                is EngineEvent.ConsoleError, is EngineEvent.PageLoaded -> Unit
            }
        }
        engine.listener = listener
        onDispose { if (engine.listener === listener) engine.listener = null }
    }

    Box(modifier = modifier.fillMaxSize()) {
        key(rebuildKey.value) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { _ ->
                    // 装配前先同步一次配置与主题底色（首装在 update 之前，见引擎 applyConfig）。
                    engine.applyConfig(config, surfaceArgb)
                    engine.acquireView(webViewFactory)
                },
                update = { _ ->
                    // 仅在配置（入口/关键设置）变化时重新应用并加载，避免每次重组都 reload
                    //（判等在引擎内部完成——旧 TAG_RUNTIME_KEY 快照判等等价）。
                    engine.applyConfig(config, surfaceArgb)
                    // 暂停/恢复：由壳抽屉的 pauseContentWhileOpen 契约驱动，去重避免重复调用。
                    engine.setPaused(paused)
                },
                onRelease = { _ ->
                    // 仅解除挂载关系；WebView 资源由引擎 destroy 统一释放（引擎池保活语义）。
                    engine.releaseView()
                },
            )
        }

        // 功能加载进度条：开关开启且尚未加载完成时显示，贴顶/贴底由设置决定。
        // 色彩 / 粗细均来自 WebSettings（progressColorOf / strokeDp），
        // 与导出壳同源（共用本文件），预览 == 导出天然一致。
        if (showProgress && progress.value < 100) {
            val settings = config.webSettings
            val indicatorColor = progressColorOf(MaterialTheme.colorScheme, settings.progressColorToken)
            val indicatorStroke = settings.progressThickness.strokeDp
            val indicator = @Composable {
                // 自定义进度条：本工程 Material3 解析版本（1.3.0）的 LinearProgressIndicator
                // 尚未提供 strokeWidth / ()->Float 重载，故用 Box 自绘以支持粗细档位与令牌取色，
                // 且预览与导出壳共用本文件 → 两侧像素级一致。
                ShellLoadingProgressIndicator(
                    progressFraction = progress.value / 100f,
                    color = indicatorColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = indicatorStroke,
                )
            }
            if (progressPosition == ProgressPosition.BOTTOM) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                ) { indicator() }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                ) { indicator() }
            }
        }
    }
}

/** 记日志 + 上抛可见提示（description 空时给兜底文案；与旧单体 reportError 等价）。 */
private fun reportLoadError(description: String?, onError: ((String) -> Unit)?) {
    val message = description?.takeIf { it.isNotBlank() } ?: "页面加载失败"
    android.util.Log.e("webview_load_error", message)
    onError?.invoke(message)
}
