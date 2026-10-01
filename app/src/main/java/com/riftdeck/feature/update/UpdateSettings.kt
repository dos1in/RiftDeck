package com.riftdeck.feature.update

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.riftdeck.data.repository.UpdateError
import com.riftdeck.data.repository.UpdateStatus
import com.riftdeck.data.repository.UpdateUiState
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

/** Controls 0–3 are Check, automatic checks, download/install, and release notes. */
@Composable
fun UpdateSettings(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onAutoCheck: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    controls: List<FocusRequester>,
    left: FocusRequester,
    after: FocusRequester,
    onFocused: (Int) -> Unit,
    onNotesVisible: (Boolean) -> Unit = {},
) {
    val colors = LocalFrontendTheme.current
    val release = state.release
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val visibility = remember { List(4) { BringIntoViewRequester() } }
    var focusedControl by remember { mutableIntStateOf(-1) }
    var notesOpen by remember { mutableStateOf(false) }
    fun controlModifier(index: Int): Modifier = Modifier.fillMaxWidth()
        .bringIntoViewRequester(visibility[index])
        .onFocusChanged {
            if (it.isFocused) focusedControl = index
            else if (focusedControl == index) focusedControl = -1
        }
    LaunchedEffect(lifecycle, focusedControl, state.status, state.error, state.lastCheckedMillis,
        release?.tag, release?.apkSize) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val index = focusedControl
            if (index >= 0 && (index < 2 || release != null)) {
                // Returning from Android installation settings can add guidance above an
                // already-focused button. Wait for restoration and layout, then reveal it.
                withFrameNanos { }
                withFrameNanos { }
                if (focusedControl == index) visibility[index].bringIntoView()
            }
            awaitCancellation()
        }
    }
    DisposableEffect(notesOpen && release != null) {
        onNotesVisible(notesOpen && release != null)
        onDispose { onNotesVisible(false) }
    }
    LaunchedEffect(release == null, notesOpen) {
        if (release == null && notesOpen) {
            notesOpen = false
            controls[0].requestFocus()
        }
    }
    val lastChecked = remember(state.lastCheckedMillis) {
        state.lastCheckedMillis?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) }
    }
    Text(stringResource(R.string.update_source_hint), color = colors.textSecondary,
        style = MaterialTheme.typography.bodySmall)
    Text(stringResource(updateStatusResource(state.status)), color = when {
        state.status == UpdateStatus.Error -> colors.error
        state.status == UpdateStatus.Available || state.status == UpdateStatus.Ready -> colors.accentText
        else -> colors.secondary
    }, style = MaterialTheme.typography.bodyMedium)
    state.error?.let { error ->
        Text(stringResource(updateErrorResource(error)), color = if (error == UpdateError.PermissionRequired) colors.textSecondary else colors.error,
            style = MaterialTheme.typography.bodySmall)
    }
    if (lastChecked != null) {
        Text(stringResource(R.string.update_last_checked, lastChecked), color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall)
    }
    NeonActionButton(stringResource(if (state.status == UpdateStatus.Checking) R.string.update_checking else R.string.update_check_now),
        { if (state.status != UpdateStatus.Checking && state.status != UpdateStatus.Downloading) onCheck() },
        controlModifier(0), focusRequester = controls[0], left = left, down = controls[1],
        onFocused = { onFocused(0) })
    NeonActionButton(stringResource(if (state.autoCheck) R.string.update_auto_on else R.string.update_auto_off),
        { onAutoCheck(!state.autoCheck) }, controlModifier(1), selected = state.autoCheck,
        focusRequester = controls[1], left = left, up = controls[0], down = if (release != null) controls[2] else after,
        onFocused = { onFocused(1) })
    Text(stringResource(R.string.update_auto_hint), color = colors.textSecondary,
        style = MaterialTheme.typography.bodySmall)
    if (release != null) {
        Text(stringResource(R.string.update_release_info, release.versionName, release.apkSize / (1024.0 * 1024.0)),
            color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
        if (state.status == UpdateStatus.Downloading) {
            val done = state.progressBytes.coerceAtLeast(0)
            if (state.totalBytes > 0) {
                val fraction = (done / state.totalBytes.toDouble()).coerceIn(0.0, 1.0)
                Text(stringResource(R.string.update_download_progress, (fraction * 100).toInt(),
                    done / (1024.0 * 1024.0), state.totalBytes / (1024.0 * 1024.0)),
                    color = colors.accentText, style = MaterialTheme.typography.bodySmall)
                Box(Modifier.fillMaxWidth().height(3.dp).background(colors.outline)) {
                    Box(Modifier.fillMaxWidth(fraction.toFloat()).fillMaxHeight().background(colors.primary))
                }
            } else Text(stringResource(R.string.update_download_bytes, done / (1024.0 * 1024.0)),
                color = colors.accentText, style = MaterialTheme.typography.bodySmall)
        }
        val packageLabel = when (state.status) {
            UpdateStatus.Downloading -> R.string.update_cancel_download
            UpdateStatus.Ready -> R.string.update_install
            else -> R.string.update_download
        }
        NeonActionButton(stringResource(packageLabel), {
            when (state.status) {
                UpdateStatus.Downloading -> onCancel()
                UpdateStatus.Ready -> onInstall()
                UpdateStatus.Checking -> Unit
                else -> onDownload()
            }
        }, controlModifier(2), primary = state.status != UpdateStatus.Downloading,
            focusRequester = controls[2], left = left, up = controls[1], down = controls[3],
            onFocused = { onFocused(2) })
        if (state.status == UpdateStatus.Ready) {
            Text(stringResource(R.string.update_install_hint), color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall)
        }
        NeonActionButton(stringResource(R.string.update_release_notes), { notesOpen = true },
            controlModifier(3), focusRequester = controls[3], left = left, up = controls[2], down = after,
            onFocused = { onFocused(3) })
    }
    if (notesOpen && release != null) {
        UpdateNotesDialog(release.versionName, release.notes) {
            notesOpen = false
            controls[3].requestFocus()
        }
    }
}

