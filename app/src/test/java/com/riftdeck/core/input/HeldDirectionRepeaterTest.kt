package com.riftdeck.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeldDirectionRepeaterTest {
    private class Fixture {
        val emitted = mutableListOf<GameAction>()
        val pending = mutableListOf<Pair<Runnable, Long>>()
        val repeater = HeldDirectionRepeater(
            schedule = { task, delay -> pending.add(task to delay) },
            cancel = { task -> pending.removeAll { it.first === task } },
            emit = { emitted.add(it) },
        )
        fun tick() = pending.removeAt(0).first.run()
    }

    @Test fun heldHatRepeatsWithoutMoreMotionEvents() {
        val f = Fixture()
        f.repeater.update(GameAction.Down)
        assertEquals(listOf(GameAction.Down), f.emitted)
        assertEquals(350L, f.pending.single().second)
        f.tick()
        assertEquals(145L, f.pending.single().second)
        f.tick()
        assertEquals(List(3) { GameAction.Down }, f.emitted)
    }

    @Test fun repeatedAxisReportsDoNotDuplicateOrRestartTheTimer() {
        val f = Fixture()
        f.repeater.update(GameAction.Down)
        repeat(20) { f.repeater.update(GameAction.Down) }
        assertEquals(1, f.emitted.size)
        assertEquals(1, f.pending.size)
        f.tick()
        repeat(20) { f.repeater.update(GameAction.Down) }
        assertEquals(145L, f.pending.single().second)
        assertEquals(2, f.emitted.size)
    }

    @Test fun releaseOrLifecycleResetCancelsPendingRepeat() {
        val f = Fixture()
        f.repeater.update(GameAction.Down)
        val pending = f.pending.single().first
        f.repeater.update(null)
        assertTrue(f.pending.isEmpty())
        pending.run()
        assertEquals(listOf(GameAction.Down), f.emitted)
        assertTrue(f.pending.isEmpty())
    }

    @Test fun directionChangeRespondsImmediatelyAndRestartsInitialDelay() {
        val f = Fixture()
        f.repeater.update(GameAction.Down)
        f.tick()
        f.repeater.update(GameAction.Up)
        assertEquals(listOf(GameAction.Down, GameAction.Down, GameAction.Up), f.emitted)
        assertEquals(350L, f.pending.single().second)
        f.tick()
        assertEquals(GameAction.Up, f.emitted.last())
    }

    @Test fun quickTapsEachMoveExactlyOnce() {
        val f = Fixture()
        repeat(3) { f.repeater.update(GameAction.Down); f.repeater.update(null) }
        assertEquals(List(3) { GameAction.Down }, f.emitted)
        assertTrue(f.pending.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun confirmationNeverEntersAutoRepeat() {
        Fixture().repeater.update(GameAction.Confirm)
    }
}
