package com.riftdeck.feature.platform

import com.riftdeck.data.repository.MockGameRepository
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFilterTest {
    private val sample = MockGameRepository().games.value.first()

    @Test
    fun hiddenGamesNeverAppearInAnyFilter() {
        val hidden = sample.copy(hidden = true, favorite = true, lastPlayedAt = 500L)
        LibraryFilter.entries.forEach { filter ->
            assertTrue(libraryGames(listOf(hidden), filter, false).isEmpty())
        }
    }

    @Test
    fun removingTheLastFavoriteProducesAnEmptyFilter() {
        val favorite = sample.copy(favorite = true)
        assertEquals(listOf(favorite), libraryGames(listOf(favorite), LibraryFilter.Favorites, false))
        assertTrue(libraryGames(listOf(favorite.copy(favorite = false)), LibraryFilter.Favorites, false).isEmpty())
    }

    @Test
    fun recentUsesPlayHistoryRegardlessOfTitleSortPreference() {
        val games = listOf(sample.copy(id = 1, lastPlayedAt = 100L), sample.copy(id = 2, lastPlayedAt = null),
            sample.copy(id = 3, lastPlayedAt = 300L), sample.copy(id = 4, lastPlayedAt = 200L))
        listOf(false, true).forEach { descending ->
            assertEquals(listOf(3L, 4L, 1L), libraryGames(games, LibraryFilter.Recent, descending).map { it.id })
        }
    }

    @Test
    fun titleOrderIsStableAcrossDeviceLocalesAndReversible() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val games = listOf(sample.copy(id = 3, sortTitle = "INFERNO"), sample.copy(id = 2, sortTitle = "alpha"),
                sample.copy(id = 1, sortTitle = "Alpha"))
            assertEquals(listOf(1L, 2L, 3L), libraryGames(games, LibraryFilter.All, false).map { it.id })
            assertEquals(listOf(3L, 2L, 1L), libraryGames(games, LibraryFilter.All, true).map { it.id })
        } finally { Locale.setDefault(previous) }
    }

    @Test
    fun largeLibraryRetainsEveryVisibleIdentityWithoutMutatingItsSource() {
        val games = (1L..5000L).map { sample.copy(id = it, sortTitle = "Game ${5001L - it}", hidden = it % 5L == 0L) }
        val sorted = libraryGames(games, LibraryFilter.All, false)
        assertEquals(4000, sorted.size)
        assertEquals(4000, sorted.map { it.id }.toSet().size)
        assertEquals(1L, games.first().id)
        assertEquals(5000L, games.last().id)
    }
}
