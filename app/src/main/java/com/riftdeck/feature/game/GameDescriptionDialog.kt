package com.riftdeck.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.riftdeck.R
import com.riftdeck.core.input.ControllerInput
import com.riftdeck.core.input.GameAction
import com.riftdeck.core.ui.components.NeonActionButton
import com.riftdeck.core.ui.theme.LocalFrontendTheme
import kotlinx.coroutines.launch

@Composable
internal fun GameDescriptionDialog(description: String, onDismiss: () -> Unit) {
    val colors = LocalFrontendTheme.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val close = remember { FocusRequester() }
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { withFrameNanos { }; close.requestFocus() }
        ControllerInput(enabled = true, onAction = {
            when (it) {
                GameAction.Back -> { onDismiss(); true }
                GameAction.Up, GameAction.Down -> { scope.launch { scroll.scrollBy(if (it == GameAction.Up) -100f else 100f) }; true }
                GameAction.Confirm -> false
                else -> true
            }
        }, modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Column(Modifier.fillMaxSize().background(colors.surface).border(2.dp, colors.focusBorder).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.game_description), color = colors.textPrimary, style = MaterialTheme.typography.headlineMedium)
                Text(description, Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.description_hint), color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                    NeonActionButton(stringResource(R.string.hint_back), onDismiss, focusRequester = close)
                }
            }
        }
    }
}
