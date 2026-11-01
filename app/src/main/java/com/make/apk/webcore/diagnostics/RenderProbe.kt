package com.make.apk.webcore.diagnostics

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import com.make.apk.webcore.engine.WebViewEngine

/**
 * 渲染层探针（rc22 白屏终审）。
 *
 * 背景：rc21 内容度量实锤「登录页 DOM 有内容（107 节点 / 83 字）但用户看到白」，
 * 但 DOM 有内容 ≠ 屏幕有内容——还差最后一层裁定：**WebView 表面到底画出了什么**。
 *
 * 两个互补观测点，各报一个事件（都带 preview_load_id，可与同次加载串因果链）：
 *
 * 1. [preview_render_metrics]：原生侧（视图尺寸 / 页面缩放 / 文档内容高度）
 *    + JS 侧（布局视口 / visualViewport 缩放 / 首个含文本元素的
 *    getBoundingClientRect 与计算样式 opacity/visibility/display/transform/zoom
 *    / 前景背景色 / 文档滚动尺寸）。
 *    判读：元素 opacity=0 / display=none / transform 位移出屏 / 页面缩放异常，
 *    哪一种都逃不掉。
 *
 * 2. [preview_surface_shot]：把 WebView 所在窗口的实际像素采样统计
 *    （白色像素占比 + 量化颜色数）。API 26+ 用 PixelCopy（硬件层真值），
 *    低版本回退软件 draw。判读：表面全白 + DOM 有内容 = 渲染管线问题；
 *    表面有内容 + 用户看白 = 显示层以外的问题。
 *
 * 全部观测只读、try/catch 兜底，绝不允许影响页面本身。
 */
internal object RenderProbe {

    private const val TAG = "webcore_render_probe"

    /**
     * 布局度量脚本：返回 `|` 分隔的紧凑串（webcore 零依赖，不引 JSON 库）。
     *
     * 字段顺序：布局视口宽高、innerWidth/innerHeight、visualViewport 缩放百分比、
     * 首个含文本元素 rect（left/top/width/height，无文本元素为 -9999/-9999/-1/-1）、
     * 该元素计算样式（opacity 百分比,visibility,display,transform,inline zoom）,
     * body 计算样式（同格式）、body 背景色、元素前景色、文档 scrollWidth/scrollHeight、
     * 元素文本样例（URL 编码截 16 字）、行内 zoom。
     * 颜色串剥掉空白防混入分隔符；出错返回 'ERR'。
     */
    internal const val LAYOUT_PROBE_JS: String =
        "(function(){try{" +
            "var de=document.documentElement,b=document.body;if(!b)return '';" +
            "function st(e){var s=getComputedStyle(e);return Math.round(parseFloat(s.opacity)*100)+" +
            "','+(s.visibility==='visible'?1:0)+','+(s.display==='none'?1:0)+','+" +
            "(s.transform&&s.transform!=='none'?1:0)+','+(s.zoom||'');}" +
            "var el=null,ws=b.querySelectorAll('*');" +
            "for(var i=0;i<ws.length;i++){var c=ws[i].childNodes;" +
            "for(var j=0;j<c.length;j++){if(c[j].nodeType===3&&c[j].textContent.trim().length>0){el=ws[i];break;}}" +
            "if(el)break;}" +
            "var r=el?el.getBoundingClientRect():null;" +
            "var vv=window.visualViewport;" +
            "return [de.clientWidth,de.clientHeight,window.innerWidth,window.innerHeight," +
            "vv?Math.round(vv.scale*100):-1," +
            "r?Math.round(r.left):-9999,r?Math.round(r.top):-9999,r?Math.round(r.width):-1,r?Math.round(r.height):-1," +
            "el?st(el):'noel',st(b)," +
            "(getComputedStyle(b).backgroundColor||'').replace(/[^a-z0-9%().#]/gi,'')," +
            "(el?(getComputedStyle(el).color||''):'').replace(/[^a-z0-9%().#]/gi,'')," +
            "b.scrollWidth,b.scrollHeight," +
            "encodeURIComponent((el?el.textContent:'').trim().slice(0,16))," +
            "(b.style.zoom||de.style.zoom||'0')].join('|');" +
            "}catch(e){return 'ERR';}})()"

