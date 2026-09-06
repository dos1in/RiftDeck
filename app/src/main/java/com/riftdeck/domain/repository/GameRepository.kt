package com.riftdeck.domain.repository

import com.riftdeck.core.model.Game
import kotlinx.coroutines.flow.Flow

interface GameRepository {
    val games: Flow<List<Game>>

    suspend fun toggleFavorite(gameId: Long)
}

