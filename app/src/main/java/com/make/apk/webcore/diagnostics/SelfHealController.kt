package com.make.apk.webcore.diagnostics

import android.webkit.WebView
import com.make.apk.webcore.diagnostics.WebViewEventHook
import com.make.apk.webcore.engine.EngineEvent
import com.make.apk.webcore.engine.WebViewEngine

/**
 * 坏缓存自愈（W4 内核重写，rc13）：缓存条目写入中断 / 版本错位时，"标准浏览器能开、
 * App 内白屏"且无任何错误回调——是坏缓存最典型的表现。
 *
 * 以 `LOAD_NO_CACHE` 临时改写缓存模式并重载一次（每轮加载至多一次，
 * 引擎闭包字段 `selfHealed` 防止"失败→重载→失败"死循环，旧 TAG_SELF_HEAL_KEY 替代）；
 * 重载前先给用户一条可见说明（[EngineEvent.SelfHealNotice]），避免"闪一下不知道
 * 发生了什么"。重载会触发新一轮 onPageStarted/Finished，探测代次随之刷新、
 * 重新走两段式探测。
 *
 * W3 ⑬ 埋点契约保持：`preview_self_heal`（reason/success/detail），级别 WARN。
 */
internal class SelfHealController(
    private val engine: WebViewEngine,
) {

    /** 自愈重载提示文案（跳过缓存重载前告知用户，避免"闪一下"无解释）。 */
    private val msgSelfHealReload: String = "检测到页面异常，正在跳过缓存重新加载"

    /**
     * 触发自愈（幂等：每轮加载至多一次）。触发即清空去重地址（自愈即新一轮处理窗口，
     * 重载后仍空才上报根因）并前移探测代次。
     */
    fun maybeSelfHeal(view: WebView, reason: String) {
        if (engine.selfHealed) return
        engine.selfHealed = true
        // 自愈即新一轮处理窗口：清空去重地址，重载后仍空才上报根因。
        engine.blankHandledUrl = null
        engine.nextProbeGeneration()
        // W3 ⑬ 埋点：自愈触发（结构化 reason 字段）。
        WebViewEventHook.report("preview_self_heal", mapOf("reason" to reason, "success" to true))
        engine.emit(EngineEvent.SelfHealNotice(msgSelfHealReload))
        val done = engine.reloadNoCache()
        if (!done) {
            WebViewEventHook.report(
                "preview_self_heal",
                mapOf("reason" to reason, "success" to false, "detail" to "engine_not_ready"),
            )
        }
    }
}
