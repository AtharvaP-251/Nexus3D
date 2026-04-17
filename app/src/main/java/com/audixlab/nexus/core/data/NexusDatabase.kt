package com.audixlab.nexus.core.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TrackEntity::class, PresetEntity::class, SettingsEntity::class], version = 6, exportSchema = false)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun presetDao(): PresetDao
    abstract fun settingsDao(): SettingsDao
}
