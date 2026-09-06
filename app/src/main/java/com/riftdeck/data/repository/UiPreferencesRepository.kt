package com.riftdeck.data.repository

import android.content.Context
import com.riftdeck.core.emulator.EmulatorConfig
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.riftdeck.core.model.ThemeMode
import com.riftdeck.core.model.ThemePalette
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.uiPreferences by preferencesDataStore(name = "ui_preferences")

data class UiPreferences(
    val reducedMotion: Boolean = false,
    val sortDescending: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val themePalette: ThemePalette = ThemePalette.Rift,
    val romFolders: Set<String> = emptySet(),
    val emulators: Map<Long, EmulatorConfig> = emptyMap(),
    val navigationRailExpanded: Boolean = true,
)

class UiPreferencesRepository internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.uiPreferences)

    private val foldersKey = stringSetPreferencesKey("rom_folders")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val themePaletteKey = stringPreferencesKey("theme_palette")
    private val reducedMotionKey = booleanPreferencesKey("reduced_motion")
    private val sortDescendingKey = booleanPreferencesKey("sort_descending")
    // Preserve the stored choice from the earlier show/hide setting.
    private val navigationRailExpandedKey = booleanPreferencesKey("navigation_rail_visible")
    val preferences = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { values ->
        UiPreferences(
            romFolders = values[foldersKey] ?: emptySet(),
            emulators = values.asMap().keys.mapNotNull { key ->
                val platform = key.name.removePrefix("emulator_package_").toLongOrNull()
                    ?.takeIf { key.name.startsWith("emulator_package_") && it > 0 } ?: return@mapNotNull null
                val pkg = values[stringPreferencesKey("emulator_package_$platform")] ?: return@mapNotNull null
                val activity = values[stringPreferencesKey("emulator_activity_$platform")] ?: return@mapNotNull null
                platform to EmulatorConfig(platform, pkg, activity,
                    values[stringPreferencesKey("emulator_name_$platform")] ?: pkg,
                    mimeType = values[stringPreferencesKey("emulator_mime_$platform")] ?: "application/octet-stream")
            }.toMap(),
            navigationRailExpanded = values[navigationRailExpandedKey] ?: true,
            reducedMotion = values[reducedMotionKey] ?: false,
            sortDescending = values[sortDescendingKey] ?: false,
            themeMode = ThemeMode.fromStorage(values[themeModeKey]),
            themePalette = ThemePalette.fromStorage(values[themePaletteKey]),
        )
    }
    suspend fun setEmulator(config: EmulatorConfig) {
        require(config.platformId > 0 && config.packageName.isNotBlank() && config.activityName.isNotBlank())
        store.edit {
            val id = config.platformId
            it[stringPreferencesKey("emulator_package_$id")] = config.packageName
            it[stringPreferencesKey("emulator_activity_$id")] = config.activityName
            it[stringPreferencesKey("emulator_name_$id")] = config.displayName
            it[stringPreferencesKey("emulator_mime_$id")] = config.mimeType
        }
    }
    suspend fun addRomFolder(uri: String) { store.edit { it[foldersKey] = (it[foldersKey] ?: emptySet()) + uri } }
    suspend fun removeRomFolder(uri: String) { store.edit { it[foldersKey] = (it[foldersKey] ?: emptySet()) - uri } }
    suspend fun setThemeMode(mode: ThemeMode) { store.edit { it[themeModeKey] = mode.storageValue } }
    suspend fun setThemePalette(palette: ThemePalette) { store.edit { it[themePaletteKey] = palette.storageValue } }
    suspend fun setNavigationRailExpanded(expanded: Boolean) { store.edit { it[navigationRailExpandedKey] = expanded } }
    suspend fun setReducedMotion(enabled: Boolean) { store.edit { it[reducedMotionKey] = enabled } }
    suspend fun setSortDescending(enabled: Boolean) { store.edit { it[sortDescendingKey] = enabled } }
}
