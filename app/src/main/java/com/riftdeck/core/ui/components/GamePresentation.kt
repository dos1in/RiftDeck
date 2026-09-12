package com.riftdeck.core.ui.components

import android.text.format.DateUtils
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riftdeck.R
import com.riftdeck.core.input.controllerClickable
import com.riftdeck.core.model.Game
import com.riftdeck.core.ui.theme.LocalFrontendTheme
import com.riftdeck.core.ui.theme.LocalReducedMotion

@Composable
fun GameStageCover(
    game: Game,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    left: FocusRequester = FocusRequester.Cancel,
    right: FocusRequester = FocusRequester.Cancel,
    up: FocusRequester = FocusRequester.Cancel,
    down: FocusRequester = FocusRequester.Cancel,
    showLabel: Boolean = true,
    onFocused: () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalFrontendTheme.current
    var focused by remember { mutableStateOf(false) }
    val reduced = LocalReducedMotion.current
    val scale by animateFloatAsState(if (focused && !reduced) 1.025f else 1f, tween(if (reduced) 0 else 110), label = "cover focus")
    val interactive = if (onClick == null) Modifier else Modifier
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .focusProperties { this.left = left; this.right = right; this.up = up; this.down = down }
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
        .controllerClickable(onClick = onClick)
    var coverRatio by remember(game.coverUri, game.coverVersion) { mutableStateOf<Float?>(null) }
    val videoEnabled = LocalVideoPreviews.current
    val expectsVideo = videoEnabled && game.videoUri != null
    var videoRatio by remember(game.videoUri, videoEnabled) { mutableStateOf<Float?>(null) }
    var videoUnavailable by remember(game.videoUri, videoEnabled) { mutableStateOf(false) }
    // Keep the frame while the next media loads. A video preview must not briefly adopt
    // the cover ratio during its playback delay or when the previous player is disposed.
    var settledRatio by remember { mutableFloatStateOf(if (expectsVideo) 1.5f else 0.83f) }
    val resolvedRatio = if (expectsVideo && !videoUnavailable) videoRatio else coverRatio
    LaunchedEffect(resolvedRatio) {
        resolvedRatio?.let { settledRatio = it.coerceIn(0.2f, 5f) }
    }
    val ratio by animateFloatAsState(settledRatio, tween(if (reduced) 0 else 160), label = "preview frame ratio")
    // Keep the surrounding layout stable while the frame fits the displayed media.
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val inset = 12.dp
        val mediaWidth = minOf((maxWidth - inset).coerceAtLeast(1.dp), (maxHeight - inset).coerceAtLeast(1.dp) * ratio)
        Box(Modifier.size(mediaWidth + inset, mediaWidth / ratio + inset)
            .graphicsLayer { scaleX = scale; scaleY = scale }.then(interactive)
            .clip(CutCornerShape(topEnd = 16.dp, bottomStart = 16.dp))
            .riftFrame(colors.outline, colors.focusBorder, colors.secondary, focused, cut = 16.dp).padding(6.dp)) {
            GameArtwork(game, stringResource(R.string.artwork_description, game.title), Modifier.fillMaxSize()
                .clip(CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp)), showLabel = showLabel,
                onAspectRatio = { coverRatio = it })
            key(game.videoUri, videoEnabled) {
                GameVideoPreview(game.videoUri, Modifier.fillMaxSize(), onAspectRatio = { videoRatio = it },
                    onFallback = { videoUnavailable = true })
            }
        }
    }
}

@Composable
fun GameInformation(game: Game, compact: Boolean, detailed: Boolean, modifier: Modifier = Modifier, condensed: Boolean = false) {
    val colors = LocalFrontendTheme.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 10.dp)) {
        if (!condensed) Text(stringResource(R.string.platform_gba), color = colors.textSecondary,
            style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Text(game.title, color = colors.textPrimary,
            style = when { condensed -> MaterialTheme.typography.headlineMedium; compact -> MaterialTheme.typography.headlineLarge; else -> MaterialTheme.typography.displayLarge },
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(if (condensed && detailed) listOfNotNull(game.developer, game.releaseYear?.toString(), game.genre).joinToString(" · ")
            else game.developer ?: stringResource(R.string.metadata_unknown), color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium, maxLines = if (condensed) 2 else 1, overflow = TextOverflow.Ellipsis)
        if (!condensed) HorizontalDivider(Modifier.padding(top = if (compact) 4.dp else 12.dp), color = colors.outline)
        if (detailed || !condensed) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetadataValue(stringResource(R.string.playtime), gamePlaytime(game), Modifier.weight(1f))
            MetadataValue(stringResource(R.string.last_played), gameLastPlayed(game), Modifier.weight(1f))
        }
        if (detailed && !condensed) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetadataValue(stringResource(R.string.release_year), game.releaseYear?.toString() ?: stringResource(R.string.metadata_unknown), Modifier.weight(1f))
                MetadataValue(stringResource(R.string.genre), game.genre ?: stringResource(R.string.metadata_unknown), Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MetadataValue(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalFrontendTheme.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = colors.textSecondary, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Text(value, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun gamePlaytime(game: Game): String = stringResource(R.string.hours_minutes, game.playTimeSeconds / 3600, game.playTimeSeconds % 3600 / 60)

@Composable
fun gameLastPlayed(game: Game): String = game.lastPlayedAt?.let {
    DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
} ?: stringResource(R.string.not_played)

@Composable
fun FavoriteButton(
    game: Game, onClick: () -> Unit, modifier: Modifier = Modifier,
    focusRequester: FocusRequester, left: FocusRequester, up: FocusRequester,
    down: FocusRequester = FocusRequester.Cancel, onFocused: () -> Unit = {},
) {
    val label = stringResource(if (game.favorite) R.string.favorite_saved else R.string.add_favorite)
    NeonActionButton(label = label, onClick = onClick, selected = game.favorite,
        modifier = modifier.semantics { stateDescription = label }, glyph = if (game.favorite) "★" else "☆",
        focusRequester = focusRequester, left = left, up = up, down = down, onFocused = onFocused)
}
