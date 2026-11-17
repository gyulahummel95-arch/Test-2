package com.autostream

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import org.json.JSONArray
import org.json.JSONObject

/** Einstellungen auf dem Handy – als lokale Seite im gleichen Stil wie die Auto-Startseite. */
class MainActivity : Activity() {
    @VisibleForTesting lateinit var webView: WebView
    private val main = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply { setBackgroundColor(Ui.BG) }
        webView = WebView(this).apply {
            setBackgroundColor(Ui.BG)
            settings.javaScriptEnabled = true
            // Nur die eigene Seite – keine fremden Seiten mit Zugriff auf die Schnittstelle
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            }
            addJavascriptInterface(Bridge(), "App")
        }
        root.addView(webView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)
        Ui.fitSystemBars(this, root)
        @Suppress("DEPRECATION")
        window.statusBarColor = Ui.BG
        webView.loadUrl(SETTINGS_URL)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        if (::webView.isInitialized) webView.evaluateJavascript("window.onStateChanged&&onStateChanged()", null)
    }

    private fun hasLocation() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun isXiaomi() = Build.MANUFACTURER.lowercase().let {
        it.contains("xiaomi") || it.contains("redmi") || it.contains("poco")
    }

    private fun tryStart(intent: Intent): Boolean = try {
        startActivity(intent); true
    } catch (e: Exception) {
        false
    }

    private fun openAppDetails() {
        tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    /** Schnittstelle für settings.html. Wird nur von der lokalen Seite genutzt. */
    private inner class Bridge {
        @JavascriptInterface
        fun getState(): String {
            val shortcuts = JSONArray()
            Shortcuts.load(this@MainActivity).forEach {
                shortcuts.put(JSONObject().put("name", it.name).put("url", it.url))
            }
            val diag = JSONArray()
            Diagnostics.ORDER.forEach { key ->
                diag.put(JSONArray().put(key).put(Diagnostics.get(this@MainActivity, key) ?: "–"))
            }
            return JSONObject()
                .put("shortcuts", shortcuts)
                .put("theme", Shortcuts.theme(this@MainActivity))
                .put("desktop", Shortcuts.desktopMode(this@MainActivity))
                .put("audio", Shortcuts.audioWhileDriving(this@MainActivity))
                .put("hasBackground", Backgrounds.exists(this@MainActivity))
                .put("location", hasLocation())
                .put("xiaomi", isXiaomi())
                .put("diagnostics", diag)
                .put("device", "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
                .put("version", packageManager.getPackageInfo(packageName, 0).versionName)
                .toString()
        }

        @JavascriptInterface
        fun saveShortcuts(json: String) {
            val arr = JSONArray(json)
            Shortcuts.save(this@MainActivity, (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Shortcut(o.getString("name"), o.getString("url"))
            })
        }

        @JavascriptInterface fun setTheme(name: String) = Shortcuts.setTheme(this@MainActivity, name)
        @JavascriptInterface fun setDesktop(value: Boolean) = Shortcuts.setDesktopMode(this@MainActivity, value)
        @JavascriptInterface fun setAudio(value: Boolean) = Shortcuts.setAudioWhileDriving(this@MainActivity, value)
        @JavascriptInterface fun clearDiagnostics() = Diagnostics.clear(this@MainActivity)

        @JavascriptInterface
        fun openService(url: String, name: String) = main.post {
            startActivity(
                Intent(this@MainActivity, LoginActivity::class.java)
                    .putExtra(LoginActivity.EXTRA_URL, url)
                    .putExtra(LoginActivity.EXTRA_NAME, name)
            )
        }.let { }

        @JavascriptInterface
        fun requestLocation() = main.post {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQ_LOCATION
            )
        }.let { }

        @JavascriptInterface
        fun pickBackground() = main.post {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), REQ_BACKGROUND)
        }.let { }

        @JavascriptInterface
        fun openXiaomi(which: String) = main.post {
            val ok = when (which) {
                "autostart" -> tryStart(Intent().setComponent(ComponentName(
                    "com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )))
                "popup" -> tryStart(Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", packageName))
                else -> false
            }
            if (!ok) openAppDetails()
        }.let { }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQ_BACKGROUND || resultCode != RESULT_OK || uri == null) return
        val ok = try { Backgrounds.saveFromUri(this, uri) } catch (e: Exception) { false }
        if (ok) Shortcuts.setTheme(this, "custom")
        Toast.makeText(this, if (ok) "Hintergrundbild gesetzt" else "Bild konnte nicht geladen werden", Toast.LENGTH_SHORT).show()
        refresh()
    }

    companion object {
        const val SETTINGS_URL = "file:///android_asset/settings.html"
        private const val REQ_BACKGROUND = 42
        private const val REQ_LOCATION = 1
    }
}
