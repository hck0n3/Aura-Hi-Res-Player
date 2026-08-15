#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <cstdlib>
#include <algorithm>

#if __has_include("Superpowered.h")
#define HAS_SUPERPOWERED 1
#include "Superpowered.h"
#include "SuperpoweredFilter.h"
#include "SuperpoweredSimple.h"
#include "SuperpoweredLimiter.h"
#include "SuperpoweredSpatializer.h"
#include "SuperpoweredReverb.h"
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

// Engine health, decided ONCE right after Superpowered::Initialize() by an empirical DSP probe.
// Superpowered::Initialize() returns void and the SDK exposes no "am I licensed?" query, so a rejected
// or disabled license key is otherwise completely silent: initSuperpowered still returns a valid
// pointer and processAudio still runs, while the EQ and Safe Volume quietly stop altering the audio.
// The probe below answers the question the SDK refuses to: does the DSP actually change audio?
// Values are mirrored by ENGINE_* in CustomEqualizerAudioProcessor.kt.
#define SP_ENGINE_UNKNOWN  0
#define SP_ENGINE_HEALTHY  1
#define SP_ENGINE_DEGRADED 2
static int superpoweredEngineHealth = SP_ENGINE_UNKNOWN;

/// Empirical proof-of-life for the Superpowered DSP.
///
/// Pushes a 1 kHz sine through a throwaway +12 dB parametric filter centred on that exact frequency and
/// checks the output actually got louder. A licensed, working engine multiplies the level by ~4x; an
/// engine that has been disabled, silenced or turned into a pass-through does not. The margin is
/// enormous (4.0 expected vs a 1.05 threshold), so this cannot realistically false-alarm on a healthy
/// engine, and it catches silence, pass-through and NaN/garbage output alike.
///
/// Runs exactly once per process, on a throwaway Filter instance — it never touches the EQ's own filters,
/// the limiter, the user's audio or any tuned DSP parameter. Cost is ~8k samples through one biquad
/// (tens of microseconds, once), so it is thermally and battery-wise free.
static bool probeSuperpoweredDsp(unsigned int samplerate) {
    // Defensive: a nonsense sample rate would make the probe tone meaningless and could report a
    // healthy engine as broken. Refusing to judge is always better than a false accusation here,
    // because this verdict can grey out the user's EQ.
    if (samplerate < 8000 || samplerate > 768000) {
        LOGI("Superpowered DSP probe skipped: implausible sample rate %u Hz.", samplerate);
        return true;
    }

    const unsigned int frames = 256;   // multiple of 4, >= 64, as the SDK recommends
    const unsigned int blocks = 16;    // let the filter's parameter smoothing settle before measuring
    const float probeHz = 1000.0f;
    const float amplitude = 0.25f;     // well clear of full scale, so a +12 dB boost cannot clip

    Superpowered::Filter probe(Superpowered::Filter::Parametric, samplerate);
    probe.frequency = probeHz;
    probe.octave = 1.0f;
    probe.decibel = 12.0f;
    probe.enabled = true;

    std::vector<float> in(frames * 2), out(frames * 2);
    const double step = 2.0 * M_PI * (double)probeHz / (double)samplerate;
    double phase = 0.0;
    bool processed = false;
    double rmsIn = 0.0, rmsOut = 0.0;

    for (unsigned int b = 0; b < blocks; ++b) {
        for (unsigned int i = 0; i < frames; ++i) {
            const float s = amplitude * (float)sin(phase);
            phase += step;
            in[i * 2] = s;
            in[i * 2 + 1] = s;
        }
        // Zero the output each block so a no-op process() (which leaves output untouched) reads as
        // silence rather than as whatever the previous block happened to leave behind.
        std::fill(out.begin(), out.end(), 0.0f);
        processed = probe.process(in.data(), out.data(), frames);

        if (b == blocks - 1) { // measure only the settled block
            for (unsigned int i = 0; i < frames * 2; ++i) {
                rmsIn += (double)in[i] * (double)in[i];
                rmsOut += (double)out[i] * (double)out[i];
            }
            rmsIn = sqrt(rmsIn / (double)(frames * 2));
            rmsOut = sqrt(rmsOut / (double)(frames * 2));
        }
    }

    if (!processed) {
        LOGE("Superpowered DSP probe: process() returned false - engine is not producing output.");
        return false;
    }
    if (!std::isfinite(rmsOut) || !std::isfinite(rmsIn) || rmsIn <= 0.0) {
        LOGE("Superpowered DSP probe: non-finite or empty signal - engine output is unusable.");
        return false;
    }
    const double ratio = rmsOut / rmsIn;
    if (ratio < 1.05) {
        LOGE("Superpowered DSP probe FAILED: +12 dB boost produced a level ratio of only %.3f "
             "(expected ~4.0). The engine is not processing audio - the license key is the likely cause.",
             ratio);
        return false;
    }
    LOGI("Superpowered DSP probe passed: level ratio %.3f (expected ~4.0).", ratio);
    return true;
}

// Native filter slots: 24-band EQ + AutoEQ bands.
#define NUM_EQ_FILTERS 64

