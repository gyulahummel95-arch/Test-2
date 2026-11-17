package com.autostream.car

import com.autostream.car.MotionState.MOVING
import com.autostream.car.MotionState.STOPPED
import com.autostream.car.MotionState.UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Test

class MotionMonitorTest {
    private fun combine(car: MotionState, gps: MotionState, still: Boolean = false) =
        MotionMonitor.combine(car, gps, still)

    @Test fun anySourceMovingMeansMoving() {
        assertEquals(MOVING, combine(MOVING, STOPPED))
        assertEquals(MOVING, combine(STOPPED, MOVING))
        assertEquals(MOVING, combine(MOVING, UNKNOWN))
        assertEquals(MOVING, combine(UNKNOWN, MOVING))
    }

    @Test fun movingWinsEvenIfSensorSaysStill() {
        assertEquals(MOVING, combine(MOVING, UNKNOWN, still = true))
        assertEquals(MOVING, combine(UNKNOWN, MOVING, still = true))
    }

    @Test fun stoppedOnlyWhenNoSourceMoving() {
        assertEquals(STOPPED, combine(STOPPED, STOPPED))
        assertEquals(STOPPED, combine(STOPPED, UNKNOWN))
        assertEquals(STOPPED, combine(UNKNOWN, STOPPED))
    }

    @Test fun sensorOnlyHelpsWithoutOtherData() {
        assertEquals(STOPPED, combine(UNKNOWN, UNKNOWN, still = true))
        assertEquals(UNKNOWN, combine(UNKNOWN, UNKNOWN, still = false))
    }
}
