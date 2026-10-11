package com.riftdeck.core.emulator

import com.riftdeck.core.model.Game
import com.riftdeck.data.database.GameEntity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class EmulatorHandoffTest {
    private val config = EmulatorConfig(1, "test.emulator", "Main", "Test")
    private val game = GameEntity(id = 1, identity = "original", platformId = 1, title = "Game", sortTitle = "game",
        romUri = "content://original", fileName = "Game.gba", fileSize = 100, modifiedAt = 1).toGame()

    @Test fun cancellingWhileQueuedPreventsTheExternalActivityFromStarting() = runBlocking {
        val occupied = CountDownLatch(1)
        val release = CountDownLatch(1)
        val queued = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val delegate = executor.asCoroutineDispatcher()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                queued.countDown()
                delegate.dispatch(context, block)
            }
        }
        val starts = AtomicInteger()
        executor.execute {
            occupied.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        try {
            assertTrue(occupied.await(5, TimeUnit.SECONDS))
            val launching = launch(Dispatchers.Default) {
                startEmulatorActivity(dispatcher) { starts.incrementAndGet() }
            }
            assertTrue("Launch should wait for its target dispatcher", queued.await(5, TimeUnit.SECONDS))
            launching.cancel()
            release.countDown()
            withTimeout(5000) { launching.join() }
            assertTrue(launching.isCancelled)
            assertEquals(0, starts.get())
        } finally {
            release.countDown()
            delegate.close()
        }
    }

    @Test fun cancellationAfterAndroidStartsKeepsTheSuccessfulHandoffResult() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val result = AtomicReference<LaunchResult>()
        try {
            val launcher = object : EmulatorLauncher {
                override suspend fun launch(config: EmulatorConfig, game: Game): LaunchResult =
                    startEmulatorActivity(dispatcher) {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                    }
            }
            val launching = launch(Dispatchers.Default) {
                result.set(launcher.launchWithCopies(config, game))
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            launching.cancel()
            release.countDown()
            withTimeout(5000) { launching.join() }
            assertTrue(launching.isCancelled)
            assertEquals(LaunchResult.Started, result.get())
        } finally {
            release.countDown()
            dispatcher.close()
        }
    }

    @Test fun rejectedAndroidLaunchStillPropagatesItsFailure() = runBlocking {
        val failure = IllegalArgumentException("Rejected intent")
        try {
            startEmulatorActivity(Dispatchers.Unconfined) { throw failure }
            fail("A rejected intent cannot report a successful launch")
        } catch (error: IllegalArgumentException) {
            assertEquals(failure.message, error.message)
        }
    }
}
