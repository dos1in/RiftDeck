package com.riftdeck.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.riftdeck.core.ui.theme.LocalFrontendTheme
import com.riftdeck.core.input.GameAction
import com.riftdeck.feature.game.GameDetailScreen
import com.riftdeck.feature.home.HomeScreen
import com.riftdeck.feature.home.HomeViewModel
import com.riftdeck.feature.platform.PlatformScreen
import com.riftdeck.feature.settings.SettingsScreen
import kotlinx.coroutines.flow.Flow

private object Route {
    const val Home = "home"
    const val Platform = "platform"
    const val Settings = "settings"
    const val GameArgument = "gameId"
    const val Game = "game/{$GameArgument}"

    fun game(gameId: Long): String = "game/$gameId"
}

@Composable
fun RiftDeckApp(
    homeViewModel: HomeViewModel,
    analogActions: Flow<GameAction>,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFrontendTheme.current
    val navController = rememberNavController()
    val focusManager = LocalFocusManager.current
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(analogActions) {
        analogActions.collect { action ->
            when (action) {
                GameAction.Up -> focusManager.moveFocus(FocusDirection.Up)
                GameAction.Down -> focusManager.moveFocus(FocusDirection.Down)
                GameAction.Left -> focusManager.moveFocus(FocusDirection.Left)
                GameAction.Right -> focusManager.moveFocus(FocusDirection.Right)
                else -> Unit
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        NavHost(
            navController = navController,
            startDestination = Route.Home,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Route.Home) {
                HomeScreen(
                    uiState = uiState,
                    onFocusGame = homeViewModel::focusGame,
                    onToggleFavorite = homeViewModel::toggleFavorite,
                    onOpenGame = { gameId ->
                        navController.navigate(Route.game(gameId)) { launchSingleTop = true }
                    },
                    onOpenPlatform = {
                        navController.navigate(Route.Platform) { launchSingleTop = true }
                    },
                    onOpenSettings = {
                        navController.navigate(Route.Settings) { launchSingleTop = true }
                    },
                    onExit = onExit,
                )
            }
            composable(Route.Platform) {
                PlatformScreen(
                    games = uiState.games,
                    focusedGameId = uiState.focusedGameId,
                    onFocusGame = homeViewModel::focusGame,
                    onToggleFavorite = homeViewModel::toggleFavorite,
                    onOpenGame = { gameId ->
                        navController.navigate(Route.game(gameId)) { launchSingleTop = true }
                    },
                    onOpenSettings = {
                        navController.navigate(Route.Settings) { launchSingleTop = true }
                    },
                    onBack = navController::popBackStack,
                )
            }
            composable(
                route = Route.Game,
                arguments = listOf(navArgument(Route.GameArgument) { type = NavType.LongType }),
            ) { entry ->
                val gameId = entry.arguments?.getLong(Route.GameArgument)
                val game = uiState.games.firstOrNull { it.id == gameId }
                if (game != null) {
                    GameDetailScreen(
                        game = game,
                        onToggleFavorite = { homeViewModel.toggleFavorite(game.id) },
                        onOpenSettings = {
                            navController.navigate(Route.Settings) { launchSingleTop = true }
                        },
                        onBack = navController::popBackStack,
                    )
                }
            }
            composable(Route.Settings) {
                SettingsScreen(onBack = navController::popBackStack)
            }
        }
    }
}
