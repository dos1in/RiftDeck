package com.riftdeck.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.riftdeck.core.sharing.SaveBaseline
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SharingPreferencesRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun identityIsStableAcrossConcurrentInitializationAndStoreRecreation() = runBlocking {
        var identity = ""
        withRepository { repository ->
            assertEquals(SharingPreferences(), repository.preferences.first())
            val identities = List(12) { async(Dispatchers.IO) { repository.ensureIdentity() } }.awaitAll()
            assertEquals(1, identities.distinct().size)
            identity = identities.first()
            assertEquals(identity, UUID.fromString(identity).toString())
        }
        withRepository { repository ->
            assertEquals(identity, repository.ensureIdentity())
            assertEquals(identity, repository.preferences.first().deviceId)
        }
    }

    @Test fun unicodePeerAndSelectedOptionsRoundTripAcrossRecreation() = runBlocking {
        val device = peer("peer-a").copy(name = "掌机二号 🎮：RiftDeck", host = "fd00::21", port = 49200)
        withRepository { repository ->
            repository.pair(device)
            repository.setEnabled(true)
            repository.setAutoSync(true)
            repository.setFolder(ROMS, saves = false)
            repository.setFolder(SAVES, saves = true)
        }
        withRepository { repository ->
            val preferences = repository.preferences.first()
            assertEquals(listOf(device), preferences.peers)
            assertEquals(ROMS, preferences.romFolder)
            assertEquals(SAVES, preferences.saveFolder)
            assertTrue(preferences.enabled)
            assertTrue(preferences.autoSync)
        }
    }

    @Test fun baselinesAndUnresolvedConflictsPersistForEachPeer() = runBlocking {
        val baseline = mapOf("GBA/游戏.sav" to SaveBaseline(HASH_A, HASH_B), "游戏.state1" to SaveBaseline(HASH_A, HASH_A))
        val conflict = StoredSaveConflict("peer-a", "GBA/游戏.sav", HASH_A, HASH_B)
        withRepository { repository ->
            repository.pair(peer("peer-a")); repository.pair(peer("peer-b"))
            repository.setFolder(SAVES, saves = true)
            repository.recordSync("peer-a", baseline, listOf(conflict))
            repository.recordSync("peer-b", mapOf("other.srm" to SaveBaseline(HASH_B, HASH_B)), emptyList())
        }
        withRepository { repository ->
            val preferences = repository.preferences.first()
            assertEquals(baseline, preferences.baselines["peer-a"])
            assertEquals(mapOf("other.srm" to SaveBaseline(HASH_B, HASH_B)), preferences.baselines["peer-b"])
            assertEquals(listOf(conflict), preferences.conflicts)
        }
    }

    @Test fun changingSaveFolderClearsItsHistoryWhileSameOrRomFolderKeepsIt() = runBlocking {
        withRepository { repository ->
            repository.pair(peer("peer-a"))
            repository.setFolder(SAVES, saves = true)
            val baselines = mapOf("game.sav" to SaveBaseline(HASH_A, HASH_B))
            val conflicts = listOf(StoredSaveConflict("peer-a", "game.sav", HASH_A, HASH_B))
            repository.recordSync("peer-a", baselines, conflicts)
            repository.setFolder(SAVES, saves = true)
            repository.setFolder(ROMS, saves = false)
            assertEquals(baselines, repository.preferences.first().baselines["peer-a"])
            assertEquals(conflicts, repository.preferences.first().conflicts)
            repository.setFolder("content://provider/tree/other-saves", saves = true)
            assertTrue(repository.preferences.first().baselines.isEmpty())
            assertTrue(repository.preferences.first().conflicts.isEmpty())
            assertEquals(listOf(peer("peer-a")), repository.preferences.first().peers)
        }
    }

    @Test fun unpairRemovesOnlyThatPeersSyncStateAndKeepsFolders() = runBlocking {
        withRepository { repository ->
            repository.pair(peer("peer-a")); repository.pair(peer("peer-b"))
            repository.setFolder(SAVES, saves = true); repository.setFolder(ROMS, saves = false)
            val baseline = mapOf("game.sav" to SaveBaseline(HASH_A, HASH_B))
            for (id in listOf("peer-a", "peer-b")) repository.recordSync(id, baseline, listOf(StoredSaveConflict(id, "game.sav", HASH_A, HASH_B)))
            repository.forget("peer-a")
            val preferences = repository.preferences.first()
            assertEquals(listOf(peer("peer-b")), preferences.peers)
            assertEquals(setOf("peer-b"), preferences.baselines.keys)
            assertEquals(listOf("peer-b"), preferences.conflicts.map { it.peerId })
            assertEquals(SAVES, preferences.saveFolder)
            assertEquals(ROMS, preferences.romFolder)
        }
    }

    @Test fun lateAndConcurrentSyncCompletionCannotResurrectForgottenPeer() = runBlocking {
        withRepository { repository ->
            repository.pair(peer("peer-a"))
            val gate = CompletableDeferred<Unit>()
            val updates = List(20) { launch(Dispatchers.IO) {
                gate.await()
                repository.recordSync("peer-a", mapOf("game.sav" to SaveBaseline(HASH_A, HASH_B)), listOf(StoredSaveConflict("peer-a", "game.sav", HASH_A, HASH_B)))
            } }
            repository.forget("peer-a")
            gate.complete(Unit)
            updates.forEach { it.join() }
            val preferences = repository.preferences.first()
            assertTrue(preferences.peers.isEmpty())
            assertTrue(preferences.baselines.isEmpty())
            assertTrue(preferences.conflicts.isEmpty())
        }
    }

    @Test fun lateSyncForThePreviousFolderCannotRestoreClearedHistory() = runBlocking {
        withRepository { repository ->
            repository.pair(peer("peer-a"))
            repository.setFolder(SAVES, saves = true)
            repository.setFolder("content://provider/tree/new-saves", saves = true)
            repository.recordSync("peer-a", mapOf("game.sav" to SaveBaseline(HASH_A, HASH_B)),
                listOf(StoredSaveConflict("peer-a", "game.sav", HASH_A, HASH_B)), expectedFolder = SAVES)
            assertTrue(repository.preferences.first().baselines.isEmpty())
            assertTrue(repository.preferences.first().conflicts.isEmpty())
        }
    }

    @Test fun incomingAcknowledgementMergesPathsAndClearsOnlyResolvedConflicts() = runBlocking {
        withRepository { repository ->
            repository.pair(peer("peer-a"))
            repository.setFolder(SAVES, saves = true)
            val baseline = SaveBaseline(HASH_A, HASH_B)
            repository.recordSync("peer-a", mapOf("game.sav" to baseline, "other.sav" to baseline),
                listOf(StoredSaveConflict("peer-a", "game.sav", HASH_A, HASH_B), StoredSaveConflict("peer-a", "other.sav", HASH_A, HASH_B)))
            repository.acknowledge("peer-a", SAVES, mapOf("game.sav" to SaveBaseline(HASH_B, HASH_B)))
            val preferences = repository.preferences.first()
            assertEquals(mapOf("game.sav" to SaveBaseline(HASH_B, HASH_B), "other.sav" to baseline), preferences.baselines["peer-a"])
            assertEquals(listOf("other.sav"), preferences.conflicts.map { it.path })
            repository.setFolder("content://provider/tree/changed", saves = true)
            repository.acknowledge("peer-a", SAVES, mapOf("game.sav" to baseline))
            assertTrue(repository.preferences.first().baselines.isEmpty())
            assertTrue(repository.preferences.first().conflicts.isEmpty())
        }
    }

    @Test fun endpointRefreshKeepsTrustAndCannotRecreateForgottenPeers() = runBlocking {
        withRepository { repository ->
            val paired = peer("peer-a")
            repository.pair(paired)
            repository.recordSync("peer-a", mapOf("game.sav" to SaveBaseline(HASH_A, HASH_A)), emptyList())
            repository.updateEndpoint("peer-a", "192.168.1.99", 49300)
            assertEquals(paired.copy(host = "192.168.1.99", port = 49300), repository.preferences.first().peers.single())
            assertEquals(SaveBaseline(HASH_A, HASH_A), repository.preferences.first().baselines["peer-a"]?.get("game.sav"))
            repository.forget("peer-a")
            repository.updateEndpoint("peer-a", "192.168.1.100", 49301)
            assertTrue(repository.preferences.first().peers.isEmpty())
        }
    }

    @Test fun lateOldKeySyncCannotPopulateStateAfterTheSameDeviceIsPairedAgain() = runBlocking {
        withRepository { repository ->
            val old = peer("peer-a")
            val renewed = old.copy(key = Base64.getEncoder().encodeToString(ByteArray(32) { 9 }))
            repository.setFolder(SAVES, saves = true)
            repository.pair(old)
            val gate = CompletableDeferred<Unit>()
            val oldCompletion = async(Dispatchers.IO) {
                gate.await()
                repository.recordSync("peer-a", mapOf("game.sav" to SaveBaseline(HASH_A, HASH_B)),
                    listOf(StoredSaveConflict("peer-a", "game.sav", HASH_A, HASH_B)), expectedFolder = SAVES, expectedKey = old.key)
            }
            val oldAcknowledgement = async(Dispatchers.IO) {
                gate.await()
                repository.acknowledge("peer-a", SAVES, mapOf("game.sav" to SaveBaseline(HASH_A, HASH_B)), expectedKey = old.key)
            }
            repository.forget(old.id)
            repository.pair(renewed)
            gate.complete(Unit)
            awaitAll(oldCompletion, oldAcknowledgement)
            val preferences = repository.preferences.first()
            assertEquals(listOf(renewed), preferences.peers)
            assertTrue(preferences.baselines.isEmpty())
            assertTrue(preferences.conflicts.isEmpty())
            repository.acknowledge("peer-a", SAVES, mapOf("game.sav" to SaveBaseline(HASH_B, HASH_B)), expectedKey = renewed.key)
            assertEquals(SaveBaseline(HASH_B, HASH_B), repository.preferences.first().baselines["peer-a"]?.get("game.sav"))
        }
    }

    @Test fun pairingCannotReplaceAnExistingTrustKeyUntilTheDeviceIsForgotten() = runBlocking {
        withRepository { repository ->
            val old = peer("peer-a")
            val renewed = old.copy(key = Base64.getEncoder().encodeToString(ByteArray(32) { 9 }))
            repository.pair(old)
            try { repository.pair(renewed); fail("Existing pairing key was replaced") } catch (_: IOException) { }
            assertEquals(listOf(old), repository.preferences.first().peers)
            repository.forget(old.id)
            repository.pair(renewed)
            assertEquals(listOf(renewed), repository.preferences.first().peers)
        }
    }

    @Test fun malformedRowsDoNotHideValidPairsOrCrashPreferences() = runBlocking {
        withRepositoryAndStore { repository, store ->
            repository.pair(peer("peer-a"))
            repository.recordSync("peer-a", mapOf("good.sav" to SaveBaseline(HASH_A, HASH_A)), emptyList())
            store.edit { preferences ->
                for (key in listOf("paired_devices", "save_baselines", "save_conflicts")) {
                    val preferenceKey = stringSetPreferencesKey(key)
                    preferences[preferenceKey] = preferences[preferenceKey].orEmpty() + setOf("not-base64!", "AAAA", "A".repeat(20_000))
                }
                val peerRows = stringSetPreferencesKey("paired_devices")
                preferences[peerRows] = preferences[peerRows].orEmpty() + encode {
                    writeUTF("corrupt-peer"); writeUTF("Broken key"); writeUTF("invalid-base64"); writeUTF("192.168.1.2"); writeInt(12345)
                }
                val baselines = stringSetPreferencesKey("save_baselines")
                preferences[baselines] = preferences[baselines].orEmpty() + encode {
                    writeUTF("peer-a"); writeUTF("bad.sav"); writeUTF("bad hash"); writeUTF(HASH_A)
                }
            }
            val preferences = repository.preferences.first()
            assertEquals(listOf(peer("peer-a")), preferences.peers)
            assertEquals(mapOf("good.sav" to SaveBaseline(HASH_A, HASH_A)), preferences.baselines["peer-a"])
            assertTrue(preferences.conflicts.isEmpty())
            assertFalse(preferences.baselines.values.any { "bad.sav" in it })
        }
    }

    private suspend fun withRepository(block: suspend (SharingPreferencesRepository) -> Unit) {
        withRepositoryAndStore { repository, _ -> block(repository) }
    }

    private suspend fun withRepositoryAndStore(block: suspend (SharingPreferencesRepository, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) -> Unit) {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { folder.root.resolve("sharing.preferences_pb") }
        try { block(SharingPreferencesRepository(store), store) } finally { job.cancelAndJoin() }
    }

    private fun peer(id: String) = PairedDevice(id, "Handheld $id", Base64.getEncoder().encodeToString(ByteArray(32) { 7 }), "192.168.1.21", 49152)

    private fun encode(block: DataOutputStream.() -> Unit): String = Base64.getEncoder().encodeToString(
        ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { it.block() } }.toByteArray())

    private companion object {
        const val HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val HASH_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        const val SAVES = "content://provider/tree/saves"
        const val ROMS = "content://provider/tree/roms"
    }
}
