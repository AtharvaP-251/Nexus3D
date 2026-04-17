#include <jni.h>
#include <android/log.h>
#include <memory>
#include "ProcessorChain.h"

#define LOG_TAG "DspNativeBridge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::unique_ptr<nexus::ProcessorChain> gProcessorChain = nullptr;

extern "C"
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    gProcessorChain = std::make_unique<nexus::ProcessorChain>();
    return JNI_VERSION_1_6;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_audixlab_nexus_DspNativeBridge_setDspParameter(JNIEnv *env, jobject thiz, jint param_id, jfloat value) {
    if (gProcessorChain) {
        gProcessorChain->getParameters().set(param_id, value);
    }
}

extern "C"
JNIEXPORT jfloat JNICALL
Java_com_audixlab_nexus_DspNativeBridge_getDspParameter(JNIEnv *env, jobject thiz, jint param_id) {
    if (gProcessorChain) {
        return gProcessorChain->getParameters().get(param_id);
    }
    return 0.0f;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_audixlab_nexus_DspNativeBridge_processAudio(JNIEnv *env, jobject thiz, jobject byte_buffer, jint size) {
    if (!gProcessorChain) return;

    int16_t *buffer = static_cast<int16_t *>(env->GetDirectBufferAddress(byte_buffer));
    if (!buffer) {
        LOGE("Failed to get direct buffer address");
        return;
    }

    int numSamples = size / sizeof(int16_t);
    // Hardcoding sample rate to 48000 for processAudio for now since Exoplayer usually tells the processor the sample rate in config, 
    // but the processor just calls this method. We should eventually pass sampleRate in.
    gProcessorChain->process(buffer, numSamples, 48000); 
}
