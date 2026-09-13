package com.riftdeck.feature.home

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riftdeck.R
import com.riftdeck.core.input.LocalControllerInputEnabled
import com.riftdeck.feature.platform.AlphabetJumpDialog
import com.riftdeck.feature.platform.alphabetPositions
import com.riftdeck.core.input.GameAction
import com.riftdeck.core.ui.components.*
import com.riftdeck.core.ui.theme.LocalFrontendTheme
import com.riftdeck.feature.platform.LibraryFilter
import com.riftdeck.feature.platform.libraryGames

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onFocusGame: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onOpenGame: (Long) -> Unit,
    onPlayGame: (Long) -> Unit,
    onSort: () -> Unit,
    onNavigate: (DeckSection) -> Unit,
    onExit: () -> Unit,
    onSearch: () -> Unit,
    onAddFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFrontendTheme.current
    val categories = remember { listOf(LibraryFilter.All, LibraryFilter.Recent, LibraryFilter.Favorites) }
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.entries.firstOrNull { it.name == uiState.defaultHomeCategory } ?: LibraryFilter.All) }
    val games = remember(uiState.games, filter, uiState.sortDescending, uiState.searchQuery) {
        libraryGames(uiState.games, filter, uiState.sortDescending, uiState.searchQuery)
    }
    var alphabetOpen by rememberSaveable { mutableStateOf(false) }
    var pendingJump by remember { mutableStateOf<Long?>(null) }
    val letters = remember(games) { alphabetPositions(games) }
    val focus = remember { listOf("list", "cover", "empty", "Recent", "All", "Favorites", "sort", "alphabet", "search")
        .associateWith { FocusRequester() } }
    var focusedKey by rememberSaveable { mutableStateOf("list") }
    var listFocused by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf(uiState.focusedGameId) }
    val game = games.firstOrNull { it.id == selectedId }
        ?: games.firstOrNull { it.id == uiState.focusedGameId } ?: games.firstOrNull()
    val index = games.indexOfFirst { it.id == game?.id }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = index)
    fun select(id: Long) {
        val targetIndex = games.indexOfFirst { it.id == id }
        val info = listState.layoutInfo
        val target = info.visibleItemsInfo.firstOrNull { it.index == targetIndex }
        if (targetIndex >= 0 && (target == null || target.offset < info.viewportStartOffset ||
                target.offset + target.size > info.viewportEndOffset)) {
            listState.requestScrollToItem(targetIndex)
        }
        selectedId = id
        onFocusGame(id)
    }
    LaunchedEffect(pendingJump) {
        pendingJump?.let { id ->
            withFrameNanos { }
            select(id)
            focus.getValue("list").requestFocus()
            pendingJump = null
        }
    }
    fun changeCategory(delta: Int) {
        filter = categories[(categories.indexOf(filter) + delta + categories.size) % categories.size]
        if (!listFocused && categories.any { it.name == focusedKey }) focus.getValue(filter.name).requestFocus()
    }
    LaunchedEffect(filter) {
        if (filter == LibraryFilter.Recent && focusedKey in listOf("sort", "alphabet")) {
            focus.getValue("search").requestFocus()
        }
    }
    LaunchedEffect(game?.id, filter) {
        if (game != null) {
            if (selectedId != game.id) { selectedId = game.id; onFocusGame(game.id) }
            val info = listState.layoutInfo
            val target = info.visibleItemsInfo.firstOrNull { it.index == index }
            if (target == null || target.offset < info.viewportStartOffset ||
                target.offset + target.size > info.viewportEndOffset) listState.scrollToItem(index)
        }
    }
    LaunchedEffect(game != null) {
        withFrameNanos { }
        val key = if (game == null) "empty" else focusedKey.takeUnless { it == "empty" } ?: "list"
        focus.getValue(key).requestFocus()
    }
    CompositionLocalProvider(LocalControllerInputEnabled provides (LocalControllerInputEnabled.current && !alphabetOpen)) {
    DeckScaffold(DeckSection.Home, focus.getValue(if (game == null) "empty" else "list"), onNavigate,
        onAction = { action ->
            when (action) {
                GameAction.Back -> { onExit(); true }
                GameAction.Details -> { game?.let { onOpenGame(it.id) }; true }
                GameAction.Favorite -> { game?.let { onToggleFavorite(it.id) }; true }
                GameAction.PreviousCategory -> { changeCategory(-1); true }
                GameAction.NextCategory -> { changeCategory(1); true }
                GameAction.Up, GameAction.Down -> if (listFocused && game != null) {
                    if (action == GameAction.Up && index == 0) false
                    else {
                        games.getOrNull(index + if (action == GameAction.Up) -1 else 1)?.let { select(it.id) }
                        true
                    }
                } else false
                GameAction.Confirm -> if (listFocused) { game?.let { onPlayGame(it.id) }; true } else false
                GameAction.PreviousPage, GameAction.NextPage -> {
                    if (games.isNotEmpty()) select(games[(index + if (action == GameAction.NextPage) 7 else -7).coerceIn(0, games.lastIndex)].id)
                    true
                }
                GameAction.Search -> { onSearch(); true }
                GameAction.Menu -> { onNavigate(DeckSection.Settings); true }
                else -> false
            }
        }, modifier = modifier, hasGame = game != null,
        headerContent = { rail, compact ->
                categories.forEachIndexed { i, category ->
                    NeonActionButton(stringResource(when (category) {
                        LibraryFilter.Recent -> R.string.recent_title
                        LibraryFilter.All -> R.string.all_games
                        LibraryFilter.Favorites -> R.string.favorites_title
                    }), { filter = category }, Modifier.width(if (compact) 86.dp else 116.dp),
                        selected = filter == category, emphasizeSelection = true, focusRequester = focus.getValue(category.name),
                        left = categories.getOrNull(i - 1)?.let { focus.getValue(it.name) } ?: rail,
                        right = categories.getOrNull(i + 1)?.let { focus.getValue(it.name) } ?: rail, up = FocusRequester.Cancel,
                        down = focus.getValue(if (filter == LibraryFilter.Recent) "search" else "sort"),
                        onFocused = { focusedKey = category.name })
                }
        },
        confirmLabel = if (listFocused) R.string.play_game else R.string.hint_confirm,
    ) { rail, compact ->
        Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 12.dp else 24.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (uiState.searchQuery.isNotBlank()) stringResource(R.string.search_active, uiState.searchQuery)
                    else stringResource(R.string.game_position, if (game == null) 0 else index + 1, games.size),
                    color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                val sorted = filter != LibraryFilter.Recent
                val entry = focus.getValue(if (game == null) "empty" else "list")
                NeonActionButton(stringResource(if (uiState.sortDescending) R.string.sort_descending_short else R.string.sort_ascending_short),
                    onSort, Modifier.width(74.dp), enabled = sorted, focusRequester = focus.getValue("sort"),
                    left = focus.getValue(filter.name), right = focus.getValue(if (games.isEmpty()) "search" else "alphabet"),
                    up = focus.getValue(filter.name), down = entry, onFocused = { focusedKey = "sort" })
                NeonActionButton(stringResource(R.string.alphabet_jump), { alphabetOpen = true }, Modifier.width(68.dp),
                    enabled = sorted && games.isNotEmpty(), focusRequester = focus.getValue("alphabet"),
                    left = focus.getValue("sort"), right = focus.getValue("search"), up = focus.getValue(filter.name), down = entry,
                    onFocused = { focusedKey = "alphabet" })
                NeonActionButton(stringResource(R.string.search_apply), onSearch, Modifier.width(68.dp),
                    selected = uiState.searchQuery.isNotBlank(), focusRequester = focus.getValue("search"),
                    left = if (!sorted) focus.getValue(filter.name) else focus.getValue(if (games.isEmpty()) "sort" else "alphabet"),
                    up = focus.getValue(filter.name), down = entry, onFocused = { focusedKey = "search" })
            }
            if (game == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val emptyLibrary = uiState.games.isEmpty()
                    EmptyLibraryState(stringResource(if (emptyLibrary) R.string.empty_library_title else R.string.empty_filter_title),
                        stringResource(if (emptyLibrary) R.string.empty_library_message else R.string.empty_filter_message),
                        stringResource(if (emptyLibrary) R.string.add_rom_folder else R.string.all_games),
                        focus.getValue("empty"), { if (emptyLibrary) onAddFolder() else {
                            filter = LibraryFilter.All
                            if (uiState.searchQuery.isNotBlank()) onSearch()
                        } }, left = rail, up = focus.getValue(filter.name))
                }
            } else {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BoxWithConstraints(Modifier.weight(0.4f).fillMaxHeight()) {
                        val rowHeight = ((maxHeight - 28.dp) / 7).coerceAtLeast(36.dp)
                        // A single persistent focus target owns navigation; recycled rows never own D-pad focus.
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()
                            .focusRequester(focus.getValue("list"))
                            .focusProperties { up = focus.getValue(filter.name); down = FocusRequester.Cancel
                                left = rail; right = focus.getValue("cover") }
                            .onFocusChanged { listFocused = it.isFocused; if (it.isFocused) focusedKey = "list" }
                            .semantics { contentDescription = game.title; selected = true }
                            .focusable(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(2.dp)) {
                            items(games, key = { it.id }) { item ->
                                val selected = item.id == game.id
                                val favoriteLabel = stringResource(if (item.favorite) R.string.favorite_saved else R.string.add_favorite)
                                Row(Modifier.fillMaxWidth().height(rowHeight)
                                    .focusProperties { canFocus = false }
                                    .clickable { select(item.id); focus.getValue("list").requestFocus() }
                                    .semantics { this.selected = selected; stateDescription = favoriteLabel }
                                    .riftSelectionFrame(selected && listFocused, selected).padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    GameArtwork(item, stringResource(R.string.artwork_description, item.title),
                                        Modifier.width(rowHeight * 0.75f).fillMaxHeight(), showLabel = false)
                                    Column(Modifier.weight(1f)) {
                                        key(item.id, selected && listFocused) {
                                            Text(item.title, color = if (selected) colors.primary else colors.textPrimary,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 17.sp),
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth().then(
                                                    if (selected && listFocused && !uiState.reducedMotion)
                                                        Modifier.basicMarquee(iterations = 1, initialDelayMillis = 1200)
                                                    else Modifier))
                                        }
                                        val metadata = listOfNotNull(item.genre?.takeIf { it.isNotBlank() }, item.releaseYear?.toString())
                                            .joinToString(" · ")
                                        val summary = if (filter == LibraryFilter.Recent || metadata.isEmpty()) gameLastPlayed(item) else metadata
                                        Text(summary, color = colors.textSecondary,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (item.favorite) Text("★", color = colors.primary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.clearAndSetSemantics { })
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(0.6f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        GameStageCover(game, Modifier.weight(1f).fillMaxWidth(), focus.getValue("cover"),
                            left = focus.getValue("list"), up = focus.getValue(filter.name), showLabel = false,
                            onFocused = { focusedKey = "cover" }, onClick = { onOpenGame(game.id) },
                            positionLabel = stringResource(R.string.game_position, index + 1, games.size))
                        Text(game.title, color = colors.textPrimary, style = MaterialTheme.typography.titleLarge,
                            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                        Text(listOfNotNull(game.developer, game.releaseYear?.toString(), game.genre).joinToString(" · ")
                            .ifEmpty { stringResource(R.string.metadata_unknown) }, color = colors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    }
    if (alphabetOpen) AlphabetJumpDialog(letters.keys.sorted(), onChoose = { letter ->
        pendingJump = letters[letter]?.let { games.getOrNull(it)?.id }
        alphabetOpen = false
    }, onDismiss = { alphabetOpen = false })

}
