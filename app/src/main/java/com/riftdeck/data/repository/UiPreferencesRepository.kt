package com.riftdeck.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
)

class UiPreferencesRepository internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.uiPreferences)

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val themePaletteKey = stringPreferencesKey("theme_palette")
    private val reducedMotionKey = booleanPreferencesKey("reduced_motion")
    private val sortDescendingKey = booleanPreferencesKey("sort_descending")
    val preferences = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { values ->
        UiPreferences(
            reducedMotion = values[reducedMotionKey] ?: false,
            sortDescending = values[sortDescendingKey] ?: false,
            themeMode = ThemeMode.fromStorage(values[themeModeKey]),
            themePalette = ThemePalette.fromStorage(values[themePaletteKey]),
        )
    }
    suspend fun setThemeMode(mode: ThemeMode) { store.edit { it[themeModeKey] = mode.storageValue } }
    suspend fun setThemePalette(palette: ThemePalette) { store.edit { it[themePaletteKey] = palette.storageValue } }
    suspend fun setReducedMotion(enabled: Boolean) { store.edit { it[reducedMotionKey] = enabled } }
    suspend fun setSortDescending(enabled: Boolean) { store.edit { it[sortDescendingKey] = enabled } }
}