/// One EQ band's COMPLETE parameter set, as a plain value type.
/// Data only — it never references a Superpowered object, so it can be copied freely between threads.
struct BandParams {
    float frequency = 1000.0f;
    float decibel = 0.0f;
    float octave = 2.0f;
    float slope = 0.6f;
    Superpowered::Filter::FilterType type = Superpowered::Filter::Parametric;
    bool enabled = false;
};

/// Everything the audio thread needs in order to configure the DSP for a block. Published as ONE
/// indivisible unit (see the triple buffer below), so the audio thread can never act on a mixture of
/// an old and a new configuration.
struct EqSnapshot {
    BandParams bands[NUM_EQ_FILTERS];
    float preampMultiplier = 1.0f;
    bool safeVolumeEnabled = false;
    float safeVolumeGain = 1.0f;
    /// Spatial stage (opt-in). Disabled by default so EQ-only playback stays bit-identical in this
    /// branch. Parameter layout is owned by Kotlin SpatialAudioProfile.toNativeParams().
    bool spatialEnabled = false;
    int spatialAlgorithm = 0; // 0 HRTF, 1 crossfeed, 2 speaker M/S
    float azL = 330.0f;
    float azR = 30.0f;
    float elL = 0.0f;
    float elR = 0.0f;
    float rearAzL = 250.0f;
    float rearAzR = 110.0f;
    float rearEl = 0.0f;
    float phantomGain = 0.0f;
    float phantomDelayMs = 12.0f;
    float reverbMix = 0.0f;
    float reverbWidth = 1.0f;
    float reverbDamp = 0.5f;
    float reverbRoomSize = 0.8f;
    float reverbPredelayMs = 0.0f;
    float reverbLowCutHz = 0.0f;
    bool sound2 = false;
    float inputVolume = 1.0f;
    float crossfeedAmt = 0.32f;
    float speakerWidth = 1.0f;
    /// Bumped by writers ONLY when a band actually changed (setEqBand / disableAllBands). Lets the audio
    /// thread skip re-writing all 64 filters for a publication that only moved a scalar — most importantly
    /// the per-track Safe Volume update, which must not touch EQ coefficients at all.
    uint32_t bandsRevision = 0;
};

class SuperpoweredProcessor {
public:
    std::vector<Superpowered::Filter*> filters;
    Superpowered::Limiter* limiter = nullptr;
    Superpowered::Filter* deEsser = nullptr;
    Superpowered::Filter* deEsserDetector = nullptr;

    Superpowered::Spatializer* spatFrontL = nullptr;
    Superpowered::Spatializer* spatFrontR = nullptr;
    Superpowered::Spatializer* spatRearL = nullptr;
    Superpowered::Spatializer* spatRearR = nullptr;
    Superpowered::Reverb* spatialRoom = nullptr;

    float spInL[MAX_AUDIO_FRAMES];
    float spInR[MAX_AUDIO_FRAMES];
    float spOutL[MAX_AUDIO_FRAMES];
    float spOutR[MAX_AUDIO_FRAMES];
    float spDelayL[2048];
    float spDelayR[2048];
    int spDelayPos = 0;
    float xfLpL = 0.0f;
    float xfLpR = 0.0f;

    float conversionBuffer[MAX_BUFFER_SIZE];
    float deEsserBuffer[MAX_BUFFER_SIZE];

    unsigned int currentSamplerate = 44100;

    // ------------------------------------------------------------------------------------------
    // AUDIO-THREAD-OWNED STATE. Touched ONLY from processAudio. No other thread may read or write
    // it, so it needs no synchronization of any kind.
    // ------------------------------------------------------------------------------------------
    float currentDeEsserDb = 0.0f;
    // De-esser detector envelope (RMS follower over the sibilance band), for smoother, level-relative
    // detection than the old block-peak binary gate.
    float deEsserEnv = 0.0f;
    // TARGET gain (what Kotlin asked for, mirrored into activeSafeVolumeGain by the snapshot) and the
    // CURRENT gain actually being applied. They differ only while ramping — see the Safe Volume stage in
    // processAudio. Stepping straight to a new target is what makes a mid-song gain change audible as a
    // jump, and now that the gain can BOOST (not just attenuate) those steps can be +16 dB or more: the
    // sanctioned "real loudness arrived within 8 s" upgrade goes from the unknown-default attenuation
    // straight to a boosted value, and toggling Safe Volume on is an instant jump from unity. Ramping
    // makes every one of them inaudible.
    float safeVolumeGainCurrent = 1.0f;
    // The most recently APPLIED snapshot values (the audio thread's private working copy).
    float activePreampMultiplier = 1.0f;
    // Optional "Safe Volume" stage (user opt-in): a per-track loudness-normalization gain + the
    // limiter/soft-clip below, run even when the EQ is off so loud masters are brought toward a
    // reference instead of blasting at full native level.
    bool activeSafeVolumeEnabled = false;
    float activeSafeVolumeGain = 1.0f;
    bool activeSpatialEnabled = false;
    int activeSpatialAlgorithm = 0;
    float activeAzL = 330.0f;
    float activeAzR = 30.0f;
    float activeElL = 0.0f;
    float activeElR = 0.0f;
    float activeRearAzL = 250.0f;
    float activeRearAzR = 110.0f;
    float activeRearEl = 0.0f;
    float activePhantomGain = 0.0f;
    float activePhantomDelayMs = 12.0f;
    float activeReverbMix = 0.0f;
    float activeReverbWidth = 1.0f;
    float activeReverbDamp = 0.5f;
    float activeReverbRoomSize = 0.8f;
    float activeReverbPredelayMs = 0.0f;
    float activeReverbLowCutHz = 0.0f;
    bool activeSound2 = false;
    float activeInputVolume = 1.0f;
    float activeCrossfeedAmt = 0.32f;
    float activeSpeakerWidth = 1.0f;
    /// bandsRevision of the snapshot whose band parameters are currently loaded into the filter objects.
    uint32_t appliedBandsRevision = 0;

