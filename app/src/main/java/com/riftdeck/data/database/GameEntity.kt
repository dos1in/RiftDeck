package com.riftdeck.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.riftdeck.core.model.Game

@Entity(tableName = "games", indices = [Index(value = ["identity"], unique = true)])
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val identity: String,
    val platformId: Long,
    val title: String,
    val sortTitle: String,
    val romUri: String,
    val fileName: String,
    val fileSize: Long,
    val modifiedAt: Long,
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val playCount: Int = 0,
    val playTimeSeconds: Long = 0,
    val lastPlayedAt: Long? = null,
    val crc32: String? = null,
    val sha1: String? = null,
    val coverUri: String? = null,
    val screenshotUri: String? = null,
    val videoUri: String? = null,
    val releaseYear: Int? = null,
    val developer: String? = null,
    val genre: String? = null,
    val coverVersion: String? = null,
) {
    fun toGame() = Game(id, platformId, title, sortTitle, romUri, fileName, fileSize, crc32, sha1,
        favorite, hidden, playCount, playTimeSeconds, lastPlayedAt, coverUri, screenshotUri, videoUri,
        releaseYear, developer, genre, coverVersion)
}

/** One document may be reachable through more than one selected SAF tree. */
@Entity(tableName = "game_locations", primaryKeys = ["folderUri", "gameId"],
    foreignKeys = [ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["gameId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["gameId"])])
data class GameLocation(val folderUri: String, val gameId: Long, val documentUri: String, val generation: String)
