package com.riftdeck.data.sharing

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.riftdeck.app.LauncherApplication
import com.riftdeck.core.sharing.LanClient
import com.riftdeck.core.sharing.LanPeer
import com.riftdeck.core.sharing.SaveAccessGate
import com.riftdeck.core.sharing.SaveBaseline
import com.riftdeck.core.sharing.ShareKind
import com.riftdeck.core.sharing.conflictSavePath
import com.riftdeck.data.repository.SharingPreferencesRepository
import com.riftdeck.data.repository.SharingRepository
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Two independent repository sessions use real encrypted loopback sockets and SAF documents. */
class LanSharingRepositoryIntegrationTest {
    private val application = ApplicationProvider.getApplicationContext<LauncherApplication>()
    private val resolver = application.contentResolver
    private val sessions = mutableListOf<Device>()
    private lateinit var a: Device
    private lateinit var b: Device
    private val initial = "original local diagnostic save".toByteArray()
    private val changed = "new progress in diagnostic save".toByteArray()

    @Before fun prepare() = runBlocking {
        control("reset")
        a = createDevice("A")
        b = createDevice("B")
    }

    @After fun cleanup() = runBlocking {
        sessions.forEach { device ->
            runCatching { device.repository.stop() }
            device.job.cancelAndJoin()
            device.directory.deleteRecursively()
        }
        sessions.clear()
        control("reset")
        Unit
    }

    @Test fun equalBaselineIsAcknowledgedBothWaysAndReverseSyncHasNoFalseConflict() = runBlocking {
        write("A/saves/Game.sav", initial); write("B/saves/Game.sav", initial)
        pair()
        a.repository.sync(b.id)
        assertBaselineBoth("Game.sav", digest(initial))

        write("A/saves/Game.sav", changed)
        a.repository.sync(b.id)
        assertArrayEquals(changed, read("B/saves/Game.sav"))
        assertBaselineBoth("Game.sav", digest(changed))

        val reverse = "progress from the other handheld".toByteArray()
        write("B/saves/Game.sav", reverse)
        b.repository.sync(a.id)
        assertArrayEquals(reverse, read("A/saves/Game.sav"))
        assertBaselineBoth("Game.sav", digest(reverse))
        assertTrue(a.preferences.preferences.first().conflicts.isEmpty())
        assertTrue(b.preferences.preferences.first().conflicts.isEmpty())
    }

    @Test fun firstPairConflictsRetainBothVersionsAndBothResolutionChoicesConverge() = runBlocking {
        write("A/saves/Game.sav", initial); write("B/saves/Game.sav", changed)
        pair()
        a.repository.sync(b.id)
        assertArrayEquals(initial, read("A/saves/Game.sav"))
        assertArrayEquals(changed, read("B/saves/Game.sav"))
        assertArrayEquals(changed, read("A/saves/${conflictSavePath("Game.sav", digest(changed))}"))
        assertArrayEquals(initial, read("B/saves/${conflictSavePath("Game.sav", digest(initial))}"))
        assertEquals(1, a.preferences.preferences.first().conflicts.size)
        assertEquals(1, b.preferences.preferences.first().conflicts.size)

        val firstConflict = a.preferences.preferences.first().conflicts.single()
        a.repository.resolveConflict(SharingRepository.conflictId(firstConflict), useRemote = true)
        assertArrayEquals(changed, read("A/saves/Game.sav"))
        assertBaselineBoth("Game.sav", digest(changed))
        assertTrue(a.preferences.preferences.first().conflicts.isEmpty())
        assertTrue(b.preferences.preferences.first().conflicts.isEmpty())

        val ours = "second local version".toByteArray()
        val theirs = "second remote version".toByteArray()
        write("A/saves/Game.sav", ours); write("B/saves/Game.sav", theirs)
        a.repository.sync(b.id)
        val nextConflict = a.preferences.preferences.first().conflicts.single()
        a.repository.resolveConflict(SharingRepository.conflictId(nextConflict), useRemote = false)
        assertArrayEquals(ours, read("A/saves/Game.sav"))
        assertArrayEquals(ours, read("B/saves/Game.sav"))
        assertBaselineBoth("Game.sav", digest(ours))
        assertArrayEquals(theirs, read("B/saves/${conflictSavePath("Game.sav", digest(theirs))}"))
        assertArrayEquals(initial, read("A/saves/${conflictSavePath("Game.sav", digest(initial))}"))
        assertTrue(a.preferences.preferences.first().conflicts.isEmpty())
        assertTrue(b.preferences.preferences.first().conflicts.isEmpty())
    }

