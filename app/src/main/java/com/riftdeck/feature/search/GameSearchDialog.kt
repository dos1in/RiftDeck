package com.riftdeck.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.riftdeck.R
import com.riftdeck.core.input.ControllerInput
import com.riftdeck.core.input.GameAction
import com.riftdeck.core.ui.components.NeonActionButton
import com.riftdeck.core.ui.theme.LocalFrontendTheme

@Composable
fun GameSearchDialog(initialQuery: String, onSearch: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalFrontendTheme.current
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val characters = remember { "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toList() }
    val keys = remember { characters.map { FocusRequester() } }
    val actions = remember { List(5) { FocusRequester() } }
    val input = remember { FocusRequester() }
    var inputFocused by remember { mutableStateOf(false) }
    val searchLabel = stringResource(R.string.search_games)
    fun submit() { onSearch(query.trim()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { withFrameNanos { }; keys.first().requestFocus() }
        ControllerInput(enabled = true, onAction = {
            when (it) {
                GameAction.Back -> { onDismiss(); true }
                GameAction.Menu, GameAction.Search -> { submit(); true }
                else -> false
            }
        }, modifier = Modifier.padding(12.dp).widthIn(max = 720.dp).fillMaxWidth()) {
            Column(Modifier.background(colors.surface).border(2.dp, colors.focusBorder)
                .verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(searchLabel, color = colors.textPrimary, style = MaterialTheme.typography.titleLarge)
                BasicTextField(value = query, onValueChange = { query = it.take(120) }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.textPrimary), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(input)
                        .focusProperties { down = keys.first(); up = FocusRequester.Cancel }
                        .onFocusChanged { inputFocused = it.isFocused }
                        .semantics { contentDescription = searchLabel }
                        .border(if (inputFocused) 2.dp else 1.dp, if (inputFocused) colors.focusBorder else colors.outline)
                        .padding(10.dp),
                    decorationBox = { field ->
                        Box {
                            if (query.isEmpty()) Text(stringResource(R.string.search_hint), color = colors.textSecondary,
                                style = MaterialTheme.typography.bodyLarge)
                            field()
                        }
                    })
                characters.chunked(10).forEachIndexed { row, entries ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        entries.forEachIndexed { column, character ->
                            val index = row * 10 + column
                            NeonActionButton(character.toString(), { if (query.length < 120) query += character }, Modifier.weight(1f),
                                focusRequester = keys[index], left = if (column > 0) keys[index - 1] else null,
                                right = if (column < entries.lastIndex) keys[index + 1] else null,
                                up = keys.getOrNull(index - 10) ?: input,
                                down = keys.getOrNull(index + 10) ?: actions[column / 2])
                        }
                        repeat(10 - entries.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                val labels = listOf(R.string.search_delete, R.string.search_space, R.string.search_clear,
                    R.string.search_apply, R.string.search_cancel)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    labels.forEachIndexed { index, label ->
                        NeonActionButton(stringResource(label), {
                            when (index) {
                                0 -> if (query.isNotEmpty()) query = query.substring(0, query.offsetByCodePoints(query.length, -1))
                                1 -> if (query.length < 120) query += " "
                                2 -> query = ""
                                3 -> submit()
                                4 -> onDismiss()
                            }
                        }, Modifier.weight(1f), primary = index == 3, focusRequester = actions[index],
                            left = actions.getOrNull(index - 1), right = actions.getOrNull(index + 1),
                            up = keys[(30 + index).coerceAtMost(keys.lastIndex)])
                    }
                }
            }
        }
    }
}
