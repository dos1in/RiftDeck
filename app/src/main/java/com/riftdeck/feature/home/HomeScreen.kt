package com.riftdeck.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riftdeck.R
import com.riftdeck.core.input.GameAction
import com.riftdeck.core.input.controllerClickable
import com.riftdeck.core.input.gamepadActions
import com.riftdeck.core.model.Game
import com.riftdeck.core.ui.components.ControllerHint
import com.riftdeck.core.ui.components.ControllerHintBar
import com.riftdeck.core.ui.components.EmptyLibraryState
import com.riftdeck.core.ui.components.GameCard
import com.riftdeck.core.ui.components.NeonActionButton
import com.riftdeck.core.ui.components.TerminalHeader
import com.riftdeck.core.ui.theme.LocalFrontendTheme
import androidx.compose.runtime.withFrameNanos

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onFocusGame: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onOpenGame: (Long) -> Unit,
    onOpenPlatform: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFrontendTheme.current
    val platformRequester = remember { FocusRequester() }
    val emptyRequester = remember { FocusRequester() }
    val recentRequester = remember { FocusRequester() }
    val favoritesRequester = remember { FocusRequester() }
    val gameRequesters = remember(uiState.games.size) {
        List(uiState.games.size) { FocusRequester() }
    }
    val focusedIndex = uiState.games.indexOfFirst { it.id == uiState.focusedGameId }
        .coerceAtLeast(0)
    val focusedGameRequester = gameRequesters.getOrNull(focusedIndex)

    LaunchedEffect(uiState.games.size) {
        withFrameNanos { }
        if (gameRequesters.isNotEmpty()) {
            gameRequesters[focusedIndex].requestFocus()
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
                        onExit()
                        true
                    }
                    GameAction.Details -> uiState.focusedGameId?.let {
                        onOpenGame(it)
                        true
                    } ?: false
                    GameAction.Favorite -> uiState.focusedGameId?.let {
                        onToggleFavorite(it)
                        true
                    } ?: false
                    GameAction.Menu -> {
                        onOpenSettings()
                        true
                    }
                    GameAction.Search -> {
                        onOpenPlatform()
                        true
                    }
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
        val railWidth = if (compact) 112.dp else 144.dp
        val cardWidth = if (compact) 132.dp else 166.dp
        val artworkHeight = if (compact) 152.dp else 196.dp
        val cardRowHeight = artworkHeight + 58.dp

        Column(Modifier.fillMaxSize()) {
            TerminalHeader(
                section = stringResource(R.string.status_local),
                status = stringResource(R.string.status_ready),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                PlatformRail(
                    modifier = Modifier
                        .width(railWidth)
                        .fillMaxHeight(),
                    focusRequester = platformRequester,
                    down = gameRequesters.firstOrNull() ?: emptyRequester,
                    right = gameRequesters.firstOrNull() ?: emptyRequester,
                    onOpenPlatform = onOpenPlatform,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(
                            start = if (compact) 16.dp else 24.dp,
                            end = if (compact) 12.dp else 20.dp,
                            top = if (compact) 8.dp else 16.dp,
                            bottom = 8.dp,
                        ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.home_continue_title),
                                color = colors.textPrimary,
                                style = if (compact) {
                                    MaterialTheme.typography.headlineMedium
                                } else {
                                    MaterialTheme.typography.headlineLarge
                                },
                            )
                            if (!compact) {
                                Text(
                                    text = stringResource(R.string.home_continue_subtitle),
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        Text(
                            text = pluralStringResource(
                                R.plurals.library_count,
                                uiState.games.size,
                                uiState.games.size,
                            ),
                            color = colors.secondary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Spacer(Modifier.height(if (compact) 7.dp else 12.dp))
                    if (uiState.games.isEmpty()) {
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
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cardRowHeight),
                            state = rememberLazyListState(),
                            horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
                        ) {
                            itemsIndexed(
                                items = uiState.games,
                                key = { _, game -> game.id },
                            ) { index, game ->
                                GameCard(
                                    game = game,
                                    artworkDescription = stringResource(
                                        R.string.artwork_description,
                                        game.title,
                                    ),
                                    favoriteDescription = stringResource(R.string.accessibility_favorite),
                                    width = cardWidth,
                                    height = artworkHeight,
                                    focusRequester = gameRequesters[index],
                                    up = platformRequester,
                                    down = recentRequester,
                                    left = gameRequesters.getOrNull(index - 1)
                                        ?: platformRequester,
                                    right = gameRequesters.getOrNull(index + 1)
                                        ?: FocusRequester.Cancel,
                                    onFocused = { onFocusGame(game.id) },
                                    onClick = { onOpenGame(game.id) },
                                )
                            }
                        }
                        Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val recentGame = uiState.recentGame
                            QuickAccessTile(
                                title = stringResource(R.string.recent_title),
                                summary = recentGame?.let {
                                    stringResource(
                                        R.string.recent_summary,
                                        it.title,
                                        it.releaseYear?.toString().orEmpty(),
                                    )
                                } ?: stringResource(R.string.not_played),
                                modifier = Modifier.weight(1.15f),
                                focusRequester = recentRequester,
                                up = focusedGameRequester,
                                right = favoritesRequester,
                                onClick = { recentGame?.let { onOpenGame(it.id) } },
                            )
                            QuickAccessTile(
                                title = stringResource(R.string.favorites_title),
                                summary = pluralStringResource(
                                    R.plurals.favorites_summary,
                                    uiState.favoriteCount,
                                    uiState.favoriteCount,
                                ),
                                modifier = Modifier.weight(0.85f),
                                focusRequester = favoritesRequester,
                                up = focusedGameRequester,
                                left = recentRequester,
                                onClick = onOpenPlatform,
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
                        onClick = { uiState.focusedGameId?.let(onOpenGame) },
                    ),
                    ControllerHint(
                        "X",
                        stringResource(R.string.hint_details),
                        onClick = { uiState.focusedGameId?.let(onOpenGame) },
                    ),
                    ControllerHint(
                        "Y",
                        stringResource(R.string.hint_favorite),
                        onClick = { uiState.focusedGameId?.let(onToggleFavorite) },
                    ),
                    ControllerHint(
                        "START",
                        stringResource(R.string.hint_menu),
                        onClick = onOpenSettings,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun PlatformRail(
    focusRequester: FocusRequester,
    down: FocusRequester?,
    right: FocusRequester?,
    onOpenPlatform: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFrontendTheme.current
    Column(
        modifier = modifier
            .background(colors.surface)
            .border(width = 1.dp, color = colors.outline)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.mock_library_label),
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            NeonActionButton(
                label = stringResource(R.string.platform_gba_short),
                onClick = onOpenPlatform,
                focusRequester = focusRequester,
                down = down,
                right = right,
                modifier = Modifier.fillMaxWidth(),
                primary = true,
            )
        }
        Text(
            text = stringResource(R.string.platform_position),
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun QuickAccessTile(
    title: String,
    summary: String,
    focusRequester: FocusRequester,
    up: FocusRequester?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    left: FocusRequester? = null,
    right: FocusRequester? = null,
) {
    val colors = LocalFrontendTheme.current
    var focused by remember { mutableStateOf(false) }
    val shape = CutCornerShape(topEnd = 10.dp, bottomStart = 10.dp)
    Column(
        modifier = modifier
            .height(66.dp)
            .focusRequester(focusRequester)
            .focusProperties {
                if (up != null) this.up = up
                if (left != null) this.left = left
                if (right != null) this.right = right
            }
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) colors.surfaceElevated else colors.surface, shape)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) colors.focusBorder else colors.outline,
                shape = shape,
            )
            .controllerClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = title,
            color = if (focused) colors.primary else colors.textPrimary,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
        Text(
            text = summary,
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
