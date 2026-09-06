package com.riftdeck.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.riftdeck.R
import com.riftdeck.core.input.controllerClickable
import com.riftdeck.core.model.ThemeMode
import com.riftdeck.core.model.ThemePalette
import com.riftdeck.core.ui.components.LocalNavigationRail
import com.riftdeck.core.ui.components.NeonActionButton
import com.riftdeck.core.ui.components.riftSelectionFrame
import com.riftdeck.core.ui.theme.LocalFrontendTheme
import com.riftdeck.core.ui.theme.frontendTheme

@Composable
internal fun AppearanceSettings(
    mode: ThemeMode,
    palette: ThemePalette,
    reducedMotion: Boolean,
    onMode: (ThemeMode) -> Unit,
    onPalette: (ThemePalette) -> Unit,
    onReducedMotion: (Boolean) -> Unit,
    controls: List<FocusRequester>,
    category: FocusRequester,
    onFocused: (Int) -> Unit,
) {
    val colors = LocalFrontendTheme.current
    val rail = LocalNavigationRail.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.theme_mode), color = colors.textSecondary, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ThemeMode.entries.forEachIndexed { index, option ->
                ThemeChoice(
                    label = stringResource(when (option) {
                        ThemeMode.System -> R.string.theme_mode_system
                        ThemeMode.Light -> R.string.theme_mode_light
                        ThemeMode.Dark -> R.string.theme_mode_dark
                    }),
                    selected = mode == option, modifier = Modifier.weight(1f), requester = controls[index],
                    left = if (index == 0) category else controls[index - 1], right = controls.getOrNull(index + 1)?.takeIf { index < 2 },
                    down = controls[index + 3], onFocused = { onFocused(index) },
                    onClick = { controls[index].requestFocus(); onMode(option) },
                )
            }
        }
        Text(stringResource(if (mode == ThemeMode.System) R.string.theme_follows_system else R.string.theme_manual_mode),
            color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.theme_palette), color = colors.textSecondary, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ThemePalette.entries.forEachIndexed { index, option ->
                ThemeChoice(
                    label = stringResource(when (option) {
                        ThemePalette.Rift -> R.string.theme_palette_rift
                        ThemePalette.Ocean -> R.string.theme_palette_ocean
                        ThemePalette.Ember -> R.string.theme_palette_ember
                    }),
                    selected = palette == option, modifier = Modifier.weight(1f), requester = controls[index + 3],
                    left = if (index == 0) category else controls[index + 2], right = controls.getOrNull(index + 4)?.takeIf { index < 2 },
                    up = controls[index], down = controls[6], onFocused = { onFocused(index + 3) },
                    onClick = { controls[index + 3].requestFocus(); onPalette(option) }, palette = option,
                )
            }
        }
    }
    NeonActionButton(stringResource(R.string.reduce_motion), { controls[6].requestFocus(); onReducedMotion(!reducedMotion) },
        Modifier.fillMaxWidth().semantics { role = Role.Switch; toggleableState = if (reducedMotion) ToggleableState.On else ToggleableState.Off },
        glyph = stringResource(if (reducedMotion) R.string.setting_on else R.string.setting_off), selected = reducedMotion,
        focusRequester = controls[6], left = category, up = controls[palette.ordinal + 3], down = controls[7], onFocused = { onFocused(6) })
    NeonActionButton(stringResource(R.string.expand_navigation_rail), { controls[7].requestFocus(); rail.setExpanded(!rail.expanded) },
        Modifier.fillMaxWidth().semantics { role = Role.Switch; toggleableState = if (rail.expanded) ToggleableState.On else ToggleableState.Off },
        glyph = stringResource(if (rail.expanded) R.string.setting_on else R.string.setting_off), selected = rail.expanded,
        focusRequester = controls[7], left = category, up = controls[6], onFocused = { onFocused(7) })
    Text(stringResource(R.string.navigation_rail_description), color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun ThemeChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    requester: FocusRequester,
    left: FocusRequester,
    right: FocusRequester? = null,
    up: FocusRequester? = null,
    down: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    palette: ThemePalette? = null,
) {
    val colors = LocalFrontendTheme.current
    var focused by remember { mutableStateOf(false) }
    Column(modifier.focusRequester(requester).focusProperties {
        this.left = left
        this.right = right ?: FocusRequester.Cancel
        this.up = up ?: FocusRequester.Cancel
        this.down = down
    }.onFocusChanged { focused = it.isFocused; if (focused) onFocused() }
        .riftSelectionFrame(focused, selected)
        .semantics { role = Role.RadioButton; this.selected = selected }
        .controllerClickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (palette != null) {
            val preview = remember(colors.isDark, palette) { frontendTheme(colors.isDark, palette) }
            Row(Modifier.fillMaxWidth().height(4.dp)) {
                listOf(preview.primary, preview.secondary, preview.tertiary).forEach { color ->
                    Box(Modifier.weight(1f).fillMaxHeight().background(color))
                }
            }
        }
        Text(label, color = if (selected) colors.secondary else colors.textPrimary, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        // Selection remains visible when controller focus moves elsewhere.
        Box(Modifier.size(10.dp).border(1.dp, if (selected) colors.secondary else colors.textSecondary)
            .padding(2.dp).background(if (selected) colors.secondary else colors.surface))
    }
}
