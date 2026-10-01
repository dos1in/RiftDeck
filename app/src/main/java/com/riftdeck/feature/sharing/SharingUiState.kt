package com.riftdeck.feature.sharing

import androidx.compose.runtime.Immutable

enum class SharingStatus {
    Idle, Starting, Ready, Pairing, Paired, Transferring, Syncing, Synced,
    Conflict, Error, FolderUnavailable, PeerUnavailable,
}

@Immutable
data class SharingUiState(
    val enabled: Boolean = false,
    val deviceName: String = "",
    val endpoint: String = "",
    val romFolderLabel: String? = null,
    val saveFolderLabel: String? = null,
    val autoSync: Boolean = false,
    val pairingEnabled: Boolean = false,
    val pairingCode: String? = null,
    val peers: List<SharingPeerUi> = emptyList(),
    val conflicts: List<SaveConflictUi> = emptyList(),
    val busy: Boolean = false,
    val status: SharingStatus = SharingStatus.Idle,
    val progressBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val lastSyncMillis: Long? = null,
)

@Immutable
data class SharingPeerUi(
    val id: String,
    val name: String,
    val address: String? = null,
    val online: Boolean = false,
    val paired: Boolean = false,
    val roms: List<SharedRomUi> = emptyList(),
    val romsLoaded: Boolean = false,
    val romsLoading: Boolean = false,
)

@Immutable
data class SharedRomUi(val id: String, val name: String, val sizeBytes: Long)

@Immutable
data class SaveConflictUi(
    val id: String,
    val peerId: String,
    val peerName: String,
    val path: String,
)
