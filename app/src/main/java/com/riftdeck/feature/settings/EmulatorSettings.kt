package com.riftdeck.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import com.riftdeck.R
import com.riftdeck.core.emulator.EmulatorConfig
import com.riftdeck.core.ui.components.NeonActionButton
import com.riftdeck.core.ui.theme.LocalFrontendTheme

@Composable
fun EmulatorSettings(targets: List<EmulatorConfig>, selected: EmulatorConfig?, onChoose: (EmulatorConfig) -> Unit,
    onRefresh: () -> Unit, first: FocusRequester, left: FocusRequester, panelFocused: Boolean, onFocused: () -> Unit) {
    val colors = LocalFrontendTheme.current
    val extra = remember(targets) { List(targets.size) { FocusRequester() } }
    fun requester(index: Int) = if (index == 0) first else extra[index - 1]
    LaunchedEffect(targets) { if (panelFocused) { withFrameNanos { }; first.requestFocus() } }
    Text(stringResource(R.string.emulator_selected, selected?.displayName ?: stringResource(R.string.not_configured)),
        color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
    if (targets.isEmpty()) Text(stringResource(R.string.emulator_none_available), color = colors.textSecondary,
        style = MaterialTheme.typography.bodyMedium)
    targets.forEachIndexed { index, target ->
        key(target.packageName, target.activityName) {
            NeonActionButton(target.displayName, { requester(index).requestFocus(); onChoose(target) }, Modifier.fillMaxWidth(),
                selected = selected?.packageName == target.packageName && selected.activityName == target.activityName,
                focusRequester = requester(index), left = left, up = if (index > 0) requester(index - 1) else null,
                down = requester(index + 1), onFocused = onFocused)
        }
    }
    NeonActionButton(stringResource(R.string.refresh_emulators), onRefresh, Modifier.fillMaxWidth(),
        focusRequester = requester(targets.size), left = left,
        up = if (targets.isNotEmpty()) requester(targets.lastIndex) else null, onFocused = onFocused)
}
