#pragma once

#include "../DspMath.h"
#include "../DspModule.h"
#include <array>

namespace nexus {

// ---------------------------------------------------------------------------
// Pre-EQ  (3-band: low shelf / parametric peak / high shelf)
// ---------------------------------------------------------------------------
class PreEqModule : public DspModule {
public:
  PreEqModule();
  void process(float *buffer, int numFrames, const DspParameters &params,
               int sampleRate) override;
  void reset() override;
  std::string getName() const override { return "Pre-EQ"; }

private:
  Biquad eqLowL, eqLowR;
  Biquad eqPeakL, eqPeakR;
  Biquad eqHighL, eqHighR;
  int prevSampleRate = 0;
  float prevLowFreq = -1, prevLowGain = -999;
  float prevPeakFreq = -1, prevPeakGain = -999, prevPeakQ = -1;
  float prevHighFreq = -1, prevHighGain = -999;
};

// ---------------------------------------------------------------------------
// Phase Alignment  (per-channel fine delay + polarity)
// ---------------------------------------------------------------------------
class PhaseAlignmentModule : public DspModule {
public:
  PhaseAlignmentModule() { reset(); }
  void process(float *buffer, int numFrames, const DspParameters &params,
               int sampleRate) override;
  void reset() override;
  std::string getName() const override { return "Phase Alignment"; }

private:
  DelayLine delayL, delayR;
};

// ---------------------------------------------------------------------------
// Crossfeed  — BS2B Level 3 speaker simulation
//   LP at CROSSFEED_CUTOFF + high-shelf -9.5 dB (head shadow) + 640 µs delay
//   Loudness-compensated: output normalized by 1/(1+mix)
// ---------------------------------------------------------------------------
class CrossfeedModule : public DspModule {
public:
  CrossfeedModule();
  void process(float *buffer, int numFrames, const DspParameters &params,
               int sampleRate) override;
  void reset() override;
  std::string getName() const override { return "Crossfeed"; }

private:
  Biquad lpL, lpR; // Low-pass: frequencies that diffract around the head
  Biquad hsL, hsR; // High-shelf: head-shadow ILD (-9.5 dB above 700 Hz)
  DelayLine delayL, delayR; // 640 µs contralateral ITD
  int prevSampleRate = 0;
  float prevMix = -1.f, prevCutoff = -1.f;
};

// ---------------------------------------------------------------------------
// Mid/Side Module — Adaptive M/S balance
//   Bass band (< 300 Hz): side capped at 1.0 (mono-safe)
//   Full band: adaptive side reduction when signal RMS > -12 dBFS
//   Transparent: MS_MID_GAIN=1.0, MS_SIDE_GAIN=1.0 = bit-exact passthrough
// ---------------------------------------------------------------------------
class MidSideModule : public DspModule {
public:
  MidSideModule();
  void process(float *buffer, int numFrames, const DspParameters &params,
               int sampleRate) override;
  void reset() override;
  std::string getName() const override { return "Mid/Side"; }

private:
  Biquad bassLpL, bassLpR; // LP crossover at 300 Hz for bass mono-protection
  RmsFollower rmsFollow;   // Adaptive widening protection
  int prevSampleRate = 0;
};

// ---------------------------------------------------------------------------
// Stereo Width  — frequency-dependent gentle complement to M/S
//   Bass  (< 300 Hz): width capped at 1.05 (mono-safe)
//   Treble width = WIDTH_AMOUNT (calibrated to 1.15 for transparency)
// ---------------------------------------------------------------------------
class StereoWidthModule : public DspModule {
public:
  StereoWidthModule();
  void process(float *buffer, int numFrames, const DspParameters &params,
               int sampleRate) override;
  void reset() override;
  std::string getName() const override { return "Stereo Width"; }

private:
  Biquad lpL, lpR, hpL, hpR;
  int prevSampleRate = 0;
};

// ---------------------------------------------------------------------------
// Haas / Precedence Effect — precision 5–15 ms cross-delay
//   Delay STRICTLY clamped to [5, 15] ms (no echoes)
//   Asymmetric L/R delays (2.3 ms offset) for natural decorrelation
//   Mix capped at 0.30 to stay below echo-perception threshold
// ---------------------------------------------------------------------------
class HaasDelayModule : public DspModule {
public:
  HaasDelayModule();
  void process(float *buffer, int numFrames, const DspParameters &params,
               int sampleRate) override;
  void reset() override;
  std::string getName() const override { return "Haas Delay"; }

private:
  DelayLine delayL, delayR;
  OnePole mixSmooth; // Prevents zipper noise on mix changes
  float haasLpStateL = 0.0f;
  float haasLpStateR = 0.0f;
};

// ---------------------------------------------------------------------------
// HRTF Pinnae Simulation + Integrated ITD
//   LEFT  (ipsilateral):  peaks 4.0 kHz, 8.5 kHz; notches 7.8 kHz, 12.5
//   kHz, 14.0 kHz RIGHT (contralateral): head-shadow LP; notch 10 kHz; treble
//   shelf –3 dB ITD: R channel delayed by ITD_AMOUNT × 700 µs (Woodworth
//   approximation) Intensity crossfade via OnePole smoother (20 ms) — no zipper
//   noise
// ---------------------------------------------------------------------------
class HrtfModule : public DspModule {
public:
  HrtfModule();
  void process(float *buffer, int numFrames, const DspParameters &params,
               int sampleRate) override;
  void reset() override;
  std::string getName() const override { return "HRTF Simulation"; }

private:
  // Left (ipsilateral): pinna resonance cascade
  Biquad pinnaPeakL1, pinnaPeakL2;
  Biquad pinnaNotchL1, pinnaNotchL2, pinnaNotchL3;
  Biquad pinnaHighL;
  // Right (contralateral): head-shadow + pinna
  Biquad pinnaPeakR1, pinnaPeakR2;
  Biquad pinnaNotchR1, pinnaNotchR2, pinnaNotchR3;
  Biquad pinnaHighR;
  // Built-in ITD: delays right channel for L/R timing asymmetry
  DelayLine itdDelayR;
  // Smooth intensity transitions (prevent zipper noise)
  OnePole intensitySmooth;
  int prevSampleRate = 0;
  float prevIntensity = -1.f;
  float prevElevation = -1.f;
  float prevItdAmount = -1.f;
};

// ---------------------------------------------------------------------------
// Early Reflections  — 8-tap per channel, single delay line per channel
//   Tap times designed for max room impression, min coloration
//   Cross-channel minor bleed for diffuse-field coherence
//   All 8 taps read from ONE delay line (memory-efficient, phase-correct)
// ---------------------------------------------------------------------------
class EarlyReflectionsModule : public DspModule {
public:
  EarlyReflectionsModule();
  void process(float *buffer, int numFrames, const DspParameters &params,
               int sampleRate) override;
  void reset() override;
  std::string getName() const override { return "Early Reflections"; }