    @Test fun changedConflictCannotBeResolvedUsingAStaleChoice() = runBlocking {
        write("A/saves/Game.sav", initial); write("B/saves/Game.sav", changed)
        pair()
        a.repository.sync(b.id)
        val conflict = a.preferences.preferences.first().conflicts.single()
        val latest = "new remote progress after conflict appeared".toByteArray()
        write("B/saves/Game.sav", latest)
        fails { a.repository.resolveConflict(SharingRepository.conflictId(conflict), useRemote = true) }
        assertArrayEquals(initial, read("A/saves/Game.sav"))
        assertArrayEquals(latest, read("B/saves/Game.sav"))
        assertEquals(conflict, a.preferences.preferences.first().conflicts.single())
    }

    @Test fun disconnectedPeerDoesNotAdvanceBaselineAndReconnectRetriesTheSameRevision() = runBlocking {
        write("A/saves/Game.sav", initial); write("B/saves/Game.sav", initial)
        pair()
        a.repository.sync(b.id)
        write("A/saves/Game.sav", changed)
        b.repository.stop(b.owner)
        fails { a.repository.sync(b.id) }
        assertBaselineBoth("Game.sav", digest(initial))
        assertArrayEquals(initial, read("B/saves/Game.sav"))

        b.repository.start(b.owner)
        updateEndpoint(a, b)
        a.repository.sync(b.id)
        assertArrayEquals(changed, read("B/saves/Game.sav"))
        assertBaselineBoth("Game.sav", digest(changed))
    }

    @Test fun unsupportedFilesStayLocalAndRomsRequireExplicitDownload() = runBlocking {
        val rom = "original diagnostic ROM payload".toByteArray()
        write("B/roms/Diagnostic.gba", rom)
        write("B/roms/Private.txt", "private unrelated data".toByteArray())
        write("B/saves/Game.sav", initial)
        write("B/saves/Private.txt", "private unrelated data".toByteArray())
        pair()
        a.repository.refreshRoms(b.id)
        assertEquals(listOf("Diagnostic.gba"), a.repository.state.value.catalogs[b.id]!!.map { it.path })
        a.repository.sync(b.id)
        assertArrayEquals(initial, read("A/saves/Game.sav"))
        assertFalse("A/roms/Diagnostic.gba" in files())
        assertFalse("A/saves/Private.txt" in files())
        a.repository.downloadRom(b.id, "Diagnostic.gba")
        assertArrayEquals(rom, read("A/roms/Diagnostic.gba"))
        assertFalse("A/roms/Private.txt" in files())
    }

    @Test fun emulatorAccessGateRejectsIncomingSaveCommitUntilTheEmulatorReturns() = runBlocking {
        write("A/saves/Game.sav", initial); write("B/saves/Game.sav", initial)
        pair()
        a.repository.sync(b.id)
        write("A/saves/Game.sav", changed)
        b.saveAccess.pauseForEmulator()
        try {
            fails { a.repository.sync(b.id) }
            assertArrayEquals(initial, read("B/saves/Game.sav"))
            assertBaselineBoth("Game.sav", digest(initial))
        } finally { b.saveAccess.resumeAfterEmulator() }
        a.repository.sync(b.id)
        assertArrayEquals(changed, read("B/saves/Game.sav"))
        assertBaselineBoth("Game.sav", digest(changed))
    }

    @Test fun consumedPairingCodeCannotPairAnotherDeviceAndUnpairRevokesIncomingAccess() = runBlocking {
        b.repository.setPairing(true)
        val code = requireNotNull(b.repository.state.value.pairingCode)
        a.repository.connectAddress("127.0.0.1:${port(b)}", code)
        updateEndpoint(b, a)
        assertNull(b.repository.state.value.pairingCode)
        LanClient("unpaired-diagnostic-device", "Unpaired test device").use { client ->
            try { client.pair(peer(b), code); fail("Consumed pairing code accepted") } catch (_: IOException) { }
        }
        val key = Base64.getDecoder().decode(b.preferences.preferences.first().peers.single { it.id == a.id }.key)
        a.repository.forget(b.id)
        LanClient(b.id, "Paired test device").use { client ->
            try { client.manifest(peer(a), key, ShareKind.Rom); fail("Unpaired device retained access") } catch (_: IOException) { }
        }
        assertTrue(a.preferences.preferences.first().peers.isEmpty())
        val stoppedPeer = peer(a)
        a.repository.stop(a.owner)
        LanClient(b.id, "Stopped test peer").use { client ->
            try { client.manifest(stoppedPeer, key, ShareKind.Rom); fail("Stopped sharing accepted access") } catch (_: IOException) { }
        }
    }

