package com.riftdeck.data.repository

import android.content.Context
import com.riftdeck.core.emulator.EmulatorConfig
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
    val romFolders: Set<String> = emptySet(),
    val emulators: Map<Long, EmulatorConfig> = emptyMap(),
    val navigationRailExpanded: Boolean = true,
    val videoPreviews: Boolean = false,
    val previewDelayMs: Int = 650,
    val loopVideoPreviews: Boolean = true,
    val defaultHomeCategory: String = "All",
)

class UiPreferencesRepository internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.uiPreferences)

    private val defaultHomeCategoryKey = stringPreferencesKey("default_home_category")
    private val foldersKey = stringSetPreferencesKey("rom_folders")
    private val previewDelayKey = intPreferencesKey("preview_delay_ms")
    private val loopVideoKey = booleanPreferencesKey("loop_video_previews")
    private val videoPreviewsKey = booleanPreferencesKey("video_previews")
    private val reducedMotionKey = booleanPreferencesKey("reduced_motion")
    private val sortDescendingKey = booleanPreferencesKey("sort_descending")
    // Preserve the stored choice from the earlier show/hide setting.
    private val navigationRailExpandedKey = booleanPreferencesKey("navigation_rail_visible")
    val preferences = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { values ->
        UiPreferences(
            defaultHomeCategory = values[defaultHomeCategoryKey]?.takeIf { it in listOf("All", "Recent", "Favorites") } ?: "All",
            romFolders = values[foldersKey] ?: emptySet(),
            emulators = values.asMap().keys.mapNotNull { key ->
                val platform = key.name.removePrefix("emulator_package_").toLongOrNull()
                    ?.takeIf { key.name.startsWith("emulator_package_") && it > 0 } ?: return@mapNotNull null
                val pkg = values[stringPreferencesKey("emulator_package_$platform")] ?: return@mapNotNull null
                val activity = values[stringPreferencesKey("emulator_activity_$platform")] ?: return@mapNotNull null
                platform to EmulatorConfig(platform, pkg, activity,
                    values[stringPreferencesKey("emulator_name_$platform")] ?: pkg,
                    action = values[stringPreferencesKey("emulator_action_$platform")] ?: "android.intent.action.VIEW",
                    mimeType = values[stringPreferencesKey("emulator_mime_$platform")] ?: "application/octet-stream",
                    extras = values[stringSetPreferencesKey("emulator_extras_$platform")].orEmpty()
                        .mapNotNull(::decodeEmulatorExtra).toMap())
            }.toMap(),
            navigationRailExpanded = values[navigationRailExpandedKey] ?: true,
            videoPreviews = values[videoPreviewsKey] ?: false,
            previewDelayMs = values[previewDelayKey]?.takeIf { it in listOf(650, 1200, 2000) } ?: 650,
            loopVideoPreviews = values[loopVideoKey] ?: true,
            reducedMotion = values[reducedMotionKey] ?: false,
            sortDescending = values[sortDescendingKey] ?: false,
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
            it[stringPreferencesKey("emulator_action_$id")] = config.action
            it[stringSetPreferencesKey("emulator_extras_$id")] = config.extras.mapTo(mutableSetOf()) { (key, value) ->
                "${key.length}:$key$value"
            }
        }
    }
    suspend fun addRomFolder(uri: String) { store.edit { it[foldersKey] = (it[foldersKey] ?: emptySet()) + uri } }
    suspend fun removeRomFolder(uri: String) { store.edit { it[foldersKey] = (it[foldersKey] ?: emptySet()) - uri } }
    suspend fun setNavigationRailExpanded(expanded: Boolean) { store.edit { it[navigationRailExpandedKey] = expanded } }
    suspend fun setPreviewDelay(delayMs: Int) {
        require(delayMs in listOf(650, 1200, 2000))
        store.edit { it[previewDelayKey] = delayMs }
    }
    suspend fun setLoopVideoPreviews(loop: Boolean) { store.edit { it[loopVideoKey] = loop } }
    suspend fun setVideoPreviews(enabled: Boolean) { store.edit { it[videoPreviewsKey] = enabled } }
    suspend fun setDefaultHomeCategory(value: String) {
        require(value in listOf("All", "Recent", "Favorites"))
        store.edit { it[defaultHomeCategoryKey] = value }
    }
    suspend fun setReducedMotion(enabled: Boolean) { store.edit { it[reducedMotionKey] = enabled } }
    suspend fun setSortDescending(enabled: Boolean) { store.edit { it[sortDescendingKey] = enabled } }
}

/** Length-prefix keys so colons, Unicode and newlines round-trip without delimiter escaping. */
private fun decodeEmulatorExtra(encoded: String): Pair<String, String>? {
    val separator = encoded.indexOf(':')
    if (separator < 0) return null
    val keyLength = encoded.substring(0, separator).toIntOrNull() ?: return null
    val payload = encoded.substring(separator + 1)
    if (keyLength !in 0..payload.length) return null
    return payload.substring(0, keyLength) to payload.substring(keyLength)
}
