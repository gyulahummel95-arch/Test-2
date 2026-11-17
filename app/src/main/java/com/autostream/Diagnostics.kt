package com.autostream

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Merkt sich, was im Auto passiert ist – wird in der Handy-App angezeigt. */
object Diagnostics {
    private const val PREFS = "autostream_diag"

    const val CONNECTED = "Android Auto verbunden"
    const val SURFACE = "Autodisplay (Auflösung)"
    const val VISIBLE = "Freier Bereich"
    const val CAR_SPEED = "Geschwindigkeit vom Auto"
    const val GPS = "GPS"
    const val MOTION = "Sperre"
    const val ERROR = "Letzter Fehler"

    val ORDER = listOf(CONNECTED, SURFACE, VISIBLE, CAR_SPEED, GPS, MOTION, ERROR)

    fun set(context: Context, key: String, value: String) {
        val time = SimpleDateFormat("dd.MM. HH:mm:ss", Locale.GERMANY).format(Date())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key, "$value  ($time)").apply()
    }

    fun get(context: Context, key: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null)

    fun report(context: Context): String = ORDER.joinToString("\n") { key ->
        "$key: ${get(context, key) ?: "–"}"
    }

    fun clear(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}