    /** 入口：在空白探针同一时点（加载完成 + 2s，SPA 已挂载）触发。 */
    fun run(view: WebView, engine: WebViewEngine) {
        if (!view.settings.javaScriptEnabled) return
        reportRenderMetrics(view, engine)
        captureSurface(view, engine)
    }

    /** 观测点 1：原生布局度量 + JS 布局度量合并上报。 */
    private fun reportRenderMetrics(view: WebView, engine: WebViewEngine) {
        runCatching {
            view.evaluateJavascript(LAYOUT_PROBE_JS) { raw ->
                val fields = linkedMapOf<String, Any?>(
                    // 视图实际布局尺寸：0 会被一眼看出。
                    "view_w" to view.width,
                    "view_h" to view.height,
                    // 页面缩放（useWideViewPort + overview 模式下若被压扁/放大，这里现形）。
                    "page_scale" to view.scale,
                    // 文档内容高度：与 view_h 对比可看出页面是否只有一屏白。
                    "content_h" to view.contentHeight,
                    // JS 侧紧凑串（见 LAYOUT_PROBE_JS 注释的字段顺序）。
                    "layout" to raw?.trim().orEmpty().trim('"'),
                    // 对应 LogFields.PREVIEW_LOAD_ID（webcore 零依赖，只能写字面量）。
                    "preview_load_id" to engine.loadTraceId,
                )
                WebViewEventHook.report("preview_render_metrics", fields)
            }
        }.onFailure { Log.w(TAG, "render_metrics_failed", it) }
    }

    /** 观测点 2：窗口像素采样（表面真值）。 */
    private fun captureSurface(view: WebView, engine: WebViewEngine) {
        val loadId = engine.loadTraceId
        val w = view.width.coerceAtLeast(1)
        val h = view.height.coerceAtLeast(1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val activity = ActivityFinder.from(view.context)
            if (activity == null) {
                Log.i(TAG, "surface_shot_skipped: not activity context")
                return
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            runCatching {
                android.view.PixelCopy.request(
                    activity.window,
                    bmp,
                    { result ->
                        if (result == android.view.PixelCopy.SUCCESS) {
                            reportShot(bmp, w, h, loadId)
                        }
                        bmp.recycle()
                    },
                    Handler(Looper.getMainLooper()),
                )
            }.onFailure {
                Log.w(TAG, "pixel_copy_failed", it)
                bmp.recycle()
            }
        } else {
            // API < 26：软件快照兜底（硬件合成内容可能取不全，仅作参考）。
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            runCatching {
                view.draw(Canvas(bmp))
                reportShot(bmp, w, h, loadId)
            }.onFailure { Log.w(TAG, "software_shot_failed", it) }
            bmp.recycle()
        }
    }

    /** 采样并上报像素统计：网格取点，统计近白占比与量化颜色数。 */
    private fun reportShot(bmp: Bitmap, w: Int, h: Int, loadId: String) {
        runCatching {
            var white = 0
            var total = 0
            val palette = HashSet<Int>()
            val stepX = (w / 60).coerceAtLeast(1)
            val stepY = (h / 60).coerceAtLeast(1)
            var y = 0
            while (y < h) {
                var x = 0
                while (x < w) {
                    val c = bmp.getPixel(x, y)
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    if (r >= 245 && g >= 245 && b >= 245) white++
                    palette.add((r shr 4) shl 8 or ((g shr 4) shl 4) or (b shr 4))
                    total++
                    x += stepX
                }
                y += stepY
            }
            WebViewEventHook.report(
                "preview_surface_shot",
                mapOf(
                    // 近白像素占比（百分比）：~100 = 表面真的全白。
                    "white_pct" to (white * 100 / total),
                    // 量化颜色数（4096 色）：个位数 = 纯色屏，几十以上 = 有真实内容。
                    "colors" to palette.size,
                    "shot_w" to w,
                    "shot_h" to h,
                    // 对应 LogFields.PREVIEW_LOAD_ID（webcore 零依赖，只能写字面量）。
                    "preview_load_id" to loadId,
                ),
            )
        }.onFailure { Log.w(TAG, "shot_sample_failed", it) }
    }

    /** 从 View 上下文向上找 Activity（PixelCopy 需要 Window）。 */
    private object ActivityFinder {
        fun from(context: android.content.Context): Activity? {
            var ctx: android.content.Context? = context
            while (ctx is android.content.ContextWrapper) {
                if (ctx is Activity) return ctx
                ctx = ctx.baseContext
            }
            return null
        }
    }
}
