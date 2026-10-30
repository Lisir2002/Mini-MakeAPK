package com.make.apk

import android.content.Context
import android.util.Log

/**
 * 导出壳里的**本地页面**读取器（配合 `scripts/gitops/inject_shell_spec.py` 的页面注入）。
 *
 * ## 它解决什么
 *
 * 壳条目的 [com.make.apk.data.model.ShellAction.OpenLocalPage] 带的是**项目内文件名**
 * （`page/about.html`），而壳契约（res/raw/shell_spec.json）不携带内容 —— 只注入壳契约
 * 会导出一个"点了没反应"的 APK。故注入脚本把项目页面一并写进
 * `assets/<LOCAL_PAGE_ASSET_DIR>/<path>`（**assets 而非 res/raw**：res/raw 的文件名
 * 不允许 `/`，无法保留 `page/about.html` 这类相对路径子目录）。
 *
 * ## 读取方式（零新增依赖）
 *
 * WebView 原生支持 `file:///android_asset/...`：
 * - 不需要引入任何浏览器内核 / 本地 HTTP 服务 / 文件拷贝管线（保护轻量 APK 卖点）；
 * - 相对路径资源（`<link href="css/style.css">`）由 WebView 按文件目录**自动解析**，
 *   无需自建拦截；
 * - `WebViewScreen` 已开启 `allowFileAccess`，且其导航客户端把 `file` 判为"应用内协议"
 *   （不外跳系统浏览器），两侧无需改动。
 *
 * @param context 用于访问 assets（注入页面随 APK 一起打包，只读）。
 */
internal class InjectedLocalPages(private val context: Context) {

    /**
     * 把项目内文件名解析成 WebView 可直接加载的 URL；**文件不存在返回 null**（调用方回落）。
     *
     * @param path 项目内页面文件名（如 `page/about.html`）；越界 / 空 / 含 `..` 一律拒绝。
     */
    fun urlOf(path: String): String? {
        val assetPath = assetPathOf(path) ?: return null
        if (!exists(assetPath)) {
            Log.w(TAG_TEMPLATE, "local_page_not_injected: $assetPath")
            return null
        }
        return ASSET_URL_PREFIX + assetPath
    }

    /** 资产内相对路径（已做越界防护）；不安全返回 null。 */
    private fun assetPathOf(path: String): String? {
        val clean = path.trim().trimStart('/')
        if (clean.isEmpty()) return null
        // 越界防护：`..` 段可能跳出注入目录；注入脚本已拦过一次，这里是运行期兜底。
        if (clean.split('/').any { it == ".." }) return null
        return "$LOCAL_PAGE_ASSET_DIR/$clean"
    }

    /** 资产是否存在（只读方式探测：开即关，不把内容读进内存）。 */
    private fun exists(assetPath: String): Boolean = runCatching {
        context.assets.open(assetPath).use { Unit }
    }.onFailure {
        Log.w(TAG_TEMPLATE, "local_page_open_failed: $assetPath")
    }.isSuccess

    private companion object {
        /**
         * 注入页面在 assets 里的根目录 —— 必须与
         * `scripts/gitops/inject_shell_spec.py` 的 `LOCAL_PAGE_ASSET_DIR` 一致。
         */
        const val LOCAL_PAGE_ASSET_DIR: String = "mma_pages"

        /** WebView 读 APK 内 assets 的标准 URL 前缀（Android 平台能力，无新增依赖）。 */
        const val ASSET_URL_PREFIX: String = "file:///android_asset/"

        /** 日志标签（与 TemplateActivity 同一出口，便于 Logcat 检索）。 */
        const val TAG_TEMPLATE: String = "template_shell"
    }
}
