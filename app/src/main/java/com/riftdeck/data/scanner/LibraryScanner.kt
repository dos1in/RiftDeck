package com.riftdeck.data.scanner

import com.riftdeck.data.database.GameDao
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ScanState(
    val running: Boolean = false,
    val discovered: Int = 0,
    val failedFolders: Set<String> = emptySet(),
    val completed: Boolean = false,
    val runId: Long = 0,
)

class LibraryScanner(private val source: RomDocumentSource, private val dao: GameDao,
    private val fingerprints: RomFingerprintReader = RomFingerprintReader { null }) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(ScanState())
    val state = mutableState.asStateFlow()

    suspend fun scan(folders: Collection<String>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val runId = mutableState.value.runId + 1
            var discovered = 0
            val failures = mutableSetOf<String>()
            mutableState.value = ScanState(running = true, runId = runId)
            try {
                for (folder in folders) {
                    currentCoroutineContext().ensureActive()
                    val generation = UUID.randomUUID().toString()
                    val batch = ArrayList<RomDocument>(50)
                    try {
                        source.enumerate(folder) { document ->
                            batch.add(document)
                            discovered++
                            if (batch.size >= 50) {
                                dao.importBatch(folder, generation, batch)
                                batch.clear()
                                mutableState.value = ScanState(true, discovered, failures.toSet(), runId = runId)
                            }
                        }
                        currentCoroutineContext().ensureActive()
                        if (batch.isNotEmpty()) dao.importBatch(folder, generation, batch)
                        dao.finishScan(folder, generation)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failures.add(folder)
                    }
                    mutableState.value = ScanState(true, discovered, failures.toSet(), runId = runId)
                }
                identifyDuplicateFiles()
                mutableState.value = ScanState(discovered = discovered, failedFolders = failures, completed = true, runId = runId)
            } finally {
                mutableState.value = mutableState.value.copy(running = false)
            }
        }
    }

    /** Repair previously imported copies in the background without rescanning any folders. */
    suspend fun identifyDuplicates() = mutex.withLock {
        withContext(Dispatchers.IO) { identifyDuplicateFiles() }
    }

    private suspend fun identifyDuplicateFiles() {
        val games = dao.observeGames().first()
        fun key(title: String) = Normalizer.normalize(title, Normalizer.Form.NFC).lowercase(Locale.ROOT)
        val matchingNames = games.flatMap { game ->
            listOf((game.platformId to key(RomFileNames.title(game.fileName))) to game,
                (game.platformId to key(game.title)) to game).distinctBy { it.first }
        }.groupBy({ it.first }, { it.second }).values.filter { it.size > 1 }
        val candidates = matchingNames.flatMap { copies ->
            if (copies.any { it.fileName.endsWith(".zip", ignoreCase = true) }) copies
            else copies.groupBy { it.fileSize }.values.filter { it.size > 1 }.flatten()
        }.distinctBy { it.id }
        for (game in candidates) {
            currentCoroutineContext().ensureActive()
            if (!game.sha1.isNullOrBlank()) continue
            val fingerprint = try {
                fingerprints.read(RomDocument(game.identity, game.romUri, game.fileName, game.fileSize,
                    game.modifiedAt, game.platformId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: java.io.IOException) {
                null
            } catch (_: SecurityException) {
                null
            } ?: continue
            dao.saveFingerprint(game.id, game.fileSize, game.modifiedAt, game.fileName, fingerprint.crc32, fingerprint.sha1)
        }
    }

    /** Serialize folder removal with scans so an in-flight batch cannot recreate deleted entries. */
    suspend fun removeFolder(folder: String) = mutex.withLock { dao.removeFolder(folder) }
}
