package com.riftdeck.feature.platform

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.riftdeck.R
import com.riftdeck.core.input.GameAction
import com.riftdeck.core.input.LocalControllerInputEnabled
import com.riftdeck.core.input.controllerClickable
import com.riftdeck.core.model.Game
import com.riftdeck.core.ui.components.*
import com.riftdeck.core.ui.theme.LocalFrontendTheme
import com.riftdeck.feature.home.HomeUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun PlatformScreen(
    uiState: HomeUiState,
    onFocusGame: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onOpenGame: (Long) -> Unit,
    onPlayGame: (Long) -> Unit,
    onFilter: (LibraryFilter) -> Unit,
    onSort: () -> Unit,
    onNavigate: (DeckSection) -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onAddFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFrontendTheme.current
    val games = uiState.libraryGames
    val filters = remember { LibraryFilter.entries.associateWith { FocusRequester() } }
    val search = remember { FocusRequester() }
    val addFolder = remember { FocusRequester() }
    val alphabet = remember { FocusRequester() }
    var alphabetOpen by rememberSaveable { mutableStateOf(false) }
    val letterPositions = remember(games) { alphabetPositions(games) }
    var pendingJump by remember { mutableStateOf<Int?>(null) }
    val sort = remember { FocusRequester() }
    val play = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val rowFocus = remember { mutableStateMapOf<Long, FocusRequester>() }
    val scope = rememberCoroutineScope()
    var moveJob by remember { mutableStateOf<Job?>(null) }
    var listFocused by remember { mutableStateOf(false) }
    var filterFocused by remember { mutableStateOf(false) }
    var cursorId by rememberSaveable { mutableStateOf(uiState.focusedGameId) }
    var focusArea by rememberSaveable { mutableStateOf("list") }
    var pendingImport by rememberSaveable { mutableStateOf(false) }
    var previousQuery by rememberSaveable { mutableStateOf(uiState.searchQuery) }
    val selected = games.firstOrNull { it.id == cursorId } ?: games.firstOrNull()

    fun requestRow(index: Int) {
        val target = games.getOrNull(index) ?: return
        cursorId = target.id
        onFocusGame(target.id)
        moveJob?.cancel()
        moveJob = scope.launch {
            if (listState.layoutInfo.visibleItemsInfo.none { it.key == target.id }) listState.scrollToItem(index)
            // Only visible rows allocate focus nodes. Wait for an offscreen row to be composed.
            withTimeoutOrNull(1500) { snapshotFlow { rowFocus[target.id] }.filterNotNull().first() }?.let {
                withFrameNanos { }
                it.requestFocus()
            }
        }
    }
    LaunchedEffect(pendingJump) {
        pendingJump?.let { index ->
            withFrameNanos { }
            focusArea = "list"
            requestRow(index)
            pendingJump = null
        }
    }
    LaunchedEffect(games, uiState.games.isEmpty(), uiState.filter, uiState.sortDescending, uiState.searchQuery) {
        val searchChanged = previousQuery != uiState.searchQuery
        previousQuery = uiState.searchQuery
        if (uiState.games.isEmpty()) {
            focusArea = "empty"
            addFolder.requestFocus()
            return@LaunchedEffect
        }
        if (pendingImport) {
            if (games.isNotEmpty()) {
                focusArea = "list"
                requestRow(0)
            } else {
                pendingImport = false
                search.requestFocus()
            }
            return@LaunchedEffect
        }
        if (searchChanged) {
            // Closing a search dialog can restore Android focus to a row that was filtered out.
            // Wait until the dialog is removed, then place focus on the actual search result.
            withFrameNanos { }
            if (games.isNotEmpty()) {
                focusArea = "list"
                requestRow(games.indexOfFirst { it.id == cursorId }.coerceAtLeast(0))
            } else {
                focusArea = "filter"
                search.requestFocus()
            }
            return@LaunchedEffect
        }
        if (games.isNotEmpty() && games.none { it.id == cursorId }) {
            cursorId = games.first().id
            onFocusGame(games.first().id)
            if (focusArea == "list" || focusArea == "empty") requestRow(0)
        } else if (games.isEmpty() && (focusArea == "list" || focusArea == "empty")) {
            filters.getValue(uiState.filter).requestFocus()
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        when {
            uiState.games.isEmpty() -> addFolder.requestFocus()
            pendingImport && selected != null -> requestRow(0)
            focusArea == "play" && selected != null -> play.requestFocus()
            focusArea == "list" && selected != null -> requestRow(games.indexOf(selected))
            else -> filters.getValue(uiState.filter).requestFocus()
        }
    }
    CompositionLocalProvider(LocalControllerInputEnabled provides (LocalControllerInputEnabled.current && !alphabetOpen)) {
    DeckScaffold(DeckSection.Library, if (uiState.games.isEmpty()) addFolder else filters.getValue(uiState.filter), onNavigate,
        onAction = { action ->
            val inList = listFocused || moveJob?.isActive == true
            when (action) {
                GameAction.Back -> { onBack(); true }
                GameAction.Details -> { selected?.let { onOpenGame(it.id) }; true }
                GameAction.Favorite -> {
                    selected?.let {
                        // Move before removing the focused row; otherwise Compose restores the first rail item.
                        if (uiState.filter == LibraryFilter.Favorites && it.favorite) filters.getValue(LibraryFilter.Favorites).requestFocus()
                        onToggleFavorite(it.id)
                    }
                    true
                }
                GameAction.Menu -> { onNavigate(DeckSection.Settings); true }
                GameAction.Search -> { onSearch(); true }
                GameAction.PreviousPage, GameAction.NextPage -> {
                    if (games.isNotEmpty()) {
                        val page = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                        val index = games.indexOfFirst { it.id == cursorId }.coerceAtLeast(0)
                        val delta = if (action == GameAction.PreviousPage) -page else page
                        requestRow((index + delta).coerceIn(0, games.lastIndex))
                    }
                    true
                }
                GameAction.PreviousCategory, GameAction.NextCategory -> {
                    val delta = if (action == GameAction.PreviousCategory) -1 else 1
                    val next = LibraryFilter.entries[(uiState.filter.ordinal + delta + LibraryFilter.entries.size) % LibraryFilter.entries.size]
                    filters.getValue(next).requestFocus()
                    onFilter(next); true
                }
                GameAction.Up, GameAction.Down -> when {
                    uiState.games.isEmpty() && action == GameAction.Down && filterFocused -> { addFolder.requestFocus(); true }
                    inList && selected != null -> {
                        val index = games.indexOfFirst { it.id == cursorId }.coerceAtLeast(0)
                        if (action == GameAction.Up && index == 0) filters.getValue(uiState.filter).requestFocus()
                        else requestRow((index + if (action == GameAction.Up) -1 else 1).coerceIn(0, games.lastIndex))
                        true
                    }
                    filterFocused && action == GameAction.Down && selected != null -> { requestRow(games.indexOf(selected)); true }
                    else -> false
                }
                GameAction.Left -> if (focusArea == "play" && !listFocused && selected != null) {
                    requestRow(games.indexOf(selected)); true
                } else false
                else -> false
            }
        }, modifier = modifier, hasGame = uiState.games.isNotEmpty(), onRailFocused = { focusArea = "rail" },
    ) { rail, compact ->
        Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 14.dp else 30.dp, vertical = if (compact) 10.dp else 24.dp)) {
            DeckHeading(R.string.settings_library, R.string.library_eyebrow, compact) {
                Text(pluralStringResource(R.plurals.library_count, games.size, games.size), color = colors.textSecondary,
                    style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)
                .onFocusChanged { filterFocused = it.hasFocus }.focusGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LibraryFilter.entries.forEachIndexed { index, filter ->
                    NeonActionButton(stringResource(when (filter) {
                        LibraryFilter.All -> R.string.all_games; LibraryFilter.Favorites -> R.string.favorites_title; LibraryFilter.Recent -> R.string.recent_title
                    }), { filters.getValue(filter).requestFocus(); onFilter(filter) }, Modifier.weight(1f), selected = uiState.filter == filter,
                        focusRequester = filters.getValue(filter),
                        left = LibraryFilter.entries.getOrNull(index - 1)?.let { filters.getValue(it) } ?: rail,
                        right = LibraryFilter.entries.getOrNull(index + 1)?.let { filters.getValue(it) }
                            ?: if (uiState.filter != LibraryFilter.Recent) sort else search,
                        onFocused = { focusArea = "filter" })
                }
                val sortDescription = stringResource(if (uiState.sortDescending) R.string.sort_descending else R.string.sort_ascending)
                NeonActionButton(if (compact) stringResource(if (uiState.sortDescending) R.string.sort_descending_short else R.string.sort_ascending_short)
                    else sortDescription, onSort,
                    Modifier.weight(1f).semantics { contentDescription = sortDescription }, enabled = uiState.filter != LibraryFilter.Recent,
                    focusRequester = sort, left = filters.getValue(LibraryFilter.Recent),
                    right = if (games.isNotEmpty()) alphabet else search, onFocused = { focusArea = "filter" })
                NeonActionButton(stringResource(R.string.alphabet_jump), { alphabetOpen = true }, Modifier.weight(1f),
                    enabled = games.isNotEmpty() && uiState.filter != LibraryFilter.Recent,
                    focusRequester = alphabet, left = sort, right = search, onFocused = { focusArea = "filter" })
                NeonActionButton(stringResource(R.string.search_apply), onSearch, Modifier.weight(1f),
                    selected = uiState.searchQuery.isNotBlank(), focusRequester = search,
                    left = if (uiState.filter == LibraryFilter.Recent) filters.getValue(LibraryFilter.Recent)
                        else if (games.isNotEmpty()) alphabet else sort,
                    onFocused = { focusArea = "filter" })
            }
            if (uiState.searchQuery.isNotBlank()) Text(stringResource(R.string.search_active, uiState.searchQuery),
                color = colors.textSecondary, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 6.dp))
            if (selected == null) {
                if (uiState.games.isEmpty()) {
                    EmptyLibraryState(
                        stringResource(R.string.empty_library_title), stringResource(R.string.empty_library_message),
                        stringResource(R.string.add_rom_folder), addFolder, { pendingImport = true; focusArea = "empty"; onAddFolder() },
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), left = rail,
                        up = filters.getValue(uiState.filter), showIllustration = !compact,
                    )
                } else {
                    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.empty_filter_title), color = colors.textPrimary, style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.empty_filter_message), color = colors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            } else {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 32.dp)) {
                    LazyColumn(state = listState,
                        modifier = Modifier.weight(1.1f).fillMaxHeight().onFocusChanged { listFocused = it.hasFocus }.focusGroup(),
                        verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 3.dp)) {
                        itemsIndexed(games, key = { _, game -> game.id }) { index, game ->
                            val requester = remember(game.id) { FocusRequester() }
                            DisposableEffect(game.id) {
                                rowFocus[game.id] = requester
                                onDispose { rowFocus.remove(game.id) }
                            }
                            LibraryRow(game, index + 1, selected.id == game.id, requester, rail, play, compact,
                                onFocused = { cursorId = game.id; onFocusGame(game.id); focusArea = "list"; pendingImport = false },
                                onClick = { onOpenGame(game.id) })
                        }
                    }
                    BoxWithConstraints(Modifier.weight(0.9f).fillMaxHeight()) {
                        val artworkHeight = (maxHeight - if (compact) 90.dp else 118.dp).coerceIn(64.dp, 310.dp)
                        val artworkWidth = (artworkHeight * 0.83f).coerceAtMost(maxWidth)
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)) {
                            GameStageCover(selected, Modifier.size(artworkWidth, artworkHeight), showLabel = artworkWidth >= 100.dp)
                            Text(selected.title, color = colors.textPrimary, style = MaterialTheme.typography.titleLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            if (!compact) Text(gamePlaytime(selected), color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                            NeonActionButton(stringResource(R.string.play_game), { onPlayGame(selected.id) }, Modifier.fillMaxWidth(),
                                primary = true, focusRequester = play, up = filters.getValue(uiState.filter),
                                onFocused = { focusArea = "play" })
                        }
                    }
                }
            }
        }
    }
    }
    if (alphabetOpen) AlphabetJumpDialog(letterPositions.keys.sorted(), onChoose = { letter ->
        pendingJump = letterPositions[letter]
        alphabetOpen = false
    }, onDismiss = { alphabetOpen = false })
}

