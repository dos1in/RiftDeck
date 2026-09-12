package com.riftdeck.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import com.riftdeck.R
import com.riftdeck.core.ui.components.LocalNavigationRail
import com.riftdeck.core.ui.components.NeonActionButton
import com.riftdeck.core.ui.theme.LocalFrontendTheme

@Composable
internal fun AppearanceSettings(
    reducedMotion: Boolean,
    onReducedMotion: (Boolean) -> Unit,
    controls: List<FocusRequester>,
    category: FocusRequester,
    onFocused: (Int) -> Unit,
) {
    val colors = LocalFrontendTheme.current
    val rail = LocalNavigationRail.current
    NeonActionButton(stringResource(R.string.reduce_motion), { controls[0].requestFocus(); onReducedMotion(!reducedMotion) },
        Modifier.fillMaxWidth().semantics { role = Role.Switch; toggleableState = if (reducedMotion) ToggleableState.On else ToggleableState.Off },
        glyph = stringResource(if (reducedMotion) R.string.setting_on else R.string.setting_off), selected = reducedMotion,
        focusRequester = controls[0], left = category, down = controls[1], onFocused = { onFocused(0) })
    NeonActionButton(stringResource(R.string.expand_navigation_rail), { controls[1].requestFocus(); rail.setExpanded(!rail.expanded) },
        Modifier.fillMaxWidth().semantics { role = Role.Switch; toggleableState = if (rail.expanded) ToggleableState.On else ToggleableState.Off },
        glyph = stringResource(if (rail.expanded) R.string.setting_on else R.string.setting_off), selected = rail.expanded,
        focusRequester = controls[1], left = category, up = controls[0], onFocused = { onFocused(1) })
    Text(stringResource(R.string.navigation_rail_description), color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
}
