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
    // Optional "Safe Volume" stage (user opt-in, default off): a per-track loudness-normalization
    // gain (attenuate-only, in (0,1]) + the limiter/soft-clip below, run even when the EQ is off so
    // loud masters are brought toward a reference instead of blasting at full native level.
    bool safeVolumeEnabled = false;
    float safeVolumeGain = 1.0f;
    // De-esser detector envelope (RMS follower over the sibilance band), for smoother, level-relative
    // detection than the old block-peak binary gate.
    float deEsserEnv = 0.0f;
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
        // Threshold below the ceiling so the limiter gain-rides EARLIER and rounds peaks smoothly,
        // instead of the old thresholdDb=0 all-or-nothing catch right at full scale (which forced the
        // tanh soft-clip to mop up = harshness). -3 dB gives gentler, more transparent limiting.
        limiter->thresholdDb = -3.0f;
        limiter->releaseSec = 0.1f; // slightly longer release avoids pumping at the lower threshold
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
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_setEqBand(JNIEnv *env, jobject thiz, jlong ptr, jint index, jfloat frequency, jfloat gainDb, jfloat Q, jint filterType) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;

    std::lock_guard<std::mutex> lock(processor->eqMutex);
    if (index >= 0 && index < (int)processor->filters.size()) {
        Superpowered::Filter* f = processor->filters[index];
        f->frequency = frequency;
        f->decibel = gainDb;
        // filterType matches the Kotlin FilterType mapping: 1 = low shelf, 2 = high shelf, else parametric.
        // Shelves (SDK LowShelf/HighShelf) hold their gain out to DC / Nyquist instead of rolling off like a
        // peak — the correct choice for the lowest/highest graphic-EQ bands (fixes the "edges roll off oddly"
        // and makes the audio match the shelf curve drawn in the UI).
        if (filterType == 1) {
            f->type = Superpowered::Filter::LowShelf;
            f->slope = 0.6f;
        } else if (filterType == 2) {
            f->type = Superpowered::Filter::HighShelf;
            f->slope = 0.6f;
        } else {
            f->type = Superpowered::Filter::Parametric;
            // Convert Q -> octave BANDWIDTH (the SDK's Parametric width unit). Q and octave are different
            // physical quantities; passing Q raw as `octave` made every band wider/more overlapping than
            // designed, and made a high-Q parametric notch (Q=10) turn into the WIDEST filter (octave clamped
            // to 5). BW_oct = (2/ln2)*asinh(1/(2Q)); clamp to the SDK's [0.05, 5].
            float oct = 2.0f;
            if (Q > 0.0001f) oct = (2.0f / logf(2.0f)) * asinhf(1.0f / (2.0f * Q));
            if (oct < 0.05f) oct = 0.05f; else if (oct > 5.0f) oct = 5.0f;
            f->octave = oct;
        }
        f->enabled = true;
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
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_setSafeVolume(JNIEnv *env, jobject thiz, jlong ptr, jboolean enabled, jfloat gainLinear) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;

    std::lock_guard<std::mutex> lock(processor->eqMutex);
    processor->safeVolumeEnabled = (enabled == JNI_TRUE);
    // Attenuate-only guard: never let Safe Volume ADD gain (it only tames loud tracks).
    processor->safeVolumeGain = (gainLinear > 0.0f && gainLinear < 1.0f) ? gainLinear : 1.0f;
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

    // Run the DSP block if the EQ is on OR the optional Safe Volume stage is on. When BOTH are off the
    // block is skipped entirely (pure float pass-through) so default playback stays bit-perfect.
    bool runEq = (enabled == JNI_TRUE);
    bool runChain = runEq || (processor && processor->safeVolumeEnabled);
    if (runChain && workBuffer && processor) {
        std::lock_guard<std::mutex> lock(processor->eqMutex);

        // FRONT gain = EQ preamp (when EQ on) * Safe-Volume normalization gain (when Safe Volume on).
        // Both default to 1.0. Preamp at the front is correct — the limiter below (thresholdDb -3) catches
        // peaks; a positive preamp raises the body audibly. Safe Volume's gain is attenuate-only (<= 1.0),
        // bringing loud masters down toward a reference so they don't blast at full native level.
        float frontGain = processor->currentPreampMultiplier;
        if (processor->safeVolumeEnabled) frontGain *= processor->safeVolumeGain;
        if (frontGain != 1.0f) {
            for (int i = 0; i < num_frames * channels; ++i) {
                workBuffer[i] *= frontGain;
            }
        }

        if (runEq) {
            // Apply EQ bands
            for (auto* filter : processor->filters) {
                if (filter->enabled) {
                    if (channels == 1) filter->processMono(workBuffer, workBuffer, num_frames);
                    else filter->process(workBuffer, workBuffer, num_frames);
                }
            }

            // Dynamic De-Esser (only with EQ on — it exists to tame EQ-boosted sibilance). Improved
            // detection: RMS energy of the 6.5 kHz band through a one-pole envelope (fast attack / slow
            // release), with a PROPORTIONAL cut (up to -5 dB) instead of the old binary -4/0 peak gate —
            // less twitchy, less likely to audibly dull the top on loud bright material.
            if (processor->deEsser && processor->deEsserDetector) {
                memcpy(processor->deEsserBuffer, workBuffer, requiredSize * sizeof(float));
                if (channels == 1) processor->deEsserDetector->processMono(processor->deEsserBuffer, processor->deEsserBuffer, num_frames);
                else processor->deEsserDetector->process(processor->deEsserBuffer, processor->deEsserBuffer, num_frames);

                double sumSq = 0.0;
                for (int i = 0; i < requiredSize; ++i) {
                    float v = processor->deEsserBuffer[i];
                    sumSq += (double)v * v;
                }
                float rms = (requiredSize > 0) ? sqrtf((float)(sumSq / (double)requiredSize)) : 0.0f;
                if (rms > processor->deEsserEnv) processor->deEsserEnv += (rms - processor->deEsserEnv) * 0.5f;
                else processor->deEsserEnv += (rms - processor->deEsserEnv) * 0.1f;

                float t = (processor->deEsserEnv - 0.05f) / 0.15f; // 0 at ~-26 dBFS RMS, 1 at ~-14 dBFS
                if (t < 0.0f) t = 0.0f; else if (t > 1.0f) t = 1.0f;
                float targetDb = -5.0f * t;
                float diff = targetDb - processor->currentDeEsserDb;
                if (diff < 0) processor->currentDeEsserDb += diff * 0.5f; // fast attack
                else processor->currentDeEsserDb += diff * 0.1f;         // slow release
                processor->deEsser->decibel = processor->currentDeEsserDb;

                if (channels == 1) processor->deEsser->processMono(workBuffer, workBuffer, num_frames);
                else processor->deEsser->process(workBuffer, workBuffer, num_frames);
            }
        }

        // Limiter (peak safety) — runs for EQ OR Safe Volume. Stereo only (Superpowered Limiter is stereo).
        if (processor->limiter && processor->limiter->enabled && channels == 2) {
            processor->limiter->process(workBuffer, workBuffer, num_frames);
        }

        // Soft-clip final safety net (rarely fires now that the limiter threshold is -3 dB).
        for (int i = 0; i < num_frames * channels; ++i) {
            float x = workBuffer[i];
            float absX = std::abs(x);
            if (absX > 0.95f) {
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
