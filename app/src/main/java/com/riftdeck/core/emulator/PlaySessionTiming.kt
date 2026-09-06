package com.riftdeck.core.emulator

/** Monotonic time avoids wall-clock changes; sessions from an earlier boot cannot be timed. */
fun playSessionSeconds(startedElapsed: Long, returnedElapsed: Long, startBoot: Int, returnBoot: Int): Long {
    if (startBoot < 0 || startBoot != returnBoot || returnedElapsed < startedElapsed) return 0
    return ((returnedElapsed - startedElapsed) / 1000).coerceAtMost(24L * 60 * 60)
}