    private suspend fun pair() {
        b.repository.setPairing(true)
        a.repository.connectAddress("127.0.0.1:${port(b)}", requireNotNull(b.repository.state.value.pairingCode))
        updateEndpoint(b, a)
    }

    private suspend fun updateEndpoint(device: Device, remote: Device) {
        val remotePort = port(remote)
        device.preferences.updateEndpoint(remote.id, "127.0.0.1", remotePort)
        withTimeout(5000) { device.repository.state.first { state -> state.preferences.peers.any { it.id == remote.id && it.port == remotePort } } }
    }

    private suspend fun assertBaselineBoth(path: String, hash: String) {
        assertEquals(SaveBaseline(hash, hash), a.preferences.preferences.first().baselines[b.id]?.get(path))
        assertEquals(SaveBaseline(hash, hash), b.preferences.preferences.first().baselines[a.id]?.get(path))
    }

    private suspend fun createDevice(label: String): Device {
        for (path in listOf("$label/saves", "$label/roms")) {
            control("mkdir", Bundle().apply { putString("path", path) })
            control("grant", Bundle().apply { putString("path", path) })
        }
        val directory = File(application.cacheDir, "lan-repository-test-$label-${UUID.randomUUID()}").also { it.mkdirs() }
        val wrapped = object : ContextWrapper(application) {
            override fun getFilesDir(): File = File(directory, "files").also { it.mkdirs() }
            override fun getCacheDir(): File = File(directory, "cache").also { it.mkdirs() }
            override fun getApplicationContext(): Context = this
        }
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        val preferences = SharingPreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) { File(directory, "device.preferences_pb") })
        val id = preferences.ensureIdentity()
        preferences.setFolder(tree("$label/saves").toString(), saves = true)
        preferences.setFolder(tree("$label/roms").toString(), saves = false)
        preferences.setAutoSync(true)
        preferences.setEnabled(true)
        val saveAccess = SaveAccessGate()
        val repository = SharingRepository(wrapped, preferences, application.libraryRepository, application.database.games(), application.emulationRepository, scope, saveAccess)
        val device = Device(repository, preferences, saveAccess, job, directory, id, "test-session-$label")
        sessions.add(device)
        repository.start(device.owner)
        withTimeout(5000) { repository.state.first { it.running && it.preferences.deviceId == id && it.preferences.saveFolder != null } }
        return device
    }

    private fun port(device: Device): Int = device.repository.state.value.endpoint.substringBefore(" / ").substringAfterLast(':').toInt()
    private fun peer(device: Device) = LanPeer(device.id, "Test device", "127.0.0.1", port(device))
    private fun tree(path: String) = DocumentsContract.buildTreeDocumentUri(SharingTestDocumentsProvider.AUTHORITY, "${SharingTestDocumentsProvider.ROOT_ID}/$path")
    private fun write(path: String, bytes: ByteArray) = control("write", Bundle().apply { putString("path", path); putByteArray("bytes", bytes) })
    private fun read(path: String): ByteArray = resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(
        SharingTestDocumentsProvider.treeUri(), "${SharingTestDocumentsProvider.ROOT_ID}/$path"))!!.use { it.readBytes() }
    private fun files() = control("stats")!!.getStringArrayList("files")!!
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun control(method: String, extras: Bundle? = null): Bundle? = resolver.call(Uri.parse("content://com.riftdeck.tests.sharing.control"), method, null, extras)
    private suspend fun fails(block: suspend () -> Unit) {
        try { block(); fail("Expected transfer failure") } catch (_: IOException) { }
    }

    private data class Device(
        val repository: SharingRepository,
        val preferences: SharingPreferencesRepository,
        val saveAccess: SaveAccessGate,
        val job: kotlinx.coroutines.CompletableJob,
        val directory: File,
        val id: String,
        val owner: String,
    )
}
