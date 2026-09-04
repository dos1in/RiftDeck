package com.riftdeck.feature.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riftdeck.R
import com.riftdeck.core.input.GameAction
import com.riftdeck.core.input.gamepadActions
import com.riftdeck.core.model.Game
import com.riftdeck.core.ui.components.ControllerHint
import com.riftdeck.core.ui.components.ControllerHintBar
import com.riftdeck.core.ui.components.EmptyLibraryState
import com.riftdeck.core.ui.components.GameCard
import com.riftdeck.core.ui.components.TerminalHeader
import com.riftdeck.core.ui.theme.LocalFrontendTheme

@Composable
fun PlatformScreen(
    games: List<Game>,
    focusedGameId: Long?,
    onFocusGame: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onOpenGame: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFrontendTheme.current
    val emptyRequester = remember { FocusRequester() }
    val requesters = remember(games.size) { List(games.size) { FocusRequester() } }
    val focusedIndex = games.indexOfFirst { it.id == focusedGameId }.coerceAtLeast(0)

    LaunchedEffect(games.size) {
        withFrameNanos { }
        if (requesters.isNotEmpty()) {
            requesters[focusedIndex].requestFocus()
        } else {
            emptyRequester.requestFocus()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .gamepadActions { action ->
                when (action) {
                    GameAction.Back -> {
                        onBack()
                        true
                    }
                    GameAction.Details -> focusedGameId?.let {
                        onOpenGame(it)
                        true
                    } ?: false
                    GameAction.Favorite -> focusedGameId?.let {
                        onToggleFavorite(it)
                        true
                    } ?: false
                    GameAction.Menu -> {
                        onOpenSettings()
                        true
                    }
                    GameAction.Search,
                    GameAction.PreviousCategory,
                    GameAction.NextCategory,
                    -> true
                    GameAction.Up,
                    GameAction.Down,
                    GameAction.Left,
                    GameAction.Right,
                    GameAction.Confirm,
                    -> false
                }
            },
    ) {
        val compact = maxHeight < 560.dp || maxWidth < 760.dp
        val columns = 5
        val cardWidth = if (compact) 106.dp else 148.dp
        val artworkHeight = if (compact) 116.dp else 168.dp

        Column(Modifier.fillMaxSize()) {
            TerminalHeader(
                section = stringResource(R.string.platform_gba_short),
                status = pluralStringResource(R.plurals.library_count, games.size, games.size),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (compact) 16.dp else 24.dp,
                        vertical = if (compact) 8.dp else 16.dp,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.platform_gba),
                        color = colors.textPrimary,
                        style = if (compact) {
                            MaterialTheme.typography.headlineMedium
                        } else {
                            MaterialTheme.typography.headlineLarge
                        },
                    )
                    Text(
                        text = stringResource(R.string.platform_screen_summary),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = stringResource(R.string.mock_library_label),
                    color = colors.secondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (games.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyLibraryState(
                        title = stringResource(R.string.empty_library_title),
                        message = stringResource(R.string.empty_library_message),
                        actionLabel = stringResource(R.string.add_rom_folder),
                        focusRequester = emptyRequester,
                        onAction = onOpenSettings,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = if (compact) 12.dp else 22.dp),
                    state = rememberLazyGridState(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(
                        items = games,
                        key = { _, game -> game.id },
                    ) { index, game ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            GameCard(
                                game = game,
                                artworkDescription = stringResource(
                                    R.string.artwork_description,
                                    game.title,
                                ),
                                favoriteDescription = stringResource(R.string.accessibility_favorite),
                                width = cardWidth,
                                height = artworkHeight,
                                focusRequester = requesters[index],
                                left = if (index % columns > 0) {
                                    requesters[index - 1]
                                } else {
                                    FocusRequester.Cancel
                                },
                                right = if (
                                    index % columns < columns - 1 &&
                                    index + 1 < requesters.size
                                ) {
                                    requesters[index + 1]
                                } else {
                                    FocusRequester.Cancel
                                },
                                up = requesters.getOrNull(index - columns)
                                    ?: FocusRequester.Cancel,
                                down = requesters.getOrNull(index + columns)
                                    ?: FocusRequester.Cancel,
                                onFocused = { onFocusGame(game.id) },
                                onClick = { onOpenGame(game.id) },
                            )
                        }
                    }
                }
            }
            ControllerHintBar(
                hints = listOf(
                    ControllerHint(
                        "A",
                        stringResource(R.string.hint_confirm),
                        onClick = { focusedGameId?.let(onOpenGame) },
                    ),
                    ControllerHint(
                        "X",
                        stringResource(R.string.hint_details),
                        onClick = { focusedGameId?.let(onOpenGame) },
                    ),
                    ControllerHint(
                        "Y",
                        stringResource(R.string.hint_favorite),
                        onClick = { focusedGameId?.let(onToggleFavorite) },
                    ),
                    ControllerHint(
                        "B",
                        stringResource(R.string.hint_back),
                        onClick = onBack,
                    ),
                ),
            )
        }
    }
}
