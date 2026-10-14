package com.telegram.webclient

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.telegram.webclient.ui.account.AccountViewModel

class MainActivity : AppCompatActivity() {

    companion object {
        /** Dipakai service untuk tahu apakah WebView masih hidup untuk di-poll. */
        @Volatile
        var isAlive: Boolean = false
            private set
    }

    private lateinit var vm: AccountViewModel
    private var pinLauncher: ActivityResultLauncher<Intent>? = null
    private var notifPermissionLauncher: ActivityResultLauncher<String>? = null
    private var badgeHookJs: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        isAlive = true
        vm = ViewModelProvider(this)[AccountViewModel::class.java]

        pinLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                unlockApp()
            } else {
                showPinError()
            }
        }

        // Izin notifikasi (Android 13+) diperlukan agar badge launcher ikut tampil.
        notifPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) UnreadBadgeManager.onPermissionGranted(this)
            }
        UnreadBadgeManager.createChannel(this)
        UnreadBadgeManager.requestNotificationPermissionIfNeeded(this, notifPermissionLauncher!!)

        badgeHookJs = try {
            assets.open("badge_hook.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }

        setupWebView()
        observeSecurity()
        BadgeKeepAliveService.start(this)
    }

    private fun setupWebView() {
        val wv = findViewById<WebView>(R.id.webView)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            allowUniversalAccessFromFileURLs = false
            allowFileAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
        }
        wv.webViewClient = SecureWebViewClient(
            badgeHookJs = badgeHookJs,
            onLoadingChanged = { loading ->
                findViewById<ProgressBar>(R.id.progressBar)?.visibility =
                    if (loading) View.VISIBLE else View.GONE
            },
            onPageStarted = { view -> injectBadgeHook(view) },
            onPageFinished = { view -> injectBadgeHook(view) }
        )
        wv.webChromeClient = SecureWebChromeClient()
        wv.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            DownloadManager(this).download(url, userAgent, contentDisposition, mimetype)
        }
        wv.addJavascriptInterface(SecureJsBridge(this), "TelegramBridge")
        wv.loadUrl(TelegramApp.TELEGRAM_WEB_URL)
    }

    /**
     * Fallback: sisipkan badge_hook.js lewat evaluateJavascript.
     *
     * Jalur utama ada di SecureWebViewClient.shouldInterceptRequest (disisipkan ke <head>
     * sebelum script halaman). Skrip ini idempoten, jadi aman dijalankan berulang.
     */
    private fun injectBadgeHook(view: WebView) {
        val hook = badgeHookJs
        if (!hook.isNullOrEmpty()) {
            view.evaluateJavascript(hook, null)
        }
    }

    private fun observeSecurity() {
        vm.locked.observe(this) { locked ->
            if (locked) showLock() else unlockApp()
        }
        vm.timeoutExpired.observe(this) { expired ->
            if (expired) vm.lock()
        }
    }

    private fun showLock() {
        val intent = Intent(this, LockActivity::class.java)
        pinLauncher?.launch(intent)
    }

    private fun unlockApp() {
        findViewById<WebView>(R.id.webView).visibility = View.VISIBLE
    }

    private fun showPinError() {
        Toast.makeText(this, R.string.pin_incorrect, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        // Notifikasi/badge tampil kembali jika user sempat men-dismissnya.
        UnreadBadgeManager.refresh(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        isAlive = false
        BadgeKeepAliveService.stop(this)
        clearWebView()
    }

    private fun clearWebView() {
        findViewById<WebView>(R.id.webView)?.apply {
            clearHistory()
            destroy()
        }
    }
}