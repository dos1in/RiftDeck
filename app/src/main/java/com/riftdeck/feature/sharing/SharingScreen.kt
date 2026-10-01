package com.riftdeck.feature.sharing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.riftdeck.R
import com.riftdeck.core.input.ControllerInput
import com.riftdeck.core.input.GameAction
import com.riftdeck.core.input.LocalControllerInputEnabled
import com.riftdeck.core.ui.components.DeckNoticeDialog
import com.riftdeck.core.ui.components.NeonActionButton
import com.riftdeck.core.ui.components.riftSelectionFrame
import com.riftdeck.core.ui.theme.LocalFrontendTheme
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.awaitCancellation

private data class SharingRow(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val sizeBytes: Long? = null,
    val primary: Boolean = false,
    val enabled: Boolean = true,
    val action: (() -> Unit)? = null,
)

private data class ConflictChoice(val conflict: SaveConflictUi, val useRemote: Boolean)

/** The feature receives immutable presentation data; folder and network work stays in its ViewModel. */
@Composable
fun SharingScreen(
    state: SharingUiState,
    onEnabled: (Boolean) -> Unit,
    onChooseRomFolder: () -> Unit,
    onChooseSaveFolder: () -> Unit,
    onAutoSync: (Boolean) -> Unit,
    onPairingEnabled: (Boolean) -> Unit,
    onPair: (peerId: String, code: String) -> Unit,
    onRefreshRoms: (peerId: String) -> Unit,
    onDownloadRom: (peerId: String, romId: String) -> Unit,
    onSync: (peerId: String?) -> Unit,
    onForget: (peerId: String) -> Unit,
    onResolveConflict: (conflictId: String, useRemote: Boolean) -> Unit,
    onConnectAddress: (address: String, code: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFrontendTheme.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedPeerId by rememberSaveable { mutableStateOf<String?>(null) }
    var pairingPeerId by rememberSaveable { mutableStateOf<String?>(null) }
    var addressDialog by rememberSaveable { mutableStateOf(false) }
    var manualAddress by rememberSaveable { mutableStateOf<String?>(null) }
    var forgetPeerId by rememberSaveable { mutableStateOf<String?>(null) }
    var conflictChoice by remember { mutableStateOf<ConflictChoice?>(null) }
    val tabFocus = remember { List(3) { FocusRequester() } }
    val backFocus = remember { FocusRequester() }
    val bodyFocus = remember { FocusRequester() }
    var bodyFocused by remember { mutableStateOf(false) }
    var focusedControl by rememberSaveable { mutableStateOf("body") }
    var focusRestored by remember { mutableStateOf(false) }
    val latestFocusedControl by rememberUpdatedState(focusedControl)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val peer = state.peers.firstOrNull { it.id == selectedPeerId }
    val pageKey = if (tab == 1 && peer != null) "peer:${peer.id}" else "tab:$tab"
    var selectedRows by rememberSaveable { mutableStateOf(mapOf<String, String>()) }
    val selectedRowId = selectedRows[pageKey]
    val listState = rememberLazyListState()
    val modalOpen = pairingPeerId != null || addressDialog || manualAddress != null ||
        forgetPeerId != null || conflictChoice != null
    val controllerEnabled = LocalControllerInputEnabled.current && !modalOpen
    val unselectedFolder = stringResource(R.string.sharing_folder_not_selected)
    val onlineLabel = stringResource(R.string.sharing_online)
    val offlineLabel = stringResource(R.string.sharing_offline)
    val pairedLabel = stringResource(R.string.sharing_paired)
    val nearbyLabel = stringResource(R.string.sharing_nearby)
    val rows = mutableListOf<SharingRow>()
    when {
        tab == 0 -> {
            rows += SharingRow("device", state.deviceName.ifBlank { stringResource(R.string.sharing_this_device) },
                state.endpoint.takeIf { it.isNotBlank() } ?: stringResource(R.string.sharing_endpoint_pending))
            rows += SharingRow("enabled", stringResource(if (state.enabled) R.string.sharing_stop else R.string.sharing_start),
                primary = true, enabled = state.enabled || state.status != SharingStatus.Starting,
                action = { onEnabled(!state.enabled) })
            rows += SharingRow("rom-folder", stringResource(R.string.sharing_choose_rom_folder),
                state.romFolderLabel ?: unselectedFolder, action = onChooseRomFolder)
            rows += SharingRow("save-folder", stringResource(R.string.sharing_choose_save_folder),
                state.saveFolderLabel ?: unselectedFolder, action = onChooseSaveFolder)
            rows += SharingRow("autosync", stringResource(if (state.autoSync) R.string.sharing_autosync_on else R.string.sharing_autosync_off),
                stringResource(R.string.sharing_autosync_hint), action = { onAutoSync(!state.autoSync) })
            rows += SharingRow("pairing", stringResource(if (state.pairingEnabled) R.string.sharing_close_pairing else R.string.sharing_open_pairing),
                stringResource(R.string.sharing_pairing_hint), enabled = state.enabled && !state.busy,
                action = { onPairingEnabled(!state.pairingEnabled) })
            rows += SharingRow("sync-all", stringResource(R.string.sharing_sync_now),
                enabled = state.enabled && !state.busy && state.saveFolderLabel != null,
                action = { onSync(null) })
            rows += SharingRow("folder-help", stringResource(R.string.sharing_folder_help_title),
                stringResource(R.string.sharing_folder_help))
        }
        tab == 1 && peer != null -> {
            val reachable = peer.online || !peer.address.isNullOrBlank()
            rows += SharingRow("peer-title", peer.name,
                listOf(if (peer.online) onlineLabel else offlineLabel,
                    if (peer.paired) pairedLabel else nearbyLabel, peer.address).filterNotNull().joinToString(" · "))
            if (peer.paired && !peer.online && !peer.address.isNullOrBlank()) {
                rows += SharingRow("cached-address", stringResource(R.string.sharing_cached_address_title),
                    stringResource(R.string.sharing_cached_address_hint))
            }
            if (!peer.paired) {
                rows += SharingRow("pair", stringResource(R.string.sharing_pair_device),
                    stringResource(R.string.sharing_pair_instructions), primary = true,
                    enabled = state.enabled && peer.online && !state.busy,
                    action = { pairingPeerId = peer.id })
            } else {
                rows += SharingRow("peer-sync", stringResource(R.string.sharing_sync_with_device),
                    stringResource(R.string.sharing_peer_sync_hint), enabled = state.enabled && reachable && !state.busy && state.saveFolderLabel != null,
                    action = { onSync(peer.id) })
                rows += SharingRow("peer-roms", stringResource(if (peer.romsLoading) R.string.sharing_loading_roms else R.string.sharing_refresh_roms),
                    stringResource(R.string.sharing_roms_hint), enabled = state.enabled && reachable && !state.busy && !peer.romsLoading,
                    action = { onRefreshRoms(peer.id) })
                if (peer.romsLoaded && peer.roms.isEmpty()) {
                    rows += SharingRow("roms-empty", stringResource(R.string.sharing_no_roms))
                }
                peer.roms.forEach { rom ->
                    rows += SharingRow("rom:${rom.id}", rom.name,
                        stringResource(R.string.sharing_receive_rom), sizeBytes = rom.sizeBytes,
                        enabled = state.enabled && reachable && !state.busy && state.romFolderLabel != null,
                        action = { onDownloadRom(peer.id, rom.id) })
                }
                rows += SharingRow("forget", stringResource(R.string.sharing_forget_device),
                    action = { forgetPeerId = peer.id })
            }
            rows += SharingRow("devices-back", stringResource(R.string.sharing_back_to_devices),
                action = { selectedPeerId = null })
        }
        tab == 1 -> {
            rows += SharingRow("connect-address", stringResource(R.string.sharing_connect_address),
                stringResource(R.string.sharing_discovery_hint), enabled = state.enabled && !state.busy,
                action = { addressDialog = true })
            if (state.peers.isEmpty()) rows += SharingRow("peers-empty", stringResource(R.string.sharing_no_devices),
                stringResource(R.string.sharing_no_devices_hint))
            state.peers.forEach { device ->
                rows += SharingRow("peer:${device.id}", device.name,
                    listOf(if (device.online) onlineLabel else offlineLabel,
                        if (device.paired) pairedLabel else nearbyLabel, device.address).filterNotNull().joinToString(" · "),
                    action = { selectedPeerId = device.id })
            }
        }
        else -> {
            rows += SharingRow("conflict-help", stringResource(R.string.sharing_conflicts_help_title),
                stringResource(R.string.sharing_conflicts_help))
            if (state.conflicts.isEmpty()) rows += SharingRow("no-conflicts", stringResource(R.string.sharing_no_conflicts))
            state.conflicts.forEach { conflict ->
                val conflictPeer = state.peers.firstOrNull { it.id == conflict.peerId }
                val canResolve = state.enabled && !state.busy && state.saveFolderLabel != null &&
                    conflictPeer != null && (conflictPeer.online || !conflictPeer.address.isNullOrBlank())
                rows += SharingRow("conflict:${conflict.id}", conflict.path, conflict.peerName)
                rows += SharingRow("local:${conflict.id}", stringResource(R.string.sharing_keep_local),
                    enabled = canResolve, action = { conflictChoice = ConflictChoice(conflict, false) })
                rows += SharingRow("remote:${conflict.id}", stringResource(R.string.sharing_use_remote),
                    enabled = canResolve, action = { conflictChoice = ConflictChoice(conflict, true) })
            }
            rows += SharingRow("conflicts-back", stringResource(R.string.sharing_back_to_setup), action = { tab = 0 })
        }
    }
    // One focus target and explicit row selection keep navigation deterministic even when a
    // remote library contains thousands of files whose LazyColumn rows are not composed yet.
    val actions = rows.filter { it.action != null }
    val activeId = selectedRowId?.takeIf { id -> actions.any { it.id == id } } ?: actions.firstOrNull()?.id
    val activeIndex = actions.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
    val activeRow = actions.getOrNull(activeIndex)
    fun select(id: String) { selectedRows = selectedRows + (pageKey to id) }
    fun move(delta: Int) {
        if (actions.isNotEmpty()) select(actions[(activeIndex + delta).coerceIn(0, actions.lastIndex)].id)
    }
    fun back() {
        if (tab == 1 && selectedPeerId != null) selectedPeerId = null else onBack()
    }
    BackHandler(enabled = controllerEnabled, onBack = ::back)
    LaunchedEffect(lifecycle, modalOpen, pageKey) {
        if (!modalOpen) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Automatic focus can arrive before the first resumed frame. Keep the saved target
            // intact until our explicit request, including returns from the folder picker.
            focusRestored = false
            val restoreTarget = latestFocusedControl
            try {
                withFrameNanos { }
                when {
                    restoreTarget == "back" -> backFocus.requestFocus()
                    restoreTarget.startsWith("tab:") -> tabFocus[tab].requestFocus()
                    else -> bodyFocus.requestFocus()
                }
                focusRestored = true
                awaitCancellation()
            } finally {
                focusRestored = false
            }
        }
    }
    LaunchedEffect(activeId, pageKey, bodyFocused) {
        // Retain a saved row while preferences or a remote catalog are still loading. Only
        // explicit navigation replaces it with a newly selected row.
        if (bodyFocused && activeId != null) {
            withFrameNanos { }
            val index = rows.indexOfFirst { it.id == activeId }
            val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            if (index >= 0 && (visible == null || visible.offset < listState.layoutInfo.viewportStartOffset ||
                    visible.offset + visible.size > listState.layoutInfo.viewportEndOffset)) {
                listState.scrollToItem(index)
            }
        }
    }
    ControllerInput(enabled = controllerEnabled, modifier = modifier.fillMaxSize(), onAction = { action ->
        when (action) {
            GameAction.Back -> { back(); true }
            GameAction.PreviousCategory, GameAction.NextCategory -> {
                tab = (tab + if (action == GameAction.PreviousCategory) -1 else 1).coerceIn(0, 2)
                selectedPeerId = null
                if (!bodyFocused) tabFocus[tab].requestFocus()
                true
            }
            GameAction.Up -> if (bodyFocused) {
                if (activeIndex == 0) tabFocus[tab].requestFocus() else move(-1)
                true
            } else false
            GameAction.Down -> if (bodyFocused) { move(1); true } else false
            GameAction.PreviousPage -> { if (bodyFocused) move(-6); true }
            GameAction.NextPage -> { if (bodyFocused) move(6); true }
            GameAction.Confirm -> if (bodyFocused) { if (activeRow?.enabled == true) activeRow.action?.invoke(); true } else false
            GameAction.Menu, GameAction.Search, GameAction.Details, GameAction.Favorite -> true
            else -> false
        }
    }) {
        Column(Modifier.fillMaxSize().background(colors.background).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.sharing_title), color = colors.textPrimary,
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                NeonActionButton(stringResource(R.string.hint_back), ::back, Modifier.width(100.dp),
                    focusRequester = backFocus, left = tabFocus.last(), down = bodyFocus,
                    onFocused = { if (focusRestored) focusedControl = "back" })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.sharing_setup, R.string.sharing_devices, R.string.sharing_conflicts).forEachIndexed { index, label ->
                    NeonActionButton(stringResource(label), { tab = index; selectedPeerId = null; bodyFocus.requestFocus() },
                        Modifier.weight(1f), selected = tab == index, focusRequester = tabFocus[index],
                        left = tabFocus.getOrNull(index - 1), right = tabFocus.getOrNull(index + 1) ?: backFocus,
                        up = backFocus, down = bodyFocus,
                        onFocused = { if (focusRestored) { tab = index; selectedPeerId = null; bodyFocused = false; focusedControl = "tab:$index" } })
                }
            }
            SharingStatusLine(state)
            if (state.pairingEnabled && state.pairingCode != null) {
                Text(stringResource(R.string.sharing_pairing_code_value,
                    state.pairingCode.filterNot(Char::isWhitespace).chunked(4).joinToString(" ")),
                    color = colors.accentText, style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace, maxLines = 2)
            }
            val selectionLabel = activeRow?.title ?: stringResource(R.string.sharing_title)
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().focusRequester(bodyFocus)
                    .focusProperties { canFocus = true; up = tabFocus[tab]; down = FocusRequester.Cancel; left = FocusRequester.Cancel; right = FocusRequester.Cancel }
                    .onFocusChanged { bodyFocused = it.isFocused; if (it.isFocused && focusRestored) focusedControl = "body" }.focusable()
                    .semantics { contentDescription = selectionLabel }) {
                items(rows, key = { it.id }) { row ->
                    SharingActionRow(row, bodyFocused && row.id == activeId) {
                        select(row.id)
                        bodyFocus.requestFocus()
                        if (row.enabled) row.action?.invoke()
                    }
                }
            }
            Text(stringResource(R.string.sharing_controller_hint), color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
    pairingPeerId?.let { id ->
        SharingEntryDialog(stringResource(R.string.sharing_pair_device), stringResource(R.string.sharing_pair_instructions),
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567", 8, 16, true,
            onSubmit = { code -> pairingPeerId = null; onPair(id, code) }, onDismiss = { pairingPeerId = null })
    }
    if (addressDialog) {
        SharingEntryDialog(stringResource(R.string.sharing_connect_address), stringResource(R.string.sharing_address_hint),
            "0123456789.:", 6, 80, false,
            onSubmit = { address -> addressDialog = false; manualAddress = address }, onDismiss = { addressDialog = false })
    }
    manualAddress?.let { address ->
        SharingEntryDialog(stringResource(R.string.sharing_pair_device), stringResource(R.string.sharing_pair_instructions),
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567", 8, 16, true,
            onSubmit = { code -> manualAddress = null; onConnectAddress(address, code) }, onDismiss = { manualAddress = null })
    }
    forgetPeerId?.let { id ->
        DeckNoticeDialog(stringResource(R.string.sharing_forget_device), stringResource(R.string.sharing_forget_confirm),
            stringResource(R.string.sharing_forget_device), onConfirm = { forgetPeerId = null; selectedPeerId = null; onForget(id) },
            onDismiss = { forgetPeerId = null }, focusCancel = true)
    }
    conflictChoice?.let { choice ->
        DeckNoticeDialog(stringResource(if (choice.useRemote) R.string.sharing_use_remote else R.string.sharing_keep_local),
            stringResource(R.string.sharing_resolve_confirm, choice.conflict.path),
            stringResource(R.string.sharing_resolve),
            onConfirm = { conflictChoice = null; onResolveConflict(choice.conflict.id, choice.useRemote) },
            onDismiss = { conflictChoice = null }, focusCancel = true)
    }
}

