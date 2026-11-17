package com.autostream.car

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import com.autostream.Backgrounds
import com.autostream.Diagnostics
import com.autostream.Shortcuts
import com.autostream.WebConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * Rendert eine WebView auf die Android-Auto-Fläche: Die Surface des Autos
 * wird als virtuelles Display angelegt, auf dem eine Presentation mit der
 * WebView läuft. Touch-Eingaben vom Auto werden als MotionEvents weitergegeben.
 */
class WebRenderer(
    private val context: Context,
    private var url: String,
    private val desktop: Boolean,
) : SurfaceCallback {

    private val main = Handler(Looper.getMainLooper())
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: WebPresentation? = null
    private var motion = MotionState.UNKNOWN
    private var visibleArea: Rect? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    private val blocked get() = motion != MotionState.STOPPED

    /** Wird aufgerufen, wenn auf der Startseite die Suchleiste angetippt wird. */
    var onOpenSearch: (() -> Unit)? = null

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var hasAudioFocus = false
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
        )
        .setOnAudioFocusChangeListener({ change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                if (change == AudioManager.AUDIOFOCUS_LOSS) hasAudioFocus = false
                presentation?.webView?.evaluateJavascript(WebConfig.JS_PAUSE_ALL, null)
            }
        }, main)
        .build()

    /** Video startet: Ton übernehmen, damit Spotify/Radio pausieren. */
    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        hasAudioFocus = audioManager.requestAudioFocus(focusRequest) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (hasAudioFocus) audioManager.abandonAudioFocusRequest(focusRequest)
        hasAudioFocus = false
    }

    /** Schnittstelle für JavaScript (Startseite und Abspiel-Erkennung). */
    private inner class JsBridge {
        @JavascriptInterface
        fun shortcuts(): String {
            val arr = JSONArray()
            Shortcuts.load(context).forEach {
                arr.put(JSONObject().put("name", it.name).put("url", it.url))
            }
            return arr.toString()
        }

        @JavascriptInterface
        fun theme(): String = Shortcuts.theme(context)

        @JavascriptInterface
        fun setTheme(name: String) {
            main.post {
                if (presentation?.webView?.url?.startsWith(LAUNCHER_URL) == true) Shortcuts.setTheme(context, name)
            }
        }

        @JavascriptInterface
        fun background(): String = Backgrounds.dataUrl(context)

        @JavascriptInterface
        fun openSearch() {
            main.post {
                // Nur von der eigenen Startseite aus, nicht von fremden Webseiten.
                if (presentation?.webView?.url?.startsWith(LAUNCHER_URL) == true) onOpenSearch?.invoke()
            }
        }

        @JavascriptInterface
        fun mediaStarted() {
            main.post { if (!blocked || audioWhileDriving()) requestAudioFocus() }
        }
    }

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        val surface = container.surface ?: return
        main.post {
            releaseDisplay()
            surfaceWidth = container.width
            surfaceHeight = container.height
            Diagnostics.set(context, Diagnostics.SURFACE,
                "${container.width} × ${container.height}, ${container.dpi} dpi")
            val dm = context.getSystemService(DisplayManager::class.java)
            val vd = dm.createVirtualDisplay(
                "AutoStream", container.width, container.height, container.dpi,
                surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            )
            virtualDisplay = vd
            try {
                presentation = WebPresentation(context, vd.display).also {
                    it.show()
                    it.applyMotion()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Presentation konnte nicht angezeigt werden", e)
                Diagnostics.set(context, Diagnostics.ERROR, "Anzeige fehlgeschlagen: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    /** Bereich, der nicht von Android-Auto-Knöpfen verdeckt ist. */
    override fun onVisibleAreaChanged(visibleArea: Rect) {
        main.post {
            this.visibleArea = Rect(visibleArea)
            Diagnostics.set(context, Diagnostics.VISIBLE, visibleArea.toShortString())
            applySafeArea()
        }
    }

    /** Übergibt die Ränder (in Pixeln) an die Startseite, damit nichts verdeckt wird. */
    private fun applySafeArea() {
        val wv = presentation?.webView ?: return
        val area = visibleArea ?: return
        if (wv.url?.startsWith(LAUNCHER_URL) != true || surfaceWidth == 0) return
        val l = area.left.coerceAtLeast(0)
        val t = area.top.coerceAtLeast(0)
        val r = (surfaceWidth - area.right).coerceAtLeast(0)
        val b = (surfaceHeight - area.bottom).coerceAtLeast(0)
        wv.evaluateJavascript("window.setSafeArea&&setSafeArea($l,$t,$r,$b)", null)
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        main.post { releaseDisplay() }
    }

    override fun onClick(x: Float, y: Float) {
        main.post { if (!blocked) presentation?.tap(x, y) }
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        main.post { if (!blocked) presentation?.drag(distanceX, distanceY) }
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        main.post { if (!blocked) presentation?.webView?.flingScroll(-velocityX.toInt(), -velocityY.toInt()) }
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        main.post {
            if (!blocked) presentation?.webView?.zoomBy(scaleFactor.coerceIn(0.5f, 2f))
        }
    }

    fun setMotionState(state: MotionState) {
        main.post {
            motion = state
            presentation?.applyMotion()
        }
    }

    fun load(newUrl: String) {
        main.post {
            url = newUrl
            presentation?.webView?.loadUrl(newUrl)
        }
    }

    fun goBack() {
        main.post { presentation?.webView?.let { if (it.canGoBack()) it.goBack() } }
    }

    fun reload() {
        main.post { presentation?.webView?.reload() }
    }

    fun togglePlay() {
        main.post {
            // Bei Fahrt nur, wenn der Ton weiterlaufen darf (wie beim Radio).
            if (!blocked || audioWhileDriving()) {
                presentation?.webView?.evaluateJavascript(WebConfig.JS_TOGGLE_PLAY, null)
            }
        }
    }

    /** Führt JavaScript in der angezeigten Seite aus (auch für Tests). */
    fun evaluate(js: String, callback: (String?) -> Unit) {
        main.post {
            val wv = presentation?.webView
            if (wv == null) callback(null) else wv.evaluateJavascript(js) { callback(it) }
        }
    }

    private fun audioWhileDriving() = Shortcuts.audioWhileDriving(context)

    fun release() {
        main.post { releaseDisplay() }
    }

    private fun releaseDisplay() {
        abandonAudioFocus()
        presentation?.let { p ->
            p.webView?.url?.let { url = it }
            CookieManager.getInstance().flush()
            p.webView?.destroy()
            try {
                p.dismiss()
            } catch (e: Exception) {
                Log.w(TAG, "dismiss", e)
            }
        }
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
    }

    private inner class WebPresentation(outer: Context, display: Display) :
        Presentation(outer, display) {

        var webView: WebView? = null
        private lateinit var overlay: TextView
        private lateinit var root: FrameLayout

        private var dragDownTime = 0L
        private var dragX = 0f
        private var dragY = 0f
        private val endDrag = Runnable { finishDrag() }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            root = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
            val wv = WebView(context)
            root.addView(wv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            overlay = TextView(context).apply {
                setBackgroundColor(Color.BLACK)
                setTextColor(Color.WHITE)
                textSize = 26f
                gravity = Gravity.CENTER
                setPadding(48, 48, 48, 48)
                visibility = View.GONE
                elevation = 100f
            }
            root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            setContentView(root)
            wv.addJavascriptInterface(JsBridge(), "AutoStream")
            WebConfig.configure(wv, desktop, root, onPageFinished = { view ->
                view.evaluateJavascript(WebConfig.JS_REPORT_PLAY, null)
                applySafeArea()
            })
            wv.loadUrl(url)
            webView = wv
            overlay.bringToFront()
        }

        fun applyMotion() {
            if (!::overlay.isInitialized) return
            if (blocked) {
                val keepAudio = audioWhileDriving()
                val hint = if (keepAudio) "\n\nTon läuft weiter." else ""
                overlay.text = if (motion == MotionState.MOVING)
                    "Bild nur im Stand.$hint"
                else
                    "Warte auf Stillstand-Erkennung …\n(Standort erlauben oder Auto parken)$hint"
                overlay.visibility = View.VISIBLE
                overlay.bringToFront()
                if (!keepAudio) {
                    webView?.evaluateJavascript(WebConfig.JS_PAUSE_ALL, null)
                    webView?.onPause()
                    abandonAudioFocus()
                }
            } else {
                overlay.visibility = View.GONE
                webView?.onResume()
            }
        }

        fun tap(x: Float, y: Float) {
            val down = SystemClock.uptimeMillis()
            inject(MotionEvent.ACTION_DOWN, x, y, down)
            main.postDelayed({ inject(MotionEvent.ACTION_UP, x, y, down) }, 60)
        }

        /** Wandelt Scroll-Gesten des Autos in eine Wischbewegung um. */
        fun drag(dx: Float, dy: Float) {
            val w = root.width.toFloat()
            val h = root.height.toFloat()
            if (dragDownTime == 0L) {
                dragDownTime = SystemClock.uptimeMillis()
                dragX = w / 2
                dragY = h / 2
                inject(MotionEvent.ACTION_DOWN, dragX, dragY, dragDownTime)
            }
            dragX -= dx
            dragY -= dy
            val clampedX = dragX.coerceIn(0f, w - 1)
            val clampedY = dragY.coerceIn(0f, h - 1)
            inject(MotionEvent.ACTION_MOVE, clampedX, clampedY, dragDownTime)
            main.removeCallbacks(endDrag)
            if (clampedX != dragX || clampedY != dragY) {
                // Rand erreicht: Geste beenden, nächste startet wieder in der Mitte.
                dragX = clampedX
                dragY = clampedY
                finishDrag()
            } else {
                main.postDelayed(endDrag, 200)
            }
        }

        private fun finishDrag() {
            if (dragDownTime == 0L) return
            inject(MotionEvent.ACTION_UP, dragX, dragY, dragDownTime)
            dragDownTime = 0L
        }

        private fun inject(action: Int, x: Float, y: Float, downTime: Long) {
            val ev = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            ev.source = InputDevice.SOURCE_TOUCHSCREEN
            window?.decorView?.dispatchTouchEvent(ev)
            ev.recycle()
        }
    }

    companion object {
        const val LAUNCHER_URL = "file:///android_asset/launcher.html"
        private const val TAG = "WebRenderer"
    }
}
