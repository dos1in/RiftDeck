package com.riftdeck.feature.emulator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.riftdeck.core.emulator.EmulatorConfig
import com.riftdeck.core.emulator.LaunchResult
import com.riftdeck.core.model.Game
import com.riftdeck.data.repository.EmulationRepository
import com.riftdeck.data.repository.UiPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EmulatorViewModel(private val repository: EmulationRepository, preferences: UiPreferencesRepository) : ViewModel() {
    val targets = repository.targets
    val selected = preferences.preferences.map { it.emulators[1L] }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val historyError = repository.historyError
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow<LaunchResult?>(null)
    val error = mutableError.asStateFlow()
    private var launchJob: Job? = null

    fun refresh() = repository.refreshTargets()
    fun choose(config: EmulatorConfig) { viewModelScope.launch {
        try { repository.choose(config) }
        catch (_: java.io.IOException) { mutableError.value = LaunchResult.HistoryUnavailable }
    } }
    fun launch(game: Game) {
        if (launchJob?.isActive == true) return
        launchJob = viewModelScope.launch {
            mutableBusy.value = true
            try {
                val result = repository.launch(game)
                if (result != LaunchResult.Started) mutableError.value = result
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: java.io.IOException) { mutableError.value = LaunchResult.RomUnavailable }
            finally { mutableBusy.value = false }
        }
    }
    fun cancel() { launchJob?.cancel() }
    fun dismissError() { mutableError.value = null; repository.dismissHistoryError() }

    class Factory(private val repository: EmulationRepository, private val preferences: UiPreferencesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(EmulatorViewModel::class.java))
            return EmulatorViewModel(repository, preferences) as T
        }
    }
}
