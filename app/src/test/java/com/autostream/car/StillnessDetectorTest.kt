package com.autostream.car

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StillnessDetectorTest {
    private val g = 9.81

    /** Simuliert Sensorwerte alle 60 ms mit gegebenem Rauschen. */
    private fun feed(d: StillnessDetector, fromMs: Long, toMs: Long, noise: Double, seed: Int = 1) {
        val rnd = Random(seed)
        var t = fromMs
        while (t <= toMs) {
            d.add(t, g + (rnd.nextDouble() * 2 - 1) * noise)
            t += 60
        }
    }

    @Test fun parkedPhoneIsStillAfter15Seconds() {
        val d = StillnessDetector()
        feed(d, 0, 16_000, noise = 0.02)
        assertTrue(d.isStill(16_000))
    }

    @Test fun notStillBefore15Seconds() {
        val d = StillnessDetector()
        feed(d, 0, 10_000, noise = 0.02)
        assertFalse(d.isStill(10_000))
    }

    @Test fun roadVibrationIsNeverStill() {
        val d = StillnessDetector()
        feed(d, 0, 60_000, noise = 0.4)
        assertFalse(d.isStill(60_000))
    }

    @Test fun smoothHighwayVibrationIsNeverStill() {
        val d = StillnessDetector()
        feed(d, 0, 60_000, noise = 0.15)
        assertFalse(d.isStill(60_000))
    }

    @Test fun singleBumpResetsTimer() {
        val d = StillnessDetector()
        feed(d, 0, 14_000, noise = 0.02)
        d.add(14_060, g + 1.5)  // Schlagloch / Anfahren
        feed(d, 14_120, 20_000, noise = 0.02, seed = 2)
        assertFalse("nach Ruck muss die Ruhezeit neu beginnen", d.isStill(20_000))
        feed(d, 20_060, 33_000, noise = 0.02, seed = 3)
        assertTrue(d.isStill(33_000))
    }

    @Test fun missingSensorDataIsNotStill() {
        val d = StillnessDetector()
        feed(d, 0, 16_000, noise = 0.02)
        assertFalse(d.isStill(20_000))
    }
}
