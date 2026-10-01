package com.riftdeck.feature.sharing

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.riftdeck.data.repository.SharingRepository
import com.riftdeck.data.repository.SharingSessionChangedException
import com.riftdeck.feature.settings.folderLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.riftdeck.core.sharing.SharedFile

class SharingViewModel(private val repository: SharingRepository) : ViewModel() {
    private var catalogs: Map<String, List<SharedFile>> = emptyMap()
    private var catalogUi: Map<String, List<SharedRomUi>> = emptyMap()
    val uiState = repository.state.map { state ->
        if (state.catalogs !== catalogs) {
            catalogs = state.catalogs
            catalogUi = catalogs.mapValues { (_, files) -> files.map { SharedRomUi(it.path, it.path, it.size) } }
        }
        val config = state.preferences
        val ids = (state.nearby.map { it.id } + config.peers.map { it.id }).distinct()
        SharingUiState(
            enabled = state.running || (config.enabled && state.status == com.riftdeck.data.repository.LanStatus.Starting), deviceName = state.deviceName, endpoint = state.endpoint,
            romFolderLabel = config.romFolder?.let(::folderLabel), saveFolderLabel = config.saveFolder?.let(::folderLabel),
            autoSync = config.autoSync, pairingEnabled = state.pairingCode != null, pairingCode = state.pairingCode,
            peers = ids.map { id ->
                val nearby = state.nearby.firstOrNull { it.id == id }
                val stored = config.peers.firstOrNull { it.id == id }
                SharingPeerUi(id, nearby?.name ?: requireNotNull(stored).name,
                    nearby?.let { "${it.host}:${it.port}" } ?: stored?.takeIf { it.port > 0 }?.let { "${it.host}:${it.port}" },
                    online = nearby != null, paired = stored != null,
                    roms = catalogUi[id].orEmpty(), romsLoaded = id in state.catalogs)
            },
            conflicts = config.conflicts.map { SaveConflictUi(SharingRepository.conflictId(it), it.peerId, config.peers.firstOrNull { peer -> peer.id == it.peerId }?.name.orEmpty(), it.path) },
            busy = state.busy, status = SharingStatus.valueOf(state.status.name), progressBytes = state.progressBytes,
            totalBytes = state.totalBytes, lastSyncMillis = state.lastSyncMillis,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, SharingUiState())

    init { operation { repository.restoreIfEnabled() } }
    fun setEnabled(value: Boolean) = operation { repository.setEnabled(value) }
    fun chooseFolder(uri: Uri, saves: Boolean) = operation(folder = true) { repository.chooseFolder(uri, saves) }
    fun setAutoSync(value: Boolean) = operation { repository.setAutoSync(value) }
    fun setPairing(value: Boolean) { repository.setPairing(value) }
    fun pair(peer: String, code: String) = operation { repository.pair(peer, code) }
    fun connectAddress(address: String, code: String) = operation { repository.connectAddress(address, code) }
    fun refreshRoms(peer: String) = operation { repository.refreshRoms(peer) }
    fun downloadRom(peer: String, path: String) = operation { repository.downloadRom(peer, path) }
    fun sync(peer: String?) = operation { repository.sync(peer) }
    fun forget(peer: String) = operation { repository.forget(peer) }
    fun resolveConflict(id: String, useRemote: Boolean) = operation { repository.resolveConflict(id, useRemote) }
    fun reportFolderError() = repository.reportFolderError()
    private fun operation(folder: Boolean = false, action: suspend () -> Unit) = viewModelScope.launch {
        try { action() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SharingSessionChangedException) { /* A stopped or replaced sharing session has no current error to display. */ }
        catch (_: Exception) { if (folder) repository.reportFolderError() else repository.reportServiceError() }
    }
    class Factory(private val repository: SharingRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SharingViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return SharingViewModel(repository) as T
        }
    }
}
