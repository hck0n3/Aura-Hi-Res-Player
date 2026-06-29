#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>
#include <cmath>
#include <cstdlib>

#if __has_include("Superpowered.h")
#define HAS_SUPERPOWERED 1
#include "Superpowered.h"
#include "SuperpoweredFilter.h"
#include "SuperpoweredSimple.h"
#include "SuperpoweredLimiter.h"
#else
#define HAS_SUPERPOWERED 0
#endif

#define LOG_TAG "SuperpoweredBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex eqMutex;

#if HAS_SUPERPOWERED
#define MAX_AUDIO_FRAMES 16384
#define MAX_AUDIO_CHANNELS 2
#define MAX_BUFFER_SIZE (MAX_AUDIO_FRAMES * MAX_AUDIO_CHANNELS)

static std::vector<Superpowered::Filter*> filters;
static Superpowered::Limiter* limiter = nullptr;
static Superpowered::Filter* deEsser = nullptr;
static Superpowered::Filter* deEsserDetector = nullptr;

static float staticConversionBuffer[MAX_BUFFER_SIZE];
static float staticDeEsserBuffer[MAX_BUFFER_SIZE];

static float currentDeEsserDb = 0.0f;
static unsigned int currentSamplerate = 44100;
static float currentPreampMultiplier = 1.0f;
static bool isSuperpoweredInitialized = false;
static int superpoweredInstanceCount = 0;
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

    superpoweredInstanceCount++;
    if (superpoweredInstanceCount == 1) {
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
        
        if (limiter) delete limiter;
        limiter = new Superpowered::Limiter(samplerate);
        limiter->ceilingDb = -1.0f;
        limiter->thresholdDb = 0.0f;
        limiter->releaseSec = 0.05f;
        limiter->enabled = true;

        if (deEsser) delete deEsser;
        deEsser = new Superpowered::Filter(Superpowered::Filter::Parametric, samplerate);
        deEsser->frequency = 6500.0f;
        deEsser->octave = 0.5f;
        deEsser->decibel = 0.0f; // Starts flat
        deEsser->enabled = true;

        if (deEsserDetector) delete deEsserDetector;
        deEsserDetector = new Superpowered::Filter(Superpowered::Filter::Bandlimited_Bandpass, samplerate);
        deEsserDetector->frequency = 6500.0f;
        deEsserDetector->octave = 1.0f;
        deEsserDetector->enabled = true;
        
        currentDeEsserDb = 0.0f;
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

    int requiredSize = num_frames * channels;
    if (requiredSize > MAX_BUFFER_SIZE) {
        LOGE("Audio buffer size %d exceeds MAX_BUFFER_SIZE %d. Aborting.", requiredSize, MAX_BUFFER_SIZE);
        return;
    }

    float* workBuffer = nullptr;

    if (encoding == 4) { // C.ENCODING_PCM_FLOAT -> FLOAT
        float* inFloat = (float*)input;
        float* outFloat = (float*)output;
        memcpy(outFloat, inFloat, requiredSize * sizeof(float));
        workBuffer = outFloat;
    } else { // C.ENCODING_PCM_16BIT -> 16BIT
        short* inShort = (short*)input;
        workBuffer = staticConversionBuffer;
        Superpowered::ShortIntToFloat(inShort, workBuffer, num_frames, channels);
    }

    if (enabled && workBuffer) {
        std::lock_guard<std::mutex> lock(eqMutex);
        
        // Dynamic De-Esser
        if (deEsser && deEsserDetector) {
            // Isolate the 6500Hz band
            memcpy(staticDeEsserBuffer, workBuffer, requiredSize * sizeof(float));
            if (channels == 1) deEsserDetector->processMono(staticDeEsserBuffer, staticDeEsserBuffer, num_frames);
            else deEsserDetector->process(staticDeEsserBuffer, staticDeEsserBuffer, num_frames);
            
            // Measure peak energy
            float peak = 0.0f;
            for (int i = 0; i < requiredSize; ++i) {
                float absVal = std::abs(staticDeEsserBuffer[i]);
                if (absVal > peak) peak = absVal;
            }
            
            // Dynamic threshold calculation
            float targetDb = (peak > 0.15f) ? -4.0f : 0.0f;
            
            // Smooth the gain changes (Envelope follower)
            float diff = targetDb - currentDeEsserDb;
            if (diff < 0) currentDeEsserDb += diff * 0.5f; // Fast attack
            else currentDeEsserDb += diff * 0.05f;         // Slow release
            
            deEsser->decibel = currentDeEsserDb;
            
            // Apply the De-Esser to the main signal
            if (channels == 1) deEsser->processMono(workBuffer, workBuffer, num_frames);
            else deEsser->process(workBuffer, workBuffer, num_frames);
        }

        // Apply preamp
        if (currentPreampMultiplier != 1.0f) {
            for (int i = 0; i < num_frames * channels; ++i) {
                workBuffer[i] *= currentPreampMultiplier;
            }
        }

        // Apply EQ
        for (auto* filter : filters) {
            if (filter->enabled) {
                if (channels == 1) filter->processMono(workBuffer, workBuffer, num_frames);
                else filter->process(workBuffer, workBuffer, num_frames);
            }
        }
        
        // Apply Limiter (strictly stereo)
        if (limiter && limiter->enabled && channels == 2) {
            limiter->process(workBuffer, workBuffer, num_frames);
        }

        // Hard clip mono fallback
        for (int i = 0; i < num_frames * channels; ++i) {
            if (workBuffer[i] > 1.0f) workBuffer[i] = 1.0f;
            else if (workBuffer[i] < -1.0f) workBuffer[i] = -1.0f;
        }
    }

    if (encoding != 4 && workBuffer) {
        short* outShort = (short*)output;
        Superpowered::FloatToShortInt(workBuffer, outShort, num_frames, channels);
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
    if (superpoweredInstanceCount > 0) {
        superpoweredInstanceCount--;
    }
    
    if (superpoweredInstanceCount == 0) {
        for (auto* filter : filters) {
            delete filter;
        }
        filters.clear();
        
        if (limiter) { delete limiter; limiter = nullptr; }
        if (deEsser) { delete deEsser; deEsser = nullptr; }
        if (deEsserDetector) { delete deEsserDetector; deEsserDetector = nullptr; }
    }
#endif
}
