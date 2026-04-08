package com.nexus.nexus3d.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1, // Single row for settings
    val activePresetId: Long = 1,
    val macroWidth: Float = 0.5f,
    val macroDepth: Float = 0.5f,
    val macroRoomSize: Float = 0.5f,
    val macroClarity: Float = 0.5f,
    val macroDistance: Float = 0.5f,
    val isDspEnabled: Boolean = true
)
