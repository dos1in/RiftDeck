package com.riftdeck.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.riftdeck.data.repository.UiPreferencesRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
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
    private val searchQuery = savedState.getStateFlow("search_query", "")
    private val mutablePreferenceError = MutableStateFlow(false)
    val preferenceError = mutablePreferenceError.asStateFlow()

    private val preferences = preferencesRepository.preferences.shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    // Focus and appearance changes do not repeat filtering or sorting a large library.
    private val library = combine(gameRepository.games, filter, searchQuery,
        preferences.map { it.sortDescending }.distinctUntilChanged()) { games, filterName, query, descending ->
        val selectedFilter = LibraryFilter.entries.firstOrNull { it.name == filterName } ?: LibraryFilter.All
        HomeUiState(
            isReady = true,
            games = games.filterNot { it.hidden },
            libraryGames = libraryGames(games, selectedFilter, descending, query),
            filter = selectedFilter,
            sortDescending = descending,
            searchQuery = query,
        )
    }.flowOn(Dispatchers.Default)
    val uiState = combine(library, focusedGameId, preferences) { state, focusedId, prefs ->
        state.copy(
            focusedGameId = focusedId?.takeIf { id -> state.games.any { it.id == id } }
                ?: state.recentGame?.id ?: state.games.firstOrNull()?.id,
            navigationRailExpanded = prefs.navigationRailExpanded,
            reducedMotion = prefs.reducedMotion,
            videoPreviews = prefs.videoPreviews,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    fun focusGame(gameId: Long) { savedState["focused_game"] = gameId }
    fun setSearchQuery(value: String) { savedState["search_query"] = value.take(120) }
    fun setFilter(value: LibraryFilter) { savedState["library_filter"] = value.name }
    fun toggleFavorite(gameId: Long) = updatePreference { gameRepository.toggleFavorite(gameId) }
    fun setNavigationRailExpanded(expanded: Boolean) = updatePreference { preferencesRepository.setNavigationRailExpanded(expanded) }
    fun setVideoPreviews(enabled: Boolean) = updatePreference { preferencesRepository.setVideoPreviews(enabled) }
    fun setReducedMotion(enabled: Boolean) = updatePreference { preferencesRepository.setReducedMotion(enabled) }
    fun toggleSort() = updatePreference { preferencesRepository.setSortDescending(!uiState.value.sortDescending) }
    fun dismissPreferenceError() { mutablePreferenceError.value = false }
    private fun updatePreference(update: suspend () -> Unit) {
        viewModelScope.launch {
            try { update() }
            catch (_: IOException) { mutablePreferenceError.value = true }
            catch (_: android.database.sqlite.SQLiteException) { mutablePreferenceError.value = true }
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
