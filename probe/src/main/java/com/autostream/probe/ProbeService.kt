package com.autostream.probe

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator

/** Zeigt im Auto nur eine Meldung – reiner Test, ob Android Auto selbst installierte Apps anzeigt. */
class ProbeService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = object : Screen(carContext) {
            override fun onGetTemplate(): Template =
                MessageTemplate.Builder("AutoStream Probe läuft – selbst installierte Apps werden angezeigt.")
                    .setTitle("AutoStream Probe")
                    .setHeaderAction(Action.APP_ICON)
                    .build()
        }
    }
}
