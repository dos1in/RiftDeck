package com.riftdeck.core.input

import android.view.InputDevice
import android.view.KeyEvent as AndroidKeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object GamepadKeyMapper {
    fun fromKeyCode(keyCode: Int): GameAction? = when (keyCode) {
        AndroidKeyEvent.KEYCODE_DPAD_UP -> GameAction.Up
        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> GameAction.Down
        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> GameAction.Left
        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> GameAction.Right
        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
        AndroidKeyEvent.KEYCODE_BUTTON_A,
        AndroidKeyEvent.KEYCODE_ENTER,
        -> GameAction.Confirm
        AndroidKeyEvent.KEYCODE_BACK,
        AndroidKeyEvent.KEYCODE_BUTTON_B,
        -> GameAction.Back
        AndroidKeyEvent.KEYCODE_BUTTON_X -> GameAction.Details
        AndroidKeyEvent.KEYCODE_BUTTON_Y -> GameAction.Favorite
        AndroidKeyEvent.KEYCODE_BUTTON_L1 -> GameAction.PreviousCategory
        AndroidKeyEvent.KEYCODE_BUTTON_R1 -> GameAction.NextCategory
        AndroidKeyEvent.KEYCODE_BUTTON_START -> GameAction.Menu
        AndroidKeyEvent.KEYCODE_BUTTON_SELECT -> GameAction.Search
        else -> null
    }
}

fun Modifier.gamepadActions(
    onAction: (GameAction) -> Boolean,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

    val action = GamepadKeyMapper.fromKeyCode(event.nativeKeyEvent.keyCode)
        ?: return@onPreviewKeyEvent false
    if (event.nativeKeyEvent.repeatCount > 0 && !action.isDirectional) {
        return@onPreviewKeyEvent true
    }
    onAction(action)
}

fun Modifier.controllerClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    this
        .onPreviewKeyEvent { event ->
            if (!enabled || event.type != KeyEventType.KeyDown || event.nativeKeyEvent.repeatCount > 0) {
                return@onPreviewKeyEvent false
            }
            if (GamepadKeyMapper.fromKeyCode(event.nativeKeyEvent.keyCode) == GameAction.Confirm) {
                onClick()
                true
            } else {
                false
            }
        }
        .clickable(
            enabled = enabled,
            role = Role.Button,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
}

private fun MotionEvent.isJoystickMove(): Boolean =
    action == MotionEvent.ACTION_MOVE &&
        (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK

class GamepadInputManager {
    private val analogRepeatGate = AnalogRepeatGate()
    private val mutableActions = MutableSharedFlow<GameAction>(extraBufferCapacity = 8)

    val actions: SharedFlow<GameAction> = mutableActions.asSharedFlow()

    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!event.isJoystickMove()) return false
        analogRepeatGate.actionFor(event)?.let(mutableActions::tryEmit)
        return true
    }
}

private class AnalogRepeatGate {
    private var lastAction: GameAction? = null
    private var lastDispatchAt = 0L

    fun actionFor(event: MotionEvent): GameAction? {
        val horizontal = strongestAxis(
            event.getAxisValue(MotionEvent.AXIS_HAT_X),
            event.getAxisValue(MotionEvent.AXIS_X),
        )
        val vertical = strongestAxis(
            event.getAxisValue(MotionEvent.AXIS_HAT_Y),
            event.getAxisValue(MotionEvent.AXIS_Y),
        )
        val action = when {
            abs(horizontal) < DEAD_ZONE && abs(vertical) < DEAD_ZONE -> null
            abs(horizontal) > abs(vertical) && horizontal < 0 -> GameAction.Left
            abs(horizontal) > abs(vertical) -> GameAction.Right
            vertical < 0 -> GameAction.Up
            else -> GameAction.Down
        }

        if (action == null) {
            lastAction = null
            return null
        }

        val now = event.eventTime
        val shouldDispatch = action != lastAction || now - lastDispatchAt >= REPEAT_INTERVAL_MS
        lastAction = action
        if (!shouldDispatch) return null

        lastDispatchAt = now
        return action
    }

    private fun strongestAxis(first: Float, second: Float): Float =
        if (abs(first) >= abs(second)) first else second

    private companion object {
        const val DEAD_ZONE = 0.55f
        const val REPEAT_INTERVAL_MS = 145L
    }
}
