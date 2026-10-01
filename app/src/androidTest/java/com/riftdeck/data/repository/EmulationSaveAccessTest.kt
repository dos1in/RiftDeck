package com.riftdeck.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.riftdeck.core.emulator.EmulatorCatalog
import com.riftdeck.core.emulator.EmulatorConfig
import com.riftdeck.core.emulator.EmulatorLauncher
import com.riftdeck.core.emulator.LaunchResult
import com.riftdeck.core.emulator.RomLaunchPreparer
import com.riftdeck.core.model.Game
import com.riftdeck.core.sharing.SaveAccessGate
import com.riftdeck.data.database.GameEntity
import com.riftdeck.data.database.LibraryDatabase
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Verify the emulator lifecycle and the LAN save-write barrier together with real Room/DataStore. */
class EmulationSaveAccessTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val preferencesFile = File(context.filesDir, "emulation-save-test-${UUID.randomUUID()}.preferences_pb")
    private val gate = SaveAccessGate()
    private val launcher = FakeLauncher()
    private val config = EmulatorConfig(1, "com.riftdeck.fixture.emulator", "FixtureActivity", "Fixture emulator")
    private lateinit var database: LibraryDatabase
    private lateinit var repository: EmulationRepository
    private lateinit var game: Game

    @Before fun prepare() = runBlocking {
        val preferences = UiPreferencesRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { preferencesFile }))
        preferences.setEmulator(config)
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        val entity = GameEntity(identity = "emulation-save-fixture", platformId = 1, title = "Diagnostic game", sortTitle = "Diagnostic game",
            romUri = "content://com.riftdeck.fixture/Game.gba", fileName = "Game.gba", fileSize = 1024, modifiedAt = 0)
        val id = database.games().insert(entity)
        game = entity.copy(id = id).toGame()
        repository = EmulationRepository(context, preferences, EmulatorCatalog(context), launcher, RomLaunchPreparer(context),
            database.games(), scope, gate)
    }

    @After fun cleanup(): Unit = runBlocking {
        job.cancelAndJoin()
        if (::database.isInitialized) database.close()
        preferencesFile.delete()
        Unit
    }

    @Test fun cancellingLaunchWhileAnExistingSaveCommitFinishesResetsPlayingAndAllowsWrites() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val existingCommit = scope.launch {
            gate.write {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS)) { "Test commit was not released" }
            }
        }
        try {
            assertTrue("Existing commit must hold the barrier", entered.await(5, TimeUnit.SECONDS))
            val launching = scope.launch { repository.launch(game) }
            withTimeout(5000) { repository.isPlaying.first { it } }
            assertEquals("The emulator cannot start before the commit completes", 0, launcher.calls.get())
            launching.cancel()
            release.countDown()
            withTimeout(5000) { launching.join(); existingCommit.join() }
            assertTrue(launching.isCancelled)
            assertFalse(repository.isPlaying.value)
            assertEquals(0, launcher.calls.get())
            assertNull(database.games().session())
            assertEquals("A cancelled launch must release save writes", "writable", gate.write { "writable" })
        } finally {
            release.countDown()
            existingCommit.cancelAndJoin()
        }
    }

    @Test fun failedExternalLaunchReleasesBarrierWithoutCreatingPlayHistory() = runBlocking {
        launcher.result = LaunchResult.NotInstalled
        assertEquals(LaunchResult.NotInstalled, repository.launch(game))
        assertEquals(1, launcher.calls.get())
        assertEquals(config, launcher.lastConfig)
        assertFalse(repository.isPlaying.value)
        assertEquals("writable", gate.write { "writable" })
        assertNull(database.games().session())
        assertEquals(0, database.games().findById(game.id)!!.playCount)
        assertNull(database.games().findById(game.id)!!.lastPlayedAt)
    }

    @Test fun successfulLaunchStoresSessionAndBlocksSaveWritesUntilFrontendReturns() = runBlocking {
        launcher.result = LaunchResult.Started
        assertEquals(LaunchResult.Started, repository.launch(game))
        assertTrue(repository.isPlaying.value)
        assertEquals(game.id, database.games().session()!!.gameId)
        assertEquals(1, database.games().findById(game.id)!!.playCount)
        assertNotNull(database.games().findById(game.id)!!.lastPlayedAt)
        try { gate.write { fail("An active emulator must deny save writes") }; fail("Expected save-access denial") }
        catch (_: IOException) { }

        repository.onFrontendResumed()
        withTimeout(5000) { repository.isPlaying.first { !it } }
        withTimeout(5000) {
            while (true) {
                try { gate.write { }; break } catch (_: IOException) { delay(10) }
            }
        }
        assertNull(database.games().session())
        assertEquals(1, database.games().findById(game.id)!!.playCount)
        assertFalse(repository.historyError.value)
    }

    private class FakeLauncher : EmulatorLauncher {
        val calls = AtomicInteger()
        @Volatile var result: LaunchResult = LaunchResult.NotInstalled
        @Volatile var lastConfig: EmulatorConfig? = null
        override suspend fun launch(config: EmulatorConfig, game: Game): LaunchResult {
            calls.incrementAndGet()
            lastConfig = config
            return result
        }
    }
}
