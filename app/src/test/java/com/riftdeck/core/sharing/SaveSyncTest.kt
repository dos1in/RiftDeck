package com.riftdeck.core.sharing

import org.junit.Assert.*
import org.junit.Test

class SaveSyncTest {
    private val old = "1".repeat(64)
    private val left = "2".repeat(64)
    private val right = "3".repeat(64)
    private val next = "4".repeat(64)
    private fun save(hash: String, path: String = "Game.sav", modifiedAt: Long = 0) = SaveRevision(path, hash, 128, modifiedAt)
    private fun baseline(local: String = old, remote: String = local) = mapOf("Game.sav" to SaveBaseline(local, remote))

    @Test fun equalInitialSavesSettleWithoutTransfer() {
        val plan = planSaveSync(listOf(save(old)), listOf(save(old, modifiedAt = 999)), emptyMap())
        assertTrue(plan.actions.isEmpty())
        assertEquals(baseline(), plan.baselines)
    }

    @Test fun localOnlySaveIsPushedWithoutAdvancingBaseline() {
        val plan = planSaveSync(listOf(save(left)), emptyList(), emptyMap())
        assertEquals(listOf(SaveSyncAction.Push(save(left), null)), plan.actions)
        assertTrue(plan.baselines.isEmpty())
        assertNull(acknowledgedSaveBaseline(plan.actions.single(), left, null))
        assertEquals(SaveBaseline(left, left), acknowledgedSaveBaseline(plan.actions.single(), left, left))
    }

    @Test fun remoteOnlySaveIsPulledWithoutAdvancingBaseline() {
        val plan = planSaveSync(emptyList(), listOf(save(right)), emptyMap())
        assertEquals(listOf(SaveSyncAction.Pull(save(right), null)), plan.actions)
        assertTrue(plan.baselines.isEmpty())
        assertNull(acknowledgedSaveBaseline(plan.actions.single(), null, right))
        assertEquals(SaveBaseline(right, right), acknowledgedSaveBaseline(plan.actions.single(), right, right))
    }

    @Test fun unrelatedInitialSavesPreserveBothVersionsRegardlessOfClock() {
        val ours = save(left, modifiedAt = Long.MAX_VALUE)
        val theirs = save(right, modifiedAt = 1)
        assertEquals(listOf(SaveSyncAction.Conflict(ours, theirs)), planSaveSync(listOf(ours), listOf(theirs), emptyMap()).actions)
    }

    @Test fun localChangePropagatesAgainstAnUnchangedCommonBaseline() {
        val plan = planSaveSync(listOf(save(left)), listOf(save(old)), baseline())
        assertEquals(listOf(SaveSyncAction.Push(save(left), old)), plan.actions)
        assertEquals(baseline(), plan.baselines)
    }

    @Test fun remoteChangePropagatesAgainstAnUnchangedCommonBaseline() {
        val plan = planSaveSync(listOf(save(old)), listOf(save(right)), baseline())
        assertEquals(listOf(SaveSyncAction.Pull(save(right), old)), plan.actions)
    }

    @Test fun bothChangesCreateConflictInsteadOfSelectingLatestClock() {
        assertEquals(listOf(SaveSyncAction.Conflict(save(left), save(right))),
            planSaveSync(listOf(save(left)), listOf(save(right)), baseline()).actions)
    }

    @Test fun sameContentChangedOnBothDevicesSettles() {
        val plan = planSaveSync(listOf(save(next)), listOf(save(next)), baseline())
        assertTrue(plan.actions.isEmpty())
        assertEquals(baseline(next), plan.baselines)
    }

    @Test fun deletionNeverRemovesTheOtherDevicesSave() {
        assertEquals(listOf(SaveSyncAction.Pull(save(old), null)), planSaveSync(emptyList(), listOf(save(old)), baseline()).actions)
        assertEquals(listOf(SaveSyncAction.Push(save(old), null)), planSaveSync(listOf(save(old)), emptyList(), baseline()).actions)
    }

    @Test fun deletionDuringRemoteChangeRestoresTheAvailableVersion() {
        assertEquals(listOf(SaveSyncAction.Pull(save(right), null)), planSaveSync(emptyList(), listOf(save(right)), baseline()).actions)
    }

    @Test fun bothMissingSavesDropOnlyTheirBaseline() {
        val plan = planSaveSync(emptyList(), emptyList(), baseline())
        assertTrue(plan.actions.isEmpty())
        assertTrue(plan.baselines.isEmpty())
    }

    @Test fun acknowledgedConflictKeepsAnUnresolvedPairWithoutRepeating() {
        val action = SaveSyncAction.Conflict(save(left), save(right))
        assertNull(acknowledgedSaveBaseline(action, left, right))
        assertNull(acknowledgedSaveBaseline(action, left, right, right, old))
        val acknowledged = requireNotNull(acknowledgedSaveBaseline(action, left, right, right, left))
        assertEquals(SaveBaseline(left, right), acknowledged)
        val nextPlan = planSaveSync(listOf(save(left)), listOf(save(right)), mapOf(action.path to acknowledged))
        assertTrue(nextPlan.actions.isEmpty())
        assertEquals(mapOf(action.path to acknowledged), nextPlan.baselines)
    }

    @Test fun changingOneUnresolvedOriginalPreservesTheOtherVersionAgain() {
        assertEquals(listOf(SaveSyncAction.Conflict(save(next), save(right))),
            planSaveSync(listOf(save(next)), listOf(save(right)), baseline(left, right)).actions)
        assertEquals(listOf(SaveSyncAction.Conflict(save(left), save(next))),
            planSaveSync(listOf(save(left)), listOf(save(next)), baseline(left, right)).actions)
    }