    // ------------------------------------------------------------------------------------------
    // LOCK-FREE PARAMETER PUBLICATION  (UI / service threads  ->  audio thread)
    //
    // The audio thread MUST NOT take a lock. A UI thread preempted by the scheduler while holding it
    // stalls the audio callback for as long as it takes to be scheduled again, which is a dropout —
    // and the UI hammers these setters at slider-drag rate, so the window is hit often.
    //
    // But the audio thread must ALSO never observe a half-updated coefficient set: a torn read of a
    // filter's parameters (new frequency against an old type, say) is an audible CLICK. That is what
    // the old mutex bought, and it may not be given up.
    //
    // Both are satisfied by a three-slot single-producer/single-consumer buffer:
    //
    //  * The three slot indices {0,1,2} are permanently partitioned between `writerSlot` (owned by
    //    writers), the index parked inside `readySlot`, and `audioSlot` (owned by the audio thread).
    //    Both handovers are a SINGLE atomic exchange, so an index can never be duplicated or lost.
    //    A writer therefore can never write the slot the audio thread is reading, and vice versa —
    //    with no lock between them and no possibility of the reader waiting.
    //
    //  * A writer fills its private slot with a COMPLETE set, then release-exchanges its index into
    //    `readySlot`. The audio thread acquire-loads `readySlot` and, only if the dirty bit is set,
    //    acquire-exchanges its own index in. That acquire synchronizes-with the writer's release, so
    //    every store into the slot happens-before the audio thread's reads of it. The audio thread
    //    sees the whole new set or the whole previous one — never a mixture.
    //
    // The release/acquire pairing is load-bearing, not decoration. Every shipped ABI is ARM
    // (armeabi-v7a / arm64-v8a), which is WEAKLY ORDERED: with a plain field, a `volatile`, or a
    // RELAXED atomic, both the compiler and the CPU are free to make the flag store visible before
    // the coefficient stores that precede it, and the audio thread would then apply a mixture of the
    // two sets — exactly the click this design exists to prevent.
    //
    // Cost on the audio thread: one acquire load per block, plus one exchange only on blocks where
    // parameters actually changed. No allocation, no waiting, no CAS retry loop — wait-free.
    // Writers still serialize among THEMSELVES on `writerMutex`; the audio thread never touches it.
    // ------------------------------------------------------------------------------------------
    static constexpr uint32_t kSlotMask = 0x3u;
    static constexpr uint32_t kDirtyFlag = 0x4u;

    EqSnapshot slots[3];
    // Own cache line: keeps the audio thread's per-block acquire load off any line a writer is dirtying.
    alignas(64) std::atomic<uint32_t> readySlot{0};

    int writerSlot = 1;      // owned by writers (only valid under writerMutex)
    int audioSlot = 2;       // owned by the audio thread
    EqSnapshot staging;      // writer-side accumulator (only valid under writerMutex)
    std::mutex writerMutex;  // writers only — the audio thread NEVER takes this
    int batchDepth = 0;      // >0 while a multi-call apply is being accumulated

    /// Publish `staging` as the new complete set. Caller MUST hold writerMutex.
    void publishLocked() {
        slots[writerSlot] = staging;
        const uint32_t prev =
                readySlot.exchange((uint32_t) writerSlot | kDirtyFlag, std::memory_order_acq_rel);
        writerSlot = (int) (prev & kSlotMask);
    }

    /// Publish unless we are inside a begin/endEqBatch pair. Caller MUST hold writerMutex.
    void publishIfNotBatchingLocked() {
        if (batchDepth == 0) publishLocked();
    }

