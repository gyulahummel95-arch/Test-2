package com.autostream.car

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.CarInfo
import androidx.car.app.hardware.info.Speed
import androidx.core.content.ContextCompat
import com.autostream.Diagnostics
import kotlin.math.abs
import kotlin.math.sqrt

enum class MotionState { STOPPED, MOVING, UNKNOWN }

/**
 * Ermittelt, ob das Fahrzeug steht. Quellen, nach Vorrang:
 * 1. Fahrzeuggeschwindigkeit (über Android Auto) und GPS des Handys –
 *    meldet eine davon Fahrt, gilt das Auto als fahrend.
 * 2. Nur wenn beide nichts wissen: Bewegungssensor des Handys (Tiefgarage).
 * Ohne jede Quelle bleibt der Zustand UNKNOWN (= gesperrt).
 */
class MotionMonitor(
    private val carContext: CarContext,
    private val onChange: (MotionState) -> Unit,
) {
    private var carState = MotionState.UNKNOWN
    private var gpsState = MotionState.UNKNOWN
    private var published: MotionState? = null
    private var carInfo: CarInfo? = null
    private val main = Handler(Looper.getMainLooper())
    private val stillness = StillnessDetector()
    private var sensorStill = false
    private var lastGpsFix = 0L

    private val speedListener = OnCarDataAvailableListener<Speed> { speed ->
        val v = speed.rawSpeedMetersPerSecond
        if (v.status == CarValue.STATUS_SUCCESS) {
            val mps = v.value ?: return@OnCarDataAvailableListener
            carState = if (abs(mps) > CAR_THRESHOLD_MPS) MotionState.MOVING else MotionState.STOPPED
            Diagnostics.set(carContext, Diagnostics.CAR_SPEED, "verfügbar, %.1f km/h".format(mps * 3.6f))
            publish()
        } else {
            Diagnostics.set(carContext, Diagnostics.CAR_SPEED, "Auto liefert keine Daten (Status ${v.status})")
        }
    }

    // Alle Methoden explizit, damit ältere Android-Versionen nicht abstürzen.
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!location.hasSpeed()) return
            lastGpsFix = SystemClock.elapsedRealtime()
            gpsState = if (location.speed > GPS_THRESHOLD_MPS) MotionState.MOVING else MotionState.STOPPED
            Diagnostics.set(carContext, Diagnostics.GPS, "Empfang, %.1f km/h".format(location.speed * 3.6f))
            publish()
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
            stillness.add(SystemClock.elapsedRealtime(), sqrt((x * x + y * y + z * z).toDouble()))
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val sensorCheck = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            var changed = false
            // Veraltetes GPS (Garage, Tunnel) zählt nicht mehr – weder "steht" noch "fährt".
            if (gpsState != MotionState.UNKNOWN && now - lastGpsFix > GPS_STALE_MS) {
                gpsState = MotionState.UNKNOWN
                Diagnostics.set(carContext, Diagnostics.GPS, "kein Empfang mehr")
                changed = true
            }
            val still = stillness.isStill(now)
            if (still != sensorStill) {
                sensorStill = still
                changed = true
            }
            if (changed) publish()
            main.postDelayed(this, 1000)
        }
    }

    /** @return false, wenn die Standortberechtigung fehlt. */
    fun start(): Boolean {
        publish()
        try {
            val info = carContext.getCarService(CarHardwareManager::class.java).carInfo
            info.addSpeedListener(ContextCompat.getMainExecutor(carContext), speedListener)
            carInfo = info
        } catch (e: Exception) {
            Log.w(TAG, "Fahrzeuggeschwindigkeit nicht verfügbar", e)
            Diagnostics.set(carContext, Diagnostics.CAR_SPEED, "nicht verfügbar (${e.javaClass.simpleName})")
        }

        val granted = ContextCompat.checkSelfPermission(
            carContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            try {
                val lm = carContext.getSystemService(LocationManager::class.java)
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, Looper.getMainLooper()
                )
                Diagnostics.set(carContext, Diagnostics.GPS, "warte auf Empfang")
            } catch (e: Exception) {
                Log.w(TAG, "GPS nicht verfügbar", e)
                Diagnostics.set(carContext, Diagnostics.GPS, "Fehler: ${e.javaClass.simpleName}")
            }
        } else {
            Diagnostics.set(carContext, Diagnostics.GPS, "keine Standort-Berechtigung")
        }

        val sm = carContext.getSystemService(SensorManager::class.java)
        sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        main.post(sensorCheck)
        return granted
    }

    fun stop() {
        try {
            carInfo?.removeSpeedListener(speedListener)
        } catch (e: Exception) {
            Log.w(TAG, "removeSpeedListener", e)
        }
        carContext.getSystemService(LocationManager::class.java)?.removeUpdates(locationListener)
        carContext.getSystemService(SensorManager::class.java)?.unregisterListener(sensorListener)
        main.removeCallbacks(sensorCheck)
        stillness.reset()
    }

    private fun publish() {
        val state = combine(carState, gpsState, sensorStill)
        if (state != published) {
            published = state
            val text = when (state) {
                MotionState.MOVING -> "Fahrt erkannt – Bild gesperrt"
                MotionState.STOPPED -> "Stillstand – frei" + if (carState == MotionState.UNKNOWN &&
                    gpsState == MotionState.UNKNOWN) " (per Bewegungssensor)" else ""
                MotionState.UNKNOWN -> "unbekannt – gesperrt"
            }
            Diagnostics.set(carContext, Diagnostics.MOTION, text)
            onChange(state)
        }
    }

    companion object {
        /**
         * Fahrt gewinnt immer; Stillstand, wenn Auto oder GPS ihn melden.
         * Der Bewegungssensor zählt nur, wenn Auto und GPS gar nichts wissen.
         */
        fun combine(car: MotionState, gps: MotionState, sensorStill: Boolean = false): MotionState = when {
            car == MotionState.MOVING || gps == MotionState.MOVING -> MotionState.MOVING
            car == MotionState.STOPPED || gps == MotionState.STOPPED -> MotionState.STOPPED
            sensorStill -> MotionState.STOPPED
            else -> MotionState.UNKNOWN
        }

        private const val TAG = "MotionMonitor"
        private const val CAR_THRESHOLD_MPS = 1.0f   // ~3,6 km/h
        private const val GPS_THRESHOLD_MPS = 2.0f   // ~7 km/h (GPS-Rauschen)
        private const val GPS_STALE_MS = 10_000L
    }
}
