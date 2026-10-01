package com.riftdeck.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.riftdeck.core.sharing.*
import com.riftdeck.data.database.GameDao
import com.riftdeck.data.sharing.LanSharingService
import com.riftdeck.data.sharing.SafSharingStore
import java.io.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

private class LanOperationToken(val generation: Long) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<LanOperationToken>
}

enum class LanStatus { Idle, Starting, Ready, Pairing, Paired, Transferring, Syncing, Synced, Conflict, Error, FolderUnavailable, PeerUnavailable }
class SharingSessionChangedException : IOException("Sharing session changed")
data class LanSharingState(
    val preferences: SharingPreferences = SharingPreferences(),
    val running: Boolean = false,
    val deviceName: String,
    val endpoint: String = "",
    val pairingCode: String? = null,
    val nearby: List<LanPeer> = emptyList(),
    val catalogs: Map<String, List<SharedFile>> = emptyMap(),
    val busy: Boolean = false,
    val status: LanStatus = LanStatus.Idle,
    val progressBytes: Long = 0,
    val totalBytes: Long = 0,
    val lastSyncMillis: Long? = null,
)

/** Owns the sharing session, not the screen, so sync continues while an emulator is foreground. */
class SharingRepository(
    private val context: Context,
    private val preferences: SharingPreferencesRepository,
    private val library: LibraryRepository,
    private val dao: GameDao,
    private val emulation: EmulationRepository,
    private val scope: CoroutineScope,
    private val saveAccess: SaveAccessGate,
) {
    private val name = Build.MODEL.take(64).ifBlank { "RiftDeck" }
    private val mutableState = MutableStateFlow(LanSharingState(deviceName = name))
    val state = mutableState.asStateFlow()
    private val store = SafSharingStore(context.contentResolver, File(context.cacheDir, "lan-incoming"), File(context.filesDir, "sharing-recovery"))
    private val operations = Mutex()
    private val lifecycle = Mutex()
    private val enabledChanges = Mutex()
    private val pairingLock = Any()
    private val permissionGeneration = AtomicLong()
    private var serviceOwner: String? = null
    private var pairingExpiry = 0L
    private var pairingJob: Job? = null
    private var autoJob: Job? = null
    private var discoveryJob: Job? = null
    private var discovery: NsdPeerDiscovery? = null
    private var server: LanServer? = null
    private var client: LanClient? = null

    init {
        scope.launch {
            try { preferences.ensureIdentity(); preferences.preferences.collect { value -> mutableState.update { it.copy(preferences = value) } } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(status = LanStatus.Error) } }
        }
    }

    suspend fun restoreIfEnabled() {
        if (preferences.preferences.first().enabled) setEnabled(true)
    }
    suspend fun setEnabled(enabled: Boolean): Unit = enabledChanges.withLock {
        if (!enabled) {
            permissionGeneration.incrementAndGet()
            preferences.setEnabled(false)
            client?.cancelAll()
            context.stopService(Intent(context, LanSharingService::class.java))
            stop()
        } else if (!state.value.running) {
            preferences.setEnabled(true)
            mutableState.update { it.copy(status = LanStatus.Starting, busy = true) }
            try { ContextCompat.startForegroundService(context, Intent(context, LanSharingService::class.java)) }
            catch (_: RuntimeException) { preferences.setEnabled(false); mutableState.update { it.copy(status = LanStatus.Error, busy = false) } }
        }
    }

    suspend fun start(owner: String) = withContext(Dispatchers.IO) {
        lifecycle.withLock {
            if (!preferences.preferences.first().enabled) return@withLock
            serviceOwner = owner
            if (server != null) return@withLock
            permissionGeneration.incrementAndGet()
            try {
                val id = preferences.ensureIdentity()
                val recoveryIssue = try {
                    if (gameRunning()) saveAccess.pauseForEmulator() else saveAccess.write { store.recoverPending() }
                    false
                } catch (_: IOException) { true } catch (_: SecurityException) { true }
                store.clearStaging()
                client = LanClient(id, name)
                server = LanServer(id, name, handler).also { it.start() }
                discovery = NsdPeerDiscovery(context, id, name).also { it.start(requireNotNull(server).port) }
                discoveryJob = scope.launch {
                    requireNotNull(discovery).peers.collect { peers ->
                        mutableState.update { it.copy(nearby = peers) }
                        for (peer in peers) {
                            val stored = state.value.preferences.peers.firstOrNull { it.id == peer.id }
                            if (stored != null && (stored.host != peer.host || stored.port != peer.port)) {
                                try { preferences.updateEndpoint(peer.id, peer.host, peer.port) }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: IOException) { mutableState.update { it.copy(status = LanStatus.Error) } }
                            }
                        }
                    }
                }
                val endpoints = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                    .filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress && !it.isLoopbackAddress }
                    .map { "${it.hostAddress}:${requireNotNull(server).port}" }.distinct().joinToString(" / ")
                mutableState.update { it.copy(running = true, busy = false, endpoint = endpoints, status = if (recoveryIssue) LanStatus.FolderUnavailable else LanStatus.Ready) }
                autoJob = scope.launch {
                    while (isActive) {
                        delay(15000)
                        if (state.value.running && state.value.preferences.autoSync && !state.value.busy) {
                            try { sync(null, automatic = true) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { /* Offline peers are retried on the next interval. */ }
                        }
                    }
                }
            } catch (error: Exception) {
                client?.close(); client = null; server?.close(); server = null
                discovery?.stop(); discovery = null
                preferences.setEnabled(false)
                mutableState.update { it.copy(running = false, busy = false, status = LanStatus.Error) }
                throw error
            }
        }
    }

    suspend fun stop(owner: String? = null) = withContext(Dispatchers.IO) {
        lifecycle.withLock {
            if (owner != null && serviceOwner != owner) return@withLock
            serviceOwner = null
            permissionGeneration.incrementAndGet()
            pairingJob?.cancel(); pairingJob = null; pairingExpiry = 0
            autoJob?.cancel(); autoJob = null
            client?.close(); client = null
            server?.close(); server = null
            discoveryJob?.cancel(); discoveryJob = null
            discovery?.stop(); discovery = null
            mutableState.update { it.copy(running = false, pairingCode = null, nearby = emptyList(), catalogs = emptyMap(), busy = false, endpoint = "", status = LanStatus.Idle, progressBytes = 0, totalBytes = 0) }
        }
    }

    fun setPairing(enabled: Boolean): Unit = synchronized(pairingLock) {
        pairingJob?.cancel()
        if (!enabled || !state.value.running) {
            pairingExpiry = 0
            mutableState.update { it.copy(pairingCode = null) }
        } else {
            val random = SecureRandom(); val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
            val code = CharArray(16) { alphabet[random.nextInt(alphabet.length)] }.concatToString()
            pairingExpiry = SystemClock.elapsedRealtime() + 120000
            mutableState.update { it.copy(pairingCode = code) }
            pairingJob = scope.launch { delay(120000); setPairing(false) }
        }
    }

    suspend fun chooseFolder(uri: Uri, saves: Boolean) = withContext(Dispatchers.IO) {
        require(android.provider.DocumentsContract.isTreeUri(uri))
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        // Force a provider access check before publishing a directory to paired devices.
        val flags = arrayOf(android.provider.DocumentsContract.Document.COLUMN_FLAGS)
        val root = android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, android.provider.DocumentsContract.getTreeDocumentId(uri))
        val writable = context.contentResolver.query(root, flags, null, null, null)?.use {
            it.moveToFirst() && it.getInt(0) and android.provider.DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0
        } ?: false
        if (!writable) throw IOException("Folder is read only")
        permissionGeneration.incrementAndGet()
        client?.cancelAll()
        operations.withLock {
            preferences.setFolder(uri.toString(), saves)
            if (!saves) { library.addFolder(uri); library.rescan() }
            refreshPreferences()
            mutableState.update { it.copy(catalogs = emptyMap(), busy = false, status = if (it.running) LanStatus.Ready else LanStatus.Idle) }
        }
    }

    suspend fun setAutoSync(enabled: Boolean) {
        if (!enabled) { permissionGeneration.incrementAndGet(); client?.cancelAll() }
        preferences.setAutoSync(enabled); refreshPreferences()
        if (!enabled) mutableState.update { it.copy(busy = false, status = if (it.running) LanStatus.Ready else LanStatus.Idle) }
        if (enabled && state.value.running) sync(null)
    }
    suspend fun pair(peerId: String, code: String) = action(LanStatus.Pairing) {
        val peer = state.value.nearby.firstOrNull { it.id == peerId } ?: peer(peerId)
        pairWith(peer, code)
    }
    suspend fun connectAddress(address: String, code: String) = action(LanStatus.Pairing) {
        val split = address.trim().split(':')
        require(split.size == 2 && split[1].toIntOrNull() in 1..65535)
        val host = split[0]
        require(host.split('.').size == 4 && host.split('.').all { it.toIntOrNull() in 0..255 })
        pairWith(LanPeer("", "", host, split[1].toInt()), code)
    }
    private suspend fun pairWith(peer: LanPeer, code: String) {
        val result = requireNotNull(client).pair(peer, code)
        ensureOperationCurrent()
        preferences.pair(PairedDevice(result.peer.id, result.peer.name, Base64.getEncoder().encodeToString(result.key), result.peer.host, result.peer.port))
        refreshPreferences()
        mutableState.update { it.copy(status = LanStatus.Paired) }
    }
    suspend fun forget(peerId: String) = withContext(Dispatchers.IO) {
        permissionGeneration.incrementAndGet()
        client?.cancelAll()
        preferences.forget(peerId); refreshPreferences()
        server?.disconnectPeer(peerId)
        mutableState.update { it.copy(catalogs = it.catalogs - peerId, busy = false, status = if (it.running) LanStatus.Ready else LanStatus.Idle) }
    }
    suspend fun refreshRoms(peerId: String) = action(LanStatus.Transferring) {
        val files = requireNotNull(client).manifest(peer(peerId), key(peerId), ShareKind.Rom)
        ensureOperationCurrent()
        mutableState.update { it.copy(catalogs = it.catalogs + (peerId to files), status = LanStatus.Ready) }
    }
    suspend fun downloadRom(peerId: String, path: String) = action(LanStatus.Transferring) {
        val folder = state.value.preferences.romFolder ?: throw IOException("Select a ROM folder")
        val file = requireNotNull(client).describe(peer(peerId), key(peerId), ShareKind.Rom, path)
        pull(peerId, ShareKind.Rom, file, folder, null)
        library.rescan()
        mutableState.update { it.copy(status = LanStatus.Ready) }
    }

    suspend fun sync(peerId: String?, automatic: Boolean = false) = withContext(Dispatchers.IO) {
        operations.withLock {
            if (!state.value.running || gameRunning()) return@withLock
            val config = preferences.preferences.first()
            if (config.saveFolder == null || (automatic && !config.autoSync)) return@withLock
            val peers = if (peerId != null) listOf(peerId) else config.peers.filter { paired ->
                val nearby = state.value.nearby.any { it.id == paired.id }
                !automatic || (nearby && config.deviceId < paired.id) || (!nearby && paired.host.isNotBlank() && paired.port > 0)
            }.map { it.id }
            if (peers.isEmpty()) return@withLock
            val generation = permissionGeneration.get()
            mutableState.update { it.copy(busy = true, status = LanStatus.Syncing) }
            var succeeded = false
            try {
                for (id in peers) {
                    try { withContext(LanOperationToken(generation)) { syncPeer(id) }; succeeded = true }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { if (!automatic) throw error }
                }
                if (generation == permissionGeneration.get()) {
                    if (succeeded) mutableState.update { it.copy(lastSyncMillis = System.currentTimeMillis(), status = if (it.preferences.conflicts.isEmpty()) LanStatus.Synced else LanStatus.Conflict) }
                    else if (automatic) mutableState.update { it.copy(status = if (it.preferences.conflicts.isEmpty()) LanStatus.Ready else LanStatus.Conflict) }
                }
            } catch (error: Exception) {
                if (error !is CancellationException && generation == permissionGeneration.get()) mutableState.update { it.copy(status = LanStatus.PeerUnavailable) }
                throw error
            } finally { if (generation == permissionGeneration.get()) mutableState.update { it.copy(busy = false, progressBytes = 0, totalBytes = 0) } }
        }
    }

    private suspend fun syncPeer(id: String) {
        val config = preferences.preferences.first()
        val folder = config.saveFolder ?: throw IOException("Select a save folder")
        ensureSavesAvailable()
        val remote = requireNotNull(client).manifest(peer(id), key(id), ShareKind.Save)
        val local = store.manifest(folder, ShareKind.Save)
        val plan = planSaveSync(local.map(::revision), remote.map(::revision), config.baselines[id].orEmpty())
        val baselines = plan.baselines.toMutableMap()
        val localHashes = local.associate { it.path to it.sha256 }
        val remoteHashes = remote.associate { it.path to it.sha256 }
        val conflicts = config.conflicts.filter { it.peerId == id && localHashes[it.path] != null && remoteHashes[it.path] != null && localHashes[it.path] != remoteHashes[it.path] }.associateBy { it.path }.toMutableMap()
        for (action in plan.actions) {
            currentCoroutineContext().ensureActive()
            ensureOperationCurrent()
            ensureSavesAvailable()
            when (action) {
                is SaveSyncAction.Pull -> pull(id, ShareKind.Save, shared(action.source), folder, action.expectedLocalSha256)
                is SaveSyncAction.Push -> push(id, shared(action.source), folder, action.expectedRemoteSha256)
                is SaveSyncAction.Conflict -> {
                    pull(id, ShareKind.Save, shared(action.remote), folder, null, action.localCopyPath)
                    val backup = shared(action.local).copy(path = action.remoteCopyPath)
                    if (remoteHashes[backup.path] != backup.sha256) push(id, backup, folder, null, action.path)
                }
            }
            ensureSavesAvailable()
            ensureOperationCurrent()
            val ours = store.currentHash(folder, ShareKind.Save, action.path)
            val theirs = requireNotNull(client).describe(peer(id), key(id), ShareKind.Save, action.path).sha256
            val acknowledged = if (action is SaveSyncAction.Conflict) {
                acknowledgedSaveBaseline(action, ours, theirs,
                    store.currentHash(folder, ShareKind.Save, action.localCopyPath),
                    requireNotNull(client).describe(peer(id), key(id), ShareKind.Save, action.remoteCopyPath).sha256)
            } else acknowledgedSaveBaseline(action, ours, theirs)
            if (acknowledged != null) {
                requireNotNull(client).acknowledge(peer(id), key(id), listOf(SaveAcknowledgement(action.path, acknowledged.localSha256, acknowledged.remoteSha256)))
                baselines[action.path] = acknowledged
                if (action is SaveSyncAction.Conflict) conflicts[action.path] = StoredSaveConflict(id, action.path, acknowledged.localSha256, acknowledged.remoteSha256)
                else conflicts.remove(action.path)
                preferences.recordSync(id, baselines, conflicts.values.toList(), folder, config.peers.first { it.id == id }.key); refreshPreferences()
            }
        }
        val observed = plan.baselines.filter { (path, baseline) -> config.baselines[id]?.get(path) != baseline && baseline.localSha256 == baseline.remoteSha256 }
        ensureOperationCurrent()
        if (observed.isNotEmpty()) requireNotNull(client).acknowledge(peer(id), key(id), observed.map { (path, baseline) -> SaveAcknowledgement(path, baseline.localSha256, baseline.remoteSha256) })
        preferences.recordSync(id, baselines, conflicts.values.toList(), folder, config.peers.first { it.id == id }.key); refreshPreferences()
    }

    suspend fun resolveConflict(conflictId: String, useRemote: Boolean) = action(LanStatus.Syncing) {
        val config = preferences.preferences.first()
        val conflict = config.conflicts.firstOrNull { conflictId(it) == conflictId } ?: return@action
        val folder = config.saveFolder ?: throw IOException("Select a save folder")
        ensureSavesAvailable()
        val localHash = store.currentHash(folder, ShareKind.Save, conflict.path)
        val remote = requireNotNull(client).describe(peer(conflict.peerId), key(conflict.peerId), ShareKind.Save, conflict.path)
        if (localHash != conflict.localHash || remote.sha256 != conflict.remoteHash) throw IOException("Conflict changed; sync again")
        // Retain both historical versions before applying the explicitly confirmed resolution.
        val localFile = store.describe(folder, ShareKind.Save, conflict.path)
        pull(conflict.peerId, ShareKind.Save, remote, folder, null, conflictSavePath(conflict.path, remote.sha256))
        push(conflict.peerId, localFile.copy(path = conflictSavePath(conflict.path, localFile.sha256)), folder, null, conflict.path)
        if (useRemote) pull(conflict.peerId, ShareKind.Save, remote, folder, localHash)
        else push(conflict.peerId, localFile, folder, remote.sha256)
        val chosen = if (useRemote) remote.sha256 else localFile.sha256
        if (store.currentHash(folder, ShareKind.Save, conflict.path) != chosen ||
            requireNotNull(client).describe(peer(conflict.peerId), key(conflict.peerId), ShareKind.Save, conflict.path).sha256 != chosen) throw IOException("Save changed during resolution")
        requireNotNull(client).acknowledge(peer(conflict.peerId), key(conflict.peerId), listOf(SaveAcknowledgement(conflict.path, chosen, chosen)))
        preferences.acknowledge(conflict.peerId, folder, mapOf(conflict.path to SaveBaseline(chosen, chosen)), config.peers.first { it.id == conflict.peerId }.key)
        refreshPreferences()
        mutableState.update { it.copy(status = if (it.preferences.conflicts.isEmpty()) LanStatus.Synced else LanStatus.Conflict) }
    }

    private suspend fun pull(id: String, kind: ShareKind, file: SharedFile, folder: String, expected: String?, destination: String = file.path) {
        val generation = currentCoroutineContext()[LanOperationToken]?.generation ?: permissionGeneration.get()
        ensureOperationCurrent()
        val sessionClient = requireNotNull(client)
        val sessionKey = key(id)
        val target = file.copy(path = destination)
        if (store.currentHash(folder, kind, destination) == file.sha256) return
        val staging = File(context.cacheDir, "lan-incoming").also { it.mkdirs() }
        if (staging.usableSpace < file.size + 8 * 1024 * 1024) throw IOException("Insufficient storage")
        val temp = File.createTempFile("incoming-", ".part", staging)
        try {
            mutableState.update { it.copy(progressBytes = 0, totalBytes = file.size) }
            temp.outputStream().use { output ->
                var transferred = 0L
                var lastProgress = 0L
                val progress = object : FilterOutputStream(output) {
                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        out.write(bytes, offset, length); transferred += length
                        val now = SystemClock.elapsedRealtime()
                        if (transferred == file.size || now - lastProgress >= 100) { mutableState.update { it.copy(progressBytes = transferred) }; lastProgress = now }
                    }
                    override fun write(value: Int) { out.write(value); transferred++; mutableState.update { it.copy(progressBytes = transferred) } }
                }
                sessionClient.download(peer(id), sessionKey, kind, file, progress)
            }
            currentCoroutineContext().ensureActive()
            if (!state.value.running || folderFor(kind) != folder) throw SharingSessionChangedException()
            if (kind == ShareKind.Save) ensureSavesAvailable()
            val commit = { store.commit(folder, kind, target, expected, temp) { checkPermission(generation, id, sessionKey, kind, folder) } }
            saveAccess.write(commit)
        } finally { temp.delete() }
    }
    private suspend fun push(id: String, file: SharedFile, folder: String, expected: String?, source: String = file.path) {
        ensureOperationCurrent()
        ensureSavesAvailable()
        if (folderFor(ShareKind.Save) != folder) throw IOException("Save folder changed")
        store.openRead(folder, ShareKind.Save, source).use { input -> requireNotNull(client).upload(peer(id), key(id), ShareKind.Save, file, input, expected) }
    }
    private suspend fun action(status: LanStatus, block: suspend () -> Unit) = withContext(Dispatchers.IO) {
        operations.withLock {
            if (!state.value.running) { mutableState.update { it.copy(status = LanStatus.Error) }; return@withLock }
            val generation = permissionGeneration.get()
            mutableState.update { it.copy(busy = true, status = status) }
            try { withContext(LanOperationToken(generation)) { block() } }
            catch (error: Exception) { if (error !is CancellationException && generation == permissionGeneration.get()) mutableState.update { it.copy(status = LanStatus.Error) }; throw error }
            finally { if (generation == permissionGeneration.get()) mutableState.update { it.copy(busy = false, progressBytes = 0, totalBytes = 0) } }
        }
    }
    private fun peer(id: String): LanPeer = state.value.nearby.firstOrNull { it.id == id } ?: state.value.preferences.peers.firstOrNull { it.id == id }
        ?.let { LanPeer(it.id, it.name, it.host, it.port) } ?: throw IOException("Peer unavailable")
    private fun key(id: String): ByteArray = state.value.preferences.peers.firstOrNull { it.id == id }?.let { Base64.getDecoder().decode(it.key) }
        ?: throw IOException("Peer not paired")
    private suspend fun refreshPreferences() { val config = preferences.preferences.first(); mutableState.update { it.copy(preferences = config) } }
    private suspend fun ensureOperationCurrent() {
        currentCoroutineContext().ensureActive()
        val generation = currentCoroutineContext()[LanOperationToken]?.generation
        if (!state.value.running || (generation != null && generation != permissionGeneration.get())) throw SharingSessionChangedException()
    }
    private suspend fun gameRunning() = emulation.isPlaying.value || dao.session() != null
    private suspend fun ensureSavesAvailable() { if (gameRunning()) throw IOException("Emulator is writing saves") }
    private fun folderFor(kind: ShareKind) = if (kind == ShareKind.Save) state.value.preferences.saveFolder else state.value.preferences.romFolder
    private fun revision(file: SharedFile) = SaveRevision(file.path, file.sha256, file.size)
    private fun shared(file: SaveRevision) = SharedFile(file.path, file.size, file.sha256)
    fun reportFolderError() { mutableState.update { it.copy(status = LanStatus.FolderUnavailable) } }
    fun reportServiceError() { mutableState.update { if (!it.running && it.status == LanStatus.Idle) it else it.copy(status = LanStatus.Error) } }
    private fun checkPermission(generation: Long, id: String, sessionKey: ByteArray, kind: ShareKind, folder: String) {
        if (generation != permissionGeneration.get() || !state.value.running || folderFor(kind) != folder ||
            state.value.preferences.peers.firstOrNull { it.id == id }?.let { MessageDigest.isEqual(Base64.getDecoder().decode(it.key), sessionKey) } != true)
            throw SharingSessionChangedException()
        if (emulation.isPlaying.value) throw IOException("Emulator is using saves")
    }

    private val handler = object : LanServer.Handler {
        override fun currentPairingCode(): String? = synchronized(pairingLock) {
            state.value.pairingCode?.takeIf { state.value.running && SystemClock.elapsedRealtime() < pairingExpiry }
        }
        override fun pairedKey(peerId: String): ByteArray? = state.value.preferences.peers.firstOrNull { it.id == peerId }?.let { Base64.getDecoder().decode(it.key) }
        override fun onPaired(peerId: String, name: String, key: ByteArray, authenticatedCode: String) = synchronized(pairingLock) {
            if (currentPairingCode() != authenticatedCode || pairedKey(peerId) != null) throw IOException("Pairing closed")
            runBlocking { preferences.pair(PairedDevice(peerId, name, Base64.getEncoder().encodeToString(key), "", 0)); refreshPreferences() }
            setPairing(false)
        }
        private fun availableFolder(peerId: String, kind: ShareKind): String {
            if (!state.value.running || !state.value.preferences.enabled || pairedKey(peerId) == null) throw IOException("Sharing closed")
            if (kind == ShareKind.Save) {
                if (!state.value.preferences.autoSync) throw IOException("Save synchronization disabled")
                runBlocking { ensureSavesAvailable() }
            }
            return folderFor(kind) ?: throw IOException("Folder not configured")
        }
        override fun manifest(peerId: String, kind: ShareKind) = store.manifest(availableFolder(peerId, kind), kind)
        override fun describe(peerId: String, kind: ShareKind, path: String) = store.describe(availableFolder(peerId, kind), kind, path)
        override fun openRead(peerId: String, kind: ShareKind, path: String) = store.openRead(availableFolder(peerId, kind), kind, path)
        override fun receive(peerId: String, kind: ShareKind, path: String, expectedSha256: String, size: Long, expectedCurrentSha256: String?, input: InputStream) {
            val generation = permissionGeneration.get()
            val sessionKey = pairedKey(peerId) ?: throw IOException("Peer not paired")
            val folder = availableFolder(peerId, kind)
            val source = SharedFile(path, size, expectedSha256)
            val staged = store.stage(kind, source, input)
            try {
                if (availableFolder(peerId, kind) != folder) throw IOException("Folder changed")
                val commit = { store.commit(folder, kind, source, expectedCurrentSha256, staged) { checkPermission(generation, peerId, sessionKey, kind, folder) } }
                saveAccess.write(commit)
                if (kind == ShareKind.Rom) library.rescan()
            } finally { staged.delete() }
        }
        override fun acknowledge(peerId: String, acknowledgements: List<SaveAcknowledgement>) {
            val generation = permissionGeneration.get()
            val sessionKey = pairedKey(peerId) ?: throw IOException("Peer not paired")
            val folder = availableFolder(peerId, ShareKind.Save)
            val mirrored = acknowledgements.associate { acknowledgement ->
                if (store.currentHash(folder, ShareKind.Save, acknowledgement.path) != acknowledgement.remoteSha256) throw IOException("Save changed before acknowledgment")
                if (acknowledgement.localSha256 != acknowledgement.remoteSha256 &&
                    store.currentHash(folder, ShareKind.Save, conflictSavePath(acknowledgement.path, acknowledgement.localSha256)) != acknowledgement.localSha256)
                    throw IOException("Conflict copy not preserved")
                acknowledgement.path to SaveBaseline(acknowledgement.remoteSha256, acknowledgement.localSha256)
            }
            checkPermission(generation, peerId, sessionKey, ShareKind.Save, folder)
            runBlocking { preferences.acknowledge(peerId, folder, mirrored, Base64.getEncoder().encodeToString(sessionKey)); refreshPreferences() }
        }
    }

    companion object { fun conflictId(conflict: StoredSaveConflict) = "${conflict.peerId}:${conflict.path}" }
}
