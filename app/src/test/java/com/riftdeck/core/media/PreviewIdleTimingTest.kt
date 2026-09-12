package com.riftdeck.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewIdleTimingTest {
    @Test fun pausesBeforeShortTimeoutDimWindow() { assertEquals(11750L, previewPauseDelay(15000)) }
    @Test fun capsDimWindowForLongTimeout() { assertEquals(1792750L, previewPauseDelay(1800000)) }
    @Test fun invalidTimeoutNeverSchedulesNegativeDelay() { assertTrue(previewPauseDelay(-1) >= 0) }
}
