package com.riftdeck.app.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.riftdeck.feature.settings.LibraryViewModel
import com.riftdeck.feature.settings.folderLabel
import com.riftdeck.feature.search.GameSearchDialog
import com.riftdeck.feature.emulator.EmulatorViewModel
import com.riftdeck.core.emulator.LaunchResult
import com.riftdeck.core.emulator.ArchiveFailure
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.riftdeck.R
import com.riftdeck.core.input.GameAction
import com.riftdeck.core.input.LocalAnalogActions
import com.riftdeck.core.input.LocalControllerInputEnabled
import com.riftdeck.core.ui.components.DeckNoticeDialog
import com.riftdeck.core.ui.components.LocalNavigationRail
import com.riftdeck.core.ui.components.NavigationRailState
import com.riftdeck.core.ui.components.DeckSection
import com.riftdeck.core.ui.theme.LocalFrontendTheme
import com.riftdeck.core.ui.theme.LocalReducedMotion
import com.riftdeck.feature.game.GameDetailScreen
import com.riftdeck.feature.home.HomeScreen
import com.riftdeck.feature.home.HomeViewModel
import com.riftdeck.feature.platform.LibraryFilter
import com.riftdeck.feature.platform.PlatformScreen
import com.riftdeck.feature.settings.SettingsScreen
import kotlinx.coroutines.flow.Flow

private object Route {
    const val Home = "home"
    const val Platform = "platform"
    const val Settings = "settings/{section}"
    const val Game = "game/{gameId}"
    fun game(id: Long) = "game/$id"
    fun settings(section: String = "library") = "settings/$section"
}

