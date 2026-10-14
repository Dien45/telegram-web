package com.telegram.webclient

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

class SecureWebViewClient(
    private val badgeHookJs: String? = null,
    private val onLoadingChanged: ((Boolean) -> Unit)? = null,
    private val onPageStarted: ((WebView) -> Unit)? = null,
    private val onPageFinished: ((WebView) -> Unit)? = null
) : WebViewClient() {

    companion object {
        private val ALLOWED_HOSTS = setOf(
            "web.telegram.org", "k.telegram.org", "telegram.org",
            "my.telegram.org", "telegram.me", "cdn.telegram.org"
        )

        /** Path document HTML di web.telegram.org yang perlu diinjeksi badge hook. */
        private val HTML_PATHS = setOf(
            "", "/", "/k", "/k/", "/k/index.html", "/a", "/a/", "/a/index.html"
        )
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val host = request.url.host
        // URL tanpa host (about:, blob:, data:, javascript:) biarkan berjalan dalam WebView.
        if (host == null) return false
        val allowed = ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }
        if (allowed) return false
        Toast.makeText(view.context, "Blocked: $host", Toast.LENGTH_LONG).show()
        return true
    }

    /**
     * Sisipkan badge_hook.js sebagai anak pertama <head> pada document HTML utama.
     *
     * Telegram Web K tidak punya header CSP dan service worker-nya tidak meng-intercept
     * navigasi HTML (hanya asset .js/.css/dll), jadi document selalu diambil dari jaringan
     * dan bisa disisipi di sini. Ini menjamin hook terpasang SEBELUM script halaman,
     * syarat wajib agar Web K mendaftarkan listener folder_unread.
     *
     * Gagal di mana pun (jaringan, decode, dsb) -> return null agar WebView muat normal.
     */
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val hook = badgeHookJs
        if (hook.isNullOrEmpty()) return null
        if (!request.isForMainFrame) return null
        if (!request.method.equals("GET", ignoreCase = true)) return null
        val url = request.url
        if (!"https".equals(url.scheme, ignoreCase = true)) return null
        if (url.host != "web.telegram.org") return null
        val path = url.path.orEmpty()
        if (path !in HTML_PATHS && !path.endsWith(".html")) return null
        return injectBadgeHook(url.toString(), hook)
    }

    private fun injectBadgeHook(url: String, hook: String): WebResourceResponse? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            CookieManager.getInstance().getCookie(url)?.let {
                conn.setRequestProperty("Cookie", it)
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return null
            }
            if (!conn.contentType.orEmpty().contains("html", ignoreCase = true)) {
                conn.disconnect()
                return null
            }
            val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            conn.disconnect()

            val tag = "<script>$hook</script>"
            val injected = injectAfterHead(body, tag)
            WebResourceResponse(
                "text/html",
                "utf-8",
                200,
                "OK",
                // Jangan biarkan WebView meng-cache versi yang sudah disisipi:
                // hook bisa berubah tiap update aplikasi.
                mapOf("Cache-Control" to "no-store"),
                ByteArrayInputStream(injected.toByteArray(Charsets.UTF_8))
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun injectAfterHead(html: String, tag: String): String {
        val headStart = html.indexOf("<head", ignoreCase = true)
        if (headStart < 0) return tag + html
        val headEnd = html.indexOf('>', headStart)
        if (headEnd <= headStart) return tag + html
        return html.substring(0, headEnd + 1) + tag + html.substring(headEnd + 1)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        // Jangan blank-kan halaman: Telegram punya state error sendiri.
        if (request.isForMainFrame) {
            onLoadingChanged?.invoke(false)
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        // Jangan blank-kan halaman pada 4xx — itu yang bikin web "hilang" saat reload.
        if (request.isForMainFrame) {
            onLoadingChanged?.invoke(false)
        }
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onLoadingChanged?.invoke(true)
        onPageStarted?.invoke(view)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        onLoadingChanged?.invoke(false)
        onPageFinished?.invoke(view)
    }
}