@Composable
private fun LibraryRow(game: Game, position: Int, selected: Boolean, requester: FocusRequester,
    rail: FocusRequester, play: FocusRequester, compact: Boolean, onFocused: () -> Unit, onClick: () -> Unit) {
    val colors = LocalFrontendTheme.current
    var focused by remember { mutableStateOf(false) }
    val favoriteState = if (game.favorite) stringResource(R.string.favorite_saved) else ""
    Row(Modifier.fillMaxWidth().focusRequester(requester)
        .focusProperties { left = rail; right = play; up = FocusRequester.Cancel; down = FocusRequester.Cancel }
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
        .riftSelectionFrame(focused, selected)
        .semantics { this.selected = selected; stateDescription = favoriteState }
        .controllerClickable(onClick = onClick).padding(if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GameArtwork(game, stringResource(R.string.artwork_description, game.title), Modifier.size(if (compact) 40.dp else 54.dp, if (compact) 46.dp else 64.dp), showLabel = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(game.title, color = if (selected) colors.secondary else colors.textPrimary, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(game.releaseYear?.toString(), game.genre).joinToString(" · "), color = colors.textSecondary,
                style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(if (game.favorite) "★" else position.toString(), color = if (game.favorite) colors.accentText else colors.textSecondary,
            style = MaterialTheme.typography.labelMedium)
    }
}
