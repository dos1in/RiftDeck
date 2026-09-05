package com.riftdeck.feature.home

import androidx.compose.runtime.Immutable
import com.riftdeck.core.model.Game
import com.riftdeck.core.model.ThemeMode
import com.riftdeck.core.model.ThemePalette
import com.riftdeck.feature.platform.LibraryFilter

@Immutable
data class HomeUiState(
    val isReady: Boolean = false,
    val games: List<Game> = emptyList(),
    val focusedGameId: Long? = null,
    val libraryGames: List<Game> = emptyList(),
    val filter: LibraryFilter = LibraryFilter.All,
    val sortDescending: Boolean = false,
    val reducedMotion: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val themePalette: ThemePalette = ThemePalette.Rift,
) {
    val focusedGame: Game?
        get() = games.firstOrNull { it.id == focusedGameId } ?: games.firstOrNull()

    val favoriteCount: Int
        get() = games.count(Game::favorite)

    val recentCount: Int
        get() = games.count { it.lastPlayedAt != null }

    val recentGame: Game?
        get() = games
            .asSequence()
            .filter { it.lastPlayedAt != null }
            .maxByOrNull { it.lastPlayedAt ?: Long.MIN_VALUE }
}
