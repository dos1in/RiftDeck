package com.riftdeck.core.emulator

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Cancellation can stop a queued launch, but cannot discard a completed Android handoff. */
internal suspend fun startEmulatorActivity(
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    startActivity: () -> Unit,
): LaunchResult {
    val caller = currentCoroutineContext()
    caller.ensureActive()
    return withContext(NonCancellable) {
        withContext(dispatcher) {
            // Check again after dispatch: the user may have cancelled while waiting for Main.
            caller.ensureActive()
            startActivity()
        }
        LaunchResult.Started
    }
}
