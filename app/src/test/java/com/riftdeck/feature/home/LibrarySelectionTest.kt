package com.riftdeck.feature.home

import com.riftdeck.data.repository.MockGameRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibrarySelectionTest {
    private val games = MockGameRepository().games.value

    @Test fun removingSelectedFavoriteKeepsItsShelfPosition() {
        val remaining = games.filterNot { it.id == games[2].id }
        assertEquals(games[3].id, librarySelection(remaining, games[2].id, 2)?.id)
    }

    @Test fun removingLastGameSelectsPreviousItem() {
        assertEquals(games[3].id, librarySelection(games.dropLast(1), games.last().id, 4)?.id)
    }

    @Test fun sortingKeepsSelectedGameInsteadOfItsOldIndex() {
        assertEquals(games[2].id, librarySelection(games.reversed(), games[2].id, 0)?.id)
    }

    @Test fun mergedRomIdRestoresCanonicalSelection() {
        val merged = games[1].copy(sourceGameIds = listOf(99L))
        assertEquals(merged.id, librarySelection(listOf(games[0], merged), 99L, 0)?.id)
    }

    @Test fun emptyResultsHaveNoSelectionAndInvalidIndexIsClamped() {
        assertNull(librarySelection(emptyList(), 1, 99))
        assertEquals(games.first().id, librarySelection(games, null, -1)?.id)
        assertEquals(games.last().id, librarySelection(games, 99, 99)?.id)
    }
}
