package com.riftdeck.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riftdeck.core.emulator.EmulatorConfig
import com.riftdeck.R
import com.riftdeck.data.scanner.ScanState
import com.riftdeck.core.input.GameAction
import com.riftdeck.core.ui.components.*
import com.riftdeck.core.ui.theme.LocalFrontendTheme

private enum class SettingSection(val title: Int, val description: Int) {
    Library(R.string.settings_library, R.string.library_settings_description),
    Emulators(R.string.settings_emulators, R.string.emulator_settings_description),
    Appearance(R.string.settings_appearance, R.string.appearance_settings_description),
    Input(R.string.settings_input, R.string.input_settings_description),
    Video(R.string.settings_video, R.string.video_settings_description),
    Performance(R.string.settings_performance, R.string.performance_settings_description),
    Launcher(R.string.settings_launcher, R.string.launcher_settings_description),
}

@Composable
fun SettingsScreen(initialSection: String, reducedMotion: Boolean, onReducedMotion: (Boolean) -> Unit,
    onAddFolder: () -> Unit, onNavigate: (DeckSection) -> Unit,
    onBack: () -> Unit, hasGame: Boolean,
    emulatorTargets: List<EmulatorConfig>, selectedEmulator: EmulatorConfig?,
    onChooseEmulator: (EmulatorConfig) -> Unit, onRefreshEmulators: () -> Unit,
    folders: Set<String>, scanState: ScanState, onRescan: () -> Unit, onCancelScan: () -> Unit, onRemoveFolder: (String) -> Unit,
    isDefaultHome: Boolean, onChooseHome: () -> Unit, onSystemSettings: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalFrontendTheme.current
    var selectedIndex by rememberSaveable { mutableIntStateOf(if (initialSection == "emulators") 1 else 0) }
    var panelFocused by rememberSaveable { mutableStateOf(false) }
    val tabs = remember { SettingSection.entries.map { FocusRequester() } }
    val action = remember { FocusRequester() }
    val systemSettings = remember { FocusRequester() }
    var systemSettingsFocused by rememberSaveable { mutableStateOf(false) }
    val appearanceControls = remember { List(2) { FocusRequester() } }
    var appearanceFocusIndex by rememberSaveable { mutableIntStateOf(0) }
    val section = SettingSection.entries[selectedIndex]
    fun entryAction(item: SettingSection): FocusRequester =
        if (item == SettingSection.Appearance) appearanceControls.first() else action
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (panelFocused) {
            if (section == SettingSection.Appearance) appearanceControls[appearanceFocusIndex.coerceIn(0, appearanceControls.lastIndex)].requestFocus()
            else if (section == SettingSection.Launcher && systemSettingsFocused) systemSettings.requestFocus()
            else action.requestFocus()
        } else tabs[selectedIndex].requestFocus()
    }
    fun step(delta: Int) {
        val next = (selectedIndex + delta).coerceIn(0, tabs.lastIndex)
        tabs[next].requestFocus()
    }
    DeckScaffold(DeckSection.Settings, tabs[selectedIndex], onNavigate, onAction = {
        when (it) {
            GameAction.Back -> { onBack(); true }
            GameAction.PreviousCategory -> { step(-1); true }
            GameAction.NextCategory -> { step(1); true }
            GameAction.Menu, GameAction.Search, GameAction.Details, GameAction.Favorite -> true
            else -> false
        }
    }, modifier = modifier, hasGame = hasGame) { rail, compact ->
        Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 14.dp else 30.dp, vertical = if (compact) 10.dp else 24.dp)) {
            DeckHeading(R.string.settings_title, R.string.settings_summary, compact)
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 30.dp)) {
                Column(Modifier.weight(0.72f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingSection.entries.forEachIndexed { index, item ->
                        // Touch selects the category; controller confirmation enters its settings.
                        NeonActionButton(stringResource(item.title), { selectedIndex = index; tabs[index].requestFocus() },
                            Modifier.fillMaxWidth(), selected = selectedIndex == index, focusRequester = tabs[index],
                            left = rail, right = entryAction(item), up = tabs.getOrNull(index - 1), down = tabs.getOrNull(index + 1),
                            onFocused = { selectedIndex = index; panelFocused = false },
                            onConfirm = { entryAction(item).requestFocus() })
                    }
                }
                Column(Modifier.weight(1.28f).fillMaxHeight().background(colors.surface).border(1.dp, colors.outline)
                    .padding(if (compact) 14.dp else 26.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp)) {
                    Text(stringResource(section.title), color = colors.textPrimary, style = MaterialTheme.typography.headlineMedium)
                    if (section != SettingSection.Appearance) {
                        Text(stringResource(section.description), color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                    when (section) {
                        SettingSection.Library -> {
                            LibrarySettings(folders, scanState, onAddFolder, onRescan, onCancelScan, onRemoveFolder,
                                action, tabs[selectedIndex], onFocused = { panelFocused = true })
                        }
                        SettingSection.Emulators -> {
                            EmulatorSettings(emulatorTargets, selectedEmulator, onChooseEmulator, onRefreshEmulators,
                                action, tabs[selectedIndex], panelFocused, onFocused = { panelFocused = true })
                        }
                        SettingSection.Appearance -> {
                            AppearanceSettings(reducedMotion, onReducedMotion,
                                appearanceControls, tabs[selectedIndex], onFocused = { index ->
                                    appearanceFocusIndex = index
                                    panelFocused = true
                                })
                        }
                        SettingSection.Launcher -> {
                            MetadataValue(stringResource(R.string.launcher_status),
                                stringResource(if (isDefaultHome) R.string.launcher_active else R.string.launcher_inactive))
                            NeonActionButton(stringResource(if (isDefaultHome) R.string.change_home else R.string.set_default_home),
                                onChooseHome, Modifier.fillMaxWidth(), primary = true, focusRequester = action,
                                left = tabs[selectedIndex], down = systemSettings,
                                onFocused = { panelFocused = true; systemSettingsFocused = false })
                            NeonActionButton(stringResource(R.string.open_system_settings), onSystemSettings,
                                Modifier.fillMaxWidth(), focusRequester = systemSettings, left = tabs[selectedIndex], up = action,
                                onFocused = { panelFocused = true; systemSettingsFocused = true })
                        }
                        else -> {
                            if (section == SettingSection.Input) {
                                MetadataValue(stringResource(R.string.input_direction), stringResource(R.string.input_direction_value))
                                MetadataValue(stringResource(R.string.input_actions), stringResource(R.string.input_actions_value))
                            }
                            NeonActionButton(stringResource(R.string.back_to_categories), { tabs[selectedIndex].requestFocus() }, Modifier.fillMaxWidth(),
                                focusRequester = action, left = tabs[selectedIndex], onFocused = { panelFocused = true })
                        }
                    }
                }
            }
        }
    }
}
