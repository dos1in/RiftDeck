package com.riftdeck.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "play_session",
    foreignKeys = [ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["gameId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["gameId"])])
data class PlaySession(
    @PrimaryKey val id: Int = 1,
    val gameId: Long,
    val startedElapsed: Long,
    val bootCount: Int,
)
