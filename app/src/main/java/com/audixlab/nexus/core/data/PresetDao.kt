package com.audixlab.nexus.core.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets")
    fun getAllPresets(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun getPresetById(id: Long): PresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: PresetEntity): Long

    @Delete
    suspend fun deletePreset(preset: PresetEntity)

    @Query("SELECT COUNT(*) FROM presets")
    suspend fun getPresetCount(): Int

    @Query("SELECT * FROM presets WHERE name = :name")
    suspend fun getPresetByName(name: String): PresetEntity?
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun getSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun getSettingsSync(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSettings(settings: SettingsEntity)
}