    /// AUDIO THREAD ONLY. Picks up a newly published set, if any, and applies it to the DSP objects.
    ///
    /// Applying happens HERE, on the audio thread, rather than in the JNI setters, because
    /// SuperpoweredFilter.h:32 requires it: "Changing the filter type often involves changing other
    /// parameters as well. Therefore in a real-time context change the parameters and the type in the
    /// same thread with the process() call." Individual property writes are documented as safe from
    /// any thread (SuperpoweredFilter.h:49) but a type + companion-parameter change is NOT, and this
    /// EQ changes type (peak <-> shelf) together with slope/octave. Doing the whole apply on the
    /// process() thread satisfies the header's stronger requirement.
    void consumeAndApplySnapshot() {
        if ((readySlot.load(std::memory_order_acquire) & kDirtyFlag) == 0) return;
        const uint32_t prev = readySlot.exchange((uint32_t) audioSlot, std::memory_order_acq_rel);
        audioSlot = (int) (prev & kSlotMask);

        const EqSnapshot& s = slots[audioSlot];

        // Scalars are cheap and always refreshed.
        activePreampMultiplier = s.preampMultiplier;
        activeSafeVolumeEnabled = s.safeVolumeEnabled;
        activeSafeVolumeGain = s.safeVolumeGain;
        activeSpatialEnabled = s.spatialEnabled;
        activeSpatialAlgorithm = s.spatialAlgorithm;
        activeAzL = s.azL;
        activeAzR = s.azR;
        activeElL = s.elL;
        activeElR = s.elR;
        activeRearAzL = s.rearAzL;
        activeRearAzR = s.rearAzR;
        activeRearEl = s.rearEl;
        activePhantomGain = s.phantomGain;
        activePhantomDelayMs = s.phantomDelayMs;
        activeReverbMix = s.reverbMix;
        activeReverbWidth = s.reverbWidth;
        activeReverbDamp = s.reverbDamp;
        activeReverbRoomSize = s.reverbRoomSize;
        activeReverbPredelayMs = s.reverbPredelayMs;
        activeReverbLowCutHz = s.reverbLowCutHz;
        activeSound2 = s.sound2;
        activeInputVolume = s.inputVolume;
        activeCrossfeedAmt = s.crossfeedAmt;
        activeSpeakerWidth = s.speakerWidth;
        applySpatializerProperties();

        // Filter objects are touched ONLY when a band genuinely changed. A publication caused purely by
        // Safe Volume (which happens on every track) therefore leaves the EQ's coefficients completely
        // alone, exactly as before this change.
        if (s.bandsRevision == appliedBandsRevision) return;
        appliedBandsRevision = s.bandsRevision;

        for (int i = 0; i < NUM_EQ_FILTERS; ++i) {
            Superpowered::Filter* f = filters[i];
            const BandParams& b = s.bands[i];
            f->frequency = b.frequency;
            f->decibel = b.decibel;
            f->type = b.type;
            // Write only the width parameter that is EFFECTIVE for this type, exactly as the old
            // per-call code did. SuperpoweredFilter.h:21-23 documents the effective parameters as
            // frequency/slope/decibel for LowShelf+HighShelf and frequency/octave/decibel for
            // Parametric, so the other one is ignored — but not writing it keeps this byte-for-byte
            // equivalent to the previous behaviour and leaves no room for doubt.
            if (b.type == Superpowered::Filter::LowShelf || b.type == Superpowered::Filter::HighShelf) {
                f->slope = b.slope;
            } else {
                f->octave = b.octave;
            }
            f->enabled = b.enabled;
        }
    }

