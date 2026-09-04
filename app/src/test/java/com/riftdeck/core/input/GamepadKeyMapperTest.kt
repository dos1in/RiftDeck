package com.riftdeck.core.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GamepadKeyMapperTest {
    @Test
    fun mapsFaceAndNavigationButtons() {
        assertEquals(GameAction.Confirm, GamepadKeyMapper.fromKeyCode(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(GameAction.Back, GamepadKeyMapper.fromKeyCode(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(GameAction.Details, GamepadKeyMapper.fromKeyCode(KeyEvent.KEYCODE_BUTTON_X))
        assertEquals(GameAction.Favorite, GamepadKeyMapper.fromKeyCode(KeyEvent.KEYCODE_BUTTON_Y))
        assertEquals(GameAction.Menu, GamepadKeyMapper.fromKeyCode(KeyEvent.KEYCODE_BUTTON_START))
        assertEquals(GameAction.Search, GamepadKeyMapper.fromKeyCode(KeyEvent.KEYCODE_BUTTON_SELECT))
    }

    @Test
    fun mapsDirectionalAndShoulderButtons() {
        assertEquals(GameAction.Up, GamepadKeyMapper.fromKeyCode(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(GameAction.Down, GamepadKeyMapper.fromKeyCode(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(GameAction.Left, GamepadKeyMapper.fromKeyCode(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(GameAction.Right, GamepadKeyMapper.fromKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(GameAction.PreviousCategory, GamepadKeyMapper.fromKeyCode(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(GameAction.NextCategory, GamepadKeyMapper.fromKeyCode(KeyEvent.KEYCODE_BUTTON_R1))
    }

    @Test
    fun ignoresUnknownKeys() {
        assertNull(GamepadKeyMapper.fromKeyCode(KeyEvent.KEYCODE_VOLUME_UP))
    }
}

