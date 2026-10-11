package com.riftdeck.feature.home

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.riftdeck.core.model.Game
import com.riftdeck.data.repository.MockGameRepository
import com.riftdeck.data.repository.UiPreferencesRepository
import com.riftdeck.domain.repository.GameRepository
import com.riftdeck.feature.platform.LibraryFilter
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HomeViewModelTest {
    private val storeJob = SupervisorJob()
    private val models = ViewModelStore()
    private lateinit var directory: File
    private lateinit var preferences: UiPreferencesRepository
    private val games = object : GameRepository {
        override val games = MutableStateFlow(MockGameRepository().games.value)
        override suspend fun toggleFavorite(gameId: Long) {
            games.value = games.value.map { if (it.matchesId(gameId)) it.copy(favorite = !it.favorite) else it }
        }
    }

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        directory = File(context.cacheDir, "home-state-${System.nanoTime()}").apply { mkdirs() }
        preferences = UiPreferencesRepository(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(storeJob + Dispatchers.IO),
        ) { File(directory, "ui.preferences_pb") })
    }

    @After fun cleanup() = runBlocking {
        withContext(Dispatchers.Main) { models.clear() }
        storeJob.cancelAndJoin()
        directory.deleteRecursively()
        Unit
    }

    @Test fun freshLaunchUsesDefaultCategoryAndSettingsDoNotMoveCurrentShelf(): Unit = runBlocking {
        preferences.setDefaultHomeCategory("Favorites")
        val model = create()
        val initial = await(model) { it.filter == LibraryFilter.Favorites }
        assertTrue(initial.libraryGames.all(Game::favorite))
        preferences.setDefaultHomeCategory("Recent")
        val changed = await(model) { it.defaultHomeCategory == "Recent" }
        assertEquals(LibraryFilter.Favorites, changed.filter)
    }

    @Test fun filterSearchAndSelectionSurviveViewModelRecreation(): Unit = runBlocking {
        val saved = withContext(Dispatchers.Main) { SavedStateHandle() }
        val model = create(saved)
        await(model)
        withContext(Dispatchers.Main) {
            model.setFilter(LibraryFilter.Favorites)
            model.setSearchQuery("Pocket")
        }
        val result = await(model) { it.libraryGames.size == 1 && it.searchQuery == "Pocket" }
        withContext(Dispatchers.Main) { model.focusGame(result.libraryGames.single().id) }
        val restored = withContext(Dispatchers.Main) {
            val values = saved.keys().associateWith { saved.get<Any?>(it) }
            models.clear()
            SavedStateHandle(values)
        }
        val next = create(restored)
        val state = await(next) { it.filter == LibraryFilter.Favorites && it.searchQuery == "Pocket" }
        assertEquals(result.libraryGames.single().id, state.focusedGameId)
        assertEquals(result.libraryGames, state.libraryGames)
    }

    @Test fun unfavoritingSelectedGameKeepsNearestRemainingPosition(): Unit = runBlocking {
        games.games.value = games.games.value.map { it.copy(favorite = true) }
        val model = create()
        await(model)
        withContext(Dispatchers.Main) { model.setFilter(LibraryFilter.Favorites) }
        val before = await(model) { it.filter == LibraryFilter.Favorites }
        val selected = before.libraryGames[2]
        withContext(Dispatchers.Main) { model.focusGame(selected.id) }
        await(model) { it.focusedGameId == selected.id }
        games.toggleFavorite(selected.id)
        val after = await(model) { it.libraryGames.size == before.libraryGames.size - 1 }
        assertEquals(before.libraryGames[3].id, after.focusedGameId)
    }

    @Test fun clearingSearchRestoresLibraryWithoutChangingCategory(): Unit = runBlocking {
        val model = create()
        await(model)
        withContext(Dispatchers.Main) {
            model.setFilter(LibraryFilter.Favorites)
            model.setSearchQuery("no matching game")
        }
        val empty = await(model) { it.searchQuery.isNotEmpty() }
        assertTrue(empty.libraryGames.isEmpty())
        assertNull(empty.focusedGameId)
        withContext(Dispatchers.Main) { model.setSearchQuery("") }
        val restored = await(model) { it.searchQuery.isEmpty() && it.filter == LibraryFilter.Favorites }
        assertFalse(restored.libraryGames.isEmpty())
        assertTrue(restored.libraryGames.all(Game::favorite))
        assertNotNull(restored.focusedGameId)
    }

    @Test fun repeatedNavigationAndImmediateConfirmUseLatestInputCursor(): Unit = runBlocking {
        val model = create()
        val initial = await(model)
        withContext(Dispatchers.Main) {
            model.focusGame(initial.libraryGames.first().id)
            model.moveGame(1, true)
            model.moveGame(1, true)
            assertEquals(initial.libraryGames[2].id, model.selectedGameId())
            model.moveGame(-1, false)
            assertEquals(initial.libraryGames[1].id, model.selectedGameId())
        }
        await(model) { it.focusedGameId == initial.libraryGames[1].id }
    }

    @Test fun repeatedShoulderPressesAdvanceEveryCategoryBeforeRecomposition(): Unit = runBlocking {
        val model = create()
        await(model)
        withContext(Dispatchers.Main) {
            assertEquals(LibraryFilter.Recent, model.changeCategory(1))
            assertEquals(LibraryFilter.Favorites, model.changeCategory(1))
            assertEquals(LibraryFilter.All, model.changeCategory(1))
            assertEquals(LibraryFilter.Favorites, model.changeCategory(-1))
        }
        await(model) { it.filter == LibraryFilter.Favorites }
    }

    @Test fun changingCategoryBeforeUnfavoritingKeepsSelectedGamesNewPosition(): Unit = runBlocking {
        games.games.value = games.games.value.map { it.copy(favorite = true) }
        val model = create()
        val initial = await(model)
        val selected = initial.libraryGames[2]
        withContext(Dispatchers.Main) {
            model.focusGame(selected.id)
            model.setFilter(LibraryFilter.Favorites)
        }
        val favorites = await(model) { it.filter == LibraryFilter.Favorites && it.focusedGameId == selected.id }
        withContext(Dispatchers.Main) { model.toggleFavorite(selected.id) }
        val remaining = await(model) { it.libraryGames.size == favorites.libraryGames.size - 1 }
        assertEquals(favorites.libraryGames[3].id, remaining.focusedGameId)
    }

    @Test fun sortingBeforeRomRemovalKeepsTheNewShelfPosition(): Unit = runBlocking {
        val saved = withContext(Dispatchers.Main) { SavedStateHandle() }
        val model = create(saved)
        val initial = await(model)
        val selected = initial.libraryGames[1]
        withContext(Dispatchers.Main) { model.focusGame(selected.id) }
        await(model) { it.focusedGameId == selected.id }
        preferences.setSortDescending(true)
        val sorted = await(model) { it.sortDescending }
        val index = sorted.libraryGames.indexOfFirst { it.id == selected.id }
        val savedIndex = withContext(Dispatchers.Main) { saved.getStateFlow("focused_index", 0) }
        withTimeout(5_000) { savedIndex.first { it == index } }
        games.games.value = games.games.value.filterNot { it.id == selected.id }
        val remaining = await(model) { it.libraryGames.size == initial.libraryGames.size - 1 }
        assertEquals(sorted.libraryGames[index + 1].id, remaining.focusedGameId)
    }

    private suspend fun create(saved: SavedStateHandle? = null) = withContext(Dispatchers.Main) {
        HomeViewModel(games, preferences, saved ?: SavedStateHandle()).also { models.put("home", it) }
    }

    private suspend fun await(model: HomeViewModel, matches: (HomeUiState) -> Boolean = { true }) =
        withTimeout(5_000) { model.uiState.first { it.isReady && matches(it) } }
}
