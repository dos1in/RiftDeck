package com.riftdeck.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.riftdeck.data.repository.UiPreferencesRepository
import com.riftdeck.domain.repository.GameRepository
import com.riftdeck.feature.platform.LibraryFilter
import com.riftdeck.feature.platform.libraryGames
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn

class HomeViewModel(
    private val gameRepository: GameRepository,
    private val preferencesRepository: UiPreferencesRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val focusedGameId = savedState.getStateFlow<Long?>("focused_game", null)
    private val filter = savedState.getStateFlow("library_filter", LibraryFilter.All.name)
    private val mutablePreferenceError = MutableStateFlow(false)
    val preferenceError = mutablePreferenceError.asStateFlow()

    // Moving focus does not repeat filtering or sorting a large library.
    private val library = combine(gameRepository.games, filter, preferencesRepository.preferences) { games, filterName, prefs ->
        val selectedFilter = LibraryFilter.entries.firstOrNull { it.name == filterName } ?: LibraryFilter.All
        HomeUiState(
            isReady = true,
            games = games.filterNot { it.hidden },
            libraryGames = libraryGames(games, selectedFilter, prefs.sortDescending),
            filter = selectedFilter,
            sortDescending = prefs.sortDescending,
            reducedMotion = prefs.reducedMotion,
        )
    }.flowOn(Dispatchers.Default)
    val uiState = combine(library, focusedGameId) { state, focusedId ->
        state.copy(focusedGameId = focusedId?.takeIf { id -> state.games.any { it.id == id } }
            ?: state.recentGame?.id ?: state.games.firstOrNull()?.id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    fun focusGame(gameId: Long) { savedState["focused_game"] = gameId }
    fun setFilter(value: LibraryFilter) { savedState["library_filter"] = value.name }
    fun toggleFavorite(gameId: Long) { viewModelScope.launch { gameRepository.toggleFavorite(gameId) } }
    fun setReducedMotion(enabled: Boolean) = updatePreference { preferencesRepository.setReducedMotion(enabled) }
    fun toggleSort() = updatePreference { preferencesRepository.setSortDescending(!uiState.value.sortDescending) }
    fun dismissPreferenceError() { mutablePreferenceError.value = false }
    private fun updatePreference(update: suspend () -> Unit) {
        viewModelScope.launch {
            try { update() } catch (_: IOException) { mutablePreferenceError.value = true }
        }
    }
}

class HomeViewModelFactory(
    private val gameRepository: GameRepository,
    private val preferencesRepository: UiPreferencesRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(HomeViewModel::class.java))
        return HomeViewModel(gameRepository, preferencesRepository, extras.createSavedStateHandle()) as T
    }
}
