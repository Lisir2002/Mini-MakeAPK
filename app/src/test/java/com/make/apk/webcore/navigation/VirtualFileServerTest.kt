package com.make.apk.webcore.navigation

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 虚拟文件服务测试（W4 T05）：HTML 多文件项目的资源供给等价性证明。
 * Robolectric 仅因 [VirtualFileServer.responseFor] 触到 android.net.Uri /
 * WebResourceResponse；解析与 MIME 推断为纯逻辑。
 */
@RunWith(RobolectricTestRunner::class)
class VirtualFileServerTest {

    private val files = mapOf(
        "index.html" to "<html></html>",
        "css/style.css" to "body{}",
        "js/app.js" to "console.log(1)",
    )

    // ── 虚拟域判定 ──

    @Test
    fun `virtual host matches case-insensitively`() {
        assertTrue(VirtualFileServer.isVirtualHost("mma.local"))
        assertTrue(VirtualFileServer.isVirtualHost("MMA.LOCAL"))
        assertFalse(VirtualFileServer.isVirtualHost("evil-mma.local"))
        assertFalse(VirtualFileServer.isVirtualHost("example.com"))
        assertFalse(VirtualFileServer.isVirtualHost(null))
    }

    // ── 路径还原 ──

    @Test
    fun `file name resolution trims slash and decodes`() {
        assertEquals("index.html", VirtualFileServer.resolveFileName("/index.html"))
        assertEquals("css/style.css", VirtualFileServer.resolveFileName("css/style.css"))
        // URL 解码：文件名可能含中文 / 空格。
        assertEquals("页 面.html", VirtualFileServer.resolveFileName("/%E9%A1%B5%20%E9%9D%A2.html"))
    }

    @Test
    fun `file name resolution rejects empty and survives bad encoding`() {
        assertNull(VirtualFileServer.resolveFileName(null))
        assertNull(VirtualFileServer.resolveFileName(""))
        assertNull(VirtualFileServer.resolveFileName("/"))
        // 非法百分号序列：解码失败回落原始路径，绝不抛异常。
        assertEquals("%zz.html", VirtualFileServer.resolveFileName("/%zz.html"))
    }

    // ── MIME 推断（与旧单体 mimeOf 映射表零增删）──

    @Test
    fun `mime inference matches legacy table`() {
        assertEquals("text/html", VirtualFileServer.mimeOf("index.html"))
        assertEquals("text/html", VirtualFileServer.mimeOf("a.htm"))
        assertEquals("text/css", VirtualFileServer.mimeOf("style.css"))
        assertEquals("application/javascript", VirtualFileServer.mimeOf("app.js"))
        assertEquals("application/javascript", VirtualFileServer.mimeOf("app.mjs"))
        assertEquals("application/json", VirtualFileServer.mimeOf("data.json"))
        assertEquals("image/svg+xml", VirtualFileServer.mimeOf("logo.svg"))
        assertEquals("image/png", VirtualFileServer.mimeOf("a.png"))
        assertEquals("image/jpeg", VirtualFileServer.mimeOf("a.jpg"))
        assertEquals("image/jpeg", VirtualFileServer.mimeOf("a.jpeg"))
        assertEquals("image/gif", VirtualFileServer.mimeOf("a.gif"))
        assertEquals("image/webp", VirtualFileServer.mimeOf("a.webp"))
        assertEquals("image/x-icon", VirtualFileServer.mimeOf("favicon.ico"))
        assertEquals("text/plain", VirtualFileServer.mimeOf("readme.md"))
        assertEquals("application/xml", VirtualFileServer.mimeOf("a.xml"))
        // 未知扩展名 / 无扩展名回落 text/plain。
        assertEquals("text/plain", VirtualFileServer.mimeOf("a.unknown"))
        assertEquals("text/plain", VirtualFileServer.mimeOf("noext"))
    }

    // ── 拦截入口 ──

    @Test
    fun `response served for virtual host file hit`() {
        val response = VirtualFileServer.responseFor(files, Uri.parse("https://mma.local/css/style.css"))
        assertEquals("text/css", response?.mimeType)
        assertEquals("utf-8", response?.encoding)
    }

    @Test
    fun `response is null for foreign host or missing file`() {
        assertNull(VirtualFileServer.responseFor(files, Uri.parse("https://example.com/index.html")))
        assertNull(VirtualFileServer.responseFor(files, Uri.parse("https://mma.local/missing.css")))
        assertNull(VirtualFileServer.responseFor(files, null))
    }

    @Test
    fun `response is null for empty file set`() {
        assertNull(VirtualFileServer.responseFor(emptyMap(), Uri.parse("https://mma.local/index.html")))
    }

    @Test
    fun `constants match legacy values`() {
        assertEquals("mma.local", VirtualFileServer.HOST)
        assertEquals("https://mma.local/", VirtualFileServer.BASE_URL)
    }
}
