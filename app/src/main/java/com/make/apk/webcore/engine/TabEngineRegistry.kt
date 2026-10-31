package com.make.apk.webcore.engine

import android.content.Context
import android.os.Bundle
import com.make.apk.data.model.WebRuntimeConfig

/**
 * 页面级引擎池（W4 内核重写）：按 tab / 页 key 惰性创建并保活 [WebViewEngine]。
 *
 * 与旧单体的行为差异（设计意图，非等价项）：
 * - 旧单体 pager 逐页各建一个 WebViewScreen，页移出组合即 destroy → 切回重载、丢历史；
 * - W4 引擎池按 key 保活引擎与页内历史（W4 拍板 4：tabStacks URL 栈退役后，
 *   切 tab / 回入口的"状态保留"由 WebView 原生 back/forward 承担）；
 * - 生命周期收口：[destroyAll] 由持有者在屏销毁 / Activity onDestroy 统一调用。
 *
 * 实例状态恢复（saveState 接线）：[restoreAll] 在 Activity onCreate 调用，
 * 之后 [engineFor] 首次创建引擎时把对应 key 的历史灌回（WebView 实例重建、
 * 导航栈恢复——Android 对 WebView 的标准恢复姿势）。
 */
class TabEngineRegistry(
    context: Context,
) {

    /**
     * WebView 构造 context：**原样持有调用方传入的 context**（W4 收口 F1）。
     *
     * 不做 `applicationContext` 漂移——官方建议 WebView 用 activity context 创建
     * （主题相关的 H5 控件样式依赖它；layoutlib/Paparazzi 环境下 ApplicationContext
     * 会直接 ClassCastException）。泄漏风险由生命周期契约兜底：本 registry 与调用方
     * （组合作用域 remember / Activity 字段）同生命周期，[destroyAll] 是唯一收口，
     * 销毁后 engines 清空、context 引用随 registry 一起释放。
     */
    private val hostContext: Context = context
    private val engines = LinkedHashMap<String, WebViewEngine>()

    /** 待恢复的实例状态（restoreAll 存入；engineFor 首建时按 key 灌回）。 */
    private var savedState: Bundle? = null

    /**
     * 取（或惰性创建）key 对应的引擎。
     *
     * @param config 首建时的初始配置；已存在的引擎**不**在此更新配置——
     *   配置更新由 chrome 层 [WebViewEngine.applyConfig] 统一受理（判等防重组重载）。
     */
    fun engineFor(key: String, config: WebRuntimeConfig): WebViewEngine =
        engines.getOrPut(key) {
            WebViewEngine(hostContext, config).also { engine ->
                savedState?.getBundle(key)?.let { engine.restoreState(it) }
            }
        }

    /** 查询已存在的引擎（不创建；供 BackHandler 等按当前 tab 判定页内历史，W4 收口 F3）。 */
    fun peek(key: String): WebViewEngine? = engines[key]

    /** 全部引擎暂停/恢复（抽屉暂停契约的批量入口；幂等，由引擎内部去重）。 */
    fun setSuspended(paused: Boolean) {
        engines.values.forEach { it.setPaused(paused) }
    }

    /** 保存全部引擎的页内导航历史（onSaveInstanceState 接线）。 */
    fun saveAll(outState: Bundle) {
        engines.forEach { (key, engine) ->
            val bundle = Bundle()
            engine.saveState(bundle)
            if (!bundle.isEmpty) outState.putBundle(key, bundle)
        }
    }

    /** 预置待恢复的实例状态（onCreate 调用；引擎在组合期才创建）。 */
    fun restoreAll(savedInstanceState: Bundle?) {
        savedState = savedInstanceState
    }

    /** 销毁全部引擎（屏销毁 / Activity onDestroy 唯一出口）。 */
    fun destroyAll() {
        engines.values.forEach { it.destroy() }
        engines.clear()
        savedState = null
    }
}
