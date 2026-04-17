package com.audixlab.nexus.audio

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.audixlab.nexus.PlaybackService
import com.audixlab.nexus.core.data.TrackEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentTrack: TrackEntity? = null,
    val progress: Long = 0L,
    val duration: Long = 0L,
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF
)

@Singleton
class AudioController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaController: MediaController? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressPolling()
            } else {
                stopProgressPolling()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.let { item ->
                // Basic extraction from metadata
                val track = TrackEntity(
                    id = item.mediaId.toLongOrNull() ?: 0L,
                    filePath = item.localConfiguration?.uri?.toString() ?: "",
                    title = item.mediaMetadata.title?.toString() ?: "Unknown",
                    artist = item.mediaMetadata.artist?.toString() ?: "Unknown Artist",
                    album = item.mediaMetadata.albumTitle?.toString() ?: "",
                    durationMs = mediaController?.duration?.takeIf { it > 0 } ?: 0L,
                    format = "",
                    dateAdded = 0L
                )
                _playbackState.update { 
                    it.copy(
                        currentTrack = track, 
                        duration = mediaController?.duration?.takeIf { d -> d > 0 } ?: 0L,
                        progress = mediaController?.currentPosition ?: 0L
                    ) 
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            _playbackState.update { it.copy(duration = mediaController?.duration?.takeIf { d -> d > 0 } ?: 0L) }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _playbackState.update { it.copy(shuffleModeEnabled = shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _playbackState.update { it.copy(repeatMode = repeatMode) }
        }
    }

    init {
        initMediaController()
    }

    private fun initMediaController() {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener(
            {
                mediaController = future.get()
                mediaController?.addListener(playerListener)
                
                // Sync initial state
                _playbackState.update { 
                    it.copy(
                        shuffleModeEnabled = mediaController?.shuffleModeEnabled ?: false,
                        repeatMode = mediaController?.repeatMode ?: Player.REPEAT_MODE_OFF
                    )
                }

                if (mediaController?.isPlaying == true) {
                    startProgressPolling()
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun playTracks(tracks: List<TrackEntity>, startIndex: Int = 0) {
        val mediaItems = tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setUri(track.filePath)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .build()
                )
                .build()
        }
        
        mediaController?.apply {
            setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
            prepare()
            play()
        }
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                val position = mediaController?.currentPosition ?: 0L
                _playbackState.update { it.copy(progress = position) }
                delay(500)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    fun playPause() {
        mediaController?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
    }

    fun skipToNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    fun toggleShuffle() {
        mediaController?.let {
            it.shuffleModeEnabled = !it.shuffleModeEnabled
        }
    }

    fun toggleRepeatMode() {
        mediaController?.let {
            val nextMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_OFF
            }
            it.repeatMode = nextMode
        }
    }
}
