package com.riftdeck.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppModalTest {
    @Test
    fun externalErrorInterruptsEveryPendingModal() {
        assertEquals(AppModal.ExternalError, activeAppModal(
            externalError = true, preparingGame = true, launchError = true,
            removeFolder = true, search = true, update = true,
        ))
    }

    @Test
    fun gamePreparationRemainsCancelableBeforeOtherRequests() {
        assertEquals(AppModal.PreparingGame, activeAppModal(
            preparingGame = true, launchError = true, removeFolder = true, search = true, update = true,
        ))
        assertEquals(AppModal.LaunchError, activeAppModal(
            launchError = true, removeFolder = true, search = true, update = true,
        ))
    }

    @Test
    fun deferredRequestsResumeWhenHigherPriorityModalCloses() {
        assertEquals(AppModal.RemoveFolder, activeAppModal(removeFolder = true, search = true, update = true))
        assertEquals(AppModal.Search, activeAppModal(search = true, update = true))
        assertEquals(AppModal.Update, activeAppModal(update = true))
        assertNull(activeAppModal())
    }
}
