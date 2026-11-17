package com.autostream.car

import kotlin.math.sqrt

/**
 * Erkennt über den Beschleunigungssensor, ob das Handy (und damit das Auto)
 * völlig ruhig ist. Nur als Notlösung, wenn weder Auto noch GPS Daten liefern.
 * Bewusst streng: schon kleine Erschütterungen (Fahrbahn) setzen die Ruhezeit zurück.
 */
class StillnessDetector(
    private val requiredMs: Long = 15_000,
    private val windowMs: Long = 2_000,
    private val maxStdDev: Double = 0.06,     // m/s²
    private val maxJump: Double = 0.35,       // m/s² Abweichung vom Fenster-Mittel
) {
    private val times = ArrayDeque<Long>()
    private val values = ArrayDeque<Double>()
    private var stillSince: Long? = null

    /** @param magnitude Betrag der Beschleunigung inkl. Schwerkraft (m/s²). */
    fun add(timeMs: Long, magnitude: Double) {
        times.addLast(timeMs)
        values.addLast(magnitude)
        while (times.isNotEmpty() && timeMs - times.first() > windowMs) {
            times.removeFirst()
            values.removeFirst()
        }
        val mean = values.average()
        val std = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        val calm = std <= maxStdDev && kotlin.math.abs(magnitude - mean) <= maxJump
        if (!calm) {
            stillSince = null
        } else if (stillSince == null) {
            // Erst ruhig, wenn das Fenster voll ist.
            if (timeMs - times.first() >= windowMs / 2) stillSince = times.first()
        }
    }

    fun isStill(nowMs: Long): Boolean {
        val since = stillSince ?: return false
        val last = times.lastOrNull() ?: return false
        // Keine frischen Sensordaten → nicht ruhig.
        if (nowMs - last > 1_000) return false
        return nowMs - since >= requiredMs
    }

    fun reset() {
        times.clear(); values.clear(); stillSince = null
    }
}