private fun updateStatusResource(status: UpdateStatus): Int = when (status) {
    UpdateStatus.Idle -> R.string.update_status_idle
    UpdateStatus.Checking -> R.string.update_checking
    UpdateStatus.Available -> R.string.update_status_available
    UpdateStatus.Latest -> R.string.update_status_latest
    UpdateStatus.NoRelease -> R.string.update_status_no_release
    UpdateStatus.Downloading -> R.string.update_status_downloading
    UpdateStatus.Ready -> R.string.update_status_ready
    UpdateStatus.Error -> R.string.update_status_error
}

private fun updateErrorResource(error: UpdateError): Int = when (error) {
    UpdateError.Network -> R.string.update_error_network
    UpdateError.Storage -> R.string.update_error_storage
    UpdateError.InvalidApk -> R.string.update_error_invalid_apk
    UpdateError.IncompatibleSignature -> R.string.update_error_signature
    UpdateError.PermissionRequired -> R.string.update_error_permission
    UpdateError.InstallerUnavailable -> R.string.update_error_installer
}

@Composable
private fun UpdateNotesDialog(version: String, notes: String, onDismiss: () -> Unit) {
    val colors = LocalFrontendTheme.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val textFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    var textFocused by remember { mutableStateOf(false) }
    val content = notes.ifBlank { stringResource(R.string.update_no_notes) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { withFrameNanos { }; textFocus.requestFocus() }
        ControllerInput(enabled = true, modifier = Modifier.padding(12.dp).widthIn(max = 700.dp).fillMaxWidth(), onAction = {
            when (it) {
                GameAction.Back -> { onDismiss(); true }
                GameAction.Up -> if (textFocused) { scope.launch { scroll.scrollTo((scroll.value - 64).coerceAtLeast(0)) }; true } else false
                GameAction.Down -> if (textFocused) {
                    if (scroll.value == scroll.maxValue) closeFocus.requestFocus()
                    else scope.launch { scroll.scrollTo((scroll.value + 64).coerceAtMost(scroll.maxValue)) }
                    true
                } else false
                GameAction.PreviousPage -> { scope.launch { scroll.scrollTo((scroll.value - 240).coerceAtLeast(0)) }; true }
                GameAction.NextPage -> { scope.launch { scroll.scrollTo((scroll.value + 240).coerceAtMost(scroll.maxValue)) }; true }
                GameAction.Confirm -> textFocused
                else -> false
            }
        }) {
            Column(Modifier.background(colors.surface).border(2.dp, colors.focusBorder).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.update_notes_title, version), color = colors.textPrimary,
                    style = MaterialTheme.typography.titleLarge)
                Box(Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 240.dp).focusRequester(textFocus)
                    .focusProperties { canFocus = true; up = FocusRequester.Cancel; down = closeFocus; left = closeFocus; right = closeFocus }
                    .onFocusChanged { textFocused = it.isFocused }.focusable()
                    .border(if (textFocused) 2.dp else 1.dp, if (textFocused) colors.focusBorder else colors.outline)
                    .semantics { contentDescription = content }.padding(10.dp).verticalScroll(scroll)) {
                    Text(content, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                }
                NeonActionButton(stringResource(R.string.update_notes_close), onDismiss, Modifier.fillMaxWidth(),
                    focusRequester = closeFocus, up = textFocus)
            }
        }
    }
}
