package com.audixlab.nexus.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isFactoryPreset: Boolean = false,

    // Global
    val globalBypass: Boolean = false,
    val globalGain: Float = 1.0f,

    // Pre-EQ
    val eqPreGain: Float = 0.0f,          // dB (0 = unity)
    val eqLowShelfFreq: Float = 150.0f,
    val eqLowShelfGain: Float = 0.0f,
    val eqPeakFreq: Float = 1000.0f,
    val eqPeakGain: Float = 0.0f,
    val eqPeakQ: Float = 0.707f,
    val eqHighShelfFreq: Float = 6000.0f,
    val eqHighShelfGain: Float = 0.0f,

    // Mid/Side — neutral defaults; presets explicitly set what they need
    val msMidGain: Float = 1.0f,
    val msSideGain: Float = 1.0f,

    // Phase Alignment
    val phaseDelayMs: Float = 0.0f,
    val phaseInvert: Boolean = false,

    // Crossfeed — off by default; specific presets turn on
    val crossfeedMix: Float = 0.0f,
    val crossfeedCutoff: Float = 700.0f,

    // Stereo Width — unity by default
    val widthAmount: Float = 1.0f,

    // Panning
    val panBalance: Float = 0.0f,

    // Haas Delay — off by default
    val haasDelayMs: Float = 8.0f,    // safe Haas zone [5–15 ms]; 8 ms = solid externalization cue
    val haasMix: Float = 0.0f,

    // HRTF — off by default; presets explicitly enable
    val hrtfIntensity: Float = 0.0f,
    val hrtfElevation: Float = 0.0f,

    // Interaural Time Difference — disabled (reserved for future head-tracking)
    val itdAmount: Float = 0.0f,

    // Distance Modeling
    val distanceAmount: Float = 0.0f,
    val distanceRolloff: Float = 0.5f,

    // Early Reflections — off by default
    val erDelaySpreadMs: Float = 25.0f,
    val erMix: Float = 0.0f,
    val erHfDamping: Float = 0.50f,

    // Reverb — off by default
    val reverbDecay: Float = 0.72f,
    val reverbDamping: Float = 0.45f,
    val reverbRoomSize: Float = 0.55f,
    val reverbWet: Float = 0.0f,
    val reverbDry: Float = 1.0f,
    val reverbPredelayMs: Float = 12.0f
)
