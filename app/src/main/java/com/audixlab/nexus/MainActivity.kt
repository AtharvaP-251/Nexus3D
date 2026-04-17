package com.audixlab.nexus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.audixlab.nexus.feature.library.LibraryScreen
import com.audixlab.nexus.feature.library.LibraryViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.audixlab.nexus.feature.player.PlayerScreen
import com.audixlab.nexus.feature.settings.SettingsScreen
import com.audixlab.nexus.feature.settings.SettingsViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.List
import com.audixlab.nexus.ui.theme.NexusTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {

            NexusTheme {
                NexusApp()
            }
        }
    }
}

@Composable
fun NexusApp() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry = navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry.value?.destination?.route

                NavigationBarItem(
                    selected = currentRoute == "library",
                    onClick = {
                        navController.navigate("library") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("Library") }
                )
                
                NavigationBarItem(
                    selected = currentRoute == "player",
                    onClick = {
                        navController.navigate("player") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    label = { Text("Player") }
                )

                NavigationBarItem(
                    selected = currentRoute == "settings",
                    onClick = {
                        navController.navigate("settings") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "player",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("library") {
                val viewModel: LibraryViewModel = hiltViewModel()
                LibraryScreen(
                    viewModel = viewModel,
                    onTrackClick = { startTrack, tracks ->
                        viewModel.playTrack(startTrack, tracks)
                        navController.navigate("player") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable("player") {
                PlayerScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen()
            }
        }
    }
}
