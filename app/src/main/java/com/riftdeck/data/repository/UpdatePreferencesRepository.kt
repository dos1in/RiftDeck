package com.riftdeck.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.riftdeck.core.update.MAX_UPDATE_APK_BYTES
import com.riftdeck.core.update.ReleaseVersion
import com.riftdeck.core.update.UpdateRelease
import kotlinx.coroutines.flow.map

private val Context.updatePreferences by preferencesDataStore(name = "app_updates")

data class UpdatePreferences(
    val autoCheck: Boolean = true,
    val lastAttemptMillis: Long = 0,
    val lastCheckedMillis: Long = 0,
    val release: UpdateRelease? = null,
    val dismissedVersion: String? = null,
)

/** Metadata and acknowledgement survive process death; APK bytes stay in private files. */
class UpdatePreferencesRepository internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.updatePreferences)

    private val auto = booleanPreferencesKey("auto_check")
    private val attempt = longPreferencesKey("last_attempt")
    private val checked = longPreferencesKey("last_checked")
    private val dismissed = stringPreferencesKey("dismissed_version")
    private val tag = stringPreferencesKey("release_tag")
    private val version = stringPreferencesKey("release_version")
    private val notes = stringPreferencesKey("release_notes")
    private val page = stringPreferencesKey("release_page")
    private val apk = stringPreferencesKey("release_apk")
    private val size = longPreferencesKey("release_size")
    private val hash = stringPreferencesKey("release_sha256")

    val preferences = store.data.map { values ->
        val name = values[version]
        val parsed = name?.let(ReleaseVersion::parse)
        val digest = values[hash]
        val bytes = values[size]
        val release = if (parsed != null && digest?.matches(Regex("[0-9a-f]{64}")) == true &&
            bytes != null && bytes in 1..MAX_UPDATE_APK_BYTES && values[tag] != null &&
            values[page]?.startsWith("https://") == true && values[apk]?.startsWith("https://") == true
        ) UpdateRelease(values[tag]!!, name, parsed, values[notes].orEmpty(), values[page]!!,
            values[apk]!!, bytes, digest) else null
        UpdatePreferences(values[auto] ?: true, values[attempt] ?: 0, values[checked] ?: 0,
            release, values[dismissed])
    }

    suspend fun setAutoCheck(enabled: Boolean) { store.edit { it[auto] = enabled } }
    suspend fun markAttempt(timeMillis: Long) { store.edit { it[attempt] = timeMillis } }
    suspend fun dismissVersion(versionName: String) { store.edit { it[dismissed] = versionName } }
    suspend fun recordSuccess(timeMillis: Long, release: UpdateRelease?) {
        store.edit { values ->
            values[checked] = timeMillis
            if (release == null) {
                listOf(tag, version, notes, page, apk, hash).forEach(values::remove)
                values.remove(size)
            } else {
                values[tag] = release.tag
                values[version] = release.versionName
                values[notes] = release.notes.take(32_768)
                values[page] = release.pageUrl
                values[apk] = release.apkUrl
                values[size] = release.apkSize
                values[hash] = release.sha256
            }
        }
    }
}
