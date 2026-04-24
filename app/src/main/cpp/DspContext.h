#pragma once

#include <atomic>
#include <cmath>

namespace nexus {

enum DspParamId {
    GLOBAL_BYPASS = 0,
    GLOBAL_GAIN = 1,
    EQ_PRE_GAIN = 2,
    EQ_LOW_SHELF_FREQ = 3,
    EQ_LOW_SHELF_GAIN = 4,
    EQ_PEAK_FREQ = 5,
    EQ_PEAK_GAIN = 6,
    EQ_PEAK_Q = 7,
    EQ_HIGH_SHELF_FREQ = 8,
    EQ_HIGH_SHELF_GAIN = 9,
    MS_MID_GAIN = 10,
    MS_SIDE_GAIN = 11,
    PHASE_DELAY_MS = 12,
    PHASE_INVERT = 13,
    CROSSFEED_MIX = 14,
    CROSSFEED_CUTOFF = 15,
    WIDTH_AMOUNT = 16,
    PAN_BALANCE = 17,
    HAAS_DELAY_MS = 18,
    HAAS_MIX = 19,
    HRTF_INTENSITY = 20,
    HRTF_ELEVATION = 21,
    ITD_AMOUNT = 22,
    DISTANCE_AMOUNT = 23,
    DISTANCE_ROLLOFF = 24,
    ER_DELAY_SPREAD_MS = 25,
    ER_MIX = 26,
    ER_HF_DAMPING = 27,
    REVERB_DECAY = 28,
    REVERB_DAMPING = 29,
    REVERB_ROOM_SIZE = 30,
    REVERB_WET = 31,
    REVERB_DRY = 32,
    REVERB_PREDELAY_MS = 33,

    PARAM_MAX_COUNT
};

class DspParameters {
public:
    DspParameters() {
        for (int i = 0; i < PARAM_MAX_COUNT; i++) {
            params[i].store(0.0f);
        }

        // --- Global ---
        set(GLOBAL_GAIN,   1.0f);
        set(GLOBAL_BYPASS, 0.0f);

        // --- Pre-EQ (transparent by default: all gains = 0 dB) ---
        set(EQ_PRE_GAIN,       0.0f);
        set(EQ_LOW_SHELF_FREQ, 150.0f);
        set(EQ_LOW_SHELF_GAIN, 0.0f);
        set(EQ_PEAK_FREQ,      1000.0f);
        set(EQ_PEAK_GAIN,      0.0f);
        set(EQ_PEAK_Q,         0.707f);
        set(EQ_HIGH_SHELF_FREQ,6000.0f);
        set(EQ_HIGH_SHELF_GAIN,0.0f);

        // --- Mid/Side (neutral: adaptive widening handles expansion) ---
        set(MS_MID_GAIN,  1.0f);
        set(MS_SIDE_GAIN, 1.0f);   // neutral; adaptive logic applies freq-aware boost

        // --- Phase Alignment (off by default) ---
        set(PHASE_DELAY_MS, 0.0f);
        set(PHASE_INVERT,   0.0f);

        // --- Crossfeed (BS2B Level 3 — subtle, non-intrusive) ---
        set(CROSSFEED_MIX,    0.30f);  // was 0.35; slightly more conservative
        set(CROSSFEED_CUTOFF, 700.0f);

        // --- Stereo Width (gentle complement to M/S adaptive) ---
        set(WIDTH_AMOUNT, 1.00f);    // 1.00 is perfectly transparent (no expansion)

        // --- Panning (dormant) ---
        set(PAN_BALANCE, 0.0f);

        // --- Haas / Precedence (inside the 5–15 ms zone: NO echo) ---
        set(HAAS_DELAY_MS, 8.0f);   // was 18.0 (caused echoes!); 8 ms = solid externalization cue
        set(HAAS_MIX,      0.22f);  // was 0.40; keeps it below echo-perception threshold

        // --- HRTF (natural-sounding, less aggressive coloration) ---
        set(HRTF_INTENSITY, 0.65f);  // was 0.75; subtler pinna coloration
        set(HRTF_ELEVATION, 0.20f);  // was 0.30; gentle front-above cue

        // --- ITD (NOW ACTIVE: essential for externalization + HRTF pairing) ---
        set(ITD_AMOUNT, 0.0f);       // 0.0 is center anchor (no panning)

        // --- Distance Modeling (dormant) ---
        set(DISTANCE_AMOUNT,  0.0f);
        set(DISTANCE_ROLLOFF, 0.5f);

        // --- Early Reflections (tighter, less reverberant mud) ---
        set(ER_DELAY_SPREAD_MS, 22.0f);  // was 25.0
        set(ER_MIX,             0.28f);  // was 0.45; less mud, more transparent
        set(ER_HF_DAMPING,      0.45f);  // was 0.50; slightly brighter room

        // --- Reverb (shorter tail, true stereo, transparent ambience) ---
        set(REVERB_DECAY,       0.65f);  // was 0.72; tighter tail
        set(REVERB_DAMPING,     0.50f);  // was 0.45; more natural HF rolloff
        set(REVERB_ROOM_SIZE,   0.52f);
        set(REVERB_WET,         0.12f);  // was 0.18; subtle spatial ambience
        set(REVERB_DRY,         1.0f);
        set(REVERB_PREDELAY_MS, 8.0f);   // was 12.0; tighter room onset
    }

    void set(int paramId, float value) {
        if (paramId >= 0 && paramId < PARAM_MAX_COUNT) {
            params[paramId].store(value, std::memory_order_relaxed);
        }
    }

    float get(int paramId) const {
        if (paramId >= 0 && paramId < PARAM_MAX_COUNT) {
            return params[paramId].load(std::memory_order_relaxed);
        }
        return 0.0f;
    }

private:
    std::atomic<float> params[PARAM_MAX_COUNT];
};

} // namespace nexus
