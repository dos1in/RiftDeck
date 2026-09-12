package com.riftdeck.feature.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riftdeck.R
import com.riftdeck.core.input.GameAction
import com.riftdeck.core.model.Game
import com.riftdeck.core.ui.components.*

@Composable
fun GameDetailScreen(game: Game, onToggleFavorite: () -> Unit, onPlay: () -> Unit,
    onNavigate: (DeckSection) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var showDescription by rememberSaveable(game.id) { mutableStateOf(false) }
    val introduction = remember { FocusRequester() }
    val play = remember { FocusRequester() }
    val favorite = remember { FocusRequester() }
    val back = remember { FocusRequester() }
    var focusedKey by rememberSaveable { mutableStateOf("play") }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        when (focusedKey) { "introduction" -> introduction; "favorite" -> favorite; "back" -> back; else -> play }.requestFocus()
    }
    if (showDescription) GameDescriptionDialog(game.description.orEmpty()) { showDescription = false }
    CompositionLocalProvider(com.riftdeck.core.input.LocalControllerInputEnabled provides (com.riftdeck.core.input.LocalControllerInputEnabled.current && !showDescription),
        LocalVideoPreviews provides (LocalVideoPreviews.current && !showDescription)) {
    DeckScaffold(DeckSection.Detail, play, onNavigate, onAction = { action ->
        when (action) {
            GameAction.Back -> { onBack(); true }
            GameAction.Favorite -> { onToggleFavorite(); true }
            GameAction.Menu -> { onNavigate(DeckSection.Settings); true }
            GameAction.Details, GameAction.PreviousCategory, GameAction.NextCategory, GameAction.Search -> true
            else -> false
        }
    }, modifier = modifier) { rail, compact ->
        Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 14.dp else 30.dp, vertical = if (compact) 10.dp else 24.dp)) {
            DeckHeading(R.string.game_detail_title, R.string.detail_eyebrow, compact)
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 40.dp)) {
                BoxWithConstraints(Modifier.weight(0.85f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    val height = maxHeight.coerceAtMost(390.dp)
                    GameStageCover(game, Modifier.size(maxWidth, height))
                }
                BoxWithConstraints(Modifier.weight(1.15f).fillMaxHeight()) {
                    val condensed = maxHeight < 260.dp
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp, Alignment.CenterVertically)) {
                        GameInformation(game, compact, detailed = true, condensed = condensed)
                        NeonActionButton(stringResource(R.string.play_game), onPlay, Modifier.fillMaxWidth(), primary = true,
                            focusRequester = play, left = rail, down = if (game.description.isNullOrBlank()) back else introduction, onFocused = { focusedKey = "play" })
                        NeonActionButton(stringResource(R.string.game_description), { showDescription = true }, Modifier.fillMaxWidth(),
                            focusRequester = introduction, left = rail, up = play, down = back,
                            enabled = !game.description.isNullOrBlank(), onFocused = { focusedKey = "introduction" })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NeonActionButton(stringResource(R.string.back_to_library), onBack, Modifier.weight(1f),
                                focusRequester = back, left = rail, up = if (game.description.isNullOrBlank()) play else introduction, right = favorite, onFocused = { focusedKey = "back" })
                            FavoriteButton(game, onToggleFavorite, Modifier.weight(1f), favorite, back, play,
                                onFocused = { focusedKey = "favorite" })
                        }
                    }
                }
            }
        }
    }
}
}
