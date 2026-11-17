package com.autostream.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.autostream.Shortcuts

/** Startbildschirm im Auto: Liste der Streaming-Dienste. */
class HomeScreen(carContext: CarContext) : Screen(carContext) {
    @Suppress("DEPRECATION")
    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()
        Shortcuts.load(carContext).take(6).forEach { s ->
            items.addItem(
                Row.Builder()
                    .setTitle(s.name)
                    .addText(s.url)
                    .setOnClickListener { screenManager.push(BrowserScreen(carContext, s.url)) }
                    .build()
            )
        }
        return ListTemplate.Builder()
            .setTitle("AutoStream")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(items.build())
            .build()
    }
}
