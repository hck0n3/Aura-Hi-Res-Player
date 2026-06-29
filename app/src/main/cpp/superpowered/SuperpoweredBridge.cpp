#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>

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
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_initSuperpowered(JNIEnv *env, jobject thiz, jstring license_key, jint samplerate) {
#if HAS_SUPERPOWERED
    const char *key = env->GetStringUTFChars(license_key, 0);
    Superpowered::Initialize(key);
    env->ReleaseStringUTFChars(license_key, key);

    std::lock_guard<std::mutex> lock(eqMutex);
    currentSamplerate = samplerate;

    for (auto* filter : filters) {
        delete filter;
    }
    filters.clear();

    for (int i = 0; i < 10; ++i) {
        auto* filter = new Superpowered::Filter(Superpowered::Filter::Parametric, currentSamplerate);
        filter->enabled = true;
        filters.push_back(filter);
    }
    LOGI("Superpowered initialized successfully at %d Hz", samplerate);
#else
    LOGE("Superpowered SDK headers not found! Please add them to cpp/superpowered_sdk");
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_setEqBand(JNIEnv *env, jobject thiz, jint index, jfloat frequency, jfloat gainDb, jfloat Q) {
#if HAS_SUPERPOWERED
    std::lock_guard<std::mutex> lock(eqMutex);
    if (index >= 0 && index < filters.size()) {
        filters[index]->frequency = frequency;
        filters[index]->octave = Q;
        filters[index]->decibel = gainDb;
    }
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_processAudio(JNIEnv *env, jobject thiz, jobject input_buffer, jobject output_buffer, jint num_frames, jint encoding, jint channels, jboolean enabled) {
#if HAS_SUPERPOWERED
    void* input = env->GetDirectBufferAddress(input_buffer);
    void* output = env->GetDirectBufferAddress(output_buffer);

    if (!input || !output || num_frames <= 0) return;

    if (encoding == 4) { // C.ENCODING_PCM_FLOAT -> FLOAT
        float* inFloat = (float*)input;
        float* outFloat = (float*)output;

        memcpy(outFloat, inFloat, num_frames * channels * sizeof(float));

        if (enabled) {
            std::lock_guard<std::mutex> lock(eqMutex);
            for (auto* filter : filters) {
                if (channels == 1) {
                    filter->processMono(outFloat, outFloat, num_frames);
                } else {
                    filter->process(outFloat, outFloat, num_frames);
                }
            }
        }
    } else { // C.ENCODING_PCM_16BIT -> 16BIT
        short* inShort = (short*)input;
        short* outShort = (short*)output;

        float* floatBuffer = (float*)malloc(num_frames * channels * sizeof(float));
        Superpowered::ShortIntToFloat(inShort, floatBuffer, num_frames, channels);

        if (enabled) {
            std::lock_guard<std::mutex> lock(eqMutex);
            for (auto* filter : filters) {
                if (channels == 1) {
                    filter->processMono(floatBuffer, floatBuffer, num_frames);
                } else {
                    filter->process(floatBuffer, floatBuffer, num_frames);
                }
            }
        }

        Superpowered::FloatToShortInt(floatBuffer, outShort, num_frames, channels);
        free(floatBuffer);
    }
#else
    void* input = env->GetDirectBufferAddress(input_buffer);
    void* output = env->GetDirectBufferAddress(output_buffer);
    if (input && output && num_frames > 0) {
        int bytesPerSample = (encoding == 4) ? 4 : 2;
        memcpy(output, input, num_frames * channels * bytesPerSample);
    }
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_releaseSuperpowered(JNIEnv *env, jobject thiz) {
#if HAS_SUPERPOWERED
    std::lock_guard<std::mutex> lock(eqMutex);
    for (auto* filter : filters) {
        delete filter;
    }
    filters.clear();
#endif
}
