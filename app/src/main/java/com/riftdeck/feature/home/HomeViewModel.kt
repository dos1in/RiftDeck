package com.riftdeck.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.riftdeck.domain.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val gameRepository: GameRepository,
) : ViewModel() {
    private val focusedGameId = MutableStateFlow<Long?>(null)

    val uiState = combine(gameRepository.games, focusedGameId) { games, focusedId ->
        val resolvedFocus = focusedId?.takeIf { id -> games.any { it.id == id } }
            ?: games.firstOrNull()?.id
        HomeUiState(
            games = games,
            focusedGameId = resolvedFocus,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(),
    )

    fun focusGame(gameId: Long) {
        focusedGameId.value = gameId
    }

    fun toggleFavorite(gameId: Long) {
        viewModelScope.launch {
            gameRepository.toggleFavorite(gameId)
        }
    }
}

class HomeViewModelFactory(
    private val gameRepository: GameRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(HomeViewModel::class.java))
        return HomeViewModel(gameRepository) as T
    }
}

