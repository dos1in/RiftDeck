package com.riftdeck.data.repository

import com.riftdeck.data.database.GameDao
import com.riftdeck.domain.repository.GameRepository
import kotlinx.coroutines.flow.map

class LocalGameRepository(private val dao: GameDao) : GameRepository {
    override val games = dao.observeGames().map { entities -> entities.map { it.toGame() } }
    override suspend fun toggleFavorite(gameId: Long) = dao.toggleFavorite(gameId)
}
