package com.riftdeck.data.repository

import com.riftdeck.core.model.Game
import com.riftdeck.domain.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MockGameRepository : GameRepository {
    private val mutableGames = MutableStateFlow(createMockGames())

    override val games: StateFlow<List<Game>> = mutableGames.asStateFlow()

    override suspend fun toggleFavorite(gameId: Long) {
        mutableGames.update { games ->
            games.map { game ->
                if (game.id == gameId) game.copy(favorite = !game.favorite) else game
            }
        }
    }

    private fun createMockGames(): List<Game> {
        val now = System.currentTimeMillis()
        return listOf(
            mockGame(1, "Golden Circuit", "Action", 2003, "Northstar Lab", 7_920, now - 3_600_000, true),
            mockGame(2, "Neon Rally", "Racing", 2002, "Vector House", 3_240, now - 86_400_000, false),
            mockGame(3, "Pocket Tactics", "Strategy", 2004, "Tiny Forge", 12_660, now - 259_200_000, true),
            mockGame(4, "Skyline Drift", "Arcade", 2001, "Mono Works", 1_860, null, false),
            mockGame(5, "Astral Garden", "Adventure", 2005, "Soft Reset", 5_520, null, false),
        )
    }

    private fun mockGame(
        id: Long,
        title: String,
        genre: String,
        year: Int,
        developer: String,
        playTimeSeconds: Long,
        lastPlayedAt: Long?,
        favorite: Boolean,
    ) = Game(
        id = id,
        platformId = 1,
        title = title,
        sortTitle = title,
        romUri = "mock://gba/$id",
        fileName = "${title.lowercase().replace(' ', '_')}.gba",
        fileSize = 0,
        crc32 = null,
        sha1 = null,
        favorite = favorite,
        hidden = false,
        playCount = if (lastPlayedAt == null) 0 else id.toInt() + 1,
        playTimeSeconds = playTimeSeconds,
        lastPlayedAt = lastPlayedAt,
        coverUri = null,
        screenshotUri = null,
        videoUri = null,
        releaseYear = year,
        developer = developer,
        genre = genre,
    )
}

