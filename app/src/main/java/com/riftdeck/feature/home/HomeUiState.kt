package com.riftdeck.feature.home

import androidx.compose.runtime.Immutable
import com.riftdeck.core.model.Game

@Immutable
data class HomeUiState(
    val games: List<Game> = emptyList(),
    val focusedGameId: Long? = null,
) {
    val focusedGame: Game?
        get() = games.firstOrNull { it.id == focusedGameId } ?: games.firstOrNull()

    val favoriteCount: Int
        get() = games.count(Game::favorite)

    val recentGame: Game?
        get() = games
            .asSequence()
            .filter { it.lastPlayedAt != null }
            .maxByOrNull { it.lastPlayedAt ?: Long.MIN_VALUE }
}

