#pragma once

#include <cmath>
#include <vector>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

namespace nexus {

// ---------------------------------------------------------------------------
// Biquad Filter (Direct Form I)
// ---------------------------------------------------------------------------
class Biquad {
public:
    Biquad() { reset(); }

    void reset() {
        d1 = d2 = 0.0f;
        b0 = 1.0f; b1 = b2 = a1 = a2 = 0.0f;
    }

    void setCoefficients(float b0_, float b1_, float b2_, float a1_, float a2_) {
        b0 = b0_; b1 = b1_; b2 = b2_;
        a1 = a1_; a2 = a2_;
    }

    /** Low shelf — S=1 slope */
    void setLowShelf(float f0, float gainDb, float sampleRate) {
        float A  = std::pow(10.0f, gainDb / 40.0f);
        float w0 = 2.0f * (float)M_PI * f0 / sampleRate;
        float cosw = std::cos(w0);
        float sinw = std::sin(w0);
        // S=1: alpha = sin(w0)/2 * sqrt(2) = sin(w0) * 0.7071
        float alpha = sinw * 0.7071f;

        float b0_ = A*((A+1)-(A-1)*cosw+2*std::sqrt(A)*alpha);
        float b1_ = 2*A*((A-1)-(A+1)*cosw);
        float b2_ = A*((A+1)-(A-1)*cosw-2*std::sqrt(A)*alpha);
        float a0_ = (A+1)+(A-1)*cosw+2*std::sqrt(A)*alpha;
        float a1_ = -2*((A-1)+(A+1)*cosw);
        float a2_ = (A+1)+(A-1)*cosw-2*std::sqrt(A)*alpha;
        setCoefficients(b0_/a0_, b1_/a0_, b2_/a0_, a1_/a0_, a2_/a0_);
    }

    /** High shelf — S=1 slope */
    void setHighShelf(float f0, float gainDb, float sampleRate) {
        float A  = std::pow(10.0f, gainDb / 40.0f);
        float w0 = 2.0f * (float)M_PI * f0 / sampleRate;
        float cosw = std::cos(w0);
        float sinw = std::sin(w0);
        float alpha = sinw * 0.7071f;

        float b0_ = A*((A+1)+(A-1)*cosw+2*std::sqrt(A)*alpha);
        float b1_ = -2*A*((A-1)+(A+1)*cosw);
        float b2_ = A*((A+1)+(A-1)*cosw-2*std::sqrt(A)*alpha);
        float a0_ = (A+1)-(A-1)*cosw+2*std::sqrt(A)*alpha;
        float a1_ = 2*((A-1)-(A+1)*cosw);
        float a2_ = (A+1)-(A-1)*cosw-2*std::sqrt(A)*alpha;
        setCoefficients(b0_/a0_, b1_/a0_, b2_/a0_, a1_/a0_, a2_/a0_);
    }

    /** Peaking EQ band */
    void setPeaking(float f0, float Q, float gainDb, float sampleRate) {
        float A  = std::pow(10.0f, gainDb / 40.0f);
        float w0 = 2.0f * (float)M_PI * f0 / sampleRate;
        float alpha = std::sin(w0) / (2.0f * Q);

        float a0_ = 1.0f + alpha / A;
        setCoefficients(
            (1.0f + alpha * A) / a0_,
            (-2.0f * std::cos(w0)) / a0_,
            (1.0f - alpha * A) / a0_,
            (-2.0f * std::cos(w0)) / a0_,
            (1.0f - alpha / A) / a0_
        );
    }

    /** Parametric notch (gainDb < 0) — alias of setPeaking */
    void setNotch(float f0, float Q, float gainDb, float sampleRate) {
        setPeaking(f0, Q, gainDb, sampleRate);
    }

