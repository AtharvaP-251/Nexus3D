package com.audixlab.nexus.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audixlab.nexus.core.data.PresetEntity
import com.audixlab.nexus.core.domain.DspRepository
import com.audixlab.nexus.core.domain.MacroMapper
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
        // Reset all macros to 0.0f atomically to prevent race conditions
        dspRepository.resetMacros()
    }
}
