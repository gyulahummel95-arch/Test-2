package com.autostream.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class StreamCarAppService : CarAppService() {
    // Sideload-App: jeden Android-Auto-Host zulassen.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = BrowserScreen(carContext, WebRenderer.LAUNCHER_URL)
    }
}
