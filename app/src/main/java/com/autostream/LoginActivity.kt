package com.autostream

import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.FrameLayout

/** Auf dem Handy einloggen – die Cookies gelten danach auch im Auto. */
class LoginActivity : Activity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        webView = WebView(this)
        root.addView(webView)
        setContentView(root)
        WebConfig.configure(webView, Shortcuts.desktopMode(this), root)
        title = intent.getStringExtra(EXTRA_NAME) ?: "Login"
        webView.loadUrl(intent.getStringExtra(EXTRA_URL) ?: "https://www.netflix.com/login")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"
    }
}
