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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.riftdeck.core.emulator.EmulatorConfig
import com.riftdeck.R
import com.riftdeck.data.scanner.ScanState
import com.riftdeck.core.input.GameAction
import com.riftdeck.core.input.LocalControllerInputEnabled
import com.riftdeck.core.ui.components.*
import com.riftdeck.core.ui.theme.LocalFrontendTheme
import com.riftdeck.data.repository.UpdateUiState
import com.riftdeck.data.repository.UpdateStatus
import kotlinx.coroutines.awaitCancellation

private enum class SettingSection(val title: Int, val description: Int) {
    Library(R.string.settings_library, R.string.library_settings_description),
    Emulators(R.string.settings_emulators, R.string.emulator_settings_description),
    Appearance(R.string.settings_appearance, R.string.appearance_settings_description),
    Input(R.string.settings_input, R.string.input_settings_description),
    Video(R.string.settings_video, R.string.video_settings_description),
    Performance(R.string.settings_performance, R.string.performance_settings_description),
    Launcher(R.string.settings_launcher, R.string.launcher_settings_description),
    Sharing(R.string.sharing_title, R.string.sharing_settings_description),
    About(R.string.settings_about, R.string.about_description),
}

@Composable
fun SettingsScreen(language: String, onLanguage: (String) -> Unit, defaultHomeCategory: String, onDefaultHomeCategory: (String) -> Unit, initialSection: String, reducedMotion: Boolean, onReducedMotion: (Boolean) -> Unit,
    onAddFolder: () -> Unit, onNavigate: (DeckSection) -> Unit,
    onBack: () -> Unit, hasGame: Boolean, videoPreviews: Boolean, onVideoPreviews: (Boolean) -> Unit,
    previewDelayMs: Int, onPreviewDelay: (Int) -> Unit,
    loopVideoPreviews: Boolean, onLoopVideoPreviews: (Boolean) -> Unit,
    emulatorTargets: List<EmulatorConfig>, selectedEmulator: EmulatorConfig?,
    onChooseEmulator: (EmulatorConfig) -> Unit, onRefreshEmulators: () -> Unit,
    folders: Set<String>, scanState: ScanState, onRescan: () -> Unit, onCancelScan: () -> Unit, onRemoveFolder: (String) -> Unit,
    isDefaultHome: Boolean, onChooseHome: () -> Unit, onSystemSettings: () -> Unit, onImportRetroArch: () -> Unit, importStatus: String?, onSharing: () -> Unit,
    updateState: UpdateUiState, onCheckUpdate: () -> Unit, onAutoCheckUpdates: (Boolean) -> Unit,
    onDownloadUpdate: () -> Unit, onCancelUpdate: () -> Unit, onInstallUpdate: () -> Unit,
    onAboutVisible: (Boolean) -> Unit = {}, modifier: Modifier = Modifier) {
    val colors = LocalFrontendTheme.current
    var selectedIndex by rememberSaveable { mutableIntStateOf(when (initialSection) { "emulators" -> 1; "sharing" -> SettingSection.Sharing.ordinal; "about" -> SettingSection.About.ordinal; else -> 0 }) }
    var panelFocused by rememberSaveable { mutableStateOf(false) }
    val tabs = remember { SettingSection.entries.map { FocusRequester() } }
    val action = remember { FocusRequester() }
    val loopControl = remember { FocusRequester() }
    var loopControlFocused by rememberSaveable { mutableStateOf(false) }
    val systemSettings = remember { FocusRequester() }
    var systemSettingsFocused by rememberSaveable { mutableStateOf(false) }
    val appearanceControls = remember { List(3) { FocusRequester() } }
    var appearanceFocusIndex by rememberSaveable { mutableIntStateOf(0) }
    val aboutControls = remember { List(5) { FocusRequester() } }
    var aboutFocusIndex by rememberSaveable { mutableIntStateOf(0) }
    var aboutNotesOpen by remember { mutableStateOf(false) }
    var restoringFocus by remember { mutableStateOf(true) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestUpdateState by rememberUpdatedState(updateState)
    val controllerEnabled = LocalControllerInputEnabled.current
    val section = SettingSection.entries[selectedIndex]
    DisposableEffect(section == SettingSection.About) {
        onAboutVisible(section == SettingSection.About)
        onDispose { onAboutVisible(false) }
    }
    fun aboutFocus(index: Int = aboutFocusIndex): FocusRequester = aboutControls[
        if (latestUpdateState.release == null && index in 2..3) 0 else index.coerceIn(0, 4)]
    fun markPanel() { if (!restoringFocus) panelFocused = true }
    fun entryAction(item: SettingSection): FocusRequester =
        when (item) {
            SettingSection.Appearance -> appearanceControls.first()
            SettingSection.About -> aboutFocus()
            else -> action
        }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) restoringFocus = true
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            restoringFocus = true
            // Capture saved selection before Android assigns its automatic first focus target.
            val categoryIndex = selectedIndex
            val restorePanel = panelFocused
            val appearanceIndex = appearanceFocusIndex
            val aboutIndex = aboutFocusIndex
            val restoreLoop = loopControlFocused
            val restoreSystem = systemSettingsFocused
            try {
                withFrameNanos { }
                val target = if (!restorePanel) tabs[categoryIndex] else when (SettingSection.entries[categoryIndex]) {
                    SettingSection.Appearance -> appearanceControls[appearanceIndex.coerceIn(0, appearanceControls.lastIndex)]
                    SettingSection.About -> aboutFocus(aboutIndex)
                    SettingSection.Performance -> if (restoreLoop) loopControl else action
                    SettingSection.Launcher -> if (restoreSystem) systemSettings else action
                    else -> action
                }
                target.requestFocus()
            } finally { restoringFocus = false }
            awaitCancellation()
        }
    }
    LaunchedEffect(updateState.release, updateState.status, aboutNotesOpen) {
        if (!restoringFocus && !aboutNotesOpen && section == SettingSection.About && panelFocused && aboutFocusIndex in 2..3) {
            if (updateState.release != null) {
                withFrameNanos { }
                aboutFocus().requestFocus()
            } else if (updateState.status in listOf(UpdateStatus.Latest, UpdateStatus.NoRelease, UpdateStatus.Error)) {
                aboutFocusIndex = 0
                aboutControls[0].requestFocus()
            }
        }
    }
    fun step(delta: Int) {
        val next = (selectedIndex + delta).coerceIn(0, tabs.lastIndex)
        tabs[next].requestFocus()
    }
    CompositionLocalProvider(LocalControllerInputEnabled provides (controllerEnabled && !aboutNotesOpen)) {
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
                            onFocused = { if (!restoringFocus) { selectedIndex = index; panelFocused = false } },
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
                        SettingSection.Sharing -> {
                            NeonActionButton(stringResource(R.string.sharing_open), onSharing, Modifier.fillMaxWidth(),
                                primary = true, focusRequester = action, left = tabs[selectedIndex], onFocused = ::markPanel)
                        }
                        SettingSection.Library -> {
                            LibrarySettings(folders, scanState, onAddFolder, onRescan, onCancelScan, onRemoveFolder,
                                action, tabs[selectedIndex], onFocused = ::markPanel, onImportRetroArch = onImportRetroArch, importStatus = importStatus)
                        }
                        SettingSection.Emulators -> {
                            EmulatorSettings(emulatorTargets, selectedEmulator, onChooseEmulator, onRefreshEmulators,
                                action, tabs[selectedIndex], panelFocused, onFocused = ::markPanel)
                        }
                        SettingSection.Appearance -> {
                            AppearanceSettings(language, onLanguage, defaultHomeCategory, onDefaultHomeCategory, reducedMotion, onReducedMotion,
                                appearanceControls, tabs[selectedIndex], onFocused = { index ->
                                    if (!restoringFocus) {
                                        appearanceFocusIndex = index
                                        panelFocused = true
                                    }
                                })
                        }
                        SettingSection.About -> {
                            AboutSettings(aboutControls, tabs[selectedIndex], onFocused = { index ->
                                if (!restoringFocus) { panelFocused = true; aboutFocusIndex = index }
                            }, updateState, onCheckUpdate, onAutoCheckUpdates, onDownloadUpdate,
                                onCancelUpdate, onInstallUpdate, onNotesVisible = { aboutNotesOpen = it })
                        }
                        SettingSection.Video -> {
                            NeonActionButton(stringResource(if (videoPreviews) R.string.preview_video else R.string.preview_cover),
                                { action.requestFocus(); onVideoPreviews(!videoPreviews) }, Modifier.fillMaxWidth(),
                                selected = videoPreviews, focusRequester = action, left = tabs[selectedIndex],
                                onFocused = ::markPanel)
                        }
                        SettingSection.Performance -> {
                            NeonActionButton(stringResource(R.string.preview_delay_value, previewDelayMs / 1000.0),
                                { action.requestFocus(); onPreviewDelay(when (previewDelayMs) { 650 -> 1200; 1200 -> 2000; else -> 650 }) },
                                Modifier.fillMaxWidth(), focusRequester = action, left = tabs[selectedIndex], down = loopControl,
                                onFocused = { if (!restoringFocus) { panelFocused = true; loopControlFocused = false } })
                            NeonActionButton(stringResource(if (loopVideoPreviews) R.string.preview_loop_on else R.string.preview_loop_off),
                                { loopControl.requestFocus(); onLoopVideoPreviews(!loopVideoPreviews) }, Modifier.fillMaxWidth(),
                                selected = loopVideoPreviews, focusRequester = loopControl, left = tabs[selectedIndex], up = action,
                                onFocused = { if (!restoringFocus) { panelFocused = true; loopControlFocused = true } })
                            Text(stringResource(R.string.preview_performance_hint), color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                        SettingSection.Launcher -> {
                            MetadataValue(stringResource(R.string.launcher_status),
                                stringResource(if (isDefaultHome) R.string.launcher_active else R.string.launcher_inactive))
                            NeonActionButton(stringResource(if (isDefaultHome) R.string.change_home else R.string.set_default_home),
                                onChooseHome, Modifier.fillMaxWidth(), primary = true, focusRequester = action,
                                left = tabs[selectedIndex], down = systemSettings,
                                onFocused = { if (!restoringFocus) { panelFocused = true; systemSettingsFocused = false } })
                            NeonActionButton(stringResource(R.string.open_system_settings), onSystemSettings,
                                Modifier.fillMaxWidth(), focusRequester = systemSettings, left = tabs[selectedIndex], up = action,
                                onFocused = { if (!restoringFocus) { panelFocused = true; systemSettingsFocused = true } })
                        }
                        else -> {
                            if (section == SettingSection.Input) {
                                MetadataValue(stringResource(R.string.input_direction), stringResource(R.string.input_direction_value))
                                MetadataValue(stringResource(R.string.input_actions), stringResource(R.string.input_actions_value))
                            }
                            NeonActionButton(stringResource(R.string.back_to_categories), { tabs[selectedIndex].requestFocus() }, Modifier.fillMaxWidth(),
                                focusRequester = action, left = tabs[selectedIndex], onFocused = ::markPanel)
                        }
                    }
                }
            }
        }
    }
    }
}
