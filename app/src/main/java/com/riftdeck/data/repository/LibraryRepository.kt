package com.riftdeck.data.repository

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.riftdeck.data.scanner.LibraryScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns folder access and scan lifetime independently of the current screen. */
class LibraryRepository(
    private val resolver: ContentResolver,
    private val preferences: UiPreferencesRepository,
    private val scanner: LibraryScanner,
    private val scope: CoroutineScope,
) {
    val folders = preferences.preferences.map { it.romFolders }.distinctUntilChanged()
    val scanState = scanner.state
    private var scanJob: Job? = null
    private val operations = Mutex()

    suspend fun addFolder(uri: Uri) = withContext(Dispatchers.IO) {
        require(DocumentsContract.isTreeUri(uri))
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        preferences.addRomFolder(uri.toString())
    }

    fun rescan() {
        scanJob?.cancel()
        scanJob = scope.launch { operations.withLock { scanner.scan(folders.first()) } }
    }

    fun cancelScan() { scanJob?.cancel() }

    suspend fun removeFolder(uri: String) = withContext(Dispatchers.IO) {
        operations.withLock {
            // Remove from preferences first; future scans must not enqueue the removed folder.
            preferences.removeRomFolder(uri)
            scanner.removeFolder(uri)
            try {
                resolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // The provider may already have revoked access (for example after an SD card removal).
            }
        }
    }
}
