package com.nexus.nexus3d

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.content.ComponentName
import androidx.media3.session.SessionToken
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem

class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    // Simple file picker contract
    private val getAudioFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            playAudio(it)
        }
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            mediaController = future.get()
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onPickFileClick = { getAudioFile.launch("audio/*") },
                        onGainChange = { gain -> DspNativeBridge.setGain(gain) },
                        onPlayPauseClick = {
                            mediaController?.let {
                                if (it.isPlaying) it.pause() else it.play()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun playAudio(uri: Uri) {
        mediaController?.let { controller ->
            val mediaItem = MediaItem.fromUri(uri)
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
    }
}

@Composable
fun MainScreen(
    onPickFileClick: () -> Unit,
    onGainChange: (Float) -> Unit,
    onPlayPauseClick: () -> Unit
) {
    var gain by remember { mutableStateOf(1.0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onPickFileClick) {
            Text("Select Audio File")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onPlayPauseClick) {
            Text("Play / Pause")
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(text = "Gain (Native DSP): ${String.format("%.2f", gain)}")
        Slider(
            value = gain,
            onValueChange = { 
                gain = it
                onGainChange(it)
            },
            valueRange = 0f..2f
        )
    }
}