    static float clampf(float v, float lo, float hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    void configureOneSpatializer(Superpowered::Spatializer* sp, float azimuth, float elevation) {
        if (!sp) return;
        sp->samplerate = currentSamplerate;
        sp->azimuth = clampf(azimuth, 0.0f, 360.0f);
        sp->elevation = clampf(elevation, -90.0f, 90.0f);
        sp->reverbmix = 0.0f; // global Spatializer reverb is process-wide; we use a per-processor Reverb
        sp->occlusion = 0.0f;
        sp->sound2 = activeSound2;
        sp->inputVolume = clampf(activeInputVolume, 0.05f, 1.5f);
    }

    void applySpatializerProperties() {
        if (!activeSpatialEnabled || activeSpatialAlgorithm != 0) return;
        configureOneSpatializer(spatFrontL, activeAzL, activeElL);
        configureOneSpatializer(spatFrontR, activeAzR, activeElR);
        configureOneSpatializer(spatRearL, activeRearAzL, activeRearEl);
        configureOneSpatializer(spatRearR, activeRearAzR, activeRearEl);
        if (spatialRoom) {
            spatialRoom->samplerate = currentSamplerate;
            spatialRoom->width = clampf(activeReverbWidth, 0.0f, 1.0f);
            spatialRoom->damp = clampf(activeReverbDamp, 0.0f, 1.0f);
            spatialRoom->roomSize = clampf(activeReverbRoomSize, 0.0f, 1.0f);
            spatialRoom->predelayMs = clampf(activeReverbPredelayMs, 0.0f, 500.0f);
            spatialRoom->lowCutHz = clampf(activeReverbLowCutHz, 0.0f, 2000.0f);
            spatialRoom->mix = clampf(activeReverbMix, 0.0f, 0.45f);
            spatialRoom->enabled = activeReverbMix > 0.001f;
        }
    }

    void processCrossfeed(float* interleaved, int frames) {
        const float amt = clampf(activeCrossfeedAmt, 0.0f, 0.6f);
        const float dry = 1.0f - amt;
        const float a = 1.0f - expf(-2.0f * 3.14159265f * 700.0f / (float) currentSamplerate);
        for (int i = 0; i < frames; ++i) {
            float L = interleaved[i * 2];
            float R = interleaved[i * 2 + 1];
            xfLpL += a * (R - xfLpL);
            xfLpR += a * (L - xfLpR);
            interleaved[i * 2] = L * dry + xfLpL * amt;
            interleaved[i * 2 + 1] = R * dry + xfLpR * amt;
        }
    }

    void processSpeakerMs(float* interleaved, int frames) {
        const float width = clampf(activeSpeakerWidth, 0.85f, 1.45f);
        if (fabsf(width - 1.0f) < 0.001f) return;
        Superpowered::StereoToMidSide(interleaved, interleaved, (unsigned int) frames);
        for (int i = 0; i < frames; ++i) {
            interleaved[i * 2 + 1] *= width;
        }
        Superpowered::MidSideToStereo(interleaved, interleaved, (unsigned int) frames);
    }

    void processHrtfChunk(float* interleaved, int frames) {
        Superpowered::DeInterleave(interleaved, spInL, spInR, (unsigned int) frames);
        bool okL = spatFrontL && spatFrontL->process(spInL, spInL, spOutL, spOutR, (unsigned int) frames, false);
        bool okR = spatFrontR && spatFrontR->process(spInR, spInR, spOutL, spOutR, (unsigned int) frames, true);
        if (!okL && !okR) return;

        if (activePhantomGain > 0.001f && spatRearL && spatRearR) {
            const int delayLen = (int) clampf(activePhantomDelayMs * (float) currentSamplerate / 1000.0f, 1.0f, 2047.0f);
            for (int i = 0; i < frames; ++i) {
                int read = spDelayPos - delayLen;
                if (read < 0) read += 2048;
                float dL = spDelayL[read] * activePhantomGain;
                float dR = spDelayR[read] * activePhantomGain;
                spDelayL[spDelayPos] = spInL[i];
                spDelayR[spDelayPos] = spInR[i];
                spDelayPos = (spDelayPos + 1) & 2047;
                spInL[i] = dL;
                spInR[i] = dR;
            }
            spatRearL->process(spInL, spInL, spOutL, spOutR, (unsigned int) frames, true);
            spatRearR->process(spInR, spInR, spOutL, spOutR, (unsigned int) frames, true);
        }
        Superpowered::Interleave(spOutL, spOutR, interleaved, (unsigned int) frames);
        if (spatialRoom && activeReverbMix > 0.001f) {
            spatialRoom->process(interleaved, interleaved, (unsigned int) frames);
        }
    }

    void processSpatial(float* interleaved, int frames) {
        if (!activeSpatialEnabled || frames < 64) return;
        switch (activeSpatialAlgorithm) {
            case 1:
                processCrossfeed(interleaved, frames);
                return;
            case 2:
                processSpeakerMs(interleaved, frames);
                return;
            default:
                break;
        }
        int offset = 0;
        while (offset < frames) {
            int chunk = frames - offset;
            if (chunk > 8192) chunk = 8192;
            if (chunk < 64) break;
            processHrtfChunk(interleaved + offset * 2, chunk);
            offset += chunk;
        }
    }

    SuperpoweredProcessor(unsigned int samplerate) : currentSamplerate(samplerate) {
        // Allocate 64 filters to support 24-band EQ + AutoEQ bands
        for (int i = 0; i < NUM_EQ_FILTERS; ++i) {
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
        limiter->releaseSec = 0.05f; // slightly longer release avoids pumping at the lower threshold
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

        spatFrontL = new Superpowered::Spatializer(samplerate);
        spatFrontR = new Superpowered::Spatializer(samplerate);
        spatRearL = new Superpowered::Spatializer(samplerate);
        spatRearR = new Superpowered::Spatializer(samplerate);
        spatialRoom = new Superpowered::Reverb(samplerate);
        spatialRoom->enabled = false;
        spatialRoom->mix = 0.0f;
        memset(spDelayL, 0, sizeof(spDelayL));
        memset(spDelayR, 0, sizeof(spDelayR));
    }

    ~SuperpoweredProcessor() {
        for (auto* filter : filters) {
            delete filter;
        }
        filters.clear();
        
        if (limiter) { delete limiter; limiter = nullptr; }
        if (deEsser) { delete deEsser; deEsser = nullptr; }
        if (deEsserDetector) { delete deEsserDetector; deEsserDetector = nullptr; }
        if (spatFrontL) { delete spatFrontL; spatFrontL = nullptr; }
        if (spatFrontR) { delete spatFrontR; spatFrontR = nullptr; }
        if (spatRearL) { delete spatRearL; spatRearL = nullptr; }
        if (spatRearR) { delete spatRearR; spatRearR = nullptr; }
        if (spatialRoom) { delete spatialRoom; spatialRoom = nullptr; }
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
            // NEVER log `key` (or any prefix/length of it) - it is a credential and the app log is
            // user-shareable. Only the pass/fail VERDICT below is ever recorded.
            Superpowered::Initialize(key);
            env->ReleaseStringUTFChars(license_key, key);
            isSuperpoweredInitialized = true;
            superpoweredEngineHealth =
                    probeSuperpoweredDsp((unsigned int) samplerate) ? SP_ENGINE_HEALTHY
                                                                    : SP_ENGINE_DEGRADED;
            LOGI("Superpowered initialized globally. Engine health: %s",
                 superpoweredEngineHealth == SP_ENGINE_HEALTHY ? "HEALTHY" : "DEGRADED");
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

/// Result of the one-shot DSP probe run at Initialize time. See SP_ENGINE_* above.
/// Returns SP_ENGINE_UNKNOWN if the SDK is absent or initSuperpowered has not run yet.
extern "C" JNIEXPORT jint JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_getEngineHealth(JNIEnv *env, jobject thiz) {
#if HAS_SUPERPOWERED
    std::lock_guard<std::mutex> lock(globalInitMutex);
    return (jint) superpoweredEngineHealth;
#else
    return (jint) 0;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_setEqBand(JNIEnv *env, jobject thiz, jlong ptr, jint index, jfloat frequency, jfloat gainDb, jfloat Q, jint filterType) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;

    std::lock_guard<std::mutex> lock(processor->writerMutex);
    if (index >= 0 && index < NUM_EQ_FILTERS) {
        // Stage the band's parameters. They reach the DSP when the set is published (below, or at
        // endEqBatch) and are applied by the audio thread itself — see consumeAndApplySnapshot.
        BandParams& b = processor->staging.bands[index];
        b.frequency = frequency;
        b.decibel = gainDb;
        // filterType matches the Kotlin FilterType mapping: 1 = low shelf, 2 = high shelf, else parametric.
        // Shelves (SDK LowShelf/HighShelf) hold their gain out to DC / Nyquist instead of rolling off like a
        // peak — the correct choice for the lowest/highest graphic-EQ bands (fixes the "edges roll off oddly"
        // and makes the audio match the shelf curve drawn in the UI).
        if (filterType == 1) {
            b.type = Superpowered::Filter::LowShelf;
            b.slope = 0.6f;
        } else if (filterType == 2) {
            b.type = Superpowered::Filter::HighShelf;
            b.slope = 0.6f;
        } else {
            b.type = Superpowered::Filter::Parametric;
            // Convert Q -> octave BANDWIDTH (the SDK's Parametric width unit). Q and octave are different
            // physical quantities; passing Q raw as `octave` made every band wider/more overlapping than
            // designed, and made a high-Q parametric notch (Q=10) turn into the WIDEST filter (octave clamped
            // to 5). BW_oct = (2/ln2)*asinh(1/(2Q)); clamp to the SDK's [0.05, 5].
            float oct = 2.0f;
            if (Q > 0.0001f) oct = (2.0f / logf(2.0f)) * asinhf(1.0f / (2.0f * Q));
            if (oct < 0.05f) oct = 0.05f; else if (oct > 5.0f) oct = 5.0f;
            b.octave = oct;
        }
        b.enabled = true;
        processor->staging.bandsRevision++;
    }
    processor->publishIfNotBatchingLocked();
#endif
}

/// Begin accumulating parameter changes WITHOUT publishing them. Every setter called until the
/// matching endEqBatch lands in the staging set only, so the whole profile reaches the audio thread
/// as ONE atomic publication.
///
/// This also removes a long-standing artifact: applyProfile used to issue disableAllBands + setPreamp
/// + N x setEqBand as separate locked calls, so the audio thread could run a block in the middle of
/// the sequence — most audibly right after disableAllBands, when every filter was off and that block
/// lost all EQ shaping (a dip on preset switch). Batched, no such intermediate state is ever visible.
/// Nesting is counted, and Kotlin calls endEqBatch from a finally block, so a throw mid-apply can
/// never leave the EQ permanently unpublished.
extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_beginEqBatch(JNIEnv *env, jobject thiz, jlong ptr) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;

    std::lock_guard<std::mutex> lock(processor->writerMutex);
    processor->batchDepth++;
#endif
}

/// Close a beginEqBatch and publish the accumulated set (when the outermost batch closes).
extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_endEqBatch(JNIEnv *env, jobject thiz, jlong ptr) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;

    std::lock_guard<std::mutex> lock(processor->writerMutex);
    if (processor->batchDepth > 0) processor->batchDepth--;
    if (processor->batchDepth == 0) processor->publishLocked();
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_setPreamp(JNIEnv *env, jobject thiz, jlong ptr, jfloat preampDb) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;
    
    std::lock_guard<std::mutex> lock(processor->writerMutex);
    processor->staging.preampMultiplier = powf(10.0f, preampDb / 20.0f);
    processor->publishIfNotBatchingLocked();
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_setSafeVolume(JNIEnv *env, jobject thiz, jlong ptr, jboolean enabled, jfloat gainLinear) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;

    std::lock_guard<std::mutex> lock(processor->writerMutex);
    processor->staging.safeVolumeEnabled = (enabled == JNI_TRUE);
    // Safe Volume levels in BOTH directions: attenuate loud masters AND bring quiet tracks up toward the
    // reference, which is what makes a library play at a consistent volume. This used to clamp to < 1.0,
    // silently discarding every boost — so the feature only ever turned things DOWN, and a quiet track
    // stayed quiet forever. (The Kotlin side computed the makeup correctly but handed it to a dead stub
    // processor, so nothing applied it either; both halves are fixed together.)
    //
    // Boosting is safe here because the limiter below runs for Safe Volume too (ceiling -1 dBFS,
    // threshold -3 dB) and catches the resulting peaks — that is the documented design. The ceiling
    // matches loudnessMakeupDb's +12 dB cap; anything beyond that is a bug upstream, not a louder track.
    // CLAMP, never fall back to unity: an out-of-range value must become a bounded gain, not a sudden jump
    // to 1.0 (that would be an audible level change if the cap upstream ever moves). NaN fails both
    // comparisons and lands on 1.0, which is the correct safe default.
    const float kMaxSafeVolumeGain = 4.0f; // +12 dB, matching loudnessMakeupDb's cap
    processor->staging.safeVolumeGain =
        (gainLinear > 0.0f) ? ((gainLinear > kMaxSafeVolumeGain) ? kMaxSafeVolumeGain : gainLinear) : 1.0f;
    processor->publishIfNotBatchingLocked();
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_setSpatial(JNIEnv *env, jobject thiz, jlong ptr, jboolean enabled, jint algorithm, jfloatArray params) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;
    std::lock_guard<std::mutex> lock(processor->writerMutex);
    processor->staging.spatialEnabled = (enabled == JNI_TRUE);
    processor->staging.spatialAlgorithm = algorithm;
    if (params != nullptr) {
        const jsize n = env->GetArrayLength(params);
        if (n >= 19) {
            jfloat* p = env->GetFloatArrayElements(params, nullptr);
            if (p) {
                processor->staging.azL = p[0];
                processor->staging.azR = p[1];
                processor->staging.elL = p[2];
                processor->staging.elR = p[3];
                processor->staging.rearAzL = p[4];
                processor->staging.rearAzR = p[5];
                processor->staging.rearEl = p[6];
                processor->staging.phantomGain = p[7];
                processor->staging.phantomDelayMs = p[8];
                processor->staging.reverbMix = p[9];
                processor->staging.reverbWidth = p[10];
                processor->staging.reverbDamp = p[11];
                processor->staging.reverbRoomSize = p[12];
                processor->staging.reverbPredelayMs = p[13];
                processor->staging.reverbLowCutHz = p[14];
                processor->staging.sound2 = p[15] > 0.5f;
                processor->staging.inputVolume = p[16];
                processor->staging.crossfeedAmt = p[17];
                processor->staging.speakerWidth = p[18];
                env->ReleaseFloatArrayElements(params, p, JNI_ABORT);
            }
        }
    }
    processor->publishIfNotBatchingLocked();
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_disableAllBands(JNIEnv *env, jobject thiz, jlong ptr) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    if (!processor) return;
    
    std::lock_guard<std::mutex> lock(processor->writerMutex);
    for (int i = 0; i < NUM_EQ_FILTERS; ++i) {
        processor->staging.bands[i].enabled = false;
    }
    processor->staging.bandsRevision++;
    processor->publishIfNotBatchingLocked();
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_processAudio(JNIEnv *env, jobject thiz, jlong ptr, jobject input_buffer, jobject output_buffer, jint num_frames, jint encoding, jint channels, jboolean enabled) {
#if HAS_SUPERPOWERED
    auto* processor = reinterpret_cast<SuperpoweredProcessor*>(ptr);
    
    void* input = env->GetDirectBufferAddress(input_buffer);
    void* output = env->GetDirectBufferAddress(output_buffer);

    if (!input || !output || num_frames <= 0) return;

    // Wait-free parameter intake. One acquire load; the exchange + apply only run on blocks where the
    // UI actually published something new. Done BEFORE any decision that depends on EQ / Safe Volume
    // state so a freshly published set takes effect on this very block. No lock is taken here — see
    // the publication contract on SuperpoweredProcessor.
    if (processor) processor->consumeAndApplySnapshot();

    int requiredSize = num_frames * channels;
    if (requiredSize > MAX_BUFFER_SIZE) {
        // Oversized block: the fixed native scratch arrays (conversionBuffer/deEsserBuffer) can't hold
        // it, so the DSP chain can't run. Do NOT bare-return: media3 reuses the same output ByteBuffer
        // and the caller always flips/publishes it, so returning without writing republishes the
        // previous block's PCM (or uninitialized memory) as an audible click/burst. Emit a bit-exact
        // pass-through instead — this rare block is played unprocessed (no EQ/Safe Volume) but clean.
        // Same layout as the #else fallback below: 4 bytes/sample float (encoding==4) else 2 (16-bit),
        // for num_frames*channels interleaved samples. memcpy on already-mapped direct buffers is
        // allocation-free and safe on the audio thread.
        LOGE("Audio buffer size %d exceeds MAX_BUFFER_SIZE %d. Passing through unprocessed.", requiredSize, MAX_BUFFER_SIZE);
        int bytesPerSample = (encoding == 4) ? 4 : 2;
        memcpy(output, input, (size_t)requiredSize * bytesPerSample);
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

    // Run the DSP block if the EQ is on, Safe Volume is on (or still ramping), OR spatial is on.
    // When ALL of those are off the block is skipped (pure float pass-through) so default playback
    // stays bit-perfect.
    bool runEq = (enabled == JNI_TRUE);
    // Also run while Safe Volume is ramping BACK to unity after being switched off — otherwise disabling it
    // would skip the chain outright and drop the gain in one step, the very jump the ramp exists to avoid.
    bool runSpatial = processor && processor->activeSpatialEnabled;
    bool runChain = runEq ||
        (processor && (processor->activeSafeVolumeEnabled || processor->safeVolumeGainCurrent != 1.0f)) ||
        runSpatial;
    if (runChain && workBuffer && processor) {
        // NO LOCK HERE. Parameters were taken in above via consumeAndApplySnapshot, which is wait-free
        // and delivers a complete set or nothing at all. Everything read below is either that applied
        // set or audio-thread-private state.

        // FRONT gain = the EQ preamp ONLY. The limiter below (thresholdDb -3) catches its peaks, and a
        // positive preamp raises the body audibly.
        //
        // Safe Volume is deliberately NOT folded in here — it runs as its own stage AFTER the EQ bands
        // (see below). Putting it at the front compounds with the band gains, and the preamp
        // auto-headroom in CustomEqualizerAudioProcessor (which trims the preamp by the positive EQ boost
        // on the premise that the limiter absorbs ~1.5 dB transparently) has no knowledge of it — so a
        // quiet track's makeup, a preamp and a boost preset would stack straight into the limiter. That
        // is worst exactly on DYNAMIC masters (classical/jazz/hi-res): low integrated loudness means a
        // large makeup while peaks already sit near full scale, so the limiter would ride ~10 dB of gain
        // reduction on every transient — the "saturation / boxy / pumping" complaint on record.
        float frontGain = processor->activePreampMultiplier * 0.7943f; // -2.0 dB headroom
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

        // SAFE VOLUME — its own stage, AFTER the EQ bands and immediately BEFORE the limiter, so the limiter
        // sees the levelled signal and the EQ's preamp auto-headroom stays valid (nothing it doesn't know
        // about is added upstream of the bands).
        //
        // Levels in BOTH directions: attenuates loud masters and brings quiet ones up toward the reference,
        // which is what makes a library play at one consistent volume. The makeup side is capped at +3 dB
        // upstream (loudnessMakeupDb) — deliberately NOT the historical +12, which was validated against a
        // STATIC tanh knee that cannot pump, whereas the limiter below rides gain with a 0.1 s release.
        //
        // Applied through a RAMP, never as a step: the target can change mid-song (the sanctioned
        // real-loudness-arrived upgrade, or the user toggling Safe Volume), and an instantaneous multiplier
        // change is exactly the audible level jump this feature exists to avoid.
        if (processor->activeSafeVolumeEnabled || processor->safeVolumeGainCurrent != 1.0f) {
            float target = processor->activeSafeVolumeEnabled ? processor->activeSafeVolumeGain : 1.0f;
            // MONO: never amplify. The limiter below is stereo-only, so a boost here would have nothing but
            // the 0.95 tanh knee between it and audible distortion — and that knee is a hard clipper above
            // ~1.5x, not a limiter. ATTENUATION stays allowed (it cannot clip), so a loud mono master is
            // still tamed; only the makeup is withheld. Stereo — what this player is actually used for —
            // gets the full two-way levelling.
            //
            // NOTE this clamps ONLY the Safe Volume gain. An earlier version clamped the combined FRONT gain,
            // which silently swallowed the user's EQ preamp on mono (a +3 dB preamp became 0 dB) — a change
            // to EQ behaviour that was never in scope. Keeping the stages separate makes that impossible.
            if (channels != 2 && target > 1.0f) target = 1.0f;

            float cur = processor->safeVolumeGainCurrent;
            if (cur != target) {
                // ~300 ms one-pole glide at 48 kHz; per-BLOCK so the cost is one lerp, not one per sample.
                const float kGlide = 0.15f;
                cur += (target - cur) * kGlide;
                if (std::abs(target - cur) < 1e-4f) cur = target; // settle exactly, no dangling epsilon
                processor->safeVolumeGainCurrent = cur;
            }

            if (cur != 1.0f) {
                for (int i = 0; i < num_frames * channels; ++i) {
                    workBuffer[i] *= cur;
                }
            }
        }

        // SPATIAL — after EQ / Safe Volume so the HRTF (or crossfeed / speaker width) sees the
        // levelled signal, and BEFORE the limiter so peaks from summing virtual sources are caught.
        // Stereo only: a Spatializer instance is a 3D point source feeding two ears.
        if (processor->activeSpatialEnabled && channels == 2 && workBuffer) {
            processor->processSpatial(workBuffer, num_frames);
        }

        // Limiter (peak safety) — runs for EQ OR Safe Volume. Stereo only (Superpowered Limiter is stereo);
        // mono relies on the soft-clip knee below, which runs for every channel count.
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
