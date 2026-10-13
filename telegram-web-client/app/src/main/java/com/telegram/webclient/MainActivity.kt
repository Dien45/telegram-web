package com.telegram.webclient

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.telegram.webclient.ui.account.AccountViewModel

class MainActivity : AppCompatActivity() {
    private lateinit var vm: AccountViewModel
    private var pinLauncher: ActivityResultLauncher<Intent>? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastInteraction = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        vm = ViewModelProvider(this)[AccountViewModel::class.java]
        pinLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                unlockApp()
            } else {
                showPinError()
            }
        }
        lastInteraction = System.currentTimeMillis()
        setupWebView()
        observeSecurity()
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
            supportMultipleWindows = false
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
        }
        wv.webViewClient = SecureWebViewClient()
        wv.webChromeClient = SecureWebChromeClient()
        wv.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            DownloadManager(this).download(url, userAgent, contentDisposition, mimetype)
        }
        wv.addJavascriptInterface(SecureJsBridge(this), "TelegramBridge")
        wv.loadUrl(TelegramApp.TELEGRAM_WEB_URL)
    }

    private fun observeSecurity() {
        vm.locked.observe(this) { locked ->
            if (locked) {
                showLock()
            } else {
                unlockApp()
            }
        }
        vm.timeoutExpired.observe(this) { expired ->
            if (expired) {
                vm.lock()
            }
        }
    }

    private fun showLock() {
        val intent = Intent(this, LockActivity::class.java)
        pinLauncher?.launch(intent)
    }

    private fun unlockApp() {
        val wv = findViewById<WebView>(R.id.webView)
        wv.visibility = View.VISIBLE
    }

    private fun showPinError() {
        Toast.makeText(this, R.string.pin_incorrect, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearWebView()
    }

    private fun clearWebView() {
        val wv = findViewById<WebView>(R.id.webView)
        wv?.apply {
            clearCache(true)
            clearHistory()
            destroy()
        }
    }
}
