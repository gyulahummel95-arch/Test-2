package com.autostream

import android.app.Activity
import android.graphics.Color
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Gemeinsame Farben und Hilfen für die Handy-Oberfläche. */
object Ui {
    val BG = Color.parseColor("#0b0f1a")
    val BAR = Color.parseColor("#151c2e")
    val FIELD = Color.parseColor("#222b40")
    val TEXT = Color.parseColor("#f2f4f8")
    val MUTED = Color.parseColor("#8c95a8")
    val ACCENT = Color.parseColor("#3d8bff")

    /**
     * Ab Android 15 zeichnen Apps unter Status- und Navigationsleiste.
     * Der Inhalt wird hier passend eingerückt, damit oben nichts verdeckt ist.
     */
    fun fitSystemBars(activity: Activity, root: View) {
        @Suppress("DEPRECATION")
        activity.window.statusBarColor = BAR
        @Suppress("DEPRECATION")
        activity.window.navigationBarColor = BG
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
    }

    fun dp(activity: Activity, value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
