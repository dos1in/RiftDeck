package com.riftdeck.domain.repository

import com.riftdeck.core.model.Game
import kotlinx.coroutines.flow.StateFlow

interface GameRepository {
    val games: StateFlow<List<Game>>

    suspend fun toggleFavorite(gameId: Long)
}

