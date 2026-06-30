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

#if HAS_SUPERPOWERED
#define MAX_AUDIO_FRAMES 16384
#define MAX_AUDIO_CHANNELS 2
#define MAX_BUFFER_SIZE (MAX_AUDIO_FRAMES * MAX_AUDIO_CHANNELS)

static std::mutex globalInitMutex;
static bool isSuperpoweredInitialized = false;

class SuperpoweredProcessor {
public:
    std::vector<Superpowered::Filter*> filters;
    Superpowered::Limiter* limiter = nullptr;
    Superpowered::Filter* deEsser = nullptr;
    Superpowered::Filter* deEsserDetector = nullptr;

    float conversionBuffer[MAX_BUFFER_SIZE];
    float deEsserBuffer[MAX_BUFFER_SIZE];

    float currentDeEsserDb = 0.0f;
    unsigned int currentSamplerate = 44100;
    float currentPreampMultiplier = 1.0f;
    std::mutex eqMutex;

    SuperpoweredProcessor(unsigned int samplerate) : currentSamplerate(samplerate) {
        // Allocate 64 filters to support 24-band EQ + AutoEQ bands
        for (int i = 0; i < 64; ++i) {
            auto* filter = new Superpowered::Filter(Superpowered::Filter::Parametric, currentSamplerate);
            filter->enabled = false; // Disabled until configured
            filters.push_back(filter);
        }
        
        limiter = new Superpowered::Limiter(samplerate);
        limiter->ceilingDb = -1.0f;
        limiter->thresholdDb = 0.0f;
        limiter->releaseSec = 0.05f;
        limiter->enabled = true;

        deEsser = new Superpowered::Filter(Superpowered::Filter::Parametric, samplerate);
        deEsser->frequency = 6500.0f;
        deEsser->octave = 0.5f;
        deEsser->decibel = 0.0f; // Starts flat
        deEsser->enabled = true;

        deEsserDetector = new Superpowered::Filter(Superpowered::Filter::Bandlimited_Bandpass, samplerate);
        deEsserDetector->frequency = 6500.0f;
        deEsserDetector->octave = 1.0f;
        deEsserDetector->enabled = true;
    }

    ~SuperpoweredProcessor() {
        for (auto* filter : filters) {
            delete filter;
        }
        filters.clear();
        
        if (limiter) { delete limiter; limiter = nullptr; }
        if (deEsser) { delete deEsser; deEsser = nullptr; }
        if (deEsserDetector) { delete deEsserDetector; deEsserDetector = nullptr; }
    }
};
#endif

