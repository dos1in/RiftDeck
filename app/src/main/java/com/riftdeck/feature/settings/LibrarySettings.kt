package com.riftdeck.feature.settings

import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riftdeck.R
import com.riftdeck.data.scanner.ScanState
import com.riftdeck.core.ui.components.NeonActionButton
import com.riftdeck.core.ui.theme.LocalFrontendTheme

fun folderLabel(uri: String): String = runCatching {
    DocumentsContract.getTreeDocumentId(Uri.parse(uri)).substringAfterLast('/').substringAfterLast(':')
        .ifBlank { DocumentsContract.getTreeDocumentId(Uri.parse(uri)) }
}.getOrDefault(uri)

@Composable
fun LibrarySettings(folders: Set<String>, scan: ScanState, onAdd: () -> Unit, onRescan: () -> Unit,
    onCancel: () -> Unit, onRemove: (String) -> Unit, first: FocusRequester, left: FocusRequester,
    onFocused: () -> Unit) {
    val colors = LocalFrontendTheme.current
    val sorted = remember(folders) { folders.sorted() }
    var previousFolderCount by remember { mutableIntStateOf(sorted.size) }
    LaunchedEffect(sorted) {
        if (sorted.size < previousFolderCount) first.requestFocus()
        previousFolderCount = sorted.size
    }
    val rescan = remember { FocusRequester() }
    val folderFocus = remember(sorted) { sorted.associateWith { FocusRequester() } }
    ScanStatus(scan)
    NeonActionButton(stringResource(R.string.add_rom_folder), onAdd, Modifier.fillMaxWidth(), primary = true,
        focusRequester = first, left = left, down = if (folders.isNotEmpty()) rescan else null, onFocused = onFocused)
    if (folders.isNotEmpty()) {
        NeonActionButton(stringResource(if (scan.running) R.string.cancel_scan else R.string.rescan_library),
            if (scan.running) onCancel else onRescan, Modifier.fillMaxWidth(), focusRequester = rescan,
            left = left, up = first, down = sorted.firstOrNull()?.let { folderFocus.getValue(it) }, onFocused = onFocused)
        sorted.forEachIndexed { index, uri ->
            key(uri) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (uri in scan.failedFolders) Text(stringResource(R.string.folder_unavailable),
                        color = colors.accentText, style = MaterialTheme.typography.labelMedium)
                    NeonActionButton(stringResource(R.string.remove_rom_folder, folderLabel(uri)), { onRemove(uri) },
                        Modifier.fillMaxWidth(), focusRequester = folderFocus.getValue(uri), left = left,
                        up = sorted.getOrNull(index - 1)?.let { folderFocus.getValue(it) } ?: rescan,
                        down = sorted.getOrNull(index + 1)?.let { folderFocus.getValue(it) }, onFocused = onFocused)
                }
            }
        }
    }
}
