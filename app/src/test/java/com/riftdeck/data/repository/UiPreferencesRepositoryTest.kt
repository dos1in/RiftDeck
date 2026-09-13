package com.riftdeck.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UiPreferencesRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun performanceChoicesPersistWithoutChangingVideoChoice() = runBlocking {
        withRepository {
            it.setVideoPreviews(true)
            it.setPreviewDelay(2000)
            it.setLoopVideoPreviews(false)
        }
        withRepository {
            val prefs = it.preferences.first()
            assertEquals(2000, prefs.previewDelayMs)
            assertEquals(false, prefs.loopVideoPreviews)
            assertEquals(true, prefs.videoPreviews)
            it.setPreviewDelay(650)
            it.setLoopVideoPreviews(true)
        }
        withRepository {
            assertEquals(650, it.preferences.first().previewDelayMs)
            assertEquals(true, it.preferences.first().loopVideoPreviews)
        }
    }

    @Test fun invalidPreviewDelayIsRejected() = runBlocking {
        withRepository {
            try { it.setPreviewDelay(-1); org.junit.Assert.fail("Invalid delay accepted") }
            catch (_: IllegalArgumentException) { }
            assertEquals(650, it.preferences.first().previewDelayMs)
        }
    }

    @Test fun videoChoicePersistsAcrossStoreRecreation() = runBlocking {
        withRepository { it.setVideoPreviews(true) }
        withRepository { assertEquals(true, it.preferences.first().videoPreviews) }
        withRepository { it.setVideoPreviews(false) }
        withRepository { assertEquals(false, it.preferences.first().videoPreviews) }
    }

    @Test fun newInstallUsesDefaultPreferences() = runBlocking {
        withRepository { repository -> assertEquals(UiPreferences(), repository.preferences.first()) }
    }

    @Test fun motionAndSortPersistAcrossStoreRecreation() = runBlocking {
        withRepository { repository ->
            repository.setReducedMotion(true)
            repository.setSortDescending(true)
        }
        withRepository { repository ->
            assertEquals(UiPreferences(true, true), repository.preferences.first())
        }
        withRepository { repository ->
            assertEquals(UiPreferences(true, true), repository.preferences.first())
        }
    }

    @Test fun removedThemePreferencesAreIgnoredWithoutLosingOtherPreferences() = runBlocking {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file() }
        try {
            store.edit { values ->
                values[booleanPreferencesKey("reduced_motion")] = true
                values[booleanPreferencesKey("sort_descending")] = true
            }
            store.edit { values ->
                values[stringPreferencesKey("theme_mode")] = "light"
                values[stringPreferencesKey("theme_palette")] = "ocean"
            }
            val repository = UiPreferencesRepository(store)
            assertEquals(UiPreferences(true, true), repository.preferences.first())
            store.edit { values ->
                values[stringPreferencesKey("theme_mode")] = "unsupported-mode"
                values[stringPreferencesKey("theme_palette")] = "unsupported-palette"
            }
            assertEquals(UiPreferences(true, true), repository.preferences.first())
            assertEquals(UiPreferences(true, true), repository.preferences.first())
        } finally { job.cancelAndJoin() }
    }

    @Test fun navigationExpansionPersistsAcrossRestart() = runBlocking {
        withRepository { repository ->
            repository.setNavigationRailExpanded(false)
        }
        withRepository { repository ->
            assertEquals(false, repository.preferences.first().navigationRailExpanded)
            repository.setNavigationRailExpanded(true)
        }
        withRepository { repository ->
            assertEquals(true, repository.preferences.first().navigationRailExpanded)
        }
    }

    @Test fun folderSelectionsPersistAndDeduplicate() = runBlocking {
        withRepository { repository ->
            repository.addRomFolder("content://provider/tree/one")
            repository.addRomFolder("content://provider/tree/two")
            repository.addRomFolder("content://provider/tree/one")
        }
        withRepository { repository ->
            assertEquals(setOf("content://provider/tree/one", "content://provider/tree/two"), repository.preferences.first().romFolders)
            repository.removeRomFolder("content://provider/tree/one")
        }
        withRepository { repository ->
            assertEquals(setOf("content://provider/tree/two"), repository.preferences.first().romFolders)
        }
    }

    @Test fun emulatorMappingPersistsPerPlatformWithoutChangingFolders() = runBlocking {
        val gba = com.riftdeck.core.emulator.EmulatorConfig(1, "example.gba", "example.gba.Play", "GBA 测试")
        val other = com.riftdeck.core.emulator.EmulatorConfig(2, "example.other", "example.other.Play", "Other",
            mimeType = "application/x-test-rom")
        withRepository { repository ->
            repository.addRomFolder("content://provider/tree/one")
            repository.setEmulator(gba)
            repository.setEmulator(other)
        }
        withRepository { repository ->
            assertEquals(mapOf(1L to gba, 2L to other), repository.preferences.first().emulators)
            assertEquals(setOf("content://provider/tree/one"), repository.preferences.first().romFolders)
        }
    }

    @Test fun customLaunchArgumentsPersistAndAreReplacedWithTheSelection() = runBlocking {
        val config = com.riftdeck.core.emulator.EmulatorConfig(1, "example.gba", "example.gba.Play", "Custom",
            action = "example.action.LAUNCH", extras = mapOf("core" to "mgba", "参数:名" to "第一行\n第二行:值", "empty" to ""))
        withRepository { it.setEmulator(config) }
        withRepository {
            assertEquals(config, it.preferences.first().emulators[1L])
            it.setEmulator(config.copy(action = "android.intent.action.VIEW", extras = emptyMap()))
        }
        withRepository {
            assertEquals(config.copy(action = "android.intent.action.VIEW", extras = emptyMap()), it.preferences.first().emulators[1L])
        }
    }

    @Test fun legacyEmulatorMappingsKeepDefaultLaunchContract() = runBlocking {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file() }
        try {
            store.edit {
                it[stringPreferencesKey("emulator_package_1")] = "example.gba"
                it[stringPreferencesKey("emulator_activity_1")] = "example.gba.Play"
            }
            val config = UiPreferencesRepository(store).preferences.first().emulators.getValue(1)
            assertEquals("android.intent.action.VIEW", config.action)
            assertEquals(emptyMap<String, String>(), config.extras)
        } finally { job.cancelAndJoin() }
    }

    @Test fun defaultHomeCategorySurvivesStoreRecreation() = runBlocking {
        for (category in listOf("Recent", "Favorites", "All")) {
            withRepository { it.setDefaultHomeCategory(category) }
            withRepository { assertEquals(category, it.preferences.first().defaultHomeCategory) }
        }
    }

    @Test fun unknownDefaultHomeCategoryFallsBackToAll() = runBlocking {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file() }
        try {
            store.edit { it[stringPreferencesKey("default_home_category")] = "unknown" }
            assertEquals("All", UiPreferencesRepository(store).preferences.first().defaultHomeCategory)
        } finally { job.cancelAndJoin() }
    }

    @Test fun languageDefaultsToSystemAndPersistsAcrossRecreation() = runBlocking {
        withRepository { assertEquals("system", it.preferences.first().language) }
        for (language in listOf("zh-CN", "en", "system")) {
            withRepository { it.setLanguage(language) }
            withRepository { assertEquals(language, it.preferences.first().language) }
        }
    }

    @Test fun unknownStoredLanguageFallsBackToSystem() = runBlocking {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file() }
        try {
            store.edit { it[stringPreferencesKey("app_language")] = "unknown" }
            assertEquals("system", UiPreferencesRepository(store).preferences.first().language)
        } finally { job.cancelAndJoin() }
    }

    private fun file() = folder.root.resolve("ui.preferences_pb")

    private suspend fun withRepository(block: suspend (UiPreferencesRepository) -> Unit) {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file() }
        try { block(UiPreferencesRepository(store)) } finally { job.cancelAndJoin() }
    }
}