    /** 2nd-order Butterworth low-pass */
    void setLowPass(float f0, float Q, float sampleRate) {
        float w0 = 2.0f * (float)M_PI * f0 / sampleRate;
        float alpha = std::sin(w0) / (2.0f * Q);
        float cosw = std::cos(w0);
        float a0_ = 1.0f + alpha;
        setCoefficients(
            (1.0f - cosw) / 2.0f / a0_,
            (1.0f - cosw) / a0_,
            (1.0f - cosw) / 2.0f / a0_,
            -2.0f * cosw / a0_,
            (1.0f - alpha) / a0_
        );
    }

    /** 2nd-order high-pass */
    void setHighPass(float f0, float Q, float sampleRate) {
        float w0 = 2.0f * (float)M_PI * f0 / sampleRate;
        float alpha = std::sin(w0) / (2.0f * Q);
        float cosw = std::cos(w0);
        float a0_ = 1.0f + alpha;
        setCoefficients(
            (1.0f + cosw) / 2.0f / a0_,
            -(1.0f + cosw) / a0_,
            (1.0f + cosw) / 2.0f / a0_,
            -2.0f * cosw / a0_,
            (1.0f - alpha) / a0_
        );
    }

    inline float process(float in) {
        float out = b0 * in + d1;
        d1 = b1 * in - a1 * out + d2;
        d2 = b2 * in - a2 * out;
        return out;
    }

private:
    float b0 = 1.f, b1 = 0.f, b2 = 0.f, a1 = 0.f, a2 = 0.f;
    float d1 = 0.f, d2 = 0.f;
};

// ---------------------------------------------------------------------------
// Soft Limiter (True-Peak Lookahead / Soft-Knee)
// ---------------------------------------------------------------------------
class SoftLimiter {
public:
    void init(float attackMs, float releaseMs, int sampleRate) {
        attackCoeff = std::exp(-1.0f / (sampleRate * attackMs / 1000.0f));
        releaseCoeff = std::exp(-1.0f / (sampleRate * releaseMs / 1000.0f));
    }

    void reset() {
        gainReduction = 1.0f;
    }

    inline float process(float sample) {
        float absSample = std::abs(sample);
        float targetGain = 1.0f;
        
        // Soft knee starting at -1 dBFS (approx 0.89 absolute value)
        const float threshold = 0.891f;
        
        if (absSample > threshold) {
            targetGain = threshold / absSample;
        }
        
        if (targetGain < gainReduction) {
            gainReduction = attackCoeff * gainReduction + (1.0f - attackCoeff) * targetGain;
        } else {
            gainReduction = releaseCoeff * gainReduction + (1.0f - releaseCoeff) * targetGain;
        }
        
        return sample * gainReduction;
    }

    /** Linked Stereo Process: applies identical gain reduction to both L and R to preserve image center */
    inline void processStereo(float& l, float& r) {
        float maxAbs = std::max(std::abs(l), std::abs(r));
        float targetGain = 1.0f;
        const float threshold = 0.891f;

        if (maxAbs > threshold) {
            targetGain = threshold / maxAbs;
        }

        if (targetGain < gainReduction) {
            gainReduction = attackCoeff * gainReduction + (1.0f - attackCoeff) * targetGain;
        } else {
            gainReduction = releaseCoeff * gainReduction + (1.0f - releaseCoeff) * targetGain;
        }

        l *= gainReduction;
        r *= gainReduction;
    }

private:
    float gainReduction = 1.0f;
    float attackCoeff = 0.0f;
    float releaseCoeff = 0.0f;
};

// ---------------------------------------------------------------------------
// One-Pole Smoother — prevents zipper noise on parameter changes
// ---------------------------------------------------------------------------
class OnePole {
public:
    OnePole() = default;

    /** Set smoothing time in milliseconds */
    void setTime(float ms, float sampleRate) {
        coeff = std::exp(-1.0f / (sampleRate * ms / 1000.0f));
    }

    inline float process(float target) {
        state = coeff * state + (1.0f - coeff) * target;
        return state;
    }

