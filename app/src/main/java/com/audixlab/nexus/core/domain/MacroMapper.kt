package com.audixlab.nexus.core.domain

import com.audixlab.nexus.DspNativeBridge
import com.audixlab.nexus.core.data.PresetEntity

object MacroMapper {

    data class MacroValues(
        val width: Float = 0.5f,
        val depth: Float = 0.5f,
        val roomSize: Float = 0.5f,
        val clarity: Float = 0.5f,
        val distance: Float = 0.5f
    )

    fun applyMacros(macros: MacroValues, preset: PresetEntity?) {
        val p = preset ?: PresetEntity(name = "Default")
        val isCustom = p.name == "Custom"

        // ── Preset Independence ──────────────────────────────────────────
        // Factory presets are locked to their unique calibrated values.
        // Macro sliders are strictly ignored by forcing inputs to 0.5f (neutral).
        val m = if (isCustom) macros else MacroValues(0.5f, 0.5f, 0.5f, 0.5f, 0.5f)

        // ── Helper: Piecewise Linear Mapping ──────────────────────────────
        // Maps macro [0.0 -> 0.5 -> 1.0] to [Off -> Flagship -> Boost]
        fun map(v: Float, off: Float, flagship: Float, boost: Float): Float {
            return if (v < 0.5f) {
                off + (flagship - off) * (v * 2.0f)
            } else {
                flagship + (boost - flagship) * ((v - 0.5f) * 2.0f)
            }
        }

        // ── 1. Width ─────────────────────────────────────────────────────
        // Off:   No crossfeed, 1.0 width, 1.0 side-gain
        // 50%:   Flagship/Preset defined value
        // Max:   0.60 mix, 1.65 width, 1.40 side
        val crossfeedMix = map(m.width, 0.00f, p.crossfeedMix, 0.60f)
        val widthAmount  = map(m.width, 1.00f, p.widthAmount,  1.80f)
        val sideGain     = map(m.width, 1.00f, p.msSideGain,   1.50f)

        DspNativeBridge.setDspParameter(DspNativeBridge.CROSSFEED_MIX, crossfeedMix)
        DspNativeBridge.setDspParameter(DspNativeBridge.WIDTH_AMOUNT,   widthAmount)
        DspNativeBridge.setDspParameter(DspNativeBridge.MS_SIDE_GAIN,   sideGain)

        // ── 2. Depth ─────────────────────────────────────────────────────
        // Off:   No Haas, No Distance Modeling
        // 50%:   Preset value (Flagship)
        // Max:   0.28 Haas Mix, 1.00 Distance Amount
        val haasMix     = map(m.depth, 0.00f, p.haasMix,        0.28f)
        val distanceAmt = map(m.depth, 0.00f, p.distanceAmount, 1.00f)
        
        // Haas delay shifts only within safe zone [5-15ms]
        val haasDelayMs = (p.haasDelayMs + (m.depth - 0.5f) * 4f).coerceIn(5f, 15f)

        DspNativeBridge.setDspParameter(DspNativeBridge.HAAS_DELAY_MS,    haasDelayMs)
        DspNativeBridge.setDspParameter(DspNativeBridge.HAAS_MIX,         haasMix)
        DspNativeBridge.setDspParameter(DspNativeBridge.DISTANCE_AMOUNT,  distanceAmt)

        // ── 3. Room Size ─────────────────────────────────────────────────
        // Controlling Reverb/ER presence here per user expectation (0% = OFF)
        val erMix       = map(m.roomSize, 0.00f, p.erMix,          0.70f)
        val reverbWet   = map(m.roomSize, 0.00f, p.reverbWet,      0.40f)
        val reverbDecay = map(m.roomSize, 0.10f, p.reverbDecay,     0.94f)
        val erSpread    = map(m.roomSize, 5.00f, p.erDelaySpreadMs, 80.0f)
        val distRolloff = map(m.roomSize, 0.10f, p.distanceRolloff, 1.0f)
        val predelayMs  = map(m.roomSize, 2.00f, p.reverbPredelayMs, 60.0f)
        val roomSize    = map(m.roomSize, 0.10f, p.reverbRoomSize,  0.95f)

        DspNativeBridge.setDspParameter(DspNativeBridge.REVERB_WET,         reverbWet)
        DspNativeBridge.setDspParameter(DspNativeBridge.ER_MIX,             erMix)
        DspNativeBridge.setDspParameter(DspNativeBridge.REVERB_DECAY,       reverbDecay)
        DspNativeBridge.setDspParameter(DspNativeBridge.ER_DELAY_SPREAD_MS, erSpread)
        DspNativeBridge.setDspParameter(DspNativeBridge.DISTANCE_ROLLOFF,   distRolloff)
        DspNativeBridge.setDspParameter(DspNativeBridge.REVERB_PREDELAY_MS, predelayMs)
        DspNativeBridge.setDspParameter(DspNativeBridge.REVERB_ROOM_SIZE,   roomSize)

        // ── 4. Clarity ───────────────────────────────────────────────────
        val highShelfGain = map(m.clarity, 0.00f, p.eqHighShelfGain, 8.0f)
        val midGain       = map(m.clarity, 1.00f, p.msMidGain,       1.30f)
        val reverbDamping = map(m.clarity, 0.80f, p.reverbDamping,   0.10f)
        val erHfDamping   = map(m.clarity, 0.80f, p.erHfDamping,     0.10f)

        DspNativeBridge.setDspParameter(DspNativeBridge.REVERB_DAMPING,     reverbDamping)
        DspNativeBridge.setDspParameter(DspNativeBridge.ER_HF_DAMPING,      erHfDamping)
        DspNativeBridge.setDspParameter(DspNativeBridge.MS_MID_GAIN,        midGain)
        DspNativeBridge.setDspParameter(DspNativeBridge.EQ_HIGH_SHELF_GAIN, highShelfGain)

        // ── 5. Distance / Externalization ────────────────────────────────
        val hrtfIntensity = map(m.distance, 0.00f, p.hrtfIntensity, 1.0f)
        val hrtfElevation = map(m.distance, 0.00f, p.hrtfElevation, 1.0f)
        val itdAmount     = map(m.distance, 0.00f, p.itdAmount,     0.60f)
        val reverbDry     = map(m.distance, 1.00f, p.reverbDry,     0.70f)

        // Gain compensation
        val boostPenalty = maxOf(0f, m.width - 0.5f) * 0.05f + maxOf(0f, m.clarity - 0.5f) * 0.05f
        val globalGain   = (p.globalGain - boostPenalty).coerceIn(0.40f, 1.0f)

        DspNativeBridge.setDspParameter(DspNativeBridge.HRTF_INTENSITY, hrtfIntensity)
        DspNativeBridge.setDspParameter(DspNativeBridge.HRTF_ELEVATION, hrtfElevation)
        DspNativeBridge.setDspParameter(DspNativeBridge.ITD_AMOUNT,     itdAmount)
        DspNativeBridge.setDspParameter(DspNativeBridge.REVERB_DRY,     reverbDry)
        DspNativeBridge.setDspParameter(DspNativeBridge.GLOBAL_GAIN,    globalGain)
    }
}
