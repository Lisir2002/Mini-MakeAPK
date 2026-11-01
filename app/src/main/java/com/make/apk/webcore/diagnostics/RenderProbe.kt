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
     * 布局度量脚本 v2（rc23 DOM 结构取证）：返回 `|` 分隔的紧凑串。
     *
     * 背景：rc22 实锤登录页表面纯白 + DOM 107 节点，而该站静态壳只有 ~8 个节点
     * （noscript + 空 root + 全屏 loading 遮罩）。分叉点：React 到底挂没挂载、
     * 白色 loading 遮罩是否卡在页面上。字段顺序：
     * 0 readyState；1 visibilityState；2 深色模式(D/L)；
     * 3 root 容器：子元素数/后代节点数/innerHTML 长度（React 是否挂载的铁证）；
     * 4 loading 遮罩 display；5 遮罩 rect；6 遮罩 color（spinner 的 currentColor，
     *   白色 spinner 叠白底 = 隐形）；7 遮罩 mask display；
     * 8 script 总数/外链数/module 数；9/10 前两个外链 src；
     * 11 body 各子树 节点名#id:后代数（107 个节点住在哪）；
     * 12 root innerHTML 前 60 字；13 innerText 长度。
     * 出错返回 ERR 前缀 + 编码后的异常文本。
     */
    internal const val LAYOUT_PROBE_JS: String =
        "(function(){try{" +
            "var de=document.documentElement,b=document.body;if(!b)return '';" +
            "var root=document.getElementById('root');" +
            "var ov=document.getElementById('__page_loading');" +
            "var mask=document.getElementById('__page_loading__mask');" +
            "function cd(e){return e?getComputedStyle(e).display:'noel';}" +
            "var ovR=ov?ov.getBoundingClientRect():null;" +
            "var dark=window.matchMedia?window.matchMedia('(prefers-color-scheme: dark)').matches:'?';" +
            "var scripts=document.scripts,ext=0,mod=0,src1='',src2='';" +
            "for(var i=0;i<scripts.length;i++){var s=scripts[i];" +
            "if(s.type==='module')mod++;" +
            "if(s.src){ext++;if(!src1)src1=s.src;else if(!src2)src2=s.src;}}" +
            "var parts=[],ch=b.children;" +
            "for(var i2=0;i2<ch.length&&i2<8;i2++){parts.push(ch[i2].tagName.toLowerCase()+" +
            "(ch[i2].id?'#'+ch[i2].id:'')+':'+ch[i2].querySelectorAll('*').length);}" +
            "return [document.readyState,document.visibilityState,dark?'D':'L'," +
            "root?(root.childElementCount+'/'+root.querySelectorAll('*').length+'/'+root.innerHTML.length):'noroot'," +
            "cd(ov),ovR?Math.round(ovR.width)+'x'+Math.round(ovR.height):'?'," +
            "ov?getComputedStyle(ov).color:'',cd(mask)," +
            "scripts.length+'/'+ext+'/'+mod," +
            "encodeURIComponent(src1.slice(0,80)),encodeURIComponent(src2.slice(0,80))," +
            "encodeURIComponent(parts.join(','))," +
            "encodeURIComponent(root?root.innerHTML.slice(0,60):'')," +
            "b.innerText.length].join('|');" +
            "}catch(e){return 'ERR:'+encodeURIComponent(''+e);}})()"

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
