package com.riftdeck.feature.settings

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.riftdeck.BuildConfig
import com.riftdeck.R
import com.riftdeck.core.ui.components.MetadataValue
import com.riftdeck.core.ui.components.NeonActionButton
import com.riftdeck.core.ui.theme.LocalFrontendTheme
import com.riftdeck.data.repository.UpdateUiState
import com.riftdeck.feature.update.UpdateSettings

@Composable
internal fun AboutSettings(
    controls: List<FocusRequester>,
    category: FocusRequester,
    onFocused: (Int) -> Unit,
    updateState: UpdateUiState,
    onCheckUpdate: () -> Unit,
    onAutoCheckUpdates: (Boolean) -> Unit,
    onDownloadUpdate: () -> Unit,
    onCancelUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onNotesVisible: (Boolean) -> Unit,
) {
    val colors = LocalFrontendTheme.current
    val handler = LocalUriHandler.current
    val context = LocalContext.current
    val repository = stringResource(R.string.repository_url)
    val linkError = stringResource(R.string.about_link_error)
    Row(Modifier.fillMaxWidth()) {
        MetadataValue(stringResource(R.string.about_version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", Modifier.weight(1f))
        MetadataValue(stringResource(R.string.about_license), stringResource(R.string.license_name), Modifier.weight(1f))
    }
    UpdateSettings(updateState, onCheckUpdate, onAutoCheckUpdates, onDownloadUpdate, onCancelUpdate,
        onInstallUpdate, controls.take(4), category, controls[4], onFocused, onNotesVisible)
    Text(repository, color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
    NeonActionButton(stringResource(R.string.about_repository), {
        try { handler.openUri(repository) }
        catch (_: ActivityNotFoundException) { Toast.makeText(context, linkError, Toast.LENGTH_SHORT).show() }
        catch (_: IllegalArgumentException) { Toast.makeText(context, linkError, Toast.LENGTH_SHORT).show() }
    }, Modifier.fillMaxWidth(), focusRequester = controls[4], left = category,
        up = if (updateState.release != null) controls[3] else controls[1], onFocused = { onFocused(4) })
}
