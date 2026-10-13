package com.telegram.webclient

import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView

class SecureWebChromeClient : WebChromeClient() {
    override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult) {
        result.confirm()
    }

    override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult) {
        result.confirm()
    }

    override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: JsPromptResult) {
        result.confirm(defaultValue)
    }

    override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
        super.onShowCustomView(view, callback)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        super.onReceivedTitle(view, title)
    }
}