@Composable
fun RiftDeckApp(homeViewModel: HomeViewModel, libraryViewModel: LibraryViewModel, emulatorViewModel: EmulatorViewModel, analogActions: Flow<GameAction>, onExit: () -> Unit,
    homeRequests: Flow<Unit>, isDefaultHome: Boolean, onChooseHome: () -> Unit, onSystemSettings: () -> Unit,
    launcherError: Boolean, onDismissLauncherError: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalFrontendTheme.current
    val nav = rememberNavController()
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val preferenceError by homeViewModel.preferenceError.collectAsStateWithLifecycle()
    val emulatorTargets by emulatorViewModel.targets.collectAsStateWithLifecycle()
    val selectedEmulator by emulatorViewModel.selected.collectAsStateWithLifecycle()
    val launchBusy by emulatorViewModel.busy.collectAsStateWithLifecycle()
    val launchError by emulatorViewModel.error.collectAsStateWithLifecycle()
    val historyError by emulatorViewModel.historyError.collectAsStateWithLifecycle()
    val folders by libraryViewModel.folders.collectAsStateWithLifecycle()
    val scanState by libraryViewModel.scanState.collectAsStateWithLifecycle()
    val libraryError by libraryViewModel.error.collectAsStateWithLifecycle()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var removeFolder by rememberSaveable { mutableStateOf<String?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(libraryViewModel::addFolder)
    }
    fun addFolder() {
        try { folderPicker.launch(null) }
        catch (_: android.content.ActivityNotFoundException) { libraryViewModel.reportFolderPickerError() }
        catch (_: SecurityException) { libraryViewModel.reportFolderPickerError() }
    }
    LaunchedEffect(homeRequests) {
        homeRequests.collect {
            removeFolder = null
            searchOpen = false
            emulatorViewModel.cancel()
            emulatorViewModel.dismissError()
            libraryViewModel.dismissError()
            homeViewModel.dismissPreferenceError()
            nav.popBackStack(Route.Home, false)
        }
    }
    fun navigate(route: String) { nav.navigate(route) { launchSingleTop = true } }
    fun back() { if (!nav.popBackStack()) onExit() }
    fun openLibrary(filter: LibraryFilter) { homeViewModel.setFilter(filter); navigate(Route.Platform) }
    val onNavigate: (DeckSection) -> Unit = navigateSection@{ section ->
        val currentSection = when (nav.currentDestination?.route) {
            Route.Home -> DeckSection.Home
            Route.Platform -> DeckSection.Library
            Route.Game -> DeckSection.Detail
            Route.Settings -> DeckSection.Settings
            else -> null
        }
        // Selecting the current section must preserve its filter, scroll position and focus.
        if (section == currentSection) return@navigateSection
        when (section) {
            DeckSection.Home -> if (!nav.popBackStack(Route.Home, false)) navigate(Route.Home)
            DeckSection.Library -> if (!nav.popBackStack(Route.Platform, false)) openLibrary(LibraryFilter.All)
            DeckSection.Detail -> uiState.focusedGameId?.let { navigate(Route.game(it)) }
            DeckSection.Settings -> navigate(Route.settings())
        }
    }
    val onPlay: (Long) -> Unit = { id -> homeViewModel.focusGame(id); uiState.games.firstOrNull { it.id == id }?.let(emulatorViewModel::launch) }
    val onOpenGame: (Long) -> Unit = { homeViewModel.focusGame(it); navigate(Route.game(it)) }
    CompositionLocalProvider(
        LocalNavigationRail provides NavigationRailState(uiState.navigationRailExpanded, homeViewModel::setNavigationRailExpanded),
        LocalAnalogActions provides analogActions, LocalReducedMotion provides uiState.reducedMotion,
        LocalControllerInputEnabled provides (!preferenceError && !launcherError && !libraryError && removeFolder == null && !searchOpen && !launchBusy && launchError == null && !historyError)) {
        // Transient system bars overlay the immersive UI instead of resizing it as they hide.
        // Hardware cutouts and an explicitly opened keyboard still need safe space.
        val contentInsets = WindowInsets.displayCutout.union(WindowInsets.waterfall).union(WindowInsets.ime)
        Box(modifier.fillMaxSize().background(colors.background).windowInsetsPadding(contentInsets)) {
            // Instant page changes keep controls responsive; focus motion is handled by the cover.
            // Restore focus only after the saved ordering is known; otherwise the target row can move offscreen mid-restore.
            NavHost(navController = nav, startDestination = Route.Home, modifier = Modifier.fillMaxSize(),
                enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None }) {
                composable(Route.Home) {
                    BackHandler(enabled = !preferenceError && !launcherError && !libraryError && removeFolder == null && !searchOpen && !launchBusy && launchError == null && !historyError, onBack = onExit)
                    if (uiState.isReady) HomeScreen(uiState, homeViewModel::focusGame, homeViewModel::toggleFavorite, onOpenGame,
                        onPlay, ::openLibrary, onNavigate, onExit, onSearch = { searchOpen = true }, onAddFolder = ::addFolder)
                }
                composable(Route.Platform) {
                    if (uiState.isReady) PlatformScreen(uiState, homeViewModel::focusGame, homeViewModel::toggleFavorite, onOpenGame,
                        onPlay, homeViewModel::setFilter, homeViewModel::toggleSort, onNavigate, ::back,
                        onSearch = { searchOpen = true }, onAddFolder = ::addFolder)
                }
                composable(Route.Game, arguments = listOf(navArgument("gameId") { type = NavType.LongType })) { entry ->
                    val game = uiState.games.firstOrNull { it.id == entry.arguments?.getLong("gameId") }
                    if (game != null) GameDetailScreen(game, { homeViewModel.toggleFavorite(game.id) }, { onPlay(game.id) }, onNavigate, ::back)
                    else if (uiState.isReady) DeckNoticeDialog(
                        title = stringResource(R.string.game_removed_title),
                        message = stringResource(R.string.game_removed_message),
                        primaryLabel = stringResource(R.string.return_to_library),
                        onConfirm = { nav.popBackStack(); onNavigate(DeckSection.Library) },
                        onDismiss = ::back,
                    )
                }
                composable(Route.Settings, arguments = listOf(navArgument("section") { type = NavType.StringType })) { entry ->
                    if (uiState.isReady) SettingsScreen(entry.arguments?.getString("section") ?: "library", uiState.reducedMotion,
                        homeViewModel::setReducedMotion, ::addFolder, onNavigate, ::back,
                        hasGame = uiState.games.isNotEmpty(), themeMode = uiState.themeMode, themePalette = uiState.themePalette,
                        onThemeMode = homeViewModel::setThemeMode, onThemePalette = homeViewModel::setThemePalette,
                        emulatorTargets = emulatorTargets, selectedEmulator = selectedEmulator,
                        onChooseEmulator = emulatorViewModel::choose, onRefreshEmulators = emulatorViewModel::refresh,
                        folders = folders, scanState = scanState, onRescan = libraryViewModel::rescan,
                        onCancelScan = libraryViewModel::cancelScan, onRemoveFolder = { removeFolder = it },
                        isDefaultHome = isDefaultHome, onChooseHome = onChooseHome, onSystemSettings = onSystemSettings)
                }
            }
            if (!uiState.isReady) Text(stringResource(R.string.loading_library), color = colors.textSecondary,
                style = MaterialTheme.typography.bodyLarge, modifier = Modifier.align(Alignment.Center))
            if (launchBusy) DeckNoticeDialog(title = stringResource(R.string.preparing_game),
                message = stringResource(R.string.preparing_game_message), primaryLabel = stringResource(R.string.search_cancel),
                onConfirm = emulatorViewModel::cancel, onDismiss = emulatorViewModel::cancel, showBackButton = false)
            if (!launchBusy && (launchError != null || historyError)) {
                val choose = launchError == LaunchResult.NotConfigured || launchError == LaunchResult.NotInstalled || launchError == LaunchResult.InvalidConfiguration
                val errorMessage = when (val error = launchError) {
                    LaunchResult.NotConfigured -> R.string.emulator_not_selected
                    LaunchResult.NotInstalled -> R.string.emulator_not_installed
                    LaunchResult.InvalidConfiguration -> R.string.emulator_invalid_configuration
                    LaunchResult.RomUnavailable -> R.string.rom_unavailable
                    is LaunchResult.InvalidArchive -> when (error.reason) {
                        ArchiveFailure.TooLarge -> R.string.archive_too_large
                        ArchiveFailure.MultipleRoms -> R.string.archive_multiple_roms
                        else -> R.string.archive_invalid
                    }
                    else -> R.string.history_save_error
                }
                DeckNoticeDialog(title = stringResource(R.string.launch_error_title), message = stringResource(errorMessage),
                    primaryLabel = stringResource(if (choose) R.string.choose_emulator else R.string.acknowledge),
                    onConfirm = { emulatorViewModel.dismissError(); if (choose) navigate(Route.settings("emulators")) },
                    onDismiss = emulatorViewModel::dismissError)
            }
            if (searchOpen) GameSearchDialog(uiState.searchQuery, onSearch = {
                homeViewModel.setSearchQuery(it)
                searchOpen = false
                if (nav.currentDestination?.route != Route.Platform) openLibrary(LibraryFilter.All)
            }, onDismiss = { searchOpen = false })
            if (removeFolder != null) {
                DeckNoticeDialog(title = stringResource(R.string.remove_folder_title),
                    message = stringResource(R.string.remove_folder_message, folderLabel(removeFolder!!)),
                    primaryLabel = stringResource(R.string.remove_folder_confirm),
                    onConfirm = { removeFolder?.let(libraryViewModel::removeFolder); removeFolder = null },
                    onDismiss = { removeFolder = null }, focusCancel = true)
            }
            if (preferenceError || launcherError || libraryError) {
                DeckNoticeDialog(
                    title = stringResource(when {
                        libraryError -> R.string.library_error_title
                        launcherError -> R.string.launcher_error_title
                        else -> R.string.preferences_error_title
                    }),
                    message = stringResource(when {
                        libraryError -> R.string.library_error_message
                        launcherError -> R.string.launcher_error_message
                        else -> R.string.preferences_error_message
                    }),
                    primaryLabel = stringResource(R.string.acknowledge),
                    onConfirm = { libraryViewModel.dismissError(); onDismissLauncherError(); homeViewModel.dismissPreferenceError() },
                    onDismiss = { libraryViewModel.dismissError(); onDismissLauncherError(); homeViewModel.dismissPreferenceError() },
                )
            }
        }
    }
}
