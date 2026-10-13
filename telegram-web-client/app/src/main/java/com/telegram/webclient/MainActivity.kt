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

    private lateinit var vm: AccountViewModel
    private var pinLauncher: ActivityResultLauncher<Intent>? = null
    private var notifPermissionLauncher: ActivityResultLauncher<String>? = null

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

        // Izin notifikasi (Android 13+) diperlukan agar badge launcher ikut tampil.
        notifPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* hasil tidak perlu ditindaklanjuti */ }
        UnreadBadgeManager.createChannel(this)
        UnreadBadgeManager.requestNotificationPermissionIfNeeded(this, notifPermissionLauncher!!)

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
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
        }
        wv.webViewClient = SecureWebViewClient(
            onLoadingChanged = { loading ->
                findViewById<ProgressBar>(R.id.progressBar)?.visibility =
                    if (loading) View.VISIBLE else View.GONE
            },
            onPageFinished = { view ->
                injectUnreadDetector(view)
            }
        )
        wv.webChromeClient = SecureWebChromeClient()
        wv.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            DownloadManager(this).download(url, userAgent, contentDisposition, mimetype)
        }
        wv.addJavascriptInterface(SecureJsBridge(this), "TelegramBridge")
        wv.loadUrl(TelegramApp.TELEGRAM_WEB_URL)
    }

    /**
     * Inject detektor jumlah chat belum terbaca ke halaman Telegram Web.
     * Telegram Web menaruh jumlah unread di document.title dengan format
     * "(N) Telegram" (atau "Telegram (N)"). Observer + interval fallback
     * memastikan badge ikut berubah secara real-time.
     */
    private fun injectUnreadDetector(view: WebView) {
        val script = """
            (function() {
              function parse() {
                try {
                  var t = document.title || '';
                  var m = t.match(/^\((\d+)\)/) || t.match(/\((\d+)\)\s*$/);
                  var n = m ? parseInt(m[1], 10) : 0;
                  if (typeof TelegramBridge !== 'undefined') {
                    TelegramBridge.onUnreadCount(n);
                  }
                } catch (e) {}
              }
              parse();
              var el = document.querySelector('title');
              if (el) {
                new MutationObserver(parse).observe(el, {
                  childList: true, subtree: true, characterData: true
                });
              }
              setInterval(parse, 5000);
            })();
        """.trimIndent()
        view.evaluateJavascript(script, null)
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

    override fun onDestroy() {
        super.onDestroy()
        clearWebView()
    }

    private fun clearWebView() {
        findViewById<WebView>(R.id.webView)?.apply {
            clearHistory()
            destroy()
        }
    }
}