@Composable
private fun SharingActionRow(row: SharingRow, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalFrontendTheme.current
    val isAction = row.action != null
    Column(Modifier.fillMaxWidth().background(if (selected) colors.surfaceElevated else colors.surface)
        .then(if (isAction) Modifier.riftSelectionFrame(selected, false) else Modifier)
        // The list owns controller focus; pointer access activates the same selected row.
        .then(if (isAction) Modifier.focusProperties { canFocus = false }
            .clickable(enabled = row.enabled, onClick = onClick) else Modifier)
        .semantics { if (isAction) { role = Role.Button; this.selected = selected; if (!row.enabled) disabled() } }
        .padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(row.title, color = when { !row.enabled -> colors.textSecondary; selected || row.primary -> colors.accentText; else -> colors.textPrimary },
            style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        row.subtitle?.let {
            Text(it, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall,
                maxLines = if (isAction) 2 else 5, overflow = TextOverflow.Ellipsis,
                fontFamily = if (row.id == "pairing-code") FontFamily.Monospace else null)
        }
        row.sizeBytes?.let { bytes ->
            Text(stringResource(R.string.sharing_rom_size, bytes / (1024.0 * 1024.0)), color = colors.textSecondary,
                style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SharingStatusLine(state: SharingUiState) {
    val colors = LocalFrontendTheme.current
    val label = when (state.status) {
        SharingStatus.Idle -> R.string.sharing_status_idle
        SharingStatus.Starting -> R.string.sharing_status_starting
        SharingStatus.Ready -> R.string.sharing_status_ready
        SharingStatus.Pairing -> R.string.sharing_status_pairing
        SharingStatus.Paired -> R.string.sharing_status_paired
        SharingStatus.Transferring -> R.string.sharing_status_transferring
        SharingStatus.Syncing -> R.string.sharing_status_syncing
        SharingStatus.Synced -> R.string.sharing_status_synced
        SharingStatus.Conflict -> R.string.sharing_status_conflict
        SharingStatus.Error -> R.string.sharing_status_error
        SharingStatus.FolderUnavailable -> R.string.sharing_status_folder_unavailable
        SharingStatus.PeerUnavailable -> R.string.sharing_status_peer_unavailable
    }
    val syncTime = remember(state.lastSyncMillis) {
        state.lastSyncMillis?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), modifier = Modifier.weight(1f), maxLines = 2,
            color = if (state.status in listOf(SharingStatus.Error, SharingStatus.FolderUnavailable, SharingStatus.PeerUnavailable)) colors.error else colors.secondary,
            style = MaterialTheme.typography.bodySmall)
        if (state.busy && state.totalBytes > 0) {
            val percentage = (state.progressBytes.coerceAtLeast(0).coerceAtMost(state.totalBytes) / state.totalBytes.toDouble() * 100).toInt()
            Text(stringResource(R.string.sharing_progress, percentage), color = colors.accentText, style = MaterialTheme.typography.labelMedium)
        } else if (syncTime != null) {
            Text(stringResource(R.string.sharing_last_sync, syncTime), color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Built-in keypad allows pairing and manual IPv4 entry using D-pad and A alone. */
@Composable
private fun SharingEntryDialog(
    title: String,
    hint: String,
    alphabet: String,
    columns: Int,
    maxLength: Int,
    pairingCode: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalFrontendTheme.current
    var value by rememberSaveable { mutableStateOf("") }
    val keys = remember(alphabet) { List(alphabet.length) { FocusRequester() } }
    val controls = remember { List(4) { FocusRequester() } }
    val input = remember { FocusRequester() }
    var inputFocused by remember { mutableStateOf(false) }
    fun normalized(text: String) = if (pairingCode) text.uppercase().filter { it in alphabet }.take(maxLength)
        else text.filter { it.isLetterOrDigit() || it in ".:-[]" }.take(maxLength)
    fun submit() {
        if ((!pairingCode && value.isNotBlank()) || (pairingCode && value.length == maxLength)) onSubmit(value.trim())
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { withFrameNanos { }; keys.first().requestFocus() }
        ControllerInput(enabled = true, modifier = Modifier.padding(12.dp).widthIn(max = 620.dp).fillMaxWidth(), onAction = {
            when (it) {
                GameAction.Back -> { onDismiss(); true }
                GameAction.Menu -> { submit(); true }
                else -> false
            }
        }) {
            Column(Modifier.background(colors.surface).border(2.dp, colors.focusBorder)
                .verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = colors.textPrimary, style = MaterialTheme.typography.titleLarge)
                Text(hint, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                BasicTextField(value = value, onValueChange = { value = normalized(it) }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(colors.textPrimary),
                    keyboardOptions = KeyboardOptions(keyboardType = if (pairingCode) KeyboardType.Ascii else KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(input)
                        .focusProperties { up = FocusRequester.Cancel; down = keys.first(); left = FocusRequester.Cancel; right = FocusRequester.Cancel }
                        .onFocusChanged { inputFocused = it.isFocused }.semantics { contentDescription = title }
                        .border(if (inputFocused) 2.dp else 1.dp, if (inputFocused) colors.focusBorder else colors.outline).padding(8.dp))
                alphabet.toList().chunked(columns).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEachIndexed { column, character ->
                            val index = rowIndex * columns + column
                            NeonActionButton(character.toString(), { if (value.length < maxLength) value += character }, Modifier.weight(1f),
                                focusRequester = keys[index], left = if (column > 0) keys[index - 1] else null,
                                right = if (column < row.lastIndex) keys[index + 1] else null,
                                up = keys.getOrNull(index - columns) ?: input,
                                down = keys.getOrNull(index + columns) ?: controls[(column * controls.size / columns).coerceAtMost(controls.lastIndex)])
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val labels = listOf(R.string.sharing_delete, R.string.sharing_clear, R.string.sharing_connect, R.string.sharing_cancel)
                    labels.forEachIndexed { index, label ->
                        NeonActionButton(stringResource(label), {
                            when (index) {
                                0 -> value = value.dropLast(1)
                                1 -> value = ""
                                2 -> submit()
                                3 -> onDismiss()
                            }
                        }, Modifier.weight(1f), primary = index == 2, focusRequester = controls[index],
                            left = controls.getOrNull(index - 1), right = controls.getOrNull(index + 1),
                            up = keys[(alphabet.length - columns + index * columns / controls.size).coerceIn(0, keys.lastIndex)])
                    }
                }
                Text(stringResource(R.string.sharing_entry_length, value.length, maxLength), color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
