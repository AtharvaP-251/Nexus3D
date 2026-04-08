package com.nexus.nexus3d.feature.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexus.nexus3d.core.data.TrackEntity
import android.content.pm.PackageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onTrackClick: (TrackEntity, List<TrackEntity>) -> Unit
) {
    val groupedTracks by viewModel.groupedTracks.collectAsState()
    var selectedFolderName by remember { mutableStateOf<String?>(null) }
    
    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasPermission = isGranted
            if (isGranted) {
                viewModel.scanLibrary()
            }
        }
    )

    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        val status = ContextCompat.checkSelfPermission(context, permissionToRequest)
        if (status == PackageManager.PERMISSION_GRANTED) {
            hasPermission = true
            viewModel.scanLibrary()
        } else {
            permissionLauncher.launch(permissionToRequest)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedFolderName ?: "Library") },
                navigationIcon = {
                    if (selectedFolderName != null) {
                        IconButton(onClick = { selectedFolderName = null }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (hasPermission) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.scanLibrary() },
                    text = { Text("Scan") },
                    icon = { }
                )
            }
        }
    ) { paddingValues ->
        if (!hasPermission) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("Storage permission is required to scan library.")
            }
        } else if (groupedTracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("No tracks found. Tap Scan to search.")
            }
        } else {
            AnimatedContent(
                targetState = selectedFolderName,
                transitionSpec = {
                    if (targetState != null) {
                        slideInHorizontally { it } + fadeIn() togetherWith
                                slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith
                                slideOutHorizontally { it } + fadeOut()
                    }
                },
                label = "LibraryNavigation"
            ) { folder ->
                if (folder == null) {
                    LazyColumn(
                        contentPadding = paddingValues,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items = groupedTracks.keys.toList(), key = { it }) { name ->
                            FolderItem(
                                name = name,
                                count = groupedTracks[name]?.size ?: 0,
                                onClick = { selectedFolderName = name }
                            )
                        }
                    }
                } else {
                    val folderTracks = groupedTracks[folder] ?: emptyList()
                    LazyColumn(
                        contentPadding = paddingValues,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items = folderTracks, key = { it.id }) { track ->
                            TrackItem(track = track) {
                                onTrackClick(track, folderTracks)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderItem(name: String, count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (name == "All Songs") Icons.Default.MusicNote else Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$count songs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun TrackItem(track: TrackEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${track.artist} • ${track.album}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
