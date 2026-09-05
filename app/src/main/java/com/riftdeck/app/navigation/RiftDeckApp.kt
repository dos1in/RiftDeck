package com.riftdeck.app.navigation

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
fun RiftDeckApp(homeViewModel: HomeViewModel, analogActions: Flow<GameAction>, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalFrontendTheme.current
    val nav = rememberNavController()
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val preferenceError by homeViewModel.preferenceError.collectAsStateWithLifecycle()
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    fun navigate(route: String) { nav.navigate(route) { launchSingleTop = true } }
    fun back() { if (!nav.popBackStack()) onExit() }
    fun openLibrary(filter: LibraryFilter) { homeViewModel.setFilter(filter); navigate(Route.Platform) }
    val onNavigate: (DeckSection) -> Unit = { section ->
        when (section) {
            DeckSection.Home -> if (!nav.popBackStack(Route.Home, false)) navigate(Route.Home)
            DeckSection.Library -> if (!nav.popBackStack(Route.Platform, false)) openLibrary(LibraryFilter.All)
            DeckSection.Detail -> uiState.focusedGameId?.let { navigate(Route.game(it)) }
            DeckSection.Settings -> navigate(Route.settings())
        }
    }
    val onPlay: (Long) -> Unit = { homeViewModel.focusGame(it); notice = "launch" }
    val onOpenGame: (Long) -> Unit = { homeViewModel.focusGame(it); navigate(Route.game(it)) }
    CompositionLocalProvider(LocalAnalogActions provides analogActions, LocalReducedMotion provides uiState.reducedMotion,
        LocalControllerInputEnabled provides (notice == null && !preferenceError)) {
        Box(modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
            // Instant page changes keep controls responsive; focus motion is handled by the cover.
            // Restore focus only after the saved ordering is known; otherwise the target row can move offscreen mid-restore.
            NavHost(navController = nav, startDestination = Route.Home, modifier = Modifier.fillMaxSize(),
                enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None }) {
                composable(Route.Home) {
                    if (uiState.isReady) HomeScreen(uiState, homeViewModel::focusGame, homeViewModel::toggleFavorite, onOpenGame,
                        onPlay, ::openLibrary, onNavigate, onExit)
                }
                composable(Route.Platform) {
                    if (uiState.isReady) PlatformScreen(uiState, homeViewModel::focusGame, homeViewModel::toggleFavorite, onOpenGame,
                        onPlay, homeViewModel::setFilter, homeViewModel::toggleSort, onNavigate, ::back)
                }
                composable(Route.Game, arguments = listOf(navArgument("gameId") { type = NavType.LongType })) { entry ->
                    val game = uiState.games.firstOrNull { it.id == entry.arguments?.getLong("gameId") }
                    if (game != null) GameDetailScreen(game, { homeViewModel.toggleFavorite(game.id) }, { onPlay(game.id) }, onNavigate, ::back)
                }
                composable(Route.Settings, arguments = listOf(navArgument("section") { type = NavType.StringType })) { entry ->
                    if (uiState.isReady) SettingsScreen(entry.arguments?.getString("section") ?: "library", uiState.reducedMotion,
                        homeViewModel::setReducedMotion, { notice = "folder" }, { notice = "configuration" }, onNavigate, ::back,
                        hasGame = uiState.games.isNotEmpty(), themeMode = uiState.themeMode, themePalette = uiState.themePalette,
                        onThemeMode = homeViewModel::setThemeMode, onThemePalette = homeViewModel::setThemePalette)
                }
            }
            if (!uiState.isReady) Text(stringResource(R.string.loading_library), color = colors.textSecondary,
                style = MaterialTheme.typography.bodyLarge, modifier = Modifier.align(Alignment.Center))
            if (notice != null || preferenceError) {
                val launch = notice == "launch" && !preferenceError
                DeckNoticeDialog(
                    title = stringResource(when { preferenceError -> R.string.preferences_error_title; launch -> R.string.emulator_missing_title; else -> R.string.demo_feature_title }),
                    message = stringResource(when {
                        preferenceError -> R.string.preferences_error_message
                        launch -> R.string.emulator_missing_message
                        notice == "folder" -> R.string.folder_demo_message
                        else -> R.string.emulator_demo_message
                    }),
                    primaryLabel = stringResource(if (launch) R.string.choose_emulator else R.string.acknowledge),
                    onConfirm = { notice = null; homeViewModel.dismissPreferenceError(); if (launch) navigate(Route.settings("emulators")) },
                    onDismiss = { notice = null; homeViewModel.dismissPreferenceError() },
                )
            }
        }
    }
}
