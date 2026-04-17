package com.audixlab.nexus.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: Long,
    val filePath: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val format: String,
    val dateAdded: Long
)
