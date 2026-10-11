package com.riftdeck.core.input

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
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
    val view = LocalView.current
    var windowFocused by remember(view) { mutableStateOf(view.hasWindowFocus()) }
    DisposableEffect(view) {
        val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { windowFocused = it }
        val observer = view.viewTreeObserver
        observer.addOnWindowFocusChangeListener(listener)
        windowFocused = view.hasWindowFocus()
        onDispose {
            val currentObserver = if (observer.isAlive) observer else view.viewTreeObserver
            if (currentObserver.isAlive) currentObserver.removeOnWindowFocusChangeListener(listener)
        }
    }
    val inputEnabled = enabled && windowFocused
    val currentAction by rememberUpdatedState(onAction)
    LaunchedEffect(actions, lifecycle, focusManager, inputEnabled) {
        if (inputEnabled) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            actions.collect { action ->
                // A new dialog can take the window before recomposition cancels this collector.
                if (!view.hasWindowFocus()) return@collect
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
    Box(modifier.gamepadActions { enabled && view.hasWindowFocus() && currentAction(it) }, content = content)
}
