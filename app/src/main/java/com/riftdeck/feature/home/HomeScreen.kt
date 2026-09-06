package com.riftdeck.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riftdeck.R
import com.riftdeck.core.input.GameAction
import com.riftdeck.core.ui.components.*
import com.riftdeck.core.ui.theme.LocalFrontendTheme
import com.riftdeck.feature.platform.LibraryFilter

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onFocusGame: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onOpenGame: (Long) -> Unit,
    onPlayGame: (Long) -> Unit,
    onOpenLibrary: (LibraryFilter) -> Unit,
    onNavigate: (DeckSection) -> Unit,
    onExit: () -> Unit,
    onSearch: () -> Unit,
    onAddFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFrontendTheme.current
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val keys = remember { listOf("cover", "play", "details", "favorite", "previous", "next", "recent", "favorites", "all", "empty") }
    val focus = remember { keys.associateWith { FocusRequester() } }
    var focusedKey by rememberSaveable { mutableStateOf("cover") }
    val game = uiState.focusedGame
    val previousLabel = stringResource(R.string.previous_game)
    val nextLabel = stringResource(R.string.next_game)
    val index = uiState.games.indexOfFirst { it.id == game?.id }.coerceAtLeast(0)
    fun step(delta: Int) {
        uiState.games.getOrNull(index + delta)?.let { onFocusGame(it.id) }
        focus.getValue("cover").requestFocus()
    }
    LaunchedEffect(game != null) {
        withFrameNanos { }
        focus.getValue(if (game == null) "empty" else focusedKey.takeUnless { it == "empty" } ?: "cover").requestFocus()
    }
    DeckScaffold(DeckSection.Home, focus.getValue(if (game == null) "empty" else "cover"), onNavigate,
        onAction = { action ->
            when (action) {
                GameAction.Back -> { onExit(); true }
                GameAction.Details -> { game?.let { onOpenGame(it.id) }; true }
                GameAction.Favorite -> { game?.let { onToggleFavorite(it.id) }; true }
                GameAction.PreviousCategory -> { if (game != null) step(-1); true }
                GameAction.NextCategory -> { if (game != null) step(1); true }
                GameAction.Search -> { onSearch(); true }
                GameAction.Menu -> { onNavigate(DeckSection.Settings); true }
                else -> false
            }
        }, modifier = modifier, hasGame = game != null,
    ) { rail, compact ->
        if (game == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyLibraryState(stringResource(R.string.empty_library_title), stringResource(R.string.empty_library_message),
                    stringResource(R.string.add_rom_folder), focus.getValue("empty"), onAddFolder, left = rail)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 14.dp else 30.dp, vertical = if (compact) 10.dp else 24.dp)) {
                DeckHeading(R.string.home_continue_title, R.string.home_eyebrow, compact)
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 36.dp)) {
                    BoxWithConstraints(Modifier.weight(0.85f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        val coverHeight = (maxHeight - 52.dp).coerceAtLeast(40.dp).coerceAtMost(350.dp)
                        val coverWidth = (coverHeight * 0.83f).coerceAtMost(maxWidth - 8.dp)
                        val controlsWidth = coverWidth.coerceAtLeast(160.dp * fontScale).coerceAtMost(maxWidth)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            GameStageCover(game, Modifier.size(coverWidth, coverHeight), focus.getValue("cover"),
                                left = rail, right = focus.getValue("play"), down = focus.getValue(when { index > 0 -> "previous"; index < uiState.games.lastIndex -> "next"; else -> "recent" }),
                                showLabel = coverWidth >= 100.dp,
                                onFocused = { focusedKey = "cover" }, onClick = { onOpenGame(game.id) })
                            Row(Modifier.width(controlsWidth), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NeonActionButton("‹", { step(-1) }, Modifier.width(42.dp).semantics { contentDescription = previousLabel }, enabled = index > 0,
                                    focusRequester = focus.getValue("previous"), up = focus.getValue("cover"), right = focus.getValue(if (index < uiState.games.lastIndex) "next" else "play"),
                                    left = rail, down = focus.getValue("recent"), onFocused = { focusedKey = "previous" })
                                Text(stringResource(R.string.game_position, index + 1, uiState.games.size), color = colors.textSecondary,
                                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                NeonActionButton("›", { step(1) }, Modifier.width(42.dp).semantics { contentDescription = nextLabel }, enabled = index < uiState.games.lastIndex,
                                    focusRequester = focus.getValue("next"), up = focus.getValue("cover"), left = if (index > 0) focus.getValue("previous") else rail,
                                    right = focus.getValue("play"), down = focus.getValue("recent"), onFocused = { focusedKey = "next" })
                            }
                        }
                    }
                    BoxWithConstraints(Modifier.weight(1.15f).fillMaxHeight()) {
                        val condensed = maxHeight < 260.dp
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp, Alignment.CenterVertically)) {
                            GameInformation(game, compact, detailed = false, condensed = condensed)
                            NeonActionButton(stringResource(R.string.play_game), { onPlayGame(game.id) }, Modifier.fillMaxWidth(), primary = true,
                                glyph = "A", focusRequester = focus.getValue("play"), left = focus.getValue("cover"),
                                down = focus.getValue("details"), onFocused = { focusedKey = "play" })
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NeonActionButton(stringResource(R.string.hint_details), { onOpenGame(game.id) }, Modifier.weight(1f),
                                    focusRequester = focus.getValue("details"), up = focus.getValue("play"), left = focus.getValue("cover"),
                                    right = focus.getValue("favorite"), down = focus.getValue("favorites"), onFocused = { focusedKey = "details" })
                                FavoriteButton(game, { onToggleFavorite(game.id) }, Modifier.weight(1f), focus.getValue("favorite"),
                                    focus.getValue("details"), focus.getValue("play"), focus.getValue("all"), { focusedKey = "favorite" })
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = if (compact) 6.dp else 14.dp), color = colors.outline)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val shortcuts = listOf(Triple("recent", R.string.recent_title, uiState.recentCount),
                        Triple("favorites", R.string.favorites_title, uiState.favoriteCount), Triple("all", R.string.all_games, uiState.games.size))
                    shortcuts.forEachIndexed { i, (key, title, count) ->
                        NeonActionButton(stringResource(title), { onOpenLibrary(listOf(LibraryFilter.Recent, LibraryFilter.Favorites, LibraryFilter.All)[i]) },
                            Modifier.weight(1f), glyph = count.toString(), focusRequester = focus.getValue(key),
                            up = focus.getValue(if (i == 0) "cover" else "play"),
                            left = shortcuts.getOrNull(i - 1)?.first?.let { focus.getValue(it) } ?: rail,
                            right = shortcuts.getOrNull(i + 1)?.first?.let { focus.getValue(it) }, onFocused = { focusedKey = key })
                    }
                }
            }
        }
    }
}
