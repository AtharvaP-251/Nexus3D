# Nexus Spatial Audio App - Implementation Plan

This document outlines the detailed plan for building a flagship-quality spatial audio application on Android. The app will feature a heavily optimized native DSP pipeline isolated from the UI to ensure deterministic, low-latency audio processing while providing a sleek and modern user experience.

## User Review Required

> [!IMPORTANT]
> Please review the proposed phasing structure and the **Open Questions**. Specifically, confirm the integration approach between Media3 and the native DSP backend.

## Proposed Changes

We will restructure the project into a modular architecture to enforce separation of concerns between UI, domain logic, media routing, and the core native sound processing.

### Phase 1: Setup & Core Audio Engine

**Architecture & Android Stack:**
*   **Modules:** `app`, `core-ui`, `core-data`, `core-domain`, `feature-library`, `feature-player`, `feature-audio-settings`, `audio-engine`, `dsp-native`.
*   **UI & Logic:** Kotlin, Jetpack Compose, Hilt/Koin (DI), Room (Metadata/Presets), DataStore (Settings).
*   **Audio Routing:** Media3 / ExoPlayer for file decoding, queuing, background services, and system audio focus.

**Implementation Steps:**
1.  Initialize the multi-module project structure.
2.  Implement Room schema for tracks, presets, and app settings.
3.  Implement Media3 playback service supporting MP3, FLAC, and WAV.
4.  Build local file scanning and offline library indexing logic.

**Expected Output:** Minimum viable offline music player that can reliable scan and play files in the background without DSP enabled.

> [!WARNING]
> **Manual Intervention Point:** Verify background playback stability and Media3 system integration across different Android OEM variants (e.g., Samsung, Pixel) before moving to Phase 2.

---

### Phase 2: DSP Chain (Flagship Native Processing)

**Audio Pipeline (In Order):**
`Input → Pre-EQ → Mid/Side Processing → Phase Alignment → Crossfeed → Stereo Width → Panning → Haas Delay → HRTF → Distance Modeling → Early Reflections → Reverb → Output`

**Threading Model & Low Latency Stack:**
*   **Environment:** NDK C++ for the `dsp-native` module.
*   **Processing:** Frame-based chain (e.g., 128, 256, or 512 sample blocks based on profiling).
*   **Constraints:** **Strictly allocation-free and lock-free audio thread.** Use C++ atomics or double-buffered immutable config snapshots to handle real-time parameter changes from the UI.
*   **Integration:** Map the C++ engine to Media3 as a custom `AudioProcessor` or pipe ExoPlayer PCM decodes through an Oboe/AAudio native stream.

**Implementation Steps:**
1.  Set up C++ JNI bridge and atomic parameter structs.
2.  Implement each DSP block as an isolated, testable native unit.
3.  Create the `ProcessorChain` to coordinate audio frame routing through all blocks.

**Expected Output:** Native `dsp-native` library. Playback routes through the C++ DSP engine without stutter or underruns.

> [!WARNING]
> **Manual Intervention Point:** Profile CPU usage and real-time execution bounds limits on minimum-supported hardware devices to select the optimal frame buffer size.

---

### Phase 3: UI/UX & Dynamic Control System

**Implementation Steps:**
1.  **Preset System:** Serialize predefined configurations like "Studio Reference", "Concert Hall", "Immersive Mode".
2.  **Macro Engine:** Translate user-friendly macro sliders (Width, Depth, Room Size, Clarity, Distance) to multiple low-level DSP parameters with safety limits to avoid ear-piercing artifacts.
3.  **UI Construction:** Create the main Audio Settings Compose UI panel to visualize the active preset and display real-time macro controls.
4.  **App Shell:** Develop a minimal bottom-navigation or swipe layout wrapping the Player, Library, and Settings.

**Expected Output:** A polished, premium Compose UI where users can adjust macro sliders, instantly experiencing smooth auditory changes without audible pops or zipper noise.

> [!WARNING]
> **Manual Intervention Point:** UX/Auditory review. Tuning the mathematical curves behind the macro sliders requires physical listening sessions with various pairs of headphones to ensure the resulting spatialization feels natural and "flagship qualitative".

---

### Phase 4: Optimization & Testing

**Implementation Steps:**
1.  Precompute filter coefficients where possible to reduce per-frame CPU load.
2.  Refine crossfade and parameter-ramp logic when users switch presets roughly.
3.  Handle process death, configuration changes, and precise audio focus restorations safely.

**Expected Output:** Release Candidate (RC) build ready for sustained, real-world deployment.

> [!WARNING]
> **Manual Intervention Point:** Testing across the Android fragmentation matrix. Extensive session endurance tests to verify thermal behavior, battery usage, and compatibility with wireless/Bluetooth versus wired headphones.

---

### Phase 5: Release Strategy

1.  **Internal Alpha:** Playback, scanner, basic UI (No complex DSP).
2.  **Closed Beta:** DSP engine activated with basic preset functionality.
3.  **Public Beta:** Final UI polish, macro controls, stability fixes.
4.  **v1.0 Stable:** Ready for wide release.

## Future Enhancements
*   Custom **HRTF Profile importing** dynamically tailored to specific user ear biometrics.
*   **Head-Tracking** support integrating device gyroscope sensors for absolute spatial anchorage.
*   Dynamic visualizers (e.g., accurate FFTs or Lissajous figures) rendering native DSP output data.
*   Non-linear parametric automation mapping (e.g., ADSR envelopes affecting Reverb size based on transient detection).

## Open Questions
*   **Media3 + NDK Routing:** Should we pipe decoded frames from Media3 to a custom C++ Oboe/AAudio stream for maximum latency reduction, or implement a standard Media3 `AudioProcessor` backed by a JNI bridge? 
*   **Audio Base Format:** Are we standardizing the DSP chain internally at 48kHz / 32-bit float to simplify initial math, and relying on resamplers for varying source files?

## Verification Plan

### Automated Tests
*   **Unit Tests:** Validate Room schemas, data migrations, Macro-to-DSP parameter mapping equations, and JSON preset serialization/deserialization logic.
*   **Native Tests:** Verify C++ DSP math against known impulses/sine sweeps. Ensure silence and clipping constraints perform as expected.

### Manual Verification
*   Execute background/foreground lifecycle transition testing.
*   Conduct long-running (2+ hour) playback tests monitoring thermal load on older architectures.
*   Perform rigorous critical listening via studio monitors and consumer IEMs to ensure spatial width algorithms behave without phase cancellation or frequency masking.
