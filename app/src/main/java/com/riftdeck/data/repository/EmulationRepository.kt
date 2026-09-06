package com.riftdeck.data.repository

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.riftdeck.core.emulator.EmulatorCatalog
import com.riftdeck.core.emulator.EmulatorConfig
import com.riftdeck.core.emulator.EmulatorLauncher
import com.riftdeck.core.emulator.LaunchResult
import com.riftdeck.core.emulator.RomLaunchPreparer
import com.riftdeck.core.model.Game
import com.riftdeck.data.database.GameDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class EmulationRepository(
    private val context: Context,
    private val preferences: UiPreferencesRepository,
    private val catalog: EmulatorCatalog,
    private val launcher: EmulatorLauncher,
    private val roms: RomLaunchPreparer,
    private val dao: GameDao,
    private val scope: CoroutineScope,
) {
    private val sessionLock = Mutex()
    private val mutableTargets = MutableStateFlow<List<EmulatorConfig>>(emptyList())
    val targets = mutableTargets.asStateFlow()
    private val mutableHistoryError = MutableStateFlow(false)
    val historyError = mutableHistoryError.asStateFlow()
    private fun bootCount() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    fun refreshTargets() { scope.launch { mutableTargets.value = catalog.installedFor(1L) } }
    suspend fun choose(config: EmulatorConfig) = preferences.setEmulator(config)
    fun dismissHistoryError() { mutableHistoryError.value = false }

    suspend fun launch(game: Game): LaunchResult = sessionLock.withLock {
        val config = preferences.preferences.first().emulators[game.platformId] ?: return@withLock LaunchResult.NotConfigured
        try { dao.finishSession(SystemClock.elapsedRealtime(), bootCount()) }
        catch (_: android.database.sqlite.SQLiteException) { mutableHistoryError.value = true }
        val result = launcher.launch(config, game)
        if (result == LaunchResult.Started) {
            // Complete the history transaction even if Android recreates the frontend on departure.
            withContext(NonCancellable) {
                try { dao.beginSession(game.id, System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount()) }
                catch (_: android.database.sqlite.SQLiteException) { mutableHistoryError.value = true }
            }
        }
        result
    }

    fun onFrontendResumed() {
        val elapsed = SystemClock.elapsedRealtime()
        val boot = bootCount()
        scope.launch {
            sessionLock.withLock {
                try { dao.finishSession(elapsed, boot) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: android.database.sqlite.SQLiteException) { mutableHistoryError.value = true }
            }
            roms.clearExpiredFiles()
        }
        refreshTargets()
    }
}