    inline void reset(float value = 0.0f) { state = value; }
    inline float get() const { return state; }

private:
    float coeff = 0.0f;
    float state = 0.0f;
};

// ---------------------------------------------------------------------------
// RMS Follower — ballistic RMS detector, used for adaptive M/S widening
//   attack/release in ms; tracks RMS amplitude of the input signal
// ---------------------------------------------------------------------------
class RmsFollower {
public:
    /** Set attack and release time constants in milliseconds */
    void setTime(float attackMs, float releaseMs, float sampleRate) {
        attackCoeff  = std::exp(-1.0f / (sampleRate * attackMs  / 1000.0f));
        releaseCoeff = std::exp(-1.0f / (sampleRate * releaseMs / 1000.0f));
    }

    inline float process(float x) {
        float xsq  = x * x;
        float coeff = (xsq > state) ? attackCoeff : releaseCoeff;
        state = coeff * state + (1.0f - coeff) * xsq;
        return std::sqrt(state + 1e-12f);
    }

    inline void reset() { state = 0.0f; }

private:
    float state       = 0.0f;
    float attackCoeff  = 0.995f;
    float releaseCoeff = 0.999f;
};

// ---------------------------------------------------------------------------
// Delay Line — with linear interpolation for sub-sample reads
// ---------------------------------------------------------------------------
class DelayLine {
public:
    DelayLine() : writeIndex(0) {}

    void init(int maxDelayFrames) {
        buffer.assign(maxDelayFrames + 1, 0.0f);
        writeIndex = 0;
    }

    void reset() {
        std::fill(buffer.begin(), buffer.end(), 0.0f);
        writeIndex = 0;
    }

    /** Integer-sample read */
    inline float read(int delayFrames) const {
        if (buffer.empty()) return 0.0f;
        int sz = (int)buffer.size();
        delayFrames = std::min(delayFrames, sz - 1);
        int ri = writeIndex - delayFrames - 1;
        if (ri < 0) ri += sz;
        return buffer[ri];
    }

    /** Fractional-sample read (linear interpolation) — for accurate ITD */
    inline float readFrac(float delayFrames) const {
        if (buffer.empty()) return 0.0f;
        int sz = (int)buffer.size();
        int d0 = (int)delayFrames;
        float frac = delayFrames - (float)d0;
        float s0 = read(d0);
        float s1 = read(std::min(d0 + 1, sz - 1));
        return s0 + frac * (s1 - s0);
    }

    inline void write(float sample) {
        if (buffer.empty()) return;
        buffer[writeIndex] = sample;
        writeIndex = (writeIndex + 1) % (int)buffer.size();
    }

    inline int size() const { return (int)buffer.size(); }

private:
    std::vector<float> buffer;
    int writeIndex;
};

// ---------------------------------------------------------------------------
// First-Order Allpass — used inside reverb
// ---------------------------------------------------------------------------
class Allpass1 {
public:
    void init(int delaySamples) {
        dl.init(delaySamples + 1);
        delay = delaySamples;
    }
    void reset() { dl.reset(); }

    inline float process(float input, float g = 0.5f) {
        float delayed = dl.read(delay);
        float feedfwd = input - g * delayed;
        dl.write(feedfwd);
        return delayed + g * feedfwd;
    }

    int delay = 0;
private:
    DelayLine dl;
};

// ---------------------------------------------------------------------------
// Schroeder Comb Filter with lowpass damping — reverb building block
// ---------------------------------------------------------------------------
class CombFilter {
public:
    void init(int delaySamples) {
        dl.init(delaySamples + 1);
        delay = delaySamples;
    }
    void reset() { dl.reset(); filterStore = 0.0f; }

    inline float process(float input, float feedback, float damp) {
        float out = dl.read(delay);
        filterStore = out * (1.0f - damp) + filterStore * damp;
        dl.write(input + filterStore * feedback);
        return out;
    }

    int delay = 0;
private:
    DelayLine dl;
    float filterStore = 0.0f;
};

} // namespace nexus