    @Test fun explicitResolutionToEqualContentClearsDivergentBaseline() {
        val plan = planSaveSync(listOf(save(right)), listOf(save(right)), baseline(left, right))
        assertTrue(plan.actions.isEmpty())
        assertEquals(baseline(right), plan.baselines)
    }

    @Test fun aSaveModifiedDuringTransferCannotBeAcknowledged() {
        val pull = SaveSyncAction.Pull(save(right), old)
        assertNull(acknowledgedSaveBaseline(pull, right, next))
        val push = SaveSyncAction.Push(save(left), old)
        assertNull(acknowledgedSaveBaseline(push, next, left))
        val conflict = SaveSyncAction.Conflict(save(left), save(right))
        assertNull(acknowledgedSaveBaseline(conflict, next, right, right, left))
    }

    @Test fun conflictNamesAreStableAcrossRetriesAndPeers() {
        val action = SaveSyncAction.Conflict(save(left, "gba/Game.sav"), save(right, "gba/Game.sav"))
        assertEquals("gba/Game.riftdeck-conflict-${right.take(12)}.sav", action.localCopyPath)
        assertEquals("gba/Game.riftdeck-conflict-${left.take(12)}.sav", action.remoteCopyPath)
        val otherPeer = SaveSyncAction.Conflict(save(next, "gba/Game.sav"), save(right, "gba/Game.sav"))
        assertEquals(action.localCopyPath, otherPeer.localCopyPath)
        assertEquals(action.localCopyPath, conflictSavePath("gba/Game.sav", right))
    }

    @Test fun conflictCopiesAreIgnoredByCanonicalPlanner() {
        val copy = save(right, conflictSavePath("Game.sav", right))
        val plan = planSaveSync(listOf(save(left), copy), listOf(save(right)), baseline(left, right))
        assertTrue(plan.actions.isEmpty())
        assertEquals(baseline(left, right), plan.baselines)
    }

    @Test fun automaticStateExtensionIsPreservedInConflictCopy() {
        assertEquals("Game.riftdeck-conflict-${left.take(12)}.state.auto", conflictSavePath("Game.state.auto", left))
    }

    @Test fun longNamesLeaveRoomForConflictSuffix() {
        val path = "a".repeat(251) + ".sav"
        val conflict = conflictSavePath(path, right)
        assertEquals(255, conflict.length)
        assertTrue(isSafeSharingPath(conflict))
        assertTrue(conflict.endsWith(".sav"))
    }

    @Test fun syncOrderIsDeterministicAndIndependentOfDirectoryEnumeration() {
        val local = listOf(save(left, "z.sav"), save(right, "a.sav"))
        assertEquals(listOf("a.sav", "z.sav"), planSaveSync(local, emptyList(), emptyMap()).actions.map { it.path })
    }

    @Test fun duplicateCanonicalPathsCannotBeSilentlyDropped() {
        try {
            planSaveSync(listOf(save(left), save(right)), emptyList(), emptyMap())
            fail("Expected ambiguous snapshot rejection")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun safePathsPermitNestedUnicodeNamesButRejectTraversalAndAmbiguousNames() {
        assertTrue(isSafeSharingPath("GBA/中文存档.sav"))
        listOf("", "/Game.sav", "Game.sav/", "../Game.sav", "gba/../Game.sav", "gba//Game.sav", "gba/./Game.sav",
            "gba\\Game.sav", "C:Game.sav", "Game\u0000.sav", "Game\n.sav", " Game.sav", "Game.sav ", "dir./Game.sav",
            "a".repeat(256) + ".sav", (1..17).joinToString("/") { "a" }, "a".repeat(1025)).forEach {
            assertFalse(it, isSafeSharingPath(it))
        }
    }

    @Test fun supportedSaveExtensionsExcludeRomAndTemporaryFiles() {
        listOf("Game.sav", "Game.SRM", "core/Game.state", "Game.state0", "Game.state12", "Game.state.auto", "Game.ss0",
            "Game.ss10", "Game.st3", "Game.rtc", "Game.eep", "Game.dsv").forEach { assertTrue(it, isSupportedSavePath(it)) }
        listOf("Game.gba", "Game.zip", "Game.sav.tmp", "Game.sav.bak", "Game.statex", "tmp/Game.sav", "core/.cache/Game.sav",
            "Game.riftdeck-conflict-123456789abc.sav").forEach { assertFalse(it, isSupportedSavePath(it)) }
    }

    @Test fun deeplyNestedPathsReserveSpaceForPreservedConflicts() {
        val parent = List(4) { "d".repeat(235) }.joinToString("/")
        val supported = "$parent/Game.sav"
        assertTrue(isSupportedSavePath(supported))
        assertTrue(isSafeSharingPath(conflictSavePath(supported, right)))
        val tooLongForBackup = "$parent/${"n".repeat(65)}.sav"
        assertTrue(isSafeSharingPath(tooLongForBackup))
        assertFalse(isSupportedSavePath(tooLongForBackup))
        assertTrue(planSaveSync(listOf(save(left, tooLongForBackup)), emptyList(), emptyMap()).actions.isEmpty())
    }

    @Test fun invalidHashesAndOversizedSavesCannotEnterPlan() {
        for (revision in listOf<() -> SaveRevision>({ save("not a hash") }, { save("A".repeat(64)) },
            { SaveRevision("Game.sav", old, MAX_SHARED_SAVE_SIZE + 1) }, { SaveRevision("Game.sav", old, -1) })) {
            try { revision(); fail("Expected invalid revision rejection") } catch (_: IllegalArgumentException) { }
        }
        assertEquals(MAX_SHARED_SAVE_SIZE, SaveRevision("Game.state", old, MAX_SHARED_SAVE_SIZE).size)
    }
}
