package com.audixlab.nexus.core.domain

import com.audixlab.nexus.DspNativeBridge
import com.audixlab.nexus.core.data.PresetDao
import com.audixlab.nexus.core.data.PresetEntity
import com.audixlab.nexus.core.data.SettingsDao
import com.audixlab.nexus.core.data.SettingsEntity
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
                MacroMapper.applyMacros(_currentMacros.value, preset)
            }
        }
    }

    private suspend fun initializePresets() {
        val factoryPresets = listOf(

            // ── Custom / Flagship Default ─────────────────────────────────────
            PresetEntity(
                name = "Custom", isFactoryPreset = true,
                hrtfIntensity     = 0.60f, hrtfElevation    = 0.15f,
                itdAmount         = 0.40f,                       
                crossfeedMix      = 0.32f, crossfeedCutoff  = 700.0f,
                msSideGain        = 1.00f, widthAmount      = 1.10f,
                haasDelayMs       = 9.0f,  haasMix          = 0.18f, 
                erMix             = 0.25f, erDelaySpreadMs  = 20.0f, erHfDamping = 0.50f,
                reverbWet         = 0.10f, reverbDecay      = 0.60f,
                reverbDamping     = 0.50f, reverbRoomSize   = 0.50f,
                reverbPredelayMs  = 10.0f, globalGain       = 0.94f
            ),

            // ── Studio Reference ─────────────────────────────────────────────
            // Tonal transparency. Minimal crossfeed for natural speaker imaging.
            PresetEntity(
                name = "Studio Reference", isFactoryPreset = true,
                hrtfIntensity = 0.20f, itdAmount = 0.15f,
                crossfeedMix  = 0.18f, crossfeedCutoff = 650.0f,
                widthAmount   = 1.00f,
                globalGain    = 0.95f
            ),

            // ── Wide 3D ──────────────────────────────────────────────────────
            // Aggressive stereo widening + spatial decorrelation.
            PresetEntity(
                name = "Wide 3D", isFactoryPreset = true,
                widthAmount   = 1.55f, msSideGain     = 1.35f,
                haasMix       = 0.25f, haasDelayMs    = 11.5f,
                hrtfIntensity = 0.35f, itdAmount      = 0.30f,
                erMix         = 0.20f, erDelaySpreadMs = 35.0f,
                globalGain    = 0.88f
            ),

            // ── Concert Hall ─────────────────────────────────────────────────
            // Large room simulation. High reverb decay and pre-delay.
            PresetEntity(
                name = "Concert Hall", isFactoryPreset = true,
                reverbWet        = 0.30f, reverbDecay     = 0.82f,
                reverbRoomSize   = 0.85f, reverbDamping   = 0.30f,
                reverbPredelayMs = 35.0f,
                erMix            = 0.38f, erDelaySpreadMs = 55.0f,
                crossfeedMix     = 0.12f, hrtfIntensity   = 0.40f,
                globalGain       = 0.85f
            ),

            // ── Vocal Focus ──────────────────────────────────────────────────
            // Mid-range presence bump + narrow stage for intimacy.
            PresetEntity(
                name = "Vocal Focus", isFactoryPreset = true,
                msMidGain    = 1.20f,
                eqPeakGain   = 2.5f,  eqPeakFreq     = 3200.0f, eqPeakQ = 1.2f,
                crossfeedMix = 0.40f, widthAmount    = 1.00f,
                reverbWet    = 0.05f, reverbDecay    = 0.40f,
                globalGain   = 0.90f
            ),

            // ── Immersive Mode ───────────────────────────────────────────────
            // Maximum externalization. High ITD and HRTF pinna cues.
            PresetEntity(
                name = "Immersive Mode", isFactoryPreset = true,
                hrtfIntensity    = 0.85f, hrtfElevation   = 0.45f,
                itdAmount        = 0.65f, 
                crossfeedMix     = 0.35f, crossfeedCutoff = 700.0f,
                msSideGain       = 1.40f, widthAmount      = 1.60f,
                haasMix          = 0.28f, haasDelayMs     = 14.0f,
                erMix            = 0.35f, erDelaySpreadMs = 25.0f,
                reverbWet        = 0.18f, reverbDecay     = 0.70f,
                globalGain       = 0.84f
            ),

            // ── Phone Speaker Enhancer ───────────────────────────────────────
            // Compensates for physical tiny-speaker limitations.
            PresetEntity(
                name = "Phone Speaker Enhancer", isFactoryPreset = true,
                msSideGain      = 1.50f, widthAmount     = 1.80f,
                eqLowShelfFreq  = 220.0f, eqLowShelfGain = 6.5f,
                eqPeakFreq      = 3500.0f, eqPeakGain    = -3.0f,
                haasMix         = 0.30f,  haasDelayMs    = 15.0f,
                globalGain      = 0.90f
            ),

            // ── Over-Ear Optimizer ───────────────────────────────────────────
            // Compensates for closed-back resonance and driver proximity.
            PresetEntity(
                name = "Over-Ear Optimizer", isFactoryPreset = true,
                crossfeedMix    = 0.22f, itdAmount       = 0.45f,
                widthAmount     = 1.15f, hrtfIntensity   = 0.40f,
                eqLowShelfFreq  = 150.0f, eqLowShelfGain = -2.0f,
                eqHighShelfFreq = 8500.0f, eqHighShelfGain = 1.5f,
                globalGain      = 0.94f
            ),

            // ── In-Ear Monitor (IEM) ─────────────────────────────────────────
            PresetEntity(
                name = "In-Ear Monitor (IEM)",
                isFactoryPreset = true,
                crossfeedMix     = 0.35f, crossfeedCutoff = 700.0f,
                widthAmount      = 1.15f,
                hrtfIntensity    = 0.30f, itdAmount        = 0.20f,
                erMix            = 0.20f, erDelaySpreadMs  = 15.0f,
                eqHighShelfFreq  = 7000.0f,
                eqHighShelfGain  = -1.5f,
                globalGain       = 0.95f
            )
        )


        if (presetDao.getPresetCount() == 0) {
            // Add Factory Presets
            factoryPresets.forEach { presetDao.insertPreset(it) }
            
            // Initial settings if none exist
            if (settingsDao.getSettingsSync() == null) {
                settingsDao.updateSettings(SettingsEntity(activePresetId = 1))
            }
        } else {
            // Ensure Custom preset exists for existing users
            if (presetDao.getPresetByName("Custom") == null) {
                presetDao.insertPreset(PresetEntity(name = "Custom", isFactoryPreset = true))
            }
            // Ensure new Phone Speaker preset exists for existing users
            if (presetDao.getPresetByName("Phone Speaker Enhancer") == null) {
                val p = factoryPresets.find { it.name == "Phone Speaker Enhancer" }
                if (p != null) presetDao.insertPreset(p)
            }
            // Ensure Over-Ear preset exists
            if (presetDao.getPresetByName("Over-Ear Optimizer") == null) {
                val p = factoryPresets.find { it.name == "Over-Ear Optimizer" }
                if (p != null) presetDao.insertPreset(p)
            }
            // Ensure IEM preset exists
            if (presetDao.getPresetByName("In-Ear Monitor (IEM)") == null) {
                val p = factoryPresets.find { it.name == "In-Ear Monitor (IEM)" }
                if (p != null) presetDao.insertPreset(p)
            }
            // Actively clean up the old 'OFF' preset from earlier versions
            val oldOffPreset = presetDao.getPresetByName("OFF")
            if (oldOffPreset != null) {
                presetDao.deletePreset(oldOffPreset)
                // If they were actively using the OFF preset, fallback to Custom
                val currentSettings = settingsDao.getSettingsSync()
                if (currentSettings?.activePresetId == oldOffPreset.id) {
                    val custom = presetDao.getPresetByName("Custom")
                    if (custom != null) {
                        settingsDao.updateSettings(currentSettings.copy(activePresetId = custom.id))
                        _activePreset.value = custom
                        applyBasePreset(custom)
                    }
                }
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
        MacroMapper.applyMacros(newMacros, _activePreset.value)
        
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

    fun resetMacros() {
        val zeroedMacros = MacroMapper.MacroValues(0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        _currentMacros.value = zeroedMacros
        MacroMapper.applyMacros(zeroedMacros, _activePreset.value)

        repositoryScope.launch {
            val currentSettings = settingsDao.getSettingsSync() ?: SettingsEntity()
            settingsDao.updateSettings(currentSettings.copy(
                macroWidth = 0.0f,
                macroDepth = 0.0f,
                macroRoomSize = 0.0f,
                macroClarity = 0.0f,
                macroDistance = 0.0f
            ))
        }
    }

    fun selectPreset(presetId: Long) {
        repositoryScope.launch {
            val preset = presetDao.getPresetById(presetId)
            if (preset != null) {
                _activePreset.value = preset
                applyBasePreset(preset)
                MacroMapper.applyMacros(_currentMacros.value, preset)
                
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
        
        DspNativeBridge.setDspParameter(DspNativeBridge.HRTF_INTENSITY,  p.hrtfIntensity)
        DspNativeBridge.setDspParameter(DspNativeBridge.HRTF_ELEVATION,  p.hrtfElevation)
        DspNativeBridge.setDspParameter(DspNativeBridge.ITD_AMOUNT,      p.itdAmount)

        DspNativeBridge.setDspParameter(DspNativeBridge.ER_DELAY_SPREAD_MS, p.erDelaySpreadMs)
        DspNativeBridge.setDspParameter(DspNativeBridge.ER_MIX,          p.erMix)
        DspNativeBridge.setDspParameter(DspNativeBridge.ER_HF_DAMPING,   p.erHfDamping)

        DspNativeBridge.setDspParameter(DspNativeBridge.REVERB_DECAY,       p.reverbDecay)
        DspNativeBridge.setDspParameter(DspNativeBridge.REVERB_DAMPING,     p.reverbDamping)
        DspNativeBridge.setDspParameter(DspNativeBridge.REVERB_ROOM_SIZE,   p.reverbRoomSize)
        DspNativeBridge.setDspParameter(DspNativeBridge.REVERB_WET,         p.reverbWet)
        DspNativeBridge.setDspParameter(DspNativeBridge.REVERB_DRY,         p.reverbDry)
        DspNativeBridge.setDspParameter(DspNativeBridge.REVERB_PREDELAY_MS, p.reverbPredelayMs)
    }
}
