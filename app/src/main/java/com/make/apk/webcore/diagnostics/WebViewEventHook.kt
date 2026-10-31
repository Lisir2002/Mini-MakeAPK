package com.make.apk.webcore.diagnostics

/**
 * WebView 埋点钩子（W3 ⑬，**预览与导出壳共享的文件**——零依赖设计）。
 *
 * ## 为什么需要这层间接
 *
 * webcore 的 diagnostics/engine/navigation 层在 template 的共享 sourceSets 内编译，
 * 而 app 侧的 `MmaEventLogger`/`MmaLogger` 依赖 data.local（Room/SQLCipher），
 * 模板模块**禁止**引入业务层。故探测链的埋点只经过本钩子：
 * - app 侧：[MmaApplication][com.make.apk.MmaApplication] 启动时把 reporter
 *   指到 `MmaEventLogger.webViewEvent`（PREVIEW 类别 → preview.log + 加密 DB）；
 * - 导出壳：reporter 保持 null（**未安装时空操作**），埋点调用零开销、零崩溃；
 * - 埋点**永不阻断业务**：reporter 抛出的任何异常都被吞掉（runCatching）。
 *
 * ## 归属（W4 收口 F5）
 *
 * 原位于 `ui/screens` 包，导致 webcore 穿透 ui 依赖约束——已下沉到
 * webcore/diagnostics（本文件），webcore 内部同包直用、零 import；
 * app 侧（MmaApplication）与测试侧经包名引用，行为零变化。
 */
object WebViewEventHook {

    /** 埋点出口（app 侧安装；null = 不埋点，导出壳默认态）。 */
    @Volatile
    var reporter: ((event: String, fields: Map<String, Any?>) -> Unit)? = null

    /** 上报一条 WebView 诊断事件；未安装 reporter 时为空操作，绝不抛异常。 */
    fun report(event: String, fields: Map<String, Any?> = emptyMap()) {
        val sink = reporter ?: return
        runCatching { sink(event, fields) }
    }
}
