package com.riftdeck.feature.settings

import com.riftdeck.data.scanner.ScanState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanCompletionFeedbackTest {
    @Test fun progressUpdatesDoNotAnimateAndSuccessfulResultPlaysOnce() {
        val feedback = ScanCompletionFeedback(ScanState())
        val running = ScanState(running = true, runId = 1)
        assertFalse(feedback.consume(running))
        assertFalse(feedback.consume(running.copy(discovered = 50)))
        val completed = running.copy(running = false, discovered = 75, completed = true)
        assertTrue(feedback.consume(completed))
        assertFalse(feedback.consume(completed))
        assertFalse(feedback.consume(completed.copy(discovered = 76)))
    }

    @Test fun fastRescansWithIdenticalCountsEachAnimateEvenWhenProgressWasConflated() {
        val first = ScanState(completed = true, discovered = 2, runId = 1)
        val feedback = ScanCompletionFeedback(ScanState())
        assertTrue(feedback.consume(first))
        assertTrue(feedback.consume(first.copy(runId = 2)))
        assertFalse(feedback.consume(first.copy(runId = 2)))
    }

    @Test fun cancelFailureAndPartialSuccessDoNotAnimate() {
        val feedback = ScanCompletionFeedback(ScanState())
        assertFalse(feedback.consume(ScanState(runId = 1)))
        assertFalse(feedback.consume(ScanState(runId = 2, failedFolders = setOf("unavailable"))))
        assertFalse(feedback.consume(ScanState(runId = 3, completed = true, discovered = 5, failedFolders = setOf("unavailable"))))
        assertTrue(feedback.consume(ScanState(runId = 4, completed = true)))
    }

    @Test fun returningToCompletedResultDoesNotReplay() {
        val completed = ScanState(runId = 2, completed = true)
        val feedback = ScanCompletionFeedback(completed)
        assertFalse(feedback.consume(completed))
        assertTrue(feedback.consume(completed.copy(runId = 3)))
    }

    @Test fun enteringDuringScanCanObserveItsCompletion() {
        val running = ScanState(runId = 4, running = true)
        val feedback = ScanCompletionFeedback(running)
        assertFalse(feedback.consume(running))
        assertTrue(feedback.consume(running.copy(running = false, completed = true)))
    }

    @Test fun leavingDuringScanSuppressesItsLaterCompletion() {
        val running = ScanState(runId = 4, running = true)
        val feedback = ScanCompletionFeedback(running)
        feedback.skip(running)
        assertFalse(feedback.consume(running.copy(running = false, completed = true)))
        assertTrue(feedback.consume(ScanState(runId = 5, completed = true)))
    }
}