  static constexpr int NUM_TAPS = 8; // per channel

private:
  // Single delay line per channel — 8 taps read at different offsets
  DelayLine mainDelayL, mainDelayR;
  // Per-tap HF absorbers (each tap is independently damped)
  std::array<Biquad, NUM_TAPS> absorberL, absorberR;
  bool initialized = false;
  float prevHfDamping = -1.f;
  int prevSampleRate = 0;
};

// ---------------------------------------------------------------------------
// Reverb  — True-Stereo Freeverb: 8 combs + 6 allpasses + pre-delay
//   Decorrelated L/R feeds: L=0.7×pdL+0.3×pdR, R=0.3×pdL+0.7×pdR
//   6 allpass diffusers (vs. 4 previously) for denser echo density
//   Alternating allpass gains to break resonance patterns
// ---------------------------------------------------------------------------
class ReverbModule : public DspModule {
public:
  ReverbModule();
  void process(float *buffer, int numFrames, const DspParameters &params,
               int sampleRate) override;
  void reset() override;
  std::string getName() const override { return "Reverb"; }

  static constexpr int NUM_COMBS = 8;
  static constexpr int NUM_ALLPASS = 6;

private:
  CombFilter combsL[NUM_COMBS], combsR[NUM_COMBS];
  Allpass1 allpassL[NUM_ALLPASS], allpassR[NUM_ALLPASS];
  DelayLine predelayL, predelayR;
  bool initialized = false;
  int prevSampleRate = 0;
  float prevRoomSize = -1.f;
};

// ---------------------------------------------------------------------------
// Panning (dormant — not in default chain, kept for preset flexibility)
// ---------------------------------------------------------------------------
class PanningModule : public DspModule {
public:
  void process(float *buffer, int numFrames, const DspParameters &params,
               int sampleRate) override;
  void reset() override {}
  std::string getName() const override { return "Panning"; }
};

// ---------------------------------------------------------------------------
// Distance Modeling (dormant — not in default chain, kept for preset
// flexibility)
// ---------------------------------------------------------------------------
class DistanceModelingModule : public DspModule {
public:
  DistanceModelingModule();
  void process(float *buffer, int numFrames, const DspParameters &params,
               int sampleRate) override;
  void reset() override;
  std::string getName() const override { return "Distance Modeling"; }

private:
  Biquad airAbsorbL1, airAbsorbL2;
  Biquad airAbsorbR1, airAbsorbR2;
  Biquad proximityL, proximityR;
  int prevSampleRate = 0;
  float prevDist = -1.f;
};

// ---------------------------------------------------------------------------
// Soft Limiter — True-peak lookahead / soft-knee dynamic compression
// ---------------------------------------------------------------------------
class SoftLimiterModule : public DspModule {
public:
  SoftLimiterModule();
  void process(float *buffer, int numFrames, const DspParameters &params,
               int sampleRate) override;
  void reset() override;
  std::string getName() const override { return "Soft Limiter"; }

private:
  SoftLimiter limiter; // Linked stereo limiter
  int prevSampleRate = 0;
};

// ---------------------------------------------------------------------------
// ITD (dormant — functionality now baked into HrtfModule)
// ---------------------------------------------------------------------------
class ItdModule : public DspModule {
public:
  ItdModule();
  void process(float *buffer, int numFrames, const DspParameters &params,
               int sampleRate) override;
  void reset() override;
  std::string getName() const override { return "ITD (legacy)"; }

private:
  DelayLine delayR;
  int prevSampleRate = 0;
};

} // namespace nexus
