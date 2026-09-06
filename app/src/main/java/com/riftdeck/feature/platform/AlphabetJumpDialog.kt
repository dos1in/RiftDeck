package com.riftdeck.feature.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.riftdeck.core.model.Game
import com.riftdeck.core.ui.components.NeonActionButton
import com.riftdeck.core.ui.theme.LocalFrontendTheme

/** Positions are taken from the displayed list, including descending order and active filters. */
fun alphabetPositions(games: List<Game>): Map<Char, Int> = buildMap {
    games.forEachIndexed { index, game ->
        val letter = game.sortTitle.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'
        if (letter !in this) put(letter, index)
    }
}

@Composable
fun AlphabetJumpDialog(letters: List<Char>, onChoose: (Char) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalFrontendTheme.current
    val keys = remember(letters) { letters.map { FocusRequester() } }
    val cancel = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(keys) { withFrameNanos { }; (keys.firstOrNull() ?: cancel).requestFocus() }
        ControllerInput(enabled = true, onAction = {
            if (it == GameAction.Back) { onDismiss(); true } else false
        }, modifier = Modifier.padding(12.dp).widthIn(max = 640.dp).fillMaxWidth()) {
            Column(Modifier.background(colors.surface).border(2.dp, colors.focusBorder)
                .verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.alphabet_jump_title), color = colors.textPrimary, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.alphabet_jump_hint), color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                letters.chunked(9).forEachIndexed { row, values ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        values.forEachIndexed { column, letter ->
                            val index = row * 9 + column
                            NeonActionButton(letter.toString(), { onChoose(letter) }, Modifier.weight(1f),
                                focusRequester = keys[index],
                                left = if (column > 0) keys[index - 1] else FocusRequester.Cancel,
                                right = if (column < values.lastIndex) keys[index + 1] else FocusRequester.Cancel,
                                up = keys.getOrNull(index - 9) ?: FocusRequester.Cancel,
                                down = keys.getOrNull(index + 9) ?: cancel)
                        }
                        repeat(9 - values.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                NeonActionButton(stringResource(R.string.search_cancel), onDismiss, Modifier.fillMaxWidth(),
                    focusRequester = cancel, up = keys.lastOrNull() ?: FocusRequester.Cancel)
            }
        }
    }
}
