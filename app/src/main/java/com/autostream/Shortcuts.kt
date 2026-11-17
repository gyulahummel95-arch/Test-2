package com.autostream

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Shortcut(val name: String, val url: String)

/** Speichert die Verknüpfungen (Netflix, YouTube, ...) und Einstellungen. */
object Shortcuts {
    private const val PREFS = "autostream"
    private const val KEY_LIST = "shortcuts"
    private const val KEY_DESKTOP = "desktop_mode"

    private val defaults = listOf(
        Shortcut("Netflix", "https://www.netflix.com/browse"),
        Shortcut("YouTube", "https://www.youtube.com"),
        Shortcut("Disney+", "https://www.disneyplus.com"),
        Shortcut("Prime Video", "https://www.primevideo.com"),
        Shortcut("Twitch", "https://www.twitch.tv"),
    )

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(c: Context): List<Shortcut> {
        val raw = prefs(c).getString(KEY_LIST, null) ?: return defaults
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Shortcut(o.getString("name"), o.getString("url"))
            }
        } catch (e: Exception) {
            defaults
        }
    }

    fun save(c: Context, list: List<Shortcut>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url)) }
        prefs(c).edit().putString(KEY_LIST, arr.toString()).apply()
    }

    fun desktopMode(c: Context) = prefs(c).getBoolean(KEY_DESKTOP, true)

    fun setDesktopMode(c: Context, value: Boolean) =
        prefs(c).edit().putBoolean(KEY_DESKTOP, value).apply()
}
