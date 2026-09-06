package com.riftdeck.feature.settings

import com.riftdeck.data.scanner.ScanState

/** Consume scan results once; opening a screen must not replay an already completed result. */
internal class ScanCompletionFeedback(initial: ScanState) {
    private var handledRunId = if (initial.running) initial.runId - 1 else initial.runId

    fun skip(scan: ScanState) { handledRunId = maxOf(handledRunId, scan.runId) }

    fun consume(scan: ScanState): Boolean {
        if (scan.running || scan.runId <= handledRunId) return false
        handledRunId = scan.runId
        return scan.completed && scan.failedFolders.isEmpty()
    }
}
