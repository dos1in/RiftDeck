package com.riftdeck.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.riftdeck.data.scanner.RomDocument
import kotlinx.coroutines.flow.Flow

@Dao
abstract class GameDao {
    @Query("SELECT * FROM games ORDER BY sortTitle COLLATE NOCASE, id")
    abstract fun observeGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE identity = :identity")
    abstract suspend fun findByIdentity(identity: String): GameEntity?

    @Query("SELECT * FROM games WHERE id = :id")
    abstract suspend fun findById(id: Long): GameEntity?

    @Insert
    abstract suspend fun insert(game: GameEntity): Long

    @Update
    abstract suspend fun update(game: GameEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putLocation(location: GameLocation)

    @Query("UPDATE games SET favorite = NOT favorite WHERE id = :id")
    abstract suspend fun toggleFavorite(id: Long)

    @Query("UPDATE games SET playCount = playCount + 1, lastPlayedAt = :time WHERE id = :id")
    abstract suspend fun recordLaunch(id: Long, time: Long)

    @Query("UPDATE games SET playTimeSeconds = playTimeSeconds + :seconds WHERE id = :id")
    abstract suspend fun addPlayTime(id: Long, seconds: Long)

    @Query("DELETE FROM game_locations WHERE folderUri = :folder AND generation != :generation")
    abstract suspend fun deleteUnseenLocations(folder: String, generation: String)

    @Query("DELETE FROM game_locations WHERE folderUri = :folder")
    abstract suspend fun deleteLocations(folder: String)

    @Query("DELETE FROM games WHERE NOT EXISTS (SELECT 1 FROM game_locations WHERE gameId = games.id)")
    abstract suspend fun deleteOrphans()

    @Query("UPDATE games SET romUri = (SELECT documentUri FROM game_locations WHERE gameId = games.id ORDER BY folderUri LIMIT 1) WHERE NOT EXISTS (SELECT 1 FROM game_locations WHERE gameId = games.id AND documentUri = games.romUri)")
    abstract suspend fun selectRemainingUris()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putSession(session: PlaySession)

    @Query("SELECT * FROM play_session WHERE id = 1")
    abstract suspend fun session(): PlaySession?

    @Query("DELETE FROM play_session")
    abstract suspend fun clearSession()

    @Transaction
    open suspend fun beginSession(gameId: Long, wallTime: Long, elapsed: Long, bootCount: Int) {
        if (findById(gameId) == null) return
        recordLaunch(gameId, wallTime)
        putSession(PlaySession(gameId = gameId, startedElapsed = elapsed, bootCount = bootCount))
    }

    @Transaction
    open suspend fun finishSession(elapsed: Long, bootCount: Int) {
        val session = session() ?: return
        // Ignore an older resume event that was queued before the current game started.
        if (session.bootCount >= 0 && bootCount == session.bootCount && elapsed < session.startedElapsed) return
        addPlayTime(session.gameId, com.riftdeck.core.emulator.playSessionSeconds(
            session.startedElapsed, elapsed, session.bootCount, bootCount))
        clearSession()
    }

    @Transaction
    open suspend fun importBatch(folder: String, generation: String, documents: List<RomDocument>) {
        documents.forEach { document ->
            val existing = findByIdentity(document.identity)
            val id = if (existing == null) {
                insert(GameEntity(identity = document.identity, platformId = document.platformId,
                    title = document.title, sortTitle = document.sortTitle, romUri = document.uri,
                        fileName = document.name, fileSize = document.size, modifiedAt = document.modifiedAt,
                        coverUri = document.coverUri, coverVersion = document.coverVersion))
            } else {
                // Keep favorites/history/metadata; an incremental scan only updates file attributes.
                if (existing.fileName != document.name || existing.fileSize != document.size || existing.modifiedAt != document.modifiedAt) {
                    update(existing.copy(fileName = document.name, fileSize = document.size,
                        modifiedAt = document.modifiedAt, romUri = document.uri,
                        coverUri = document.coverUri, coverVersion = document.coverVersion,
                        crc32 = null, sha1 = null))
                } else if (existing.romUri != document.uri || existing.coverUri != document.coverUri || existing.coverVersion != document.coverVersion) {
                    // A successful overlapping tree scan may restore access after another grant was revoked.
                    update(existing.copy(romUri = document.uri, coverUri = document.coverUri, coverVersion = document.coverVersion))
                }
                existing.id
            }
            putLocation(GameLocation(folder, id, document.uri, generation))
        }
    }

    @Transaction
    open suspend fun finishScan(folder: String, generation: String) {
        // Only called after every directory was read successfully. Failures preserve old entries.
        deleteUnseenLocations(folder, generation)
        deleteOrphans()
        selectRemainingUris()
    }

    @Transaction
    open suspend fun removeFolder(folder: String) {
        deleteLocations(folder)
        deleteOrphans()
        selectRemainingUris()
    }
}
