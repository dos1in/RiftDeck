package com.riftdeck.feature.platform

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
    val columns = 3
    val listState = rememberLazyGridState()
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
        val visibleRequester = rowFocus[target.id]?.takeIf {
            listState.layoutInfo.visibleItemsInfo.any { it.key == target.id }
        }
        if (visibleRequester != null) {
            // Attached visible rows can take focus in this input event. Only offscreen rows
            // need to wait for scrolling and composition to create their focus target.
            visibleRequester.requestFocus()
            return
        }
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
                        val layout = listState.layoutInfo
                        val visibleRows = layout.visibleItemsInfo.filter {
                            it.offset.y >= layout.viewportStartOffset &&
                                it.offset.y + it.size.height <= layout.viewportEndOffset
                        }.map { it.row }.distinct().size.coerceAtLeast(1)
                        val page = visibleRows * columns
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
                        when {
                            action == GameAction.Up && index < columns -> { moveJob?.cancel(); filters.getValue(uiState.filter).requestFocus() }
                            action == GameAction.Up -> requestRow(index - columns)
                            index / columns == games.lastIndex / columns -> { moveJob?.cancel(); play.requestFocus() }
                            else -> requestRow((index + columns).coerceAtMost(games.lastIndex))
                        }
                        true
                    }
                    focusArea == "play" && action == GameAction.Up && selected != null -> { requestRow(games.indexOf(selected)); true }
                    focusArea == "play" && action == GameAction.Down -> true
                    filterFocused && action == GameAction.Down && selected != null -> { requestRow(games.indexOf(selected)); true }
                    else -> false
                }
                GameAction.Left, GameAction.Right -> if (inList && selected != null) {
                    val index = games.indexOfFirst { it.id == cursorId }.coerceAtLeast(0)
                    if (action == GameAction.Left && index % columns == 0) {
                        moveJob?.cancel()
                        false // Explicit card focusProperties sends the left edge to the rail.
                    } else {
                        requestRow((index + if (action == GameAction.Left) -1 else 1).coerceAtMost(games.lastIndex))
                        true
                    }
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
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val gap = if (compact) 8.dp else 12.dp
                    // Keep two rows readable, while allowing the grid to scroll on short displays.
                    val cardHeight = ((maxHeight - gap - 6.dp) / 2).coerceAtLeast(80.dp)
                    LazyVerticalGrid(columns = GridCells.Fixed(columns), state = listState,
                        modifier = Modifier.fillMaxSize().onFocusChanged { listFocused = it.hasFocus }.focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalArrangement = Arrangement.spacedBy(gap), contentPadding = PaddingValues(vertical = 3.dp)) {
                        itemsIndexed(games, key = { _, game -> game.id }) { _, game ->
                            val requester = remember(game.id) { FocusRequester() }
                            DisposableEffect(game.id) {
                                rowFocus[game.id] = requester
                                onDispose { rowFocus.remove(game.id) }
                            }
                            LibraryCoverCard(game, selected.id == game.id, requester,
                                rail,
                                Modifier.height(cardHeight), compact,
                                onFocused = { cursorId = game.id; onFocusGame(game.id); focusArea = "list"; pendingImport = false },
                                onClick = { onOpenGame(game.id) })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = if (compact) 8.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(selected.title, color = colors.textPrimary, style = MaterialTheme.typography.titleLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!compact) Text(gamePlaytime(selected), color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                    NeonActionButton(stringResource(R.string.play_game), { onPlayGame(selected.id) },
                        Modifier.width(if (compact) 112.dp else 154.dp), primary = true, focusRequester = play, left = rail,
                        up = FocusRequester.Cancel, down = FocusRequester.Cancel,
                        onFocused = { focusArea = "play" })
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
private fun LibraryCoverCard(game: Game, selected: Boolean, requester: FocusRequester,
    left: FocusRequester, modifier: Modifier, compact: Boolean, onFocused: () -> Unit, onClick: () -> Unit) {
    val colors = LocalFrontendTheme.current
    var focused by remember { mutableStateOf(false) }
    val favoriteState = if (game.favorite) stringResource(R.string.favorite_saved) else ""
    Column(modifier.fillMaxWidth().focusRequester(requester)
        .focusProperties { this.left = left; right = FocusRequester.Cancel; up = FocusRequester.Cancel; down = FocusRequester.Cancel }
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
        .riftSelectionFrame(focused, selected)
        .semantics { this.selected = selected; stateDescription = favoriteState }
        .controllerClickable(onClick = onClick).padding(if (compact) 5.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GameArtwork(game, stringResource(R.string.artwork_description, game.title),
            Modifier.fillMaxWidth().weight(1f), showLabel = false)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(game.title, color = if (selected) colors.secondary else colors.textPrimary,
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            if (game.favorite) Text("★", color = colors.accentText, style = MaterialTheme.typography.labelMedium)
        }
    }
}
