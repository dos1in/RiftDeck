package com.riftdeck.core.input

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

val LocalAnalogActions = staticCompositionLocalOf<Flow<GameAction>> { emptyFlow() }
val LocalControllerInputEnabled = staticCompositionLocalOf { true }

/** Physical D-pad and analog directions share the screen's navigation rules. */
@Composable
fun ControllerInput(
    onAction: (GameAction) -> Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalControllerInputEnabled.current,
    content: @Composable BoxScope.() -> Unit,
) {
    val actions = LocalAnalogActions.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focusManager = LocalFocusManager.current
    val currentAction by rememberUpdatedState(onAction)
    LaunchedEffect(actions, lifecycle, focusManager, enabled) {
        if (enabled) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            actions.collect { action ->
                if (!currentAction(action)) {
                    val direction = when (action) {
                        GameAction.Up -> FocusDirection.Up
                        GameAction.Down -> FocusDirection.Down
                        GameAction.Left -> FocusDirection.Left
                        GameAction.Right -> FocusDirection.Right
                        else -> null
                    }
                    direction?.let(focusManager::moveFocus)
                }
            }
        }
    }
    Box(modifier.gamepadActions { enabled && currentAction(it) }, content = content)
}
