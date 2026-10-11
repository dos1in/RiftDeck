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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

class HomeViewModel(
    private val gameRepository: GameRepository,
    private val preferencesRepository: UiPreferencesRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val focusedGameId = savedState.getStateFlow<Long?>("focused_game", null)
    private val focusedIndex = savedState.getStateFlow("focused_index", 0)
    private val filter = savedState.getStateFlow<String?>("library_filter", null)
    private val searchQuery = savedState.getStateFlow("search_query", "")
    private val mutablePreferenceError = MutableStateFlow(false)
    val preferenceError = mutablePreferenceError.asStateFlow()

    private val preferences = preferencesRepository.preferences.shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    init {
        viewModelScope.launch {
            val prefs = preferences.first()
            // Apply the start category once. A settings change must not replace the current shelf.
            if (LibraryFilter.entries.none { it.name == filter.value }) {
                savedState["library_filter"] = prefs.defaultHomeCategory
            }
        }
    }

    // Focus and appearance changes do not repeat filtering or sorting a large library.
    private val library = combine(gameRepository.games, filter.filterNotNull(), searchQuery,
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
    val uiState = combine(library, focusedGameId, focusedIndex, preferences) { state, focusedId, index, prefs ->
        state.copy(
            focusedGameId = librarySelection(state.libraryGames, focusedId ?: state.recentGame?.id, index)?.id,
            navigationRailExpanded = prefs.navigationRailExpanded,
            language = prefs.language,
            defaultHomeCategory = prefs.defaultHomeCategory,
            reducedMotion = prefs.reducedMotion,
            videoPreviews = prefs.videoPreviews,
            previewDelayMs = prefs.previewDelayMs,
            loopVideoPreviews = prefs.loopVideoPreviews,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                if (!state.isReady || state.filter.name != filter.value || state.searchQuery != searchQuery.value) return@collect
                val selected = librarySelection(state.libraryGames, focusedGameId.value ?: state.focusedGameId, focusedIndex.value)
                // A newer input may already be queued; never replace it with an older Flow emission.
                if (selected != null && selected.id == state.focusedGameId) {
                    savedState["focused_index"] = state.libraryGames.indexOfFirst { it.id == selected.id }
                    savedState["focused_game"] = selected.id
                }
            }
        }
    }

    fun focusGame(gameId: Long) {
        val index = uiState.value.libraryGames.indexOfFirst { it.matchesId(gameId) }
        if (index >= 0) savedState["focused_index"] = index
        savedState["focused_game"] = gameId
    }
    fun setSearchQuery(value: String) {
        savedState["focused_index"] = 0
        savedState["search_query"] = value.take(120)
    }
    fun setFilter(value: LibraryFilter) {
        savedState["focused_index"] = 0
        savedState["library_filter"] = value.name
    }
    fun changeCategory(delta: Int): LibraryFilter {
        val categories = listOf(LibraryFilter.All, LibraryFilter.Recent, LibraryFilter.Favorites)
        val current = LibraryFilter.entries.firstOrNull { it.name == filter.value } ?: LibraryFilter.All
        val next = categories[Math.floorMod(categories.indexOf(current) + delta, categories.size)]
        setFilter(next)
        return next
    }

    // Read the input cursor directly so repeated presses and a following A/X/Y do not wait for Flow.
    fun selectedGameId(): Long? {
        val state = uiState.value
        if (state.filter.name != filter.value || state.searchQuery != searchQuery.value) return null
        return librarySelection(state.libraryGames, focusedGameId.value ?: state.focusedGameId, focusedIndex.value)?.id
    }

    fun moveGame(delta: Int, wrap: Boolean): Boolean {
        val games = uiState.value.libraryGames
        val current = selectedGameId() ?: return true
        val index = games.indexOfFirst { it.id == current }
        if (delta < 0 && index == 0 && !wrap) return false
        val next = if (wrap) Math.floorMod(index + delta, games.size)
            else (index + delta).coerceIn(0, games.lastIndex)
        focusGame(games[next].id)
        return true
    }
    fun toggleFavorite(gameId: Long) {
        focusGame(gameId)
        updatePreference { gameRepository.toggleFavorite(gameId) }
    }
    fun setNavigationRailExpanded(expanded: Boolean) = updatePreference { preferencesRepository.setNavigationRailExpanded(expanded) }
    fun setPreviewDelay(delayMs: Int) = updatePreference { preferencesRepository.setPreviewDelay(delayMs) }
    fun setLoopVideoPreviews(loop: Boolean) = updatePreference { preferencesRepository.setLoopVideoPreviews(loop) }
    fun setVideoPreviews(enabled: Boolean) = updatePreference { preferencesRepository.setVideoPreviews(enabled) }
    fun setLanguage(value: String) = updatePreference { preferencesRepository.setLanguage(value) }
    fun setDefaultHomeCategory(value: String) = updatePreference { preferencesRepository.setDefaultHomeCategory(value) }
    fun setReducedMotion(enabled: Boolean) = updatePreference { preferencesRepository.setReducedMotion(enabled) }
    fun toggleSort() = updatePreference { preferencesRepository.toggleSortDescending() }
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
