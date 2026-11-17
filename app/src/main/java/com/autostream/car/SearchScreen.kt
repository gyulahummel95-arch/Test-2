package com.autostream.car

import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template

/** Tastatur im Auto (nur im Stand – erzwingt Android Auto selbst). */
class SearchScreen(
    carContext: CarContext,
    private val onUrl: (String) -> Unit,
) : Screen(carContext) {

    override fun onGetTemplate(): Template =
        SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchSubmitted(searchText: String) {
                if (searchText.isBlank()) return
                onUrl(toUrl(searchText))
                screenManager.pop()
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint("Adresse oder Suchbegriff")
            .setShowKeyboardByDefault(true)
            .build()

    companion object {
        /** "netflix.com" → https://netflix.com, alles andere → Google-Suche. */
        fun toUrl(input: String): String {
            val text = input.trim()
            if (text.startsWith("http://") || text.startsWith("https://")) return text
            val looksLikeHost = !text.contains(' ') && text.contains('.') && !text.endsWith('.')
            return if (looksLikeHost) "https://$text"
            else "https://www.google.com/search?q=" + Uri.encode(text)
        }
    }
}
