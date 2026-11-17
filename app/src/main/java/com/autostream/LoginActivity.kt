package com.autostream

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import com.autostream.car.SearchScreen

/** Browser auf dem Handy – zum Einloggen und Ausprobieren. Cookies gelten auch im Auto. */
class LoginActivity : Activity() {
    @VisibleForTesting lateinit var webView: WebView
    @VisibleForTesting lateinit var toolbar: LinearLayout
    @VisibleForTesting lateinit var urlField: EditText
    private lateinit var progress: ProgressBar
    private lateinit var modeButton: TextView
    private var desktop = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        desktop = Shortcuts.desktopMode(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BAR)
        }

        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Ui.BAR)
            setPadding(Ui.dp(this@LoginActivity, 4), Ui.dp(this@LoginActivity, 6), Ui.dp(this@LoginActivity, 4), Ui.dp(this@LoginActivity, 6))
        }
        toolbar.addView(iconButton("✕", "Schließen") { finish() })
        toolbar.addView(iconButton("‹", "Zurück") { if (webView.canGoBack()) webView.goBack() })
        toolbar.addView(iconButton("›", "Vor") { if (webView.canGoForward()) webView.goForward() })

        urlField = EditText(this).apply {
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.MUTED)
            hint = "Adresse oder Suche"
            textSize = 15f
            setSelectAllOnFocus(true)
            background = GradientDrawable().apply {
                setColor(Ui.FIELD)
                cornerRadius = Ui.dp(this@LoginActivity, 20).toFloat()
            }
            setPadding(Ui.dp(this@LoginActivity, 14), Ui.dp(this@LoginActivity, 8), Ui.dp(this@LoginActivity, 14), Ui.dp(this@LoginActivity, 8))
            setOnEditorActionListener { _, actionId, event ->
                val go = actionId == EditorInfo.IME_ACTION_GO ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                if (go) navigate(text.toString())
                go
            }
        }
        toolbar.addView(urlField, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
            marginStart = Ui.dp(this@LoginActivity, 4)
            marginEnd = Ui.dp(this@LoginActivity, 4)
        })
        toolbar.addView(iconButton("⟳", "Neu laden") { webView.reload() })
        modeButton = iconButton(if (desktop) "🖥" else "📱", "Desktop/Mobil") { toggleMode() }
        toolbar.addView(modeButton)
        root.addView(toolbar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Ui.ACCENT)
        }
        root.addView(progress, LinearLayout.LayoutParams(MATCH_PARENT, Ui.dp(this, 3)))

        val content = FrameLayout(this).apply { setBackgroundColor(Ui.BG) }
        webView = WebView(this)
        content.addView(webView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        setContentView(root)
        Ui.fitSystemBars(this, root)

        WebConfig.configure(
            webView, desktop, content,
            onProgress = { p ->
                progress.progress = p
                progress.visibility = if (p >= 100) View.INVISIBLE else View.VISIBLE
            },
            onUrlChanged = { url -> if (!urlField.hasFocus()) urlField.setText(url) },
        )
        val start = intent.getStringExtra(EXTRA_URL) ?: "https://www.netflix.com/login"
        urlField.setText(start)
        webView.loadUrl(start)
    }

    private fun iconButton(label: String, description: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        contentDescription = description
        textSize = 20f
        setTextColor(Ui.TEXT)
        gravity = Gravity.CENTER
        minWidth = Ui.dp(this@LoginActivity, 40)
        minHeight = Ui.dp(this@LoginActivity, 40)
        setPadding(Ui.dp(this@LoginActivity, 6), 0, Ui.dp(this@LoginActivity, 6), 0)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun navigate(input: String) {
        if (input.isBlank()) return
        val url = SearchScreen.toUrl(input)
        urlField.clearFocus()
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(urlField.windowToken, 0)
        urlField.setText(url)
        webView.loadUrl(url)
    }

    /** Desktop- oder Handy-Ansicht der Webseite (z. B. YouTube-Suche auf dem Handy). */
    private fun toggleMode() {
        desktop = !desktop
        webView.settings.userAgentString = if (desktop) WebConfig.DESKTOP_UA else null
        modeButton.text = if (desktop) "🖥" else "📱"
        webView.reload()
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
