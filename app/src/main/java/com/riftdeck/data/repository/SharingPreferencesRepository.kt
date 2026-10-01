package com.riftdeck.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.riftdeck.core.sharing.SaveBaseline
import com.riftdeck.core.sharing.isSupportedSavePath
import java.io.*
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sharingPreferences by preferencesDataStore(name = "lan_sharing")

data class PairedDevice(val id: String, val name: String, val key: String, val host: String, val port: Int)
data class StoredSaveConflict(val peerId: String, val path: String, val localHash: String, val remoteHash: String)
data class SharingPreferences(
    val deviceId: String = "",
    val enabled: Boolean = false,
    val romFolder: String? = null,
    val saveFolder: String? = null,
    val autoSync: Boolean = false,
    val peers: List<PairedDevice> = emptyList(),
    val baselines: Map<String, Map<String, SaveBaseline>> = emptyMap(),
    val conflicts: List<StoredSaveConflict> = emptyList(),
)

/** Pairing keys stay in local DataStore, excluded from device/cloud backup. */
class SharingPreferencesRepository internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.sharingPreferences)
    private val identity = stringPreferencesKey("device_id")
    private val enabled = booleanPreferencesKey("enabled")
    private val romFolder = stringPreferencesKey("rom_folder")
    private val saveFolder = stringPreferencesKey("save_folder")
    private val autoSync = booleanPreferencesKey("auto_sync")
    private val peers = stringSetPreferencesKey("paired_devices")
    private val baselines = stringSetPreferencesKey("save_baselines")
    private val conflicts = stringSetPreferencesKey("save_conflicts")

    val preferences = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { values ->
        SharingPreferences(
            deviceId = values[identity]?.takeIf(::validId).orEmpty(), enabled = values[enabled] ?: false,
            romFolder = values[romFolder], saveFolder = values[saveFolder], autoSync = values[autoSync] ?: false,
            peers = values[peers].orEmpty().mapNotNull { decodeFull(it) { PairedDevice(readUTF(), readUTF(), readUTF(), readUTF(), readInt()).also(::validateDevice) } },
            baselines = values[baselines].orEmpty().mapNotNull { decodeFull(it) {
                val peer = readUTF(); val path = readUTF()
                require(validId(peer) && isSupportedSavePath(path))
                Triple(peer, path, SaveBaseline(readUTF(), readUTF()))
            } }.groupBy { it.first }.mapValues { (_, rows) -> rows.associate { it.second to it.third } },
            conflicts = values[conflicts].orEmpty().mapNotNull { decodeFull(it) { StoredSaveConflict(readUTF(), readUTF(), readUTF(), readUTF()).also { row ->
                require(validId(row.peerId) && isSupportedSavePath(row.path)); SaveBaseline(row.localHash, row.remoteHash)
            } } },
        )
    }

    suspend fun ensureIdentity(): String {
        store.edit { if (!validId(it[identity].orEmpty())) it[identity] = UUID.randomUUID().toString() }
        return preferences.first().deviceId
    }
    suspend fun setEnabled(value: Boolean) { store.edit { it[enabled] = value } }
    suspend fun setAutoSync(value: Boolean) { store.edit { it[autoSync] = value } }
    suspend fun setFolder(uri: String, saves: Boolean) {
        store.edit {
            if (saves && it[saveFolder] != uri) { it.remove(baselines); it.remove(conflicts) }
            it[if (saves) saveFolder else romFolder] = uri
        }
    }
    suspend fun pair(device: PairedDevice) {
        validateDevice(device)
        store.edit { values ->
            val existing = values[peers].orEmpty().mapNotNull { decodeFull(it) { PairedDevice(readUTF(), readUTF(), readUTF(), readUTF(), readInt()).also(::validateDevice) } }.firstOrNull { it.id == device.id }
            if (existing != null && existing.key != device.key) throw IOException("Device already paired")
            val rows = values[peers].orEmpty().filterNot { decode(it) { readUTF() } == device.id }.toSet()
            values[peers] = rows + encode { writeUTF(device.id); writeUTF(device.name); writeUTF(device.key); writeUTF(device.host); writeInt(device.port) }
        }
    }
    suspend fun updateEndpoint(id: String, host: String, port: Int) {
        require(host.length in 1..256 && port in 1..65535)
        store.edit { values ->
            values[peers] = values[peers].orEmpty().mapTo(mutableSetOf()) { row ->
                val device = decodeFull(row) { PairedDevice(readUTF(), readUTF(), readUTF(), readUTF(), readInt()).also(::validateDevice) }
                if (device?.id != id) row else encode { writeUTF(device.id); writeUTF(device.name); writeUTF(device.key); writeUTF(host); writeInt(port) }
            }
        }
    }
    suspend fun forget(id: String) {
        store.edit { values ->
            for (key in listOf(peers, baselines, conflicts)) values[key] = values[key].orEmpty().filterNot { decode(it) { readUTF() } == id }.toSet()
        }
    }
    suspend fun recordSync(peerId: String, revisions: Map<String, SaveBaseline>, unresolved: List<StoredSaveConflict>, expectedFolder: String? = null, expectedKey: String? = null) {
        store.edit { values ->
            // A concurrent unpair must not recreate a device's synchronization state.
            if (values[peers].orEmpty().none { decode(it) { readUTF() } == peerId }) return@edit
            if (expectedFolder != null && values[saveFolder] != expectedFolder) return@edit
            if (expectedKey != null && !hasPairKey(values, peerId, expectedKey)) return@edit
            values[baselines] = values[baselines].orEmpty().filterNot { decode(it) { readUTF() } == peerId }.toSet() +
                revisions.map { (path, baseline) -> encode { writeUTF(peerId); writeUTF(path); writeUTF(baseline.localSha256); writeUTF(baseline.remoteSha256) } }
            values[conflicts] = values[conflicts].orEmpty().filterNot { decode(it) { readUTF() } == peerId }.toSet() +
                unresolved.map { row -> encode { writeUTF(peerId); writeUTF(row.path); writeUTF(row.localHash); writeUTF(row.remoteHash) } }
        }
    }
    suspend fun acknowledge(peerId: String, folder: String, acknowledged: Map<String, SaveBaseline>, expectedKey: String? = null) {
        store.edit { values ->
            if (values[saveFolder] != folder || values[peers].orEmpty().none { decode(it) { readUTF() } == peerId }) return@edit
            if (expectedKey != null && !hasPairKey(values, peerId, expectedKey)) return@edit
            val paths = acknowledged.keys
            values[baselines] = values[baselines].orEmpty().filterNot { decode(it) { readUTF() to readUTF() }?.let { row -> row.first == peerId && row.second in paths } == true }.toSet() +
                acknowledged.map { (path, baseline) -> encode { writeUTF(peerId); writeUTF(path); writeUTF(baseline.localSha256); writeUTF(baseline.remoteSha256) } }
            values[conflicts] = values[conflicts].orEmpty().filterNot { decode(it) { readUTF() to readUTF() }?.let { row -> row.first == peerId && row.second in paths } == true }.toSet() +
                acknowledged.filterValues { it.localSha256 != it.remoteSha256 }.map { (path, baseline) -> encode { writeUTF(peerId); writeUTF(path); writeUTF(baseline.localSha256); writeUTF(baseline.remoteSha256) } }
        }
    }

    private fun encode(block: DataOutputStream.() -> Unit): String = Base64.getEncoder().encodeToString(
        ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { it.block() } }.toByteArray())
    private fun <T> decode(row: String, block: DataInputStream.() -> T): T? = try {
        if (row.length > 16384) null else DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(row))).use { it.block() }
    } catch (_: IOException) { null } catch (_: IllegalArgumentException) { null }
    private fun <T> decodeFull(row: String, block: DataInputStream.() -> T): T? = decode(row) { block().also { require(available() == 0) } }
    private fun validId(id: String) = id.matches(Regex("[A-Za-z0-9_-]{1,80}"))
    private fun hasPairKey(values: Preferences, peerId: String, expectedKey: String) = values[peers].orEmpty().any { row ->
        decode(row) { val id = readUTF(); readUTF(); id to readUTF() } == (peerId to expectedKey)
    }
    private fun validateDevice(device: PairedDevice) {
        require(validId(device.id) && device.name.toByteArray().size in 1..256 && device.name.none { it.isISOControl() })
        require(Base64.getDecoder().decode(device.key).size == 32)
        require((device.host.isEmpty() && device.port == 0) || (device.host.length in 1..256 && device.host.none { it.isISOControl() } && device.port in 1..65535))
    }
}
