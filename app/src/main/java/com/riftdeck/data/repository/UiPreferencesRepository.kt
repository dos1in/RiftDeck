package com.riftdeck.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.uiPreferences by preferencesDataStore(name = "ui_preferences")

data class UiPreferences(val reducedMotion: Boolean = false, val sortDescending: Boolean = false)

class UiPreferencesRepository(context: Context) {
    private val store = context.applicationContext.uiPreferences
    private val reducedMotionKey = booleanPreferencesKey("reduced_motion")
    private val sortDescendingKey = booleanPreferencesKey("sort_descending")
    val preferences = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { values ->
        UiPreferences(values[reducedMotionKey] ?: false, values[sortDescendingKey] ?: false)
    }
    suspend fun setReducedMotion(enabled: Boolean) { store.edit { it[reducedMotionKey] = enabled } }
    suspend fun setSortDescending(enabled: Boolean) { store.edit { it[sortDescendingKey] = enabled } }
}
