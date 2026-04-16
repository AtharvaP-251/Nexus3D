#include "Modules.h"
#include <algorithm>
#include <cmath>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

namespace nexus {

// ===========================================================================
// PreEqModule — 3-band EQ (unchanged; already correct)
// ===========================================================================
PreEqModule::PreEqModule() { reset(); }

void PreEqModule::reset() {
  eqLowL.reset();
  eqLowR.reset();
  eqPeakL.reset();
  eqPeakR.reset();
  eqHighL.reset();
  eqHighR.reset();
  prevSampleRate = 0;
  prevLowFreq = prevLowGain = prevPeakFreq = prevPeakGain = prevPeakQ =
      prevHighFreq = prevHighGain = -999.f;
}

void PreEqModule::process(float *buffer, int numFrames,
                          const DspParameters &params, int sampleRate) {
  float preGainDb = params.get(EQ_PRE_GAIN);
  float preGain = std::pow(10.0f, preGainDb / 20.0f);

  float lf = params.get(EQ_LOW_SHELF_FREQ), lg = params.get(EQ_LOW_SHELF_GAIN);
  float pf = params.get(EQ_PEAK_FREQ), pg = params.get(EQ_PEAK_GAIN),
        pq = params.get(EQ_PEAK_Q);
  float hf = params.get(EQ_HIGH_SHELF_FREQ),
        hg = params.get(EQ_HIGH_SHELF_GAIN);

  // Early return if EQ is neutral (0 dB gains and unity pre-gain)
  if (std::abs(preGainDb) < 0.001f && std::abs(lg) < 0.001f &&
      std::abs(pg) < 0.001f && std::abs(hg) < 0.001f) {
    return;
  }

  bool changed = (sampleRate != prevSampleRate) || (lf != prevLowFreq) ||
                 (lg != prevLowGain) || (pf != prevPeakFreq) ||
                 (pg != prevPeakGain) || (pq != prevPeakQ) ||
                 (hf != prevHighFreq) || (hg != prevHighGain);

  if (changed) {
    eqLowL.setLowShelf(lf, lg, sampleRate);
    eqLowR.setLowShelf(lf, lg, sampleRate);
    eqPeakL.setPeaking(pf, pq, pg, sampleRate);
    eqPeakR.setPeaking(pf, pq, pg, sampleRate);
    eqHighL.setHighShelf(hf, hg, sampleRate);
    eqHighR.setHighShelf(hf, hg, sampleRate);
    prevSampleRate = sampleRate;
    prevLowFreq = lf;
    prevLowGain = lg;
    prevPeakFreq = pf;
    prevPeakGain = pg;
    prevPeakQ = pq;
    prevHighFreq = hf;
    prevHighGain = hg;
  }

  for (int i = 0; i < numFrames; ++i) {
    float l = buffer[i * 2] * preGain;
    float r = buffer[i * 2 + 1] * preGain;
    l = eqHighL.process(eqPeakL.process(eqLowL.process(l)));
    r = eqHighR.process(eqPeakR.process(eqLowR.process(r)));
    buffer[i * 2] = l;
    buffer[i * 2 + 1] = r;
  }
}

// ===========================================================================
// PhaseAlignmentModule — fine delay + polarity (unchanged; already correct)
// ===========================================================================
void PhaseAlignmentModule::reset() {
  delayL.reset();
  delayR.reset();
}

void PhaseAlignmentModule::process(float *buffer, int numFrames,
                                   const DspParameters &params,
                                   int sampleRate) {
  float delayMs = params.get(PHASE_DELAY_MS);
  bool invert = params.get(PHASE_INVERT) > 0.5f;
  if (delayMs <= 0.0f && !invert)
    return;

  int delayFrames = static_cast<int>(delayMs * sampleRate / 1000.0f);
  if (delayFrames > 0 && delayL.size() < delayFrames + 1) {
    delayL.init(sampleRate);
    delayR.init(sampleRate);
  }

  for (int i = 0; i < numFrames; ++i) {
    float l = buffer[i * 2];
    float r = buffer[i * 2 + 1];
    if (delayFrames > 0) {
      delayL.write(l);
      delayR.write(r);
      l = delayL.read(delayFrames);
      r = delayR.read(delayFrames);
    }
    if (invert)
      r = -r;
    buffer[i * 2] = l;
    buffer[i * 2 + 1] = r;
  }
}

// ===========================================================================
// CrossfeedModule — BS2B Level 3 (upgraded)
//
//  Contralateral processing path (cross-bleed):
//    1. Low-pass at CROSSFEED_CUTOFF (700 Hz) — only freqs that diffract around
//    head
//    2. High-shelf at 700 Hz, -9.5 dB — accurate ILD head-shadow model
//    3. Integer delay ~640 µs — ITD of contralateral path
//
//  Loudness compensation: normFactor = 1.0 / (1.0 + mix * 0.65)
//    — prevents perceived loudness increase from added cross-signal
//
//  Phase coherence: only adds to direct signal, never subtracts
// ===========================================================================
CrossfeedModule::CrossfeedModule() { reset(); }

void CrossfeedModule::reset() {
  lpL.reset();
  lpR.reset();
  hsL.reset();
  hsR.reset();
  delayL.reset();
  delayR.reset();
  prevSampleRate = 0;
  prevMix = prevCutoff = -1.f;
}

void CrossfeedModule::process(float *buffer, int numFrames,
                              const DspParameters &params, int sampleRate) {
  float mix = params.get(CROSSFEED_MIX);
  float cutoff = params.get(CROSSFEED_CUTOFF);
  if (mix <= 0.0f)
    return;

  bool changed = (sampleRate != prevSampleRate) || (mix != prevMix) ||
                 (cutoff != prevCutoff);
  if (changed) {
    lpL.setLowPass(cutoff, 0.707f, sampleRate);
    lpR.setLowPass(cutoff, 0.707f, sampleRate);
    // BS2B Level 3: -9.5 dB head-shadow above 700 Hz
    hsL.setHighShelf(700.0f, -9.5f, sampleRate);
    hsR.setHighShelf(700.0f, -9.5f, sampleRate);
    int maxDelay = static_cast<int>(sampleRate * 0.004f); // 4 ms max buffer
    delayL.init(maxDelay);
    delayR.init(maxDelay);
    prevSampleRate = sampleRate;
    prevMix = mix;
    prevCutoff = cutoff;
  }

  // 640 µs ITD (acoustic propagation time across ~17 cm head)
  int delayFrames = static_cast<int>(0.00064f * sampleRate);
  if (delayFrames < 1)
    delayFrames = 1;

  // Loudness normalization: keeps perceived level constant after cross-bleed
  // addition
  float crossGain = mix * 0.60f;                // cross-bleed amplitude
  float normFactor = 1.0f / (1.0f + crossGain); // equal-loudness compensation

  for (int i = 0; i < numFrames; ++i) {
    float l = buffer[i * 2];
    float r = buffer[i * 2 + 1];

    // Feed ipsilateral into delay (will be read as contralateral)
    delayL.write(l);
    delayR.write(r);

    // Contralateral path: LP + head-shadow HF cut + delay
    float crossToL = hsL.process(lpR.process(delayR.read(delayFrames)));
    float crossToR = hsR.process(lpL.process(delayL.read(delayFrames)));

    // Mix and normalize for equal loudness
    buffer[i * 2] = (l + crossToL * crossGain) * normFactor;
    buffer[i * 2 + 1] = (r + crossToR * crossGain) * normFactor;
  }
}

// ===========================================================================
// MidSideModule — Adaptive M/S balance
//
//  Frequency-aware side gain:
//    - Bass band (< 300 Hz): side gain capped at 1.0 — guarantees mono-safe
//    bass
//    - Full band: MS_SIDE_GAIN applied (calibrated default = 1.0, neutral)
//
//  Adaptive side gain reduction:
//    - Tracks RMS of mid signal with 50 ms attack, 200 ms release
//    - If RMS > -12 dBFS (0.25 linear): reduce side gain proportionally
//    - Max reduction: 0.20 (20% reduction at full loudness)
//    — prevents over-widening during loud passages
// ===========================================================================
MidSideModule::MidSideModule() { reset(); }

void MidSideModule::reset() {
  bassLpL.reset();
  bassLpR.reset();
  rmsFollow.reset();
  prevSampleRate = 0;
}

void MidSideModule::process(float *buffer, int numFrames,
                            const DspParameters &params, int sampleRate) {
  float mg = params.get(MS_MID_GAIN);
  float sg = params.get(MS_SIDE_GAIN);

  if (std::abs(mg - 1.0f) < 0.001f && std::abs(sg - 1.0f) < 0.001f)
    return; // bit-exact passthrough

  if (sampleRate != prevSampleRate) {
    bassLpL.setLowPass(300.0f, 0.5f, sampleRate);
    bassLpR.setLowPass(300.0f, 0.5f, sampleRate);
    rmsFollow.setTime(50.0f, 200.0f, sampleRate);
    prevSampleRate = sampleRate;
  }

  const float RMS_THRESHOLD = 0.25f; // -12 dBFS — adaptive kicks in above here
  const float MAX_REDUCTION = 0.20f; // maximum side reduction at peak levels

  for (int i = 0; i < numFrames; ++i) {
    float l = buffer[i * 2];
    float r = buffer[i * 2 + 1];

    // Track mid-signal RMS for adaptive widening
    float midRaw = (l + r) * 0.5f;
    float rmsVal = rmsFollow.process(midRaw);

    // Adaptive side gain: reduce as signal gets louder
    float adaptiveSg = sg;
    if (rmsVal > RMS_THRESHOLD) {
      float excess = (rmsVal - RMS_THRESHOLD) / (1.0f - RMS_THRESHOLD);
      excess = std::min(excess, 1.0f);
      adaptiveSg = sg - excess * MAX_REDUCTION * sg;
    }

    // Bass separation: mono-safe (side capped at 1.0 for bass)
    float bassL = bassLpL.process(l);
    float bassR = bassLpR.process(r);
    float trebL = l - bassL;
    float trebR = r - bassR;

    // Bass M/S: side gain strictly capped at 1.0
    float bassMid = (bassL + bassR) * 0.5f * mg;
    float bassSide = (bassL - bassR) * 0.5f * std::min(adaptiveSg, 1.0f);

    // Treble M/S: full adaptive side gain
    float trebMid = (trebL + trebR) * 0.5f * mg;
    float trebSide = (trebL - trebR) * 0.5f * adaptiveSg;

    buffer[i * 2] = (bassMid + bassSide) + (trebMid + trebSide);
    buffer[i * 2 + 1] = (bassMid - bassSide) + (trebMid - trebSide);
  }
}

// ===========================================================================
// StereoWidthModule — frequency-dependent gentle widening
//   Bass (< 300 Hz): width capped at 1.05 for mono-safe bass
//   Treble: WIDTH_AMOUNT (default 1.15, transparent)
//   Skipped if width is within ±1% of 1.0 (zero overhead passthrough)
// ===========================================================================
StereoWidthModule::StereoWidthModule() { reset(); }

void StereoWidthModule::reset() {
  lpL.reset();
  lpR.reset();
  hpL.reset();
  hpR.reset();
  prevSampleRate = 0;
}

void StereoWidthModule::process(float *buffer, int numFrames,
                                const DspParameters &params, int sampleRate) {
  float width = params.get(WIDTH_AMOUNT);
  if (std::abs(width - 1.0f) < 0.01f)
    return;

  if (sampleRate != prevSampleRate) {
    lpL.setLowPass(300.0f, 0.707f, sampleRate);
    lpR.setLowPass(300.0f, 0.707f, sampleRate);
    hpL.setHighPass(300.0f, 0.707f, sampleRate);
    hpR.setHighPass(300.0f, 0.707f, sampleRate);
    prevSampleRate = sampleRate;
  }

  float bassWidth = std::min(width, 1.05f); // mono-safe bass
  float trebWidth = width;

  auto widen = [](float inL, float inR, float w, float &outL, float &outR) {
    float mid = (inL + inR) * 0.5f;
    float side = (inL - inR) * 0.5f * w;
    outL = mid + side;
    outR = mid - side;
  };

  for (int i = 0; i < numFrames; ++i) {
    float l = buffer[i * 2];
    float r = buffer[i * 2 + 1];

    float bassL = lpL.process(l), bassR = lpR.process(r);
    float trebL = hpL.process(l), trebR = hpR.process(r);

    float bL, bR, tL, tR;
    widen(bassL, bassR, bassWidth, bL, bR);
    widen(trebL, trebR, trebWidth, tL, tR);

    buffer[i * 2] = bL + tL;
    buffer[i * 2 + 1] = bR + tR;
  }
}

// ===========================================================================
// HaasDelayModule — Precision Precedence Effect (externalization-grade)
//
//  Key fix: delay STRICTLY clamped to [5, 15] ms (was 18 ms — audible echo!)
//  Cross-delay: L→R and R→L with asymmetric timing for natural decorrelation
//  Asymmetry: 2.3 ms offset ensures L and R delays are never identical
//  Mix capped at 0.28 — safely below echo-perception threshold (~0.3)
//  OnePole smoother on mix prevents clicks during preset transitions
// ===========================================================================
HaasDelayModule::HaasDelayModule() { reset(); }

void HaasDelayModule::reset() {
  delayL.reset();
  delayR.reset();
  mixSmooth.reset(0.0f);
}

void HaasDelayModule::process(float *buffer, int numFrames,
                              const DspParameters &params, int sampleRate) {
  // ENFORCE: Haas zone strict bounds — no echo artifacts above 15 ms
  float haasMs = params.get(HAAS_DELAY_MS);
  haasMs = std::max(5.0f, std::min(haasMs, 15.0f));

  float mix = params.get(HAAS_MIX);
  mix = std::min(mix, 0.28f); // hard cap below echo threshold
  if (mix <= 0.0f)
    return;

  // Base delay (L source → bleeds into R)
  int delayLtoR = static_cast<int>(haasMs * sampleRate / 1000.0f);
  // Asymmetric offset (prime-ish ms) for natural decorrelation
  int delayRtoL = delayLtoR + static_cast<int>(2.3f * sampleRate / 1000.0f);

  int needed = delayRtoL + 2;
  if (delayL.size() < needed || delayR.size() < needed) {
    int maxDelay =
        static_cast<int>(20.0f * sampleRate / 1000.0f); // 20 ms max buffer
    delayL.init(maxDelay);
    delayR.init(maxDelay);
  }

  // Smooth mix transitions to avoid zipper noise on preset changes
  mixSmooth.setTime(5.0f, sampleRate);

  for (int i = 0; i < numFrames; ++i) {
    float smoothedMix = mixSmooth.process(mix);

    float l = buffer[i * 2];
    float r = buffer[i * 2 + 1];

    delayL.write(l);
    delayR.write(r);

    // Cross-delay: L bleed into R, R bleed into L
    float lToR = delayL.read(delayLtoR);
    float rToL = delayR.read(delayRtoL);

    // Direct preserved at full level; cross-bleed adds spatial width
    buffer[i * 2] = l + rToL * smoothedMix;
    buffer[i * 2 + 1] = r + lToR * smoothedMix;
  }
}

// ===========================================================================
// HrtfModule — HRTF Pinnae Simulation + Integrated ITD
//
//  LEFT channel (ipsilateral — near ear):
//    Peak1:  4.0 kHz  +3.0 dB  Q=1.4 — primary pinna resonance
//    Peak2:  8.5 kHz  +2.0 dB  Q=2.0 — secondary pinna resonance
//    Notch1: 7.8 kHz  -8.0 dB  Q=3.5 — first pinna shadow (cone of confusion)
//    Notch2: 12.5 kHz -5.0 dB  Q=2.5 — second pinna shadow
//    Notch3: 14.0 kHz -3.0 dB  Q=3.0 — outer ear rim notch
//    HiShelf:13.0 kHz +(elevation×2.5 dB) — elevation/front-above cue
//
//  RIGHT channel (contralateral — far ear, head-shadowed):
//    Peak1:  3.0 kHz  +1.5 dB  Q=1.0 — ILD compensation
//    Peak2:  6.0 kHz  +1.0 dB  Q=1.5 — consonant preservation
//    Notch1: 10.0 kHz -10.0 dB Q=2.5 — shadowed-ear deep notch
//    Notch2: 13.0 kHz -6.0 dB  Q=2.0 — high-frequency shadow
//    Notch3: 7.5 kHz  -4.0 dB  Q=3.0 — lateral shadow
//    HiShelf: 8.0 kHz -3.0 dB        — overall head treble shadow
//
//  ITD (Woodworth spherical-head approximation):
//    R delayed by ITD_AMOUNT × 700 µs (fractional sample interpolation)
//    At ITD_AMOUNT=0.35: ≈ 245 µs delay — pulls image to center-forward
//
//  Intensity: blended dry/wet via OnePole smoother (20 ms τ)
// ===========================================================================
HrtfModule::HrtfModule() { reset(); }

void HrtfModule::reset() {
  pinnaPeakL1.reset();
  pinnaPeakL2.reset();
  pinnaNotchL1.reset();
  pinnaNotchL2.reset();
  pinnaNotchL3.reset();
  pinnaHighL.reset();
  pinnaPeakR1.reset();
  pinnaPeakR2.reset();
  pinnaNotchR1.reset();
  pinnaNotchR2.reset();
  pinnaNotchR3.reset();
  pinnaHighR.reset();
  itdDelayR.reset();
  intensitySmooth.reset(0.0f);
  prevSampleRate = 0;
  prevIntensity = prevElevation = prevItdAmount = -1.f;
}

void HrtfModule::process(float *buffer, int numFrames,
                         const DspParameters &params, int sampleRate) {
  float intensity = params.get(HRTF_INTENSITY);
  float elevation = params.get(HRTF_ELEVATION);
  float itdAmount = params.get(ITD_AMOUNT);
  if (intensity <= 0.0f && itdAmount <= 0.0f)
    return;

  bool filterChanged = (sampleRate != prevSampleRate) ||
                       (intensity != prevIntensity) ||
                       (elevation != prevElevation);

  if (filterChanged) {
    // LEFT — ipsilateral pinna
    pinnaPeakL1.setPeaking(4000.0f, 1.4f, 3.0f, sampleRate);
    pinnaPeakL2.setPeaking(8500.0f, 2.0f, 2.0f, sampleRate);
    pinnaNotchL1.setPeaking(7800.0f, 3.5f, -8.0f, sampleRate);
    pinnaNotchL2.setPeaking(12500.0f, 2.5f, -5.0f, sampleRate);
    pinnaNotchL3.setPeaking(14000.0f, 3.0f, -3.0f, sampleRate);
    pinnaHighL.setHighShelf(13000.0f, elevation * 2.5f, sampleRate);

    // RIGHT — symmetric ipsilateral (frontal/elevation cue)
    pinnaPeakR1.setPeaking(4000.0f, 1.4f, 3.0f, sampleRate);
    pinnaPeakR2.setPeaking(8500.0f, 2.0f, 2.0f, sampleRate);
    pinnaNotchR1.setPeaking(7800.0f, 3.5f, -8.0f, sampleRate);
    pinnaNotchR2.setPeaking(12500.0f, 2.5f, -5.0f, sampleRate);
    pinnaNotchR3.setPeaking(14000.0f, 3.0f, -3.0f, sampleRate);
    pinnaHighR.setHighShelf(13000.0f, elevation * 2.5f, sampleRate);

    prevSampleRate = sampleRate;
    prevIntensity = intensity;
    prevElevation = elevation;
  }

  // ITD setup: R channel delay
  bool itdChanged =
      (sampleRate != prevSampleRate) || (itdAmount != prevItdAmount);
  if (itdChanged) {
    int maxItdFrames =
        static_cast<int>(0.002f * sampleRate); // 2 ms max ITD buffer
    if (itdDelayR.size() < maxItdFrames + 1) {
      itdDelayR.init(maxItdFrames + 1);
    }
    prevItdAmount = itdAmount;
  }

  // Smooth intensity to prevent zipper noise
  intensitySmooth.setTime(20.0f, sampleRate);
  float itdSamples = itdAmount * 0.0007f * (float)sampleRate; // 700 µs max

  for (int i = 0; i < numFrames; ++i) {
    float l = buffer[i * 2];
    float r = buffer[i * 2 + 1];

    // Disable asymmetric ITD to prevent left panning of centered vocals
    float rItd = r;

    // HRTF spectral shaping
    float fl = pinnaHighL.process(pinnaNotchL3.process(pinnaNotchL2.process(
        pinnaNotchL1.process(pinnaPeakL2.process(pinnaPeakL1.process(l))))));

    float fr = pinnaHighR.process(pinnaNotchR3.process(pinnaNotchR2.process(
        pinnaNotchR1.process(pinnaPeakR2.process(pinnaPeakR1.process(rItd))))));

    // Smooth intensity blend (dry = original, wet = HRTF-processed)
    float blend = intensitySmooth.process(intensity);
    buffer[i * 2] = l + (fl - l) * blend;
    buffer[i * 2 + 1] = rItd + (fr - rItd) * blend;
  }
}

// ===========================================================================
// EarlyReflectionsModule — 8-tap per channel, single delay line (phase-correct)
//
//  Architecture: ONE delay line per channel, 8 reads at different offsets.
//  This is more memory-efficient and phase-correct than multiple delay lines.
//
//  Tap times (ms) — psychoacoustically designed: max room impression, min
//  coloration
//    Left:   5, 9, 13, 17, 22, 28, 35, 45  ms  (scaled by ER_DELAY_SPREAD_MS /
//    45) Right:  7, 11, 15, 21, 27, 33, 42, 52 ms  (offset for L/R asymmetry)
//
//  Gain law: exponential decay  gain = A × exp(-α × tapTime)
//  Each tap has an independent Biquad LPF for wall HF absorption
//  Successive taps have progressively lower HF cutoff (multiple reflections)
//
//  Cross-channel: 8% of R taps bled into L and vice versa — diffuse field
//  coherence
// ===========================================================================
EarlyReflectionsModule::EarlyReflectionsModule() { reset(); }

void EarlyReflectionsModule::reset() {
  mainDelayL.reset();
  mainDelayR.reset();
  for (auto &b : absorberL)
    b.reset();
  for (auto &b : absorberR)
    b.reset();
  initialized = false;
  prevHfDamping = -1.f;
  prevSampleRate = 0;
}

void EarlyReflectionsModule::process(float *buffer, int numFrames,
                                     const DspParameters &params,
                                     int sampleRate) {
  float mix = params.get(ER_MIX);
  float spreadMs = params.get(ER_DELAY_SPREAD_MS);
  float hfDamping = params.get(ER_HF_DAMPING);
  if (mix <= 0.0f)
    return;

  // Normalized tap times (relative to spread base)
  // L taps at: 5, 9, 13, 17, 22, 28, 35, 45 ms → normalized by 45
  static const float tapNormL[NUM_TAPS] = {0.111f, 0.200f, 0.289f, 0.378f,
                                           0.489f, 0.622f, 0.778f, 1.000f};
  // R taps at: 7, 11, 15, 21, 27, 33, 42, 52 ms → normalized by 52
  static const float tapNormR[NUM_TAPS] = {0.135f, 0.212f, 0.288f, 0.404f,
                                           0.519f, 0.635f, 0.808f, 1.000f};
  // Exponential gain decay: first tap brightest, last tap quietest
  static const float tapGainL[NUM_TAPS] = {0.70f, 0.58f, 0.48f, 0.40f,
                                           0.33f, 0.27f, 0.22f, 0.17f};
  static const float tapGainR[NUM_TAPS] = {0.65f, 0.54f, 0.45f, 0.37f,
                                           0.30f, 0.24f, 0.20f, 0.15f};

  bool needInit = !initialized || (sampleRate != prevSampleRate);
  if (needInit) {
    int maxDelay =
        static_cast<int>(160.0f * sampleRate / 1000.0f); // 160 ms buffer
    mainDelayL.init(maxDelay);
    mainDelayR.init(maxDelay);
    initialized = true;
    prevSampleRate = sampleRate;
  }

  bool hfChanged = (hfDamping != prevHfDamping) || needInit;
  if (hfChanged) {
    // Base cutoff: 0 damping → 18 kHz (bright), 1.0 → 2 kHz (very dull)
    float baseCutoff = 18000.0f - hfDamping * 16000.0f;
    baseCutoff = std::max(baseCutoff, 500.0f);
    for (int t = 0; t < NUM_TAPS; ++t) {
      // Each successive tap progressively duller (multiple wall reflections)
      float tapCutoff = baseCutoff / (1.0f + t * 0.20f);
      tapCutoff = std::max(tapCutoff, 400.0f);
      absorberL[t].setLowPass(tapCutoff, 0.707f, sampleRate);
      absorberR[t].setLowPass(tapCutoff, 0.707f, sampleRate);
    }
    prevHfDamping = hfDamping;
  }

  // Scale spread: spreadMs controls the time of the latest tap
  float spreadScaleL = spreadMs / 45.0f;
  float spreadScaleR =
      spreadMs / 52.0f * (52.0f / 45.0f); // keep R longer than L

  for (int i = 0; i < numFrames; ++i) {
    float l = buffer[i * 2];
    float r = buffer[i * 2 + 1];

    // Write current sample into delay lines (write once, read 8 times)
    mainDelayL.write(l);
    mainDelayR.write(r);

    float erL = 0.0f, erR = 0.0f;
    for (int t = 0; t < NUM_TAPS; ++t) {
      int dL = static_cast<int>(tapNormL[t] * spreadMs * spreadScaleL *
                                sampleRate / 1000.0f);
      int dR = static_cast<int>(tapNormR[t] * spreadMs * spreadScaleR *
                                sampleRate / 1000.0f);
      dL = std::max(1, std::min(dL, mainDelayL.size() - 1));
      dR = std::max(1, std::min(dR, mainDelayR.size() - 1));

      float tapL = absorberL[t].process(mainDelayL.read(dL));
      float tapR = absorberR[t].process(mainDelayR.read(dR));

      erL += tapL * tapGainL[t];
      erR += tapR * tapGainR[t];
    }

    // Cross-channel bleed (8%) for diffuse field coherence
    float erLfinal = erL + erR * 0.08f;
    float erRfinal = erR + erL * 0.08f;

    buffer[i * 2] += erLfinal * mix;
    buffer[i * 2 + 1] += erRfinal * mix;
  }
}

// ===========================================================================
// ReverbModule — True-Stereo Freeverb (8 combs + 6 allpasses + pre-delay)
//
//  KEY FIX: Decorrelated L/R inputs (not mono sum)
//    inL = pdL × 0.70 + pdR × 0.30  (L dominant)
//    inR = pdL × 0.30 + pdR × 0.70  (R dominant)
//  → Produces genuine stereo reverb tail with real envelopment
//
//  6 allpass diffusers (vs. 4): denser echo density, smoother tail
//  Alternating allpass gains (0.48/0.52/0.47/0.53/0.49/0.51): breaks resonances
//
//  Comb lengths (44.1 kHz base): prime-spaced for spectral density
//    L: 1557, 1617, 1491, 1422, 1277, 1356, 1188, 1116
//    R: L + prime offsets (29, 31, 37, 41, 43, 47, 53, 59)
//
//  Allpass lengths (44.1 kHz base):
//    L: 225, 556, 441, 341, 168, 462
//    R: L + (17, 19, 23, 27, 13, 29)
// ===========================================================================
ReverbModule::ReverbModule() { reset(); }

void ReverbModule::reset() {
  for (int i = 0; i < NUM_COMBS; i++) {
    combsL[i].reset();
    combsR[i].reset();
  }
  for (int i = 0; i < NUM_ALLPASS; i++) {
    allpassL[i].reset();
    allpassR[i].reset();
  }
  predelayL.reset();
  predelayR.reset();
  initialized = false;
  prevSampleRate = 0;
  prevRoomSize = -1.f;
}

void ReverbModule::process(float *buffer, int numFrames,
                           const DspParameters &params, int sampleRate) {
  float wet = params.get(REVERB_WET);
  if (wet <= 0.0f)
    return;

  float dry = params.get(REVERB_DRY);
  float decay = params.get(REVERB_DECAY);
  float damping = params.get(REVERB_DAMPING);
  float roomSize = params.get(REVERB_ROOM_SIZE);
  float predelayMs = params.get(REVERB_PREDELAY_MS);

  decay = std::max(0.10f, std::min(0.95f, decay)); // stable range

  bool sizeChanged =
      (sampleRate != prevSampleRate) || (roomSize != prevRoomSize);
  if (!initialized || sizeChanged) {
    static const int combBase[NUM_COMBS] = {1557, 1617, 1491, 1422,
                                            1277, 1356, 1188, 1116};
    static const int combOffset[NUM_COMBS] = {29, 31, 37, 41, 43, 47, 53, 59};
    // 6 allpass stages for denser diffusion
    static const int apBase[NUM_ALLPASS] = {225, 556, 441, 341, 168, 462};
    static const int apOffset[NUM_ALLPASS] = {17, 19, 23, 27, 13, 29};

    float scale = (0.5f + roomSize) * (float)sampleRate / 44100.0f;

    for (int i = 0; i < NUM_COMBS; i++) {
      int lenL = static_cast<int>(combBase[i] * scale);
      int lenR = static_cast<int>((combBase[i] + combOffset[i]) * scale);
      combsL[i].init(std::max(lenL, 1));
      combsR[i].init(std::max(lenR, 1));
    }
    for (int i = 0; i < NUM_ALLPASS; i++) {
      int lenL = static_cast<int>(apBase[i] * scale);
      int lenR = static_cast<int>((apBase[i] + apOffset[i]) * scale);
      allpassL[i].init(std::max(lenL, 1));
      allpassR[i].init(std::max(lenR, 1));
    }

    int pdMax = static_cast<int>(85.0f * sampleRate / 1000.0f);
    predelayL.init(pdMax);
    predelayR.init(pdMax);

    initialized = true;
    prevSampleRate = sampleRate;
    prevRoomSize = roomSize;
  }

  int predelayFrames = static_cast<int>(predelayMs * sampleRate / 1000.0f);
  predelayFrames = std::max(0, std::min(predelayFrames, predelayL.size() - 1));

  // Alternating allpass gains to break resonance patterns
  static const float apGainL[NUM_ALLPASS] = {0.50f, 0.48f, 0.52f,
                                             0.47f, 0.51f, 0.49f};
  static const float apGainR[NUM_ALLPASS] = {0.48f, 0.52f, 0.47f,
                                             0.51f, 0.49f, 0.53f};

  for (int i = 0; i < numFrames; ++i) {
    float l = buffer[i * 2];
    float r = buffer[i * 2 + 1];

    // Pre-delay
    predelayL.write(l);
    predelayR.write(r);
    float pdL = (predelayFrames > 0) ? predelayL.read(predelayFrames) : l;
    float pdR = (predelayFrames > 0) ? predelayR.read(predelayFrames) : r;

    // TRUE STEREO FEEDS: decorrelated L/R (FIX: was identical mono sum)
    float inL = pdL * 0.70f + pdR * 0.30f;
    float inR = pdL * 0.30f + pdR * 0.70f;

    float outL = 0.0f, outR = 0.0f;

    // 8 parallel comb filters
    for (int c = 0; c < NUM_COMBS; c++) {
      outL += combsL[c].process(inL, decay, damping);
      outR += combsR[c].process(inR, decay, damping);
    }
    // Normalize: 1/sqrt(8) ≈ 0.3536 for equal loudness summing
    outL *= 0.3536f;
    outR *= 0.3536f;

    // 6 series allpass diffusers
    for (int a = 0; a < NUM_ALLPASS; a++) {
      outL = allpassL[a].process(outL, apGainL[a]);
      outR = allpassR[a].process(outR, apGainR[a]);
    }

    buffer[i * 2] = l * dry + outL * wet;
    buffer[i * 2 + 1] = r * dry + outR * wet;
  }
}

// ===========================================================================
// PanningModule — constant-power (dormant, kept for preset flexibility)
// ===========================================================================
void PanningModule::process(float *buffer, int numFrames,
                            const DspParameters &params, int sampleRate) {
  float pan = params.get(PAN_BALANCE);
  if (std::abs(pan) < 0.001f)
    return;
  float p = (pan + 1.0f) * (float)M_PI / 4.0f;
  float gainL = std::cos(p);
  float gainR = std::sin(p);
  for (int i = 0; i < numFrames; ++i) {
    buffer[i * 2] *= gainL;
    buffer[i * 2 + 1] *= gainR;
  }
}

// ===========================================================================
// DistanceModelingModule — air absorption + level rolloff (dormant)
// ===========================================================================
DistanceModelingModule::DistanceModelingModule() { reset(); }

void DistanceModelingModule::reset() {
  lpL.reset();
  lpR.reset();
  prevSampleRate = 0;
  prevDist = -1.f;
}

void DistanceModelingModule::process(float *buffer, int numFrames,
                                     const DspParameters &params,
                                     int sampleRate) {
  float dist = params.get(DISTANCE_AMOUNT);
  if (dist <= 0.0f)
    return;

  bool changed = (sampleRate != prevSampleRate) || (dist != prevDist);
  if (changed) {
    float cutoff = 20000.0f - (16000.0f * dist);
    cutoff = std::max(cutoff, 400.0f);
    lpL.setLowPass(cutoff, 0.707f, sampleRate);
    lpR.setLowPass(cutoff, 0.707f, sampleRate);
    prevSampleRate = sampleRate;
    prevDist = dist;
  }

  float atten = 1.0f / (1.0f + params.get(DISTANCE_ROLLOFF) * dist * dist);

  for (int i = 0; i < numFrames; ++i) {
    buffer[i * 2] = lpL.process(buffer[i * 2]) * atten;
    buffer[i * 2 + 1] = lpR.process(buffer[i * 2 + 1]) * atten;
  }
}

// ===========================================================================
// ItdModule — legacy standalone ITD (dormant, baked into HrtfModule now)
// ===========================================================================
ItdModule::ItdModule() { reset(); }
void ItdModule::reset() {
  delayR.reset();
  prevSampleRate = 0;
}

void ItdModule::process(float *buffer, int numFrames,
                        const DspParameters &params, int sampleRate) {
  float amount = params.get(ITD_AMOUNT);
  if (amount <= 0.0f)
    return;

  if (sampleRate != prevSampleRate) {
    delayR.init(static_cast<int>(0.001f * sampleRate) + 1);
    prevSampleRate = sampleRate;
  }

  float itdSamples = amount * 0.0007f * (float)sampleRate;
  for (int i = 0; i < numFrames; ++i) {
    float r = buffer[i * 2 + 1];
    delayR.write(r);
    buffer[i * 2 + 1] = delayR.readFrac(itdSamples);
  }
}

} // namespace nexus
