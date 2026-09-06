package com.riftdeck.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.riftdeck.core.model.ThemeMode
import com.riftdeck.core.model.ThemePalette
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

    @Test fun newInstallFollowsSystemWithRiftPalette() = runBlocking {
        withRepository { repository -> assertEquals(UiPreferences(), repository.preferences.first()) }
    }

    @Test fun appearancePersistsAcrossStoreRecreationWithoutLosingOtherPreferences() = runBlocking {
        withRepository { repository ->
            repository.setReducedMotion(true)
            repository.setSortDescending(true)
            repository.setThemeMode(ThemeMode.Light)
            repository.setThemePalette(ThemePalette.Ocean)
        }
        withRepository { repository ->
            assertEquals(UiPreferences(true, true, ThemeMode.Light, ThemePalette.Ocean), repository.preferences.first())
            repository.setThemeMode(ThemeMode.System)
            repository.setThemePalette(ThemePalette.Ember)
        }
        withRepository { repository ->
            assertEquals(UiPreferences(true, true, ThemeMode.System, ThemePalette.Ember), repository.preferences.first())
        }
    }

    @Test fun legacyAndUnknownAppearanceValuesFallBackWithoutLosingPreferences() = runBlocking {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file() }
        try {
            store.edit { values ->
                values[booleanPreferencesKey("reduced_motion")] = true
                values[booleanPreferencesKey("sort_descending")] = true
            }
            val repository = UiPreferencesRepository(store)
            assertEquals(UiPreferences(true, true), repository.preferences.first())
            store.edit { values ->
                values[stringPreferencesKey("theme_mode")] = "unsupported-mode"
                values[stringPreferencesKey("theme_palette")] = "unsupported-palette"
            }
            assertEquals(UiPreferences(true, true), repository.preferences.first())
            repository.setThemeMode(ThemeMode.Dark)
            repository.setThemePalette(ThemePalette.Rift)
            assertEquals(UiPreferences(true, true, ThemeMode.Dark), repository.preferences.first())
        } finally { job.cancelAndJoin() }
    }

    @Test fun folderSelectionsPersistAndDeduplicateWithoutChangingAppearance() = runBlocking {
        withRepository { repository ->
            repository.setThemeMode(ThemeMode.Light)
            repository.addRomFolder("content://provider/tree/one")
            repository.addRomFolder("content://provider/tree/two")
            repository.addRomFolder("content://provider/tree/one")
        }
        withRepository { repository ->
            assertEquals(setOf("content://provider/tree/one", "content://provider/tree/two"), repository.preferences.first().romFolders)
            repository.removeRomFolder("content://provider/tree/one")
            assertEquals(ThemeMode.Light, repository.preferences.first().themeMode)
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

    private fun file() = folder.root.resolve("ui.preferences_pb")

    private suspend fun withRepository(block: suspend (UiPreferencesRepository) -> Unit) {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file() }
        try { block(UiPreferencesRepository(store)) } finally { job.cancelAndJoin() }
    }
}
