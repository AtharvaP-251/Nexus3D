package com.nexus.nexus3d.core.domain

import com.nexus.nexus3d.DspNativeBridge
import com.nexus.nexus3d.core.data.PresetDao
import com.nexus.nexus3d.core.data.PresetEntity
import com.nexus.nexus3d.core.data.SettingsDao
import com.nexus.nexus3d.core.data.SettingsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DspRepository @Inject constructor(
    private val presetDao: PresetDao,
    private val settingsDao: SettingsDao
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO)
    
    val allPresets = presetDao.getAllPresets()

    private val _activePreset = MutableStateFlow<PresetEntity?>(null)
    val activePreset: StateFlow<PresetEntity?> = _activePreset.asStateFlow()

    private val _currentMacros = MutableStateFlow(MacroMapper.MacroValues())
    val currentMacros: StateFlow<MacroMapper.MacroValues> = _currentMacros.asStateFlow()

    private val _isDspEnabled = MutableStateFlow(true)
    val isDspEnabled: StateFlow<Boolean> = _isDspEnabled.asStateFlow()


    init {
        repositoryScope.launch {
            // Initial setup and settings synchronization
            initializePresets()
            
            settingsDao.getSettings().collectLatest { settings ->
                val activeSettings = settings ?: SettingsEntity()
                val preset = presetDao.getPresetById(activeSettings.activePresetId)
                
                _activePreset.value = preset
                _currentMacros.value = MacroMapper.MacroValues(
                    width = activeSettings.macroWidth,
                    depth = activeSettings.macroDepth,
                    roomSize = activeSettings.macroRoomSize,
                    clarity = activeSettings.macroClarity,
                    distance = activeSettings.macroDistance
                )
                _isDspEnabled.value = activeSettings.isDspEnabled
                
                applyBasePreset(preset)
                MacroMapper.applyMacros(_currentMacros.value)
            }
        }
    }

    private suspend fun initializePresets() {
        val factoryPresets = listOf(
            PresetEntity(name = "OFF", isFactoryPreset = true, globalBypass = true),
            PresetEntity(name = "Studio Reference", isFactoryPreset = true),
            PresetEntity(name = "Wide 3D", isFactoryPreset = true, widthAmount = 1.5f, crossfeedMix = 0.2f),
            PresetEntity(name = "Concert Hall", isFactoryPreset = true, reverbWet = 0.3f, reverbDecay = 0.8f, erMix = 0.4f),
            PresetEntity(name = "Vocal Focus", isFactoryPreset = true, msMidGain = 1.2f, eqPeakGain = 3.0f, eqPeakFreq = 2500.0f),
            PresetEntity(name = "Immersive Mode", isFactoryPreset = true, hrtfIntensity = 0.8f, haasMix = 0.5f, widthAmount = 2.0f)
        )

        if (presetDao.getPresetCount() == 0) {
            // Add Factory Presets
            factoryPresets.forEach { presetDao.insertPreset(it) }
            
            // Initial settings if none exist
            if (settingsDao.getSettingsSync() == null) {
                settingsDao.updateSettings(SettingsEntity(activePresetId = 1))
            }
        } else {
            // Ensure OFF preset exists for existing users
            if (presetDao.getPresetByName("OFF") == null) {
                presetDao.insertPreset(PresetEntity(name = "OFF", isFactoryPreset = true, globalBypass = true))
            }
        }
    }

    fun updateMacro(macroType: String, value: Float) {
        val newMacros = when (macroType.lowercase()) {
            "width" -> _currentMacros.value.copy(width = value)
            "depth" -> _currentMacros.value.copy(depth = value)
            "roomsize", "room_size" -> _currentMacros.value.copy(roomSize = value)
            "clarity" -> _currentMacros.value.copy(clarity = value)
            "distance" -> _currentMacros.value.copy(distance = value)
            else -> _currentMacros.value
        }
        
        _currentMacros.value = newMacros
        MacroMapper.applyMacros(newMacros)
        
        // Save to DB (debounced or just on change)
        repositoryScope.launch {
            val currentSettings = settingsDao.getSettingsSync() ?: SettingsEntity()
            settingsDao.updateSettings(currentSettings.copy(
                macroWidth = newMacros.width,
                macroDepth = newMacros.depth,
                macroRoomSize = newMacros.roomSize,
                macroClarity = newMacros.clarity,
                macroDistance = newMacros.distance
            ))
        }
    }

    fun selectPreset(presetId: Long) {
        repositoryScope.launch {
            val preset = presetDao.getPresetById(presetId)
            if (preset != null) {
                _activePreset.value = preset
                applyBasePreset(preset)
                MacroMapper.applyMacros(_currentMacros.value)
                
                val currentSettings = settingsDao.getSettingsSync() ?: SettingsEntity()
                settingsDao.updateSettings(currentSettings.copy(activePresetId = presetId))
            }
        }
    }

    fun toggleDsp(enabled: Boolean) {
        _isDspEnabled.value = enabled
        // Immediately apply to bridge
        val shouldBypass = !enabled || (_activePreset.value?.globalBypass ?: false)
        DspNativeBridge.setDspParameter(DspNativeBridge.GLOBAL_BYPASS, if (shouldBypass) 1.0f else 0.0f)
        
        repositoryScope.launch {
            val currentSettings = settingsDao.getSettingsSync() ?: SettingsEntity()
            settingsDao.updateSettings(currentSettings.copy(isDspEnabled = enabled))
        }
    }


    private fun applyBasePreset(preset: PresetEntity?) {
        val p = preset ?: PresetEntity(name = "Default")
        
        val shouldBypass = !_isDspEnabled.value || p.globalBypass
        DspNativeBridge.setDspParameter(DspNativeBridge.GLOBAL_BYPASS, if (shouldBypass) 1.0f else 0.0f)
        DspNativeBridge.setDspParameter(DspNativeBridge.GLOBAL_GAIN, p.globalGain)
        
        DspNativeBridge.setDspParameter(DspNativeBridge.EQ_PRE_GAIN, p.eqPreGain)
        DspNativeBridge.setDspParameter(DspNativeBridge.EQ_LOW_SHELF_FREQ, p.eqLowShelfFreq)
        DspNativeBridge.setDspParameter(DspNativeBridge.EQ_LOW_SHELF_GAIN, p.eqLowShelfGain)
        DspNativeBridge.setDspParameter(DspNativeBridge.EQ_PEAK_FREQ, p.eqPeakFreq)
        DspNativeBridge.setDspParameter(DspNativeBridge.EQ_PEAK_GAIN, p.eqPeakGain)
        DspNativeBridge.setDspParameter(DspNativeBridge.EQ_PEAK_Q, p.eqPeakQ)
        DspNativeBridge.setDspParameter(DspNativeBridge.EQ_HIGH_SHELF_FREQ, p.eqHighShelfFreq)
        DspNativeBridge.setDspParameter(DspNativeBridge.EQ_HIGH_SHELF_GAIN, p.eqHighShelfGain)
        
        DspNativeBridge.setDspParameter(DspNativeBridge.MS_MID_GAIN, p.msMidGain)
        DspNativeBridge.setDspParameter(DspNativeBridge.MS_SIDE_GAIN, p.msSideGain)
        
        DspNativeBridge.setDspParameter(DspNativeBridge.PHASE_DELAY_MS, p.phaseDelayMs)
        DspNativeBridge.setDspParameter(DspNativeBridge.PHASE_INVERT, if (p.phaseInvert) 1.0f else 0.0f)
        
        DspNativeBridge.setDspParameter(DspNativeBridge.CROSSFEED_MIX, p.crossfeedMix)
        DspNativeBridge.setDspParameter(DspNativeBridge.CROSSFEED_CUTOFF, p.crossfeedCutoff)
        
        DspNativeBridge.setDspParameter(DspNativeBridge.WIDTH_AMOUNT, p.widthAmount)
        DspNativeBridge.setDspParameter(DspNativeBridge.PAN_BALANCE, p.panBalance)
        
        DspNativeBridge.setDspParameter(DspNativeBridge.HAAS_DELAY_MS, p.haasDelayMs)
        DspNativeBridge.setDspParameter(DspNativeBridge.HAAS_MIX, p.haasMix)
        
        DspNativeBridge.setDspParameter(DspNativeBridge.DISTANCE_AMOUNT, p.distanceAmount)
        DspNativeBridge.setDspParameter(DspNativeBridge.DISTANCE_ROLLOFF, p.distanceRolloff)
        
        DspNativeBridge.setDspParameter(DspNativeBridge.HRTF_INTENSITY, p.hrtfIntensity)
        
        DspNativeBridge.setDspParameter(DspNativeBridge.ER_DELAY_SPREAD_MS, p.erDelaySpreadMs)
        DspNativeBridge.setDspParameter(DspNativeBridge.ER_MIX, p.erMix)
        
        DspNativeBridge.setDspParameter(DspNativeBridge.REVERB_DECAY, p.reverbDecay)
        DspNativeBridge.setDspParameter(DspNativeBridge.REVERB_DAMPING, p.reverbDamping)
        DspNativeBridge.setDspParameter(DspNativeBridge.REVERB_ROOM_SIZE, p.reverbRoomSize)
        DspNativeBridge.setDspParameter(DspNativeBridge.REVERB_WET, p.reverbWet)
        DspNativeBridge.setDspParameter(DspNativeBridge.REVERB_DRY, p.reverbDry)
    }
}
