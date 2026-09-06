package com.riftdeck.data.scanner

import com.riftdeck.data.database.GameDao
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ScanState(
    val running: Boolean = false,
    val discovered: Int = 0,
    val failedFolders: Set<String> = emptySet(),
    val completed: Boolean = false,
)

class LibraryScanner(private val source: RomDocumentSource, private val dao: GameDao) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(ScanState())
    val state = mutableState.asStateFlow()

    suspend fun scan(folders: Collection<String>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            var discovered = 0
            val failures = mutableSetOf<String>()
            mutableState.value = ScanState(running = true)
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
                                mutableState.value = ScanState(true, discovered, failures.toSet())
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
                    mutableState.value = ScanState(true, discovered, failures.toSet())
                }
                mutableState.value = ScanState(discovered = discovered, failedFolders = failures, completed = true)
            } finally {
                mutableState.value = mutableState.value.copy(running = false)
            }
        }
    }

    /** Serialize folder removal with scans so an in-flight batch cannot recreate deleted entries. */
    suspend fun removeFolder(folder: String) = mutex.withLock { dao.removeFolder(folder) }
}
