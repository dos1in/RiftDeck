package com.riftdeck.data.database

import androidx.room.Database
import androidx.room.AutoMigration
import androidx.room.RoomDatabase

@Database(entities = [GameEntity::class, GameLocation::class, PlaySession::class], version = 5, exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5)])
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun games(): GameDao
}
