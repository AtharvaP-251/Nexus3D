package com.nexus.nexus3d.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.nexus3d.core.data.PresetEntity
import com.nexus.nexus3d.core.domain.DspRepository
import com.nexus.nexus3d.core.domain.MacroMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dspRepository: DspRepository
) : ViewModel() {

    val activePreset = dspRepository.activePreset
    val allPresets = dspRepository.allPresets.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    val currentMacros = dspRepository.currentMacros
    val isDspEnabled = dspRepository.isDspEnabled

    fun selectPreset(preset: PresetEntity) {
        dspRepository.selectPreset(preset.id)
    }

    fun toggleDsp(enabled: Boolean) {
        dspRepository.toggleDsp(enabled)
    }

    fun updateMacro(type: String, value: Float) {
        dspRepository.updateMacro(type, value)
    }


    fun resetToDefaults() {
        // Reset macros to 0.5f (neutral)
        dspRepository.updateMacro("width", 0.5f)
        dspRepository.updateMacro("depth", 0.5f)
        dspRepository.updateMacro("room_size", 0.5f)
        dspRepository.updateMacro("clarity", 0.5f)
        dspRepository.updateMacro("distance", 0.5f)
    }
}
