package com.autostream.car

import com.autostream.car.MotionState.MOVING
import com.autostream.car.MotionState.STOPPED
import com.autostream.car.MotionState.UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Test

class MotionMonitorTest {
    private fun combine(car: MotionState, gps: MotionState) = MotionMonitor.combine(car, gps)

    @Test fun anySourceMovingMeansMoving() {
        assertEquals(MOVING, combine(MOVING, STOPPED))
        assertEquals(MOVING, combine(STOPPED, MOVING))
        assertEquals(MOVING, combine(MOVING, UNKNOWN))
        assertEquals(MOVING, combine(UNKNOWN, MOVING))
    }

    @Test fun stoppedOnlyWhenNoSourceMoving() {
        assertEquals(STOPPED, combine(STOPPED, STOPPED))
        assertEquals(STOPPED, combine(STOPPED, UNKNOWN))
        assertEquals(STOPPED, combine(UNKNOWN, STOPPED))
    }

    @Test fun noDataMeansUnknown() {
        assertEquals(UNKNOWN, combine(UNKNOWN, UNKNOWN))
    }
}
