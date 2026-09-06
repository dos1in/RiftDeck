package com.riftdeck.feature.platform

import com.riftdeck.data.repository.MockGameRepository
import org.junit.Assert.*
import org.junit.Test

class AlphabetPositionsTest {
    private val sample = MockGameRepository().games.value.first()
    @Test fun jumpsToFirstMatchingItemInTheDisplayedOrder() {
        val games = listOf("alpha", "amber", "beta", "zulu").mapIndexed { i, title -> sample.copy(id = i.toLong(), sortTitle = title) }
        assertEquals(mapOf('A' to 0, 'B' to 2, 'Z' to 3), alphabetPositions(games))
        assertEquals(mapOf('Z' to 0, 'B' to 1, 'A' to 2), alphabetPositions(games.reversed()))
    }
    @Test fun groupsDigitsAndNonLatinTitlesAndHandlesEmptyLists() {
        val games = listOf("123", "中文", "", "alpha").map { sample.copy(sortTitle = it) }
        assertEquals(mapOf('#' to 0, 'A' to 3), alphabetPositions(games))
        assertTrue(alphabetPositions(emptyList()).isEmpty())
    }
}
