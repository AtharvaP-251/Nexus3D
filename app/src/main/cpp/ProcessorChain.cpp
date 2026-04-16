#include "ProcessorChain.h"
#include "modules/Modules.h"
#include <android/log.h>

#define LOG_TAG "DspNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace nexus {

ProcessorChain::ProcessorChain() { init(); }

ProcessorChain::~ProcessorChain() {}

void ProcessorChain::init() {
  modules.clear();

  // ── Flagship DSP Chain ─────────────────────────────────────────────────
  // Order follows the mandated spec:
  //   Pre-EQ → Phase → Crossfeed → Adaptive M/S → Stereo Width →
  //   Haas Delay → HRTF(+ITD) → Early Reflections → Late Reverb → Limiter
  //
  // Rationale:
  //  1. PreEQ first: any corrective EQ before spatialization
  //  2. Phase alignment before any widening/crossfeed
  //  3. Crossfeed BEFORE M/S widening — BS2B simulation sets baseline
  //     width, then adaptive M/S enhances it (not re-widening after collapse)
  //  4. Haas: within [5-15 ms] for clean externalization, no echo
  //  5. HRTF+ITD: pinna spectral shape + timing cue together
  //  6. Early Reflections → Late Reverb: room build-up in natural order
  //  7. Limiter last: gain safety across the entire processed signal
  // ───────────────────────────────────────────────────────────────────────

  // Pre-processing
  modules.push_back(std::make_unique<PreEqModule>());
  modules.push_back(std::make_unique<PhaseAlignmentModule>());

  // Spatial chain (spec order)
  modules.push_back(std::make_unique<CrossfeedModule>());
  modules.push_back(std::make_unique<MidSideModule>());
  modules.push_back(std::make_unique<StereoWidthModule>());
  modules.push_back(std::make_unique<HaasDelayModule>());
  modules.push_back(std::make_unique<HrtfModule>());
  modules.push_back(std::make_unique<DistanceModelingModule>());

  // Room simulation
  modules.push_back(std::make_unique<EarlyReflectionsModule>());
  modules.push_back(std::make_unique<ReverbModule>());

  // PanningModule is NOT in the chain.
  // Classes retained in Modules.h/cpp for future preset use.

  LOGD("ProcessorChain initialized with %zu modules", modules.size());
}

DspParameters &ProcessorChain::getParameters() { return parameters; }

void ProcessorChain::process(int16_t *pcmBuffer, int numSamples,
                             int sampleRate) {
  if (parameters.get(GLOBAL_BYPASS) > 0.5f) {
    return; // Complete bypass
  }

  int numFrames = numSamples / 2; // Stereo assumed

  // Resize float buffer if needed
  if (floatBuffer.size() < static_cast<size_t>(numSamples)) {
    floatBuffer.resize(numSamples);
  }

  // Convert int16_t to float [-1.0, 1.0]
  for (int i = 0; i < numSamples; i++) {
    floatBuffer[i] = static_cast<float>(pcmBuffer[i]) / 32768.0f;
  }

  // Apply global gain before the chain
  float globalGain = parameters.get(GLOBAL_GAIN);
  if (globalGain != 1.0f) {
    for (int i = 0; i < numSamples; i++) {
      floatBuffer[i] *= globalGain;
    }
  }

  // Run the DSP chain
  for (auto &module : modules) {
    module->process(floatBuffer.data(), numFrames, parameters, sampleRate);
  }

  // Convert back to int16_t (Limiter ensures bounds; clamp is safety net only)
  for (int i = 0; i < numSamples; i++) {
    float sample = floatBuffer[i];
    if (sample > 1.0f)
      sample = 1.0f;
    else if (sample < -1.0f)
      sample = -1.0f;
    pcmBuffer[i] = static_cast<int16_t>(sample * 32767.0f);
  }
}

} // namespace nexus
