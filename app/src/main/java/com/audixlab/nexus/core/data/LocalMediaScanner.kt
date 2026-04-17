package com.audixlab.nexus.core.data

import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao
) {
    suspend fun scanDevice() = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        
        val tracks = mutableListOf<TrackEntity>()
        
        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            
            val currentTime = System.currentTimeMillis()
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val filePath = cursor.getString(dataColumn) ?: continue
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn)
                val album = cursor.getString(albumColumn)
                val duration = cursor.getLong(durationColumn)
                val format = cursor.getString(mimeTypeColumn) ?: ""
                
                if (format.contains("audio/mpeg") || format.contains("audio/flac") || format.contains("audio/wav") || format.contains("x-wav")) {
                    tracks.add(
                        TrackEntity(
                            id = id,
                            filePath = filePath,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            format = format,
                            dateAdded = currentTime
                        )
                    )
                }
            }
        }
        
        // Save scan results to DB
        if (tracks.isNotEmpty()) {
            trackDao.insertTracks(tracks)
        }
    }
}
