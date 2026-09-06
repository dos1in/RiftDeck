package com.riftdeck.core.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaySessionTimingTest {
    @Test fun countsWholeSecondsUsingMonotonicClock() { assertEquals(12L, playSessionSeconds(1000, 13999, 2, 2)) }
    @Test fun rebootAndUnavailableBootCounterDoNotCreateFalseHistory() {
        assertEquals(0L, playSessionSeconds(1000, 50000, 2, 3))
        assertEquals(0L, playSessionSeconds(1000, 50000, -1, -1))
    }
    @Test fun rejectsBackwardsElapsedClock() { assertEquals(0L, playSessionSeconds(50000, 1000, 2, 2)) }
    @Test fun forgottenSessionHasBoundedDuration() { assertEquals(86400L, playSessionSeconds(0, 172800000, 2, 2)) }
}
