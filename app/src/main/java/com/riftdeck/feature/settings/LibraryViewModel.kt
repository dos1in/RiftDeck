package com.riftdeck.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.riftdeck.data.repository.LibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val repository: LibraryRepository) : ViewModel() {
    val folders = repository.folders.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val scanState = repository.scanState
    private val mutableError = MutableStateFlow(false)
    val error = mutableError.asStateFlow()

    fun addFolder(uri: Uri) = runOperation { repository.addFolder(uri); repository.rescan() }
    fun removeFolder(uri: String) = runOperation { repository.cancelScan(); repository.removeFolder(uri) }
    fun rescan() = repository.rescan()
    fun cancelScan() = repository.cancelScan()
    fun reportFolderPickerError() { mutableError.value = true }
    fun dismissError() { mutableError.value = false }

    private fun runOperation(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: java.io.IOException) { mutableError.value = true }
            catch (_: SecurityException) { mutableError.value = true }
            catch (_: android.database.sqlite.SQLiteException) { mutableError.value = true }
            catch (_: IllegalArgumentException) { mutableError.value = true }
        }
    }

    class Factory(private val repository: LibraryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LibraryViewModel::class.java))
            return LibraryViewModel(repository) as T
        }
    }
}
