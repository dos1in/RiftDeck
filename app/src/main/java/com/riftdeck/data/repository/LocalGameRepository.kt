package com.riftdeck.data.repository

import com.riftdeck.data.database.GameDao
import com.riftdeck.domain.repository.GameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class LocalGameRepository(private val dao: GameDao) : GameRepository {
    override val games = dao.observeGames().map { entities -> uniqueGames(entities.map { it.toGame() }) }
        .flowOn(Dispatchers.Default)
    override suspend fun toggleFavorite(gameId: Long) = dao.toggleFavoriteCopies(gameId)
}
