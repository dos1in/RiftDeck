package com.riftdeck.feature.search

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.riftdeck.R
import com.riftdeck.core.input.ControllerInput
import com.riftdeck.core.input.GameAction
import com.riftdeck.core.ui.components.NeonActionButton
import com.riftdeck.core.ui.theme.LocalFrontendTheme
import kotlinx.coroutines.awaitCancellation

@Composable
fun GameSearchDialog(initialQuery: String, onSearch: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalFrontendTheme.current
    val query = rememberTextFieldState(initialText = initialQuery.take(120))
    var systemInput by rememberSaveable { mutableStateOf(false) }
    var returnToKeyboardEntry by remember { mutableStateOf(false) }
    val characters = remember { "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toList() }
    val keys = remember { characters.map { FocusRequester() } }
    val actions = remember { List(5) { FocusRequester() } }
    val input = remember { FocusRequester() }
    val keyboardEntry = remember { FocusRequester() }
    val keyboardSettings = remember { FocusRequester() }
    var inputFocused by remember { mutableStateOf(false) }
    val searchLabel = stringResource(R.string.search_games)
    fun leaveSystemInput() { returnToKeyboardEntry = true; systemInput = false }
    fun insert(text: String) {
        query.edit {
            if (length - selection.length + text.length <= 120) {
                val end = selection.min + text.length
                replace(selection.min, selection.max, text)
                placeCursorBeforeCharAt(end)
            }
        }
    }
    Dialog(onDismissRequest = { if (systemInput) leaveSystemInput() else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // The keyboard controller belongs to this dialog's window, not the activity behind it.
        val keyboard = LocalSoftwareKeyboardController.current
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        var settingsUnavailable by remember { mutableStateOf(false) }
        fun submit() { keyboard?.hide(); onSearch(query.text.toString().trim()) }
        fun dismiss() { keyboard?.hide(); onDismiss() }
        LaunchedEffect(systemInput, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                withFrameNanos { }
                if (systemInput) {
                    input.requestFocus()
                    withFrameNanos { }
                    keyboard?.show()
                } else {
                    keyboard?.hide()
                    if (returnToKeyboardEntry) keyboardEntry.requestFocus() else keys.first().requestFocus()
                }
                awaitCancellation()
            }
        }
        DisposableEffect(keyboard) { onDispose { keyboard?.hide() } }
        ControllerInput(enabled = !systemInput, onAction = {
            when (it) {
                GameAction.Back -> { dismiss(); true }
                GameAction.Menu, GameAction.Search -> { submit(); true }
                else -> false
            }
        }, modifier = Modifier.padding(12.dp).widthIn(max = 720.dp).fillMaxWidth()
            .onPreviewKeyEvent {
                if (systemInput && it.type == KeyEventType.KeyDown && (it.key == Key.ButtonB || it.key == Key.Back)) {
                    leaveSystemInput()
                    true
                } else false
            }) {
            Column(Modifier.background(colors.surface).border(2.dp, colors.focusBorder)
                .verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(searchLabel, Modifier.weight(1f), color = colors.textPrimary, style = MaterialTheme.typography.titleLarge)
                    NeonActionButton(stringResource(if (systemInput) R.string.search_controller_keyboard else R.string.search_system_input),
                        { if (systemInput) leaveSystemInput() else systemInput = true }, Modifier.weight(1f),
                        focusRequester = keyboardEntry, right = input,
                        down = if (systemInput) input else keys.first(), up = FocusRequester.Cancel)
                }
                BasicTextField(state = query, lineLimits = TextFieldLineLimits.SingleLine,
                    inputTransformation = InputTransformation.maxLength(120),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.textPrimary), keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text, imeAction = ImeAction.Search,
                        showKeyboardOnFocus = systemInput, hintLocales = LocaleList(Locale("zh-CN"), Locale("en-US"))),
                    onKeyboardAction = { submit() },
                    modifier = Modifier.fillMaxWidth().focusRequester(input)
                        .focusProperties { down = if (systemInput) keyboardSettings else keys.first(); up = keyboardEntry }
                        .onFocusChanged { inputFocused = it.isFocused; if (it.isFocused) systemInput = true }
                        .semantics { contentDescription = searchLabel }
                        .border(if (inputFocused) 2.dp else 1.dp, if (inputFocused) colors.focusBorder else colors.outline)
                        .padding(10.dp),
                    decorator = { field ->
                        Box {
                            if (query.text.isEmpty()) Text(stringResource(R.string.search_hint), color = colors.textSecondary,
                                style = MaterialTheme.typography.bodyLarge)
                            field()
                        }
                    })
                if (systemInput) {
                    Text(stringResource(if (settingsUnavailable) R.string.search_keyboard_unavailable else R.string.search_system_input_hint),
                        color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NeonActionButton(stringResource(R.string.search_keyboard_settings), {
                            keyboard?.hide()
                            try { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
                            catch (_: ActivityNotFoundException) { settingsUnavailable = true }
                            catch (_: SecurityException) { settingsUnavailable = true }
                        }, Modifier.weight(1f), focusRequester = keyboardSettings, up = input, right = actions[3])
                        NeonActionButton(stringResource(R.string.search_apply), ::submit, Modifier.weight(1f), primary = true,
                            focusRequester = actions[3], up = input, left = keyboardSettings, right = actions[4])
                        NeonActionButton(stringResource(R.string.search_cancel), ::dismiss, Modifier.weight(1f),
                            focusRequester = actions[4], up = input, left = actions[3])
                    }
                } else {
                    characters.chunked(10).forEachIndexed { row, entries ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            entries.forEachIndexed { column, character ->
                                val index = row * 10 + column
                                NeonActionButton(character.toString(), { insert(character.toString()) }, Modifier.weight(1f),
                                    focusRequester = keys[index], left = if (column > 0) keys[index - 1] else null,
                                    right = if (column < entries.lastIndex) keys[index + 1] else null,
                                    up = keys.getOrNull(index - 10) ?: keyboardEntry,
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
                                    0 -> query.edit {
                                        if (!selection.collapsed) delete(selection.min, selection.max)
                                        else if (selection.start > 0) {
                                            val end = selection.start
                                            val start = Character.offsetByCodePoints(asCharSequence(), end, -1)
                                            delete(start, end)
                                            placeCursorBeforeCharAt(start)
                                        }
                                    }
                                    1 -> insert(" ")
                                    2 -> query.edit { delete(0, length) }
                                    3 -> submit()
                                    4 -> dismiss()
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
}
