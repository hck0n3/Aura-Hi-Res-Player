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
static float currentPreampMultiplier = 1.0f;
static bool isSuperpoweredInitialized = false;
#endif

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_initSuperpowered(JNIEnv *env, jobject thiz, jstring license_key, jint samplerate) {
#if HAS_SUPERPOWERED
    if (!isSuperpoweredInitialized) {
        const char *key = env->GetStringUTFChars(license_key, 0);
        Superpowered::Initialize(key);
        env->ReleaseStringUTFChars(license_key, key);
        isSuperpoweredInitialized = true;
        LOGI("Superpowered initialized globally.");
    }

    std::lock_guard<std::mutex> lock(eqMutex);
    currentSamplerate = samplerate;

    for (auto* filter : filters) {
        delete filter;
    }
    filters.clear();

    // Allocate 64 filters to support 24-band EQ + AutoEQ bands
    for (int i = 0; i < 64; ++i) {
        auto* filter = new Superpowered::Filter(Superpowered::Filter::Parametric, currentSamplerate);
        filter->enabled = false; // Disabled until configured
        filters.push_back(filter);
    }
    LOGI("Superpowered sample rate updated to %d Hz", samplerate);
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
        filters[index]->enabled = true;
    }
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_setPreamp(JNIEnv *env, jobject thiz, jfloat preampDb) {
#if HAS_SUPERPOWERED
    std::lock_guard<std::mutex> lock(eqMutex);
    currentPreampMultiplier = powf(10.0f, preampDb / 20.0f);
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_disableAllBands(JNIEnv *env, jobject thiz) {
#if HAS_SUPERPOWERED
    std::lock_guard<std::mutex> lock(eqMutex);
    for (auto* filter : filters) {
        filter->enabled = false;
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
            
            // Apply preamp
            if (currentPreampMultiplier != 1.0f) {
                for (int i = 0; i < num_frames * channels; ++i) {
                    outFloat[i] *= currentPreampMultiplier;
                }
            }

            for (auto* filter : filters) {
                if (filter->enabled) {
                    if (channels == 1) {
                        filter->processMono(outFloat, outFloat, num_frames);
                    } else {
                        filter->process(outFloat, outFloat, num_frames);
                    }
                }
            }
            
            // Hard clip protection to prevent AudioTrack digital distortion wrap-around
            for (int i = 0; i < num_frames * channels; ++i) {
                if (outFloat[i] > 1.0f) outFloat[i] = 1.0f;
                else if (outFloat[i] < -1.0f) outFloat[i] = -1.0f;
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
