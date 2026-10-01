package com.riftdeck.feature.update

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.riftdeck.R
import com.riftdeck.core.ui.components.DeckNoticeDialog
import com.riftdeck.data.repository.UpdateUiState

/** Root navigation decides when this notice can safely appear and acknowledges either choice. */
@Composable
fun UpdateAvailableDialog(state: UpdateUiState, onOpenSettings: () -> Unit, onLater: () -> Unit) {
    val release = state.release ?: return
    DeckNoticeDialog(
        title = stringResource(R.string.update_available_title),
        message = stringResource(R.string.update_available_message, release.versionName),
        primaryLabel = stringResource(R.string.update_open_settings),
        secondaryLabel = stringResource(R.string.update_later),
        onConfirm = onOpenSettings,
        onDismiss = onLater,
        focusCancel = true,
    )
}
