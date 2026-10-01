package com.riftdeck.core.sharing

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveAccessGateTest {
    @Test(timeout = 10_000)
    fun emulatorLaunchWaitsForActiveCommitAndPreventsNewWrites() {
        val gate = SaveAccessGate()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val launching = CountDownLatch(1)
        val paused = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val writer = executor.submit {
                gate.write { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)) }
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val launcher = executor.submit { launching.countDown(); gate.pauseForEmulator(); paused.countDown() }
            assertTrue(launching.await(2, TimeUnit.SECONDS))
            assertFalse(paused.await(100, TimeUnit.MILLISECONDS))
            release.countDown()
            writer.get(2, TimeUnit.SECONDS)
            launcher.get(2, TimeUnit.SECONDS)
            assertTrue(paused.await(2, TimeUnit.SECONDS))
            assertThrows(IOException::class.java) { gate.write { throw AssertionError("Save write entered while emulator runs") } }
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun returningFromEmulatorAllowsTheNextSaveWrite() {
        val gate = SaveAccessGate()
        gate.pauseForEmulator()
        assertThrows(IOException::class.java) { gate.write { 12 } }
        gate.resumeAfterEmulator()
        assertEquals(12, gate.write { 12 })
    }

    @Test fun failedCommitReleasesTheGateForTheNextWrite() {
        val gate = SaveAccessGate()
        assertThrows(IOException::class.java) { gate.write { throw IOException("Diagnostic failure") } }
        assertEquals("next commit", gate.write { "next commit" })
        gate.pauseForEmulator()
        assertThrows(IOException::class.java) { gate.write { "blocked" } }
    }
}
