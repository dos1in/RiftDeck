package com.riftdeck.feature.game

import android.text.format.DateUtils
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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riftdeck.R
import com.riftdeck.core.input.GameAction
import com.riftdeck.core.input.gamepadActions
import com.riftdeck.core.model.Game
import com.riftdeck.core.ui.components.ControllerHint
import com.riftdeck.core.ui.components.ControllerHintBar
import com.riftdeck.core.ui.components.GameArtwork
import com.riftdeck.core.ui.components.NeonActionButton
import com.riftdeck.core.ui.components.TerminalHeader
import com.riftdeck.core.ui.theme.LocalFrontendTheme

@Composable
fun GameDetailScreen(
    game: Game,
    onToggleFavorite: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFrontendTheme.current
    var showLaunchError by rememberSaveable { mutableStateOf(false) }
    val playRequester = remember { FocusRequester() }
    val favoriteRequester = remember { FocusRequester() }
    val settingsRequester = remember { FocusRequester() }

    LaunchedEffect(showLaunchError) {
        withFrameNanos { }
        if (showLaunchError) settingsRequester.requestFocus() else playRequester.requestFocus()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .gamepadActions { action ->
                when (action) {
                    GameAction.Back -> {
                        if (showLaunchError) showLaunchError = false else onBack()
                        true
                    }
                    GameAction.Favorite -> {
                        onToggleFavorite()
                        true
                    }
                    GameAction.Menu -> {
                        onOpenSettings()
                        true
                    }
                    GameAction.Details,
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
        val artWidth = if (compact) 154.dp else 218.dp
        val artHeight = if (compact) 220.dp else 306.dp

        Column(Modifier.fillMaxSize()) {
            TerminalHeader(
                section = stringResource(R.string.game_detail_title),
                status = stringResource(R.string.platform_gba_short),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (compact) 16.dp else 32.dp,
                        vertical = if (compact) 12.dp else 20.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 18.dp else 32.dp),
            ) {
                GameArtwork(
                    game = game,
                    contentDescription = stringResource(R.string.artwork_description, game.title),
                    modifier = Modifier
                        .width(artWidth)
                        .height(artHeight)
                        .border(1.dp, colors.outline),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    Text(
                        text = game.title,
                        color = colors.textPrimary,
                        style = if (compact) {
                            MaterialTheme.typography.headlineLarge
                        } else {
                            MaterialTheme.typography.displayLarge
                        },
                    )
                    Text(
                        text = stringResource(R.string.platform_gba),
                        color = colors.secondary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(if (compact) 8.dp else 20.dp))
                    MetadataGrid(game = game, compact = compact)
                    Spacer(Modifier.weight(1f))
                    if (showLaunchError) {
                        LaunchErrorPanel(
                            focusRequester = settingsRequester,
                            onOpenSettings = onOpenSettings,
                            compact = compact,
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NeonActionButton(
                                label = stringResource(R.string.play_game),
                                onClick = { showLaunchError = true },
                                focusRequester = playRequester,
                                right = favoriteRequester,
                                primary = true,
                                modifier = Modifier.width(if (compact) 138.dp else 186.dp),
                            )
                            NeonActionButton(
                                label = stringResource(
                                    if (game.favorite) R.string.remove_favorite else R.string.add_favorite,
                                ),
                                onClick = onToggleFavorite,
                                focusRequester = favoriteRequester,
                                left = playRequester,
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
                        onClick = {
                            if (showLaunchError) onOpenSettings() else showLaunchError = true
                        },
                    ),
                    ControllerHint(
                        "Y",
                        stringResource(R.string.hint_favorite),
                        onClick = onToggleFavorite,
                    ),
                    ControllerHint(
                        "B",
                        stringResource(R.string.hint_back),
                        onClick = {
                            if (showLaunchError) showLaunchError = false else onBack()
                        },
                    ),
                ),
            )
        }
    }
}

@Composable
private fun MetadataGrid(
    game: Game,
    compact: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            MetadataValue(
                label = stringResource(R.string.release_year),
                value = game.releaseYear?.toString().orEmpty(),
                modifier = Modifier.weight(1f),
            )
            MetadataValue(
                label = stringResource(R.string.developer),
                value = game.developer.orEmpty(),
                modifier = Modifier.weight(1f),
            )
            MetadataValue(
                label = stringResource(R.string.genre),
                value = game.genre.orEmpty(),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val hours = game.playTimeSeconds / 3_600
            val minutes = (game.playTimeSeconds % 3_600) / 60
            MetadataValue(
                label = stringResource(R.string.playtime),
                value = stringResource(R.string.hours_minutes, hours, minutes),
                modifier = Modifier.weight(1f),
            )
            MetadataValue(
                label = stringResource(R.string.last_played),
                value = game.lastPlayedAt?.let {
                    DateUtils.getRelativeTimeSpanString(
                        it,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString()
                } ?: stringResource(R.string.not_played),
                modifier = Modifier.weight(2f),
            )
        }
    }
}

@Composable
private fun MetadataValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFrontendTheme.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun LaunchErrorPanel(
    focusRequester: FocusRequester,
    onOpenSettings: () -> Unit,
    compact: Boolean,
) {
    val colors = LocalFrontendTheme.current
    val shape = CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp)
    val containerModifier = Modifier
        .fillMaxWidth()
        .background(colors.surface, shape)
        .border(1.dp, colors.error, shape)

    if (compact) {
        Row(
            modifier = containerModifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.emulator_missing_title),
                    color = colors.error,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.emulator_missing_message),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
            NeonActionButton(
                label = stringResource(R.string.choose_emulator),
                onClick = onOpenSettings,
                focusRequester = focusRequester,
                primary = true,
                modifier = Modifier.width(156.dp),
            )
        }
        return
    }

    Column(
        modifier = containerModifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.emulator_missing_title),
            color = colors.error,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.emulator_missing_message),
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        NeonActionButton(
            label = stringResource(R.string.choose_emulator),
            onClick = onOpenSettings,
            focusRequester = focusRequester,
            primary = true,
        )
    }
}
