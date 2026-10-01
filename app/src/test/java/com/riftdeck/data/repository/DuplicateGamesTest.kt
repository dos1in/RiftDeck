package com.riftdeck.data.repository

import com.riftdeck.data.database.GameEntity
import org.junit.Assert.*
import org.junit.Test

class DuplicateGamesTest {
    private fun game(id: Long, sha1: String? = "same") = GameEntity(id = id, identity = "doc:$id", platformId = 1,
        title = "Game", sortTitle = "game", romUri = "content://rom/$id", fileName = "Game.gba", fileSize = 100,
        modifiedAt = 1, sha1 = sha1).toGame()

    @Test fun confirmedCopiesKeepAStableIdAndCombineUserStateAndArtwork() {
        val oldest = game(1).copy(playCount = 2, playTimeSeconds = 20, lastPlayedAt = 10)
        val copy = game(2).copy(favorite = true, playCount = 3, playTimeSeconds = 30, lastPlayedAt = 100,
            coverUri = "content://cover", coverVersion = "version", description = "Description")
        val merged = uniqueGames(listOf(copy, oldest)).single()
        assertEquals(1L, merged.id)
        assertTrue(merged.favorite)
        assertEquals(5, merged.playCount)
        assertEquals(50L, merged.playTimeSeconds)
        assertEquals(100L, merged.lastPlayedAt)
        assertEquals(copy.coverUri, merged.coverUri)
        assertEquals(copy.coverVersion, merged.coverVersion)
        assertEquals(copy.description, merged.description)
        assertTrue(merged.matchesId(copy.id))
        assertTrue(merged.matchesId(oldest.id))
        assertFalse(merged.matchesId(99))
        assertFalse(oldest.favorite)
    }

    @Test fun matchingTitlesOrSizesDoNotMergeUnverifiedOrDifferentVersions() {
        assertEquals(2, uniqueGames(listOf(game(1, null), game(2, null))).size)
        assertEquals(2, uniqueGames(listOf(game(1, "version-one"), game(2, "version-two"))).size)
        assertEquals(2, uniqueGames(listOf(game(1), game(2).copy(platformId = 2))).size)
    }

    @Test fun hiddenCopiesDoNotContributeFavoritesOrHistory() {
        val hidden = game(1).copy(hidden = true, favorite = true, playCount = 5)
        assertEquals(game(2), uniqueGames(listOf(hidden, game(2))).single())
    }

    @Test fun largeLibraryRetainsUniqueGamesAndDeterministicRepresentatives() {
        val games = (1L..5000L).map { game(it, "hash:$it") }
        val copies = games.take(100).map { it.copy(id = it.id + 5000) }
        val unique = uniqueGames(games + copies)
        assertEquals(5000, unique.size)
        assertEquals(games.map { it.id }, unique.map { it.id })
    }
}
