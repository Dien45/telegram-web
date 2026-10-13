package com.telegram.webclient

import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class SecureWebViewClient : WebViewClient() {
    companion object {
        private val ALLOWED_HOSTS = setOf(
            "web.telegram.org",
            "k.telegram.org",
            "telegram.org",
            "my.telegram.org",
            "telegram.me",
            "cdn.telegram.org"
        )
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val host = request.url.host ?: return true
        if (ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }) {
            return false
        }
        Toast.makeText(view.context, "Blocked: $host", Toast.LENGTH_LONG).show()
        return true
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) {
            view.loadUrl("about:blank")
        }
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        return null
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        if (request.isForMainFrame && errorResponse.statusCode in 400..499) {
            view.loadUrl("about:blank")
        }
    }
}
