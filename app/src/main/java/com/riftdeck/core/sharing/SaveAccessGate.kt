package com.riftdeck.core.sharing

import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** IO-thread barrier: finish a save commit before launching an emulator; deny subsequent writes. */
class SaveAccessGate {
    private val lock = ReentrantLock()
    @Volatile private var emulatorRunning = false
    fun pauseForEmulator() { emulatorRunning = true; lock.withLock { } }
    fun resumeAfterEmulator() { emulatorRunning = false }
    fun <T> write(block: () -> T): T = lock.withLock {
        if (emulatorRunning) throw IOException("Emulator is using saves")
        block()
    }
}
