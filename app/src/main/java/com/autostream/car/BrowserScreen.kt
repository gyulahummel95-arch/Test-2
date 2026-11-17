package com.autostream.car

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.autostream.R
import com.autostream.Shortcuts

/** Hauptbildschirm im Auto: Startseite mit Kacheln und Browser auf der Auto-Fläche. */
class BrowserScreen(carContext: CarContext, url: String) : Screen(carContext) {

    private val renderer = WebRenderer(carContext, url, Shortcuts.desktopMode(carContext))
    private val motion = MotionMonitor(carContext) { state -> renderer.setMotionState(state) }

    init {
        renderer.onOpenSearch = { openSearch() }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)
                if (!motion.start()) {
                    CarToast.makeText(
                        carContext,
                        "Standort in der AutoStream-App auf dem Handy erlauben",
                        CarToast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                motion.stop()
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
                renderer.release()
            }
        })
    }

    private fun openSearch() {
        screenManager.push(SearchScreen(carContext) { url -> renderer.load(url) })
    }

    override fun onGetTemplate(): Template {
        val actions = ActionStrip.Builder()
            .addAction(
                Action.Builder().setTitle("Home")
                    .setOnClickListener { renderer.load(WebRenderer.LAUNCHER_URL) }.build()
            )
            .addAction(
                Action.Builder().setTitle("Zurück")
                    .setOnClickListener { renderer.goBack() }.build()
            )
            .addAction(
                Action.Builder().setTitle("Suche")
                    .setOnClickListener { openSearch() }.build()
            )
            .addAction(
                Action.Builder().setTitle("Play/Pause")
                    .setOnClickListener { renderer.togglePlay() }.build()
            )
            .build()

        val mapActions = ActionStrip.Builder()
            // PAN aktiviert Wisch-/Scrollgesten auf der Fläche.
            .addAction(Action.PAN)
            .addAction(
                Action.Builder()
                    .setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(carContext, R.drawable.ic_refresh)
                        ).build()
                    )
                    .setOnClickListener { renderer.reload() }
                    .build()
            )
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actions)
            .setMapActionStrip(mapActions)
            .build()
    }
}
