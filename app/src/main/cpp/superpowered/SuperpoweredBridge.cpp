#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>

// We wrap the Superpowered includes so that if the SDK isn't present, we can still compile a stub
// to prevent the entire app from failing to build during initial setup.
#if __has_include("Superpowered.h")
#define HAS_SUPERPOWERED 1
#include "Superpowered.h"
#include "SuperpoweredFilter.h"
#include "SuperpoweredSimple.h"
#else
#define HAS_SUPERPOWERED 0
#endif

#define LOG_TAG "SuperpoweredBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex eqMutex;

#if HAS_SUPERPOWERED
static std::vector<Superpowered::Filter*> filters;
static unsigned int currentSamplerate = 44100;
#endif

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_playback_audio_SuperpoweredAudioProcessor_initSuperpowered(JNIEnv *env, jobject thiz, jstring license_key, jint samplerate) {
#if HAS_SUPERPOWERED
    const char *key = env->GetStringUTFChars(license_key, 0);
    Superpowered::Initialize(key, true, false, false, false, false, false, false);
    env->ReleaseStringUTFChars(license_key, key);

    std::lock_guard<std::mutex> lock(eqMutex);
    currentSamplerate = samplerate;

    // Clear old filters if re-initialized
    for (auto* filter : filters) {
        delete filter;
    }
    filters.clear();

    // Default 10 band parametric EQ setup
    for (int i = 0; i < 10; ++i) {
        auto* filter = new Superpowered::Filter(Superpowered::ParametricEQ, currentSamplerate);
        filter->enable(true);
        filters.push_back(filter);
    }
    LOGI("Superpowered initialized successfully at %d Hz", samplerate);
#else
    LOGE("Superpowered SDK headers not found! Please add them to cpp/superpowered_sdk");
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_playback_audio_SuperpoweredAudioProcessor_setEqBand(JNIEnv *env, jobject thiz, jint index, jfloat frequency, jfloat gainDb, jfloat Q) {
#if HAS_SUPERPOWERED
    std::lock_guard<std::mutex> lock(eqMutex);
    if (index >= 0 && index < filters.size()) {
        filters[index]->setParametricEQParameters(frequency, 1.0f, gainDb, Q); // (frequency, octave/Q, gain, width) - API signature might vary slightly by version, assuming standard paramEQ
    }
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_playback_audio_SuperpoweredAudioProcessor_processAudio(JNIEnv *env, jobject thiz, jobject input_buffer, jobject output_buffer, jint num_frames) {
#if HAS_SUPERPOWERED
    short* input = (short*)env->GetDirectBufferAddress(input_buffer);
    short* output = (short*)env->GetDirectBufferAddress(output_buffer);

    if (!input || !output) return;

    // We process num_frames * 2 samples (since stereo)
    // Convert 16-bit short to 32-bit float
    float* floatBuffer = (float*)malloc(num_frames * 2 * sizeof(float));
    Superpowered::ShortIntToFloat(input, floatBuffer, num_frames, 2);

    // Apply EQ sequentially
    std::lock_guard<std::mutex> lock(eqMutex);
    for (auto* filter : filters) {
        filter->process(floatBuffer, floatBuffer, num_frames);
    }

    // Convert back to 16-bit short
    Superpowered::FloatToShortInt(floatBuffer, output, num_frames, 2);
    free(floatBuffer);
#else
    // Stub implementation: just copy input to output
    short* input = (short*)env->GetDirectBufferAddress(input_buffer);
    short* output = (short*)env->GetDirectBufferAddress(output_buffer);
    if (input && output) {
        memcpy(output, input, num_frames * 2 * sizeof(short)); // assuming stereo
    }
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_playback_audio_SuperpoweredAudioProcessor_releaseSuperpowered(JNIEnv *env, jobject thiz) {
#if HAS_SUPERPOWERED
    std::lock_guard<std::mutex> lock(eqMutex);
    for (auto* filter : filters) {
        delete filter;
    }
    filters.clear();
#endif
}
