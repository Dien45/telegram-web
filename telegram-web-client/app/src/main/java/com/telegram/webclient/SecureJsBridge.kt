package com.telegram.webclient

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.os.Build
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast

class SecureJsBridge(private val context: Context) {
    @JavascriptInterface
    fun ping(): String = "pong"

    @JavascriptInterface
    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("telegram", text))
    }

    @JavascriptInterface
    fun getBuildInfo(): String {
        return """{"version":"${BuildConfig.VERSION_NAME}","api":${Build.VERSION.SDK_INT}}"""
    }

    @JavascriptInterface
    fun verifySignature(): Boolean {
        return try {
            val pm = context.packageManager
            val info: PackageInfo = pm.getPackageInfo(context.packageName, PackageInfo.GET_SIGNATURES)
            val sig = info.signatures[0]
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val hash = Base64.encodeToString(md.digest(sig.toByteArray()), Base64.NO_WRAP)
            hash == BuildConfig.EXPECTED_SIG_HASH
        } catch (e: Exception) {
            false
        }
    }
}