package com.telegram.webclient

import android.content.Context
import android.os.Build
import android.util.Base64
import android.webkit.JavascriptInterface

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
        return "{\"version\":\"${BuildConfig.VERSION_NAME}\",\"api\":${Build.VERSION.SDK_INT}}"
    }

    /**
     * Dipanggil dari JS (web.telegram.org) setiap kali jumlah chat belum
     * terbaca berubah. Nilai [count] = 0 berarti badge dibersihkan.
     */
    @JavascriptInterface
    fun onUnreadCount(count: Int) {
        UnreadBadgeManager.update(context, count)
    }

    @JavascriptInterface
    fun verifySignature(): Boolean {
        return try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            }
            val expected = BuildConfig.EXPECTED_SIG_HASH
            if (expected.isBlank()) return true
            val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }
            if (sigs.isNullOrEmpty()) return false
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val hash = Base64.encodeToString(md.digest(sigs[0].toByteArray()), Base64.NO_WRAP)
            hash == expected
        } catch (e: Exception) {
            false
        }
    }
}