extern "C" JNIEXPORT jlong JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_initSuperpowered(JNIEnv *env, jobject thiz, jstring license_key, jint samplerate) {
#if HAS_SUPERPOWERED
    {
        std::lock_guard<std::mutex> lock(globalInitMutex);
        if (!isSuperpoweredInitialized) {
            const char *key = env->GetStringUTFChars(license_key, 0);
            Superpowered::Initialize(key);
            env->ReleaseStringUTFChars(license_key, key);
            isSuperpoweredInitialized = true;
            LOGI("Superpowered initialized globally.");
        }
    }

    auto* processor = new SuperpoweredProcessor(samplerate);
    LOGI("Superpowered processor instantiated for sample rate %d Hz", samplerate);
    return reinterpret_cast<jlong>(processor);
#else
    LOGE("Superpowered SDK headers not found! Please add them to cpp/superpowered_sdk");
    return 0L;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_setEqBand(JNIEnv *env, jobject thiz, jlong ptr, jint index, jfloat frequency, jfloat gainDb, jfloat Q) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;
    
    std::lock_guard<std::mutex> lock(processor->eqMutex);
    if (index >= 0 && index < processor->filters.size()) {
        processor->filters[index]->frequency = frequency;
        processor->filters[index]->octave = Q;
        processor->filters[index]->decibel = gainDb;
        processor->filters[index]->enabled = true;
    }
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_setPreamp(JNIEnv *env, jobject thiz, jlong ptr, jfloat preampDb) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;
    
    std::lock_guard<std::mutex> lock(processor->eqMutex);
    processor->currentPreampMultiplier = powf(10.0f, preampDb / 20.0f);
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_disableAllBands(JNIEnv *env, jobject thiz, jlong ptr) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;
    
    std::lock_guard<std::mutex> lock(processor->eqMutex);
    for (auto* filter : processor->filters) {
        filter->enabled = false;
    }
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_processAudio(JNIEnv *env, jobject thiz, jlong ptr, jobject input_buffer, jobject output_buffer, jint num_frames, jint encoding, jint channels, jboolean enabled) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    
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
        if (processor) {
            workBuffer = processor->conversionBuffer;
            Superpowered::ShortIntToFloat(inShort, workBuffer, num_frames, channels);
        } else {
            return;
        }
    }

    if (enabled && workBuffer && processor) {
        std::lock_guard<std::mutex> lock(processor->eqMutex);
        
        // Apply preamp at the FRONT of the chain. This is the correct, safe place: the limiter below uses
        // thresholdDb = 0 dBFS (see SuperpoweredLimiter.h — it only limits signal that EXCEEDS the threshold),
        // so a positive preamp raises the whole track's body (e.g. -14 LUFS -> -8 LUFS) and ONLY true peaks
        // above 0 dBFS get caught — the boost stays audible while peaks stay safe under the -1 dBFS ceiling.
        // (Applying preamp AFTER the limiter instead multiplied an already -1 dBFS-capped signal past full
        // scale and slammed the soft-clip = over-volume/saturation. Never do that.)
        if (processor->currentPreampMultiplier != 1.0f) {
            for (int i = 0; i < num_frames * channels; ++i) {
                workBuffer[i] *= processor->currentPreampMultiplier;
            }
        }

        // Apply EQ first
        for (auto* filter : processor->filters) {
            if (filter->enabled) {
                if (channels == 1) filter->processMono(workBuffer, workBuffer, num_frames);
                else filter->process(workBuffer, workBuffer, num_frames);
            }
        }
        
        // Dynamic De-Esser (Applied AFTER EQ to catch sibilance introduced by treble boosts)
        if (processor->deEsser && processor->deEsserDetector) {
            // Isolate the 6500Hz band
            memcpy(processor->deEsserBuffer, workBuffer, requiredSize * sizeof(float));
            if (channels == 1) processor->deEsserDetector->processMono(processor->deEsserBuffer, processor->deEsserBuffer, num_frames);
            else processor->deEsserDetector->process(processor->deEsserBuffer, processor->deEsserBuffer, num_frames);
            
            // Measure peak energy
            float peak = 0.0f;
            for (int i = 0; i < requiredSize; ++i) {
                float absVal = std::abs(processor->deEsserBuffer[i]);
                if (absVal > peak) peak = absVal;
            }
            
            // Dynamic threshold calculation
            float targetDb = (peak > 0.15f) ? -4.0f : 0.0f;
            
            // Smooth the gain changes (Envelope follower)
            float diff = targetDb - processor->currentDeEsserDb;
            if (diff < 0) processor->currentDeEsserDb += diff * 0.5f; // Fast attack
            else processor->currentDeEsserDb += diff * 0.05f;         // Slow release
            
            processor->deEsser->decibel = processor->currentDeEsserDb;
            
            // Apply the De-Esser to the main signal
            if (channels == 1) processor->deEsser->processMono(workBuffer, workBuffer, num_frames);
            else processor->deEsser->process(workBuffer, workBuffer, num_frames);
        }
        
        // Apply Limiter (strictly stereo)
        if (processor->limiter && processor->limiter->enabled && channels == 2) {
            processor->limiter->process(workBuffer, workBuffer, num_frames);
        }

        // Soft clip fallback (prevents harsh digital clipping)
        for (int i = 0; i < num_frames * channels; ++i) {
            float x = workBuffer[i];
            float absX = std::abs(x);
            if (absX > 0.95f) {
                // Soft knee above 0.95 to safely round off peaks instead of hard-chopping
                float out = 0.95f + std::tanh(absX - 0.95f) * 0.05f;
                workBuffer[i] = (x > 0) ? out : -out;
            }
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
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_releaseSuperpowered(JNIEnv *env, jobject thiz, jlong ptr) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (processor) {
        delete processor;
    }
#endif
}
