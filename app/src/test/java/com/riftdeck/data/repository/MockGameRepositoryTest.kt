package com.riftdeck.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MockGameRepositoryTest {
    @Test
    fun togglingFavoriteOnlyUpdatesRequestedGame() = runBlocking {
        val repository = MockGameRepository()
        val original = repository.games.value
        val target = original.first()

        repository.toggleFavorite(target.id)

        val updated = repository.games.value
        assertNotEquals(target.favorite, updated.first { it.id == target.id }.favorite)
        assertEquals(
            original.drop(1).map { it.favorite },
            updated.drop(1).map { it.favorite },
        )
    }
}
