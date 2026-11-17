package com.autostream

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * Gemeinsame WebView-Konfiguration für Handy (Login) und Auto-Display,
 * damit Cookies/Logins und User-Agent identisch sind.
 */
object WebConfig {
    // Streaming-Seiten wie Netflix liefern den Player nur an Desktop-Browser aus.
    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(
        webView: WebView,
        desktop: Boolean,
        fullscreenHost: ViewGroup?,
        onPageFinished: ((WebView) -> Unit)? = null,
    ) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            if (desktop) userAgentString = DESKTOP_UA
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme ?: return true
                // intent://, market:// usw. nicht an native Apps weiterreichen.
                return scheme != "http" && scheme != "https"
            }

            override fun onPageFinished(view: WebView, url: String?) {
                onPageFinished?.invoke(view)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            private var customView: View? = null

            // Widevine-DRM (Netflix, Disney+, Prime) freigeben.
            override fun onPermissionRequest(request: PermissionRequest) {
                val allowed = request.resources.filter {
                    it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
                }
                if (allowed.isNotEmpty()) request.grant(allowed.toTypedArray()) else request.deny()
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                val host = fullscreenHost ?: return callback.onCustomViewHidden()
                customView?.let { host.removeView(it) }
                customView = view
                host.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }

            override fun onHideCustomView() {
                customView?.let { fullscreenHost?.removeView(it) }
                customView = null
            }
        }
    }

    const val JS_PAUSE_ALL =
        "document.querySelectorAll('video,audio').forEach(function(m){m.pause();});"

    /** Meldet Video-/Audio-Start an die App (für den Audio-Fokus). */
    const val JS_REPORT_PLAY =
        "if(!window.__asPlayHook){window.__asPlayHook=1;document.addEventListener('play'," +
            "function(){if(window.AutoStream)AutoStream.mediaStarted();},true);}"

    const val JS_TOGGLE_PLAY =
        "(function(){var v=document.querySelector('video');if(!v)return;" +
            "if(v.paused){v.play();}else{v.pause();}})();"
}
