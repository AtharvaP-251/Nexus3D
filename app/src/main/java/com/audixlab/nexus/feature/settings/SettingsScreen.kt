package com.audixlab.nexus.feature.settings

import kotlin.math.roundToInt

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.audixlab.nexus.core.data.PresetEntity
import com.audixlab.nexus.core.domain.MacroMapper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val activePreset by viewModel.activePreset.collectAsState()
    val allPresets by viewModel.allPresets.collectAsState()
    val macros by viewModel.currentMacros.collectAsState()
    val isDspEnabled by viewModel.isDspEnabled.collectAsState()
    val areMacrosEnabled = isDspEnabled && activePreset?.name == "Custom"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Audio Setup",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Sculpt your spatial experience",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Master Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Master Spatial Engine",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isDspEnabled) "Binaural processing active" else "Processing bypassed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isDspEnabled,
                onCheckedChange = { viewModel.toggleDsp(it) }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Preset Section
        Text(
            text = "Presets",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        LazyRow(
            contentPadding = PaddingValues(end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(allPresets) { preset ->
                PresetCard(
                    preset = preset,
                    isSelected = preset.id == activePreset?.id,
                    enabled = isDspEnabled,
                    onClick = { if (isDspEnabled) viewModel.selectPreset(preset) }
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Macros Section
        Text(
            text = "Sound Character",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(16.dp))

        MacroSlider(
            label = "Width",
            value = macros.width,
            onValueChange = { viewModel.updateMacro("width", it) },
            description = "Expansion & Crossfeed",
            enabled = areMacrosEnabled
        )
        MacroSlider(
            label = "Depth",
            value = macros.depth,
            onValueChange = { viewModel.updateMacro("depth", it) },
            description = "Haas & Distance Modeling",
            enabled = areMacrosEnabled
        )
        MacroSlider(
            label = "Room Size",
            value = macros.roomSize,
            onValueChange = { viewModel.updateMacro("room_size", it) },
            description = "Reverb Decay & ER Spread",
            enabled = areMacrosEnabled
        )
        MacroSlider(
            label = "Clarity",
            value = macros.clarity,
            onValueChange = { viewModel.updateMacro("clarity", it) },
            description = "M/S Focus & EQ Tilt",
            enabled = areMacrosEnabled
        )
        MacroSlider(
            label = "Distance",
            value = macros.distance,
            onValueChange = { viewModel.updateMacro("distance", it) },
            description = "HRTF & Air Absorption",
            enabled = areMacrosEnabled
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Reset Button
        OutlinedButton(
            onClick = { viewModel.resetToDefaults() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = areMacrosEnabled
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset to Neutral")
        }
        
    }
}

@Composable
fun PresetCard(
    preset: PresetEntity,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1.0f else 0.4f
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .width(140.dp)
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor.copy(alpha = alpha))
            .clickable(enabled = enabled) { onClick() }
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Text(
            text = preset.name,
            style = MaterialTheme.typography.titleSmall,
            color = textColor,
            fontWeight = FontWeight.Bold,
            maxLines = 2
        )
    }
}

@Composable
fun MacroSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    description: String,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1.0f else 0.4f
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = label, 
                    style = MaterialTheme.typography.bodyLarge, 
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                Text(
                    text = description, 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
            }
            Text(
                text = "${(value * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            steps = 9
        )
    }
}

