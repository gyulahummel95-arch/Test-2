package com.autostream.car

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.CarInfo
import androidx.car.app.hardware.info.Speed
import androidx.core.content.ContextCompat
import kotlin.math.abs

enum class MotionState { STOPPED, MOVING, UNKNOWN }

/**
 * Ermittelt, ob das Fahrzeug steht. Quellen: Fahrzeuggeschwindigkeit
 * (über Android Auto) und GPS des Handys. Sobald eine Quelle Fahrt meldet,
 * gilt das Auto als fahrend. Ohne jede Quelle bleibt der Zustand UNKNOWN
 * (= gesperrt).
 */
class MotionMonitor(
    private val carContext: CarContext,
    private val onChange: (MotionState) -> Unit,
) {
    private var carState = MotionState.UNKNOWN
    private var gpsState = MotionState.UNKNOWN
    private var published: MotionState? = null
    private var carInfo: CarInfo? = null

    private val speedListener = OnCarDataAvailableListener<Speed> { speed ->
        val v = speed.rawSpeedMetersPerSecond
        if (v.status == CarValue.STATUS_SUCCESS) {
            val mps = v.value ?: return@OnCarDataAvailableListener
            carState = if (abs(mps) > CAR_THRESHOLD_MPS) MotionState.MOVING else MotionState.STOPPED
            publish()
        }
    }

    // Alle Methoden explizit, damit ältere Android-Versionen nicht abstürzen.
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!location.hasSpeed()) return
            gpsState = if (location.speed > GPS_THRESHOLD_MPS) MotionState.MOVING else MotionState.STOPPED
            publish()
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
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
            } catch (e: Exception) {
                Log.w(TAG, "GPS nicht verfügbar", e)
            }
        }
        return granted
    }

    fun stop() {
        try {
            carInfo?.removeSpeedListener(speedListener)
        } catch (e: Exception) {
            Log.w(TAG, "removeSpeedListener", e)
        }
        carContext.getSystemService(LocationManager::class.java)?.removeUpdates(locationListener)
    }

    private fun publish() {
        val state = when {
            carState == MotionState.MOVING || gpsState == MotionState.MOVING -> MotionState.MOVING
            carState == MotionState.STOPPED || gpsState == MotionState.STOPPED -> MotionState.STOPPED
            else -> MotionState.UNKNOWN
        }
        if (state != published) {
            published = state
            onChange(state)
        }
    }

    companion object {
        private const val TAG = "MotionMonitor"
        private const val CAR_THRESHOLD_MPS = 1.0f   // ~3,6 km/h
        private const val GPS_THRESHOLD_MPS = 2.0f   // ~7 km/h (GPS-Rauschen)
    }
}
