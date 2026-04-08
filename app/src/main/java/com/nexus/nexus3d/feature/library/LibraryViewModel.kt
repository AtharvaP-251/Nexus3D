package com.nexus.nexus3d.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.nexus3d.core.data.LocalMediaScanner
import com.nexus.nexus3d.core.data.TrackDao
import com.nexus.nexus3d.core.data.TrackEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

import com.nexus.nexus3d.audio.AudioController

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val scanner: LocalMediaScanner,
    private val audioController: AudioController
) : ViewModel() {

    val tracks: StateFlow<List<TrackEntity>> = trackDao.getAllTracks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val groupedTracks: StateFlow<Map<String, List<TrackEntity>>> = tracks
        .map { list ->
            val grouped = list.groupBy { track ->
                File(track.filePath).parentFile?.name ?: "Internal Storage"
            }.toSortedMap()
            
            linkedMapOf<String, List<TrackEntity>>().apply {
                if (list.isNotEmpty()) {
                    put("All Songs", list)
                }
                putAll(grouped)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    fun scanLibrary() {
        viewModelScope.launch {
            scanner.scanDevice()
        }
    }
    
    fun playTrack(track: TrackEntity, allTracks: List<TrackEntity>) {
        val index = allTracks.indexOf(track).takeIf { it >= 0 } ?: 0
        audioController.playTracks(allTracks, index)
    }
}

