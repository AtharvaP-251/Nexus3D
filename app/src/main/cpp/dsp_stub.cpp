#include <jni.h>
#include <android/log.h>
#include <string>

#define LOG_TAG "DspNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Global parameter for gain (simulating a live parameter update)
static float g_gain = 1.0f;

extern "C"
JNIEXPORT void JNICALL
Java_com_nexus_nexus3d_DspNativeBridge_setGain(JNIEnv *env, jobject thiz, jfloat gain) {
    g_gain = gain;
    LOGD("Gain updated to: %f", g_gain);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_nexus_nexus3d_DspNativeBridge_processAudio(JNIEnv *env, jobject thiz, jobject byte_buffer, jint size) {
    // Media3 AudioProcessors work directly with DirectByteBuffers wrapping PCM data.
    // Assuming 16-bit PCM for this prototype (ExoPlayer's default).
    
    // Get the direct buffer memory
    int16_t *buffer = static_cast<int16_t *>(env->GetDirectBufferAddress(byte_buffer));
    if (!buffer) return;

    // process samples
    int num_samples = size / sizeof(int16_t);
    float current_gain = g_gain; // atomic snapshot in real code, simple copy here
    
    for (int i = 0; i < num_samples; i++) {
        // Apply gain and clip
        float sample = static_cast<float>(buffer[i]) * current_gain;
        
        // Hard clipping for safety
        if (sample > 32767.0f) {
            sample = 32767.0f;
        } else if (sample < -32768.0f) {
            sample = -32768.0f;
        }
        
        buffer[i] = static_cast<int16_t>(sample);
    }
}
