package com.riftdeck.core.emulator

import com.riftdeck.core.model.Game
import com.riftdeck.core.model.RomCopy
import com.riftdeck.data.database.GameEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RomCopyLaunchTest {
    private val config = EmulatorConfig(1, "test.emulator", "Main", "Test")
    private val game = GameEntity(id = 1, identity = "original", platformId = 1, title = "Game", sortTitle = "game",
        romUri = "content://original", fileName = "Game.gba", fileSize = 100, modifiedAt = 1).toGame()
        .copy(romCopies = listOf(RomCopy("content://copy", "Game.zip", 50)))

    @Test fun missingPrimaryRomFallsBackWithoutChangingTheLogicalGameId() = runBlocking {
        val attempts = mutableListOf<Game>()
        val launcher = object : EmulatorLauncher {
            override suspend fun launch(config: EmulatorConfig, game: Game): LaunchResult {
                attempts.add(game)
                return if (game.romUri == "content://original") LaunchResult.RomUnavailable else LaunchResult.Started
            }
        }
        assertEquals(LaunchResult.Started, launcher.launchWithCopies(config, game))
        assertEquals(listOf("content://original", "content://copy"), attempts.map { it.romUri })
        assertTrue(attempts.all { it.id == game.id })
        assertEquals("Game.zip", attempts.last().fileName)
        assertEquals(50L, attempts.last().fileSize)
    }

    @Test fun emulatorErrorsDoNotRetryOtherRoms() = runBlocking {
        var attempts = 0
        val launcher = object : EmulatorLauncher {
            override suspend fun launch(config: EmulatorConfig, game: Game): LaunchResult {
                attempts++
                return LaunchResult.NotInstalled
            }
        }
        assertEquals(LaunchResult.NotInstalled, launcher.launchWithCopies(config, game))
        assertEquals(1, attempts)
    }

    @Test fun allCopiesUnavailableKeepsTheOriginalError() = runBlocking {
        val launcher = object : EmulatorLauncher {
            override suspend fun launch(config: EmulatorConfig, game: Game) = LaunchResult.RomUnavailable
        }
        assertEquals(LaunchResult.RomUnavailable, launcher.launchWithCopies(config, game))
    }
}
