package com.riftdeck.app

import android.app.Application
import androidx.room.Room
import com.riftdeck.core.emulator.AndroidEmulatorLauncher
import com.riftdeck.core.emulator.EmulatorCatalog
import com.riftdeck.core.emulator.RomLaunchPreparer
import com.riftdeck.data.repository.EmulationRepository
import com.riftdeck.data.database.LibraryDatabase
import com.riftdeck.data.repository.LibraryRepository
import com.riftdeck.data.repository.LocalGameRepository
import com.riftdeck.data.repository.UiPreferencesRepository
import com.riftdeck.data.scanner.LibraryScanner
import com.riftdeck.data.scanner.SafRomDocumentSource
import com.riftdeck.data.scanner.SafRomFingerprintReader
import com.riftdeck.domain.repository.GameRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.riftdeck.data.repository.SharingPreferencesRepository
import com.riftdeck.data.repository.SharingRepository

class LauncherApplication : Application() {
    private val saveAccess by lazy { com.riftdeck.core.sharing.SaveAccessGate() }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { Room.databaseBuilder(this, LibraryDatabase::class.java, "library.db").build() }
    val uiPreferencesRepository by lazy { UiPreferencesRepository(this) }
    val updateRepository by lazy { com.riftdeck.data.repository.AppUpdateRepository.create(this, applicationScope) }
    val gameRepository: GameRepository by lazy { LocalGameRepository(database.games()) }
    private val romLaunchPreparer by lazy { RomLaunchPreparer(this) }
    val emulationRepository by lazy {
        EmulationRepository(this, uiPreferencesRepository, EmulatorCatalog(this),
            AndroidEmulatorLauncher(this, romLaunchPreparer), romLaunchPreparer, database.games(), applicationScope, saveAccess)
    }
    val libraryRepository by lazy {
        LibraryRepository(contentResolver, uiPreferencesRepository,
            LibraryScanner(SafRomDocumentSource(contentResolver), database.games(), SafRomFingerprintReader(contentResolver)), applicationScope,
            com.riftdeck.data.repository.RetroArchImporter(contentResolver, database))
    }
    val sharingRepository by lazy {
        SharingRepository(this, SharingPreferencesRepository(this), libraryRepository, database.games(), emulationRepository, applicationScope, saveAccess)
    }
    fun stopSharingSession(owner: String) { applicationScope.launch { sharingRepository.stop(owner) } }
}
