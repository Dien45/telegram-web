package com.telegram.webclient

import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class SecureWebViewClient(
    private val onLoadingChanged: ((Boolean) -> Unit)? = null,
    private val onPageFinished: ((WebView) -> Unit)? = null
) : WebViewClient() {

    companion object {
        private val ALLOWED_HOSTS = setOf(
            "web.telegram.org", "k.telegram.org", "telegram.org",
            "my.telegram.org", "telegram.me", "cdn.telegram.org"
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
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        onLoadingChanged?.invoke(false)
        onPageFinished?.invoke(view)
    }
}
