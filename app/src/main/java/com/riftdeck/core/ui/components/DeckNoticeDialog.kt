package com.riftdeck.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import com.riftdeck.core.ui.theme.LocalFrontendTheme

@Composable
fun DeckNoticeDialog(title: String, message: String, primaryLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalFrontendTheme.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val confirm = remember { FocusRequester() }
        val cancel = remember { FocusRequester() }
        LaunchedEffect(Unit) { withFrameNanos { }; confirm.requestFocus() }
        ControllerInput(onAction = {
            when (it) {
                GameAction.Back -> { onDismiss(); true }
                GameAction.Up, GameAction.Down, GameAction.Left, GameAction.Right, GameAction.Confirm -> false
                else -> true
            }
        }, enabled = true, modifier = Modifier.padding(24.dp).widthIn(max = 520.dp).fillMaxWidth()) {
            Column(Modifier.background(colors.surface).border(2.dp, colors.primary).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, color = colors.textPrimary, style = MaterialTheme.typography.headlineMedium)
                Text(message, color = colors.textSecondary, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeonActionButton(primaryLabel, onConfirm, Modifier.weight(1f), primary = true, focusRequester = confirm, right = cancel)
                    NeonActionButton(stringResource(R.string.hint_back), onDismiss, Modifier.weight(0.6f), focusRequester = cancel, left = confirm)
                }
            }
        }
    }
}
