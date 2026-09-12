package com.riftdeck.core.media

/** Android has no public pre-dim event. Use the AOSP dim window as a conservative estimate. */
internal fun previewPauseDelay(screenOffTimeoutMs: Long): Long {
    val timeout = screenOffTimeoutMs.coerceAtLeast(1000L)
    val dimWindow = minOf(7000L, timeout / 5L)
    return (timeout - dimWindow - 250L).coerceAtLeast(0L)
}
