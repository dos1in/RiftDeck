package com.riftdeck.feature.search

import com.riftdeck.data.repository.MockGameRepository
import com.riftdeck.feature.platform.LibraryFilter
import com.riftdeck.feature.platform.libraryGames
import org.junit.Assert.*
import org.junit.Test

class GameSearchTest {
    private val sample = MockGameRepository().games.value.first()
    @Test fun matchesCaseAccentsWidthAndMultipleTerms() {
        val game = sample.copy(title = "Pokémon Emerald", fileName = "Pokemon_Emerald (USA).gba")
        assertEquals(listOf(game), searchGames(listOf(game), "  ＰＯＫＥＭＯＮ usa "))
        assertTrue(searchGames(listOf(game), "pokemon japan").isEmpty())
    }
    @Test fun supportsChineseAndLiteralPunctuation() {
        val game = sample.copy(title = "口袋妖怪 [汉化]", fileName = "demo.gba")
        assertEquals(listOf(game), searchGames(listOf(game), "口袋 [汉化]"))
        assertTrue(searchGames(listOf(game), "[.*]").isEmpty())
    }
    @Test fun searchComposesWithFavoritesAndRecentWithoutIncludingHiddenGames() {
        val games = listOf(sample.copy(id = 1, title = "Match", favorite = true, lastPlayedAt = 1),
            sample.copy(id = 2, title = "Match", favorite = false, lastPlayedAt = 3),
            sample.copy(id = 3, title = "Match", favorite = true, hidden = true, lastPlayedAt = 5))
        assertEquals(listOf(1L), libraryGames(games, LibraryFilter.Favorites, false, "match").map { it.id })
        assertEquals(listOf(2L, 1L), libraryGames(games, LibraryFilter.Recent, false, "match").map { it.id })
        assertEquals(games, searchGames(games, "  "))
    }
}
