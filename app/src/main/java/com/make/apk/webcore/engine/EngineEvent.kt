package com.make.apk.webcore.engine

/**
 * 引擎上行事件（W4 内核重写）：WebViewEngine 与其协作层（navigation / diagnostics）
 * 向 chrome 层（WebViewSurface）单向通报的唯一通道。
 *
 * 依赖单向铁律：diagnostics 只经本事件接口上行，**反摸不到 chrome**——
 * chrome 消费事件后自行决定 UI 呈现（onError 提示 / 进度条 / 重建 WebView）。
 *
 * 事件 → 旧单体行为的等价映射（对照表详见 W4 报告）：
 * - [ProgressChanged] ← WebChromeClient.onProgressChanged（进度条数据源）；
 * - [TitleReceived] ← onReceivedTitle（「页面标题」来源 = PAGE_TITLE，仅非空回传）；
 * - [ConsoleError] ← onConsoleMessage ERROR（JS console 采集，去重后上行）；
 * - [MainFrameError] ← onReceivedError 主帧分支（description 空由 chrome 层兜底文案）；
 * - [SslOutcome] ← onReceivedSslError（绝不调 super；文案在此生成，语义等价旧 TAG_SSL_KEY）；
 * - [RendererGone] ← onRenderProcessGone（chrome 层 rebuildKey++ 重建，真恢复）；
 * - [PageLoaded] ← onPageFinished（duration = onPageStarted → onPageFinished，W3 ⑬ 口径）；
 * - [BlankConfirmed] ← 空内容两段式探测最终命中（reason 为 W3 埋点契约四级之一；
 *   message 为按 [com.make.apk.webcore.diagnostics.BlankRootCause] 分级的中文文案，W4 拍板 5）；
 * - [SelfHealNotice] ← 坏缓存自愈重载前的可见提示（旧 MSG_SELF_HEAL_RELOAD）。
 */
sealed interface EngineEvent {

    /** 加载进度变化（0-100）。 */
    data class ProgressChanged(val progress: Int) : EngineEvent

    /** 页面标题回传（[android.webkit.WebChromeClient.onReceivedTitle]，仅非空）。 */
    data class TitleReceived(val title: String) : EngineEvent

    /** JS console 错误（已去重：与上一条相同不重复上行）。 */
    data class ConsoleError(val record: String) : EngineEvent

    /** 主帧加载错误（仅主帧上行，子资源失败不刷屏）。 */
    data class MainFrameError(val description: String?) : EngineEvent

    /** SSL 证书错误处理结果（proceed/cancel 均上行；message 为用户可见中文文案）。 */
    data class SslOutcome(val url: String, val ignored: Boolean, val message: String) : EngineEvent

    /** 渲染进程崩溃（chrome 层据 rebuildKey 驱动全新 WebView 重建并自动重载）。 */
    data object RendererGone : EngineEvent

    /** 主帧加载完成（durationMs = onPageStarted → onPageFinished；起点缺失按 0）。 */
    data class PageLoaded(val durationMs: Long) : EngineEvent

    /**
     * 空内容最终确认（两段式探测都为空 + 去重 + 自愈分流后仍空）。
     *
     * @param reason W3 埋点契约四级：PROBE_FAILED / ENGINE_TOO_OLD / JS_ERROR / UNKNOWN。
     * @param message 按 BlankRootCause 分级的中文文案（W4 拍板 5）。
     */
    data class BlankConfirmed(val reason: String, val message: String) : EngineEvent

    /** 自愈重载提示（跳过缓存重载前告知用户，避免"闪一下"无解释）。 */
    data class SelfHealNotice(val message: String) : EngineEvent
}
