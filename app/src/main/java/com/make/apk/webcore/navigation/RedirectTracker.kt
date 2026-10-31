package com.make.apk.webcore.navigation

/**
 * 重定向链判定（rc17 新增）：**纯函数**，不持有状态、不依赖 WebView，便于单测。
 *
 * 背景：`WebViewClient` 没有重定向回调（`onReceivedHttpRedirectFor` 在 android-34 与
 * `androidx.webkit.WebViewClientCompat` 中均不存在，已用 javap 核对）。唯一全 API 可用的
 * 观测点是 `doUpdateVisitedHistory`——每次主帧导航提交都会触发，含 302 跳。
 *
 * 判定口径：
 * - 首次提交（[prevUrl] 为空）且地址等于起始地址 → 0 跳，不报；
 * - 首次提交但地址已不同于起始地址 → 记作第 1 跳（内核未给起始回调的极端情况）；
 * - 后续每次地址变化 → 跳数 +1，记为一次重定向；
 * - 地址未变（同页 reload / 重复回调）→ 跳数与上报均不变，避免刷屏。
 *
 * 拿不到状态码，故不伪造：`preview_redirect` 只记 from/to 与第几跳。
 */
internal object RedirectTracker {

    /**
     * 判定结果。
     *
     * @property changed 提交地址是否发生变化（变化才需要刷新 [prevUrl]）。
     * @property hops 判定后的跳数。
     * @property isHop 本次是否为一次重定向跳（决定是否上报 `preview_redirect`）。
     * @property fromUrl 重定向来源地址（上报用）。
     */
    data class Step(
        val changed: Boolean,
        val hops: Int,
        val isHop: Boolean,
        val fromUrl: String,
    )

    /** 地址未变化时的惰性结果：不刷新、不计数、不上报。 */
    private fun unchanged(hops: Int) = Step(changed = false, hops = hops, isHop = false, fromUrl = "")

    fun evaluate(
        prevUrl: String,
        url: String,
        startUrl: String,
        hops: Int,
    ): Step {
        if (url.isBlank() || url == prevUrl) return unchanged(hops)
        val from = prevUrl.ifEmpty { startUrl }
        // 起始地址缺失时（内核未回调 onPageStarted 的极端情况）不臆造来源，
        // 但仍计跳——跳数本身是可信的，from_url 留空即可。
        val next = if (prevUrl.isEmpty() && url == startUrl) hops else hops + 1
        val isHop = url != startUrl || prevUrl.isNotEmpty()
        return Step(
            changed = true,
            hops = next,
            isHop = isHop && from.isNotEmpty(),
            fromUrl = from,
        )
    }
}
