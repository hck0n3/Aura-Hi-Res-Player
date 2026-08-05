package iad1tya.echo.music.eq.audio

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import iad1tya.echo.music.eq.data.FilterType
import iad1tya.echo.music.eq.data.ParametricEQ
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * Whether the native Superpowered engine is actually altering audio.
 *
 * The Superpowered SDK deliberately gives us no way to ask "is my license key still valid?":
 * `Superpowered::Initialize` returns void, and their agreement states they may disable an
 * out-of-compliance key with or without notice. If that happened, `initSuperpowered` would still hand
 * back a valid pointer and `processAudio` would still be called every block, while the EQ and Safe
 * Volume silently stopped doing anything — a placebo. [HEALTHY] / [DEGRADED] come from an empirical
 * DSP probe run once at init (see `probeSuperpoweredDsp` in SuperpoweredBridge.cpp).
 */
enum class SuperpoweredEngineStatus {
    /** Not initialized yet — no audio has been configured through the processor so far. */
    UNKNOWN,

    /** The probe confirmed the DSP measurably alters audio. */
    HEALTHY,

    /**
     * The engine loaded but the probe proved it is NOT altering audio (license rejection is the likely
     * cause). The processor stops routing audio through it and falls back to a straight bypass.
     */
    DEGRADED,

    /**
     * The engine was never started: the native library or processor is absent, or this build has no
     * licence key it can trust (see [SuperpoweredLicense]). The processor is a straight bypass.
     */
    UNAVAILABLE,
}

/**
 * Positive EQ boost (dB) the gentle -3 dBFS limiter can absorb transparently before we trim the preamp.
 * 1.5 dB: keeps every preset's composite peak in the limiter's ~≤2.5 dB gain-reduction (transparent) band
 * on a worst-case 0 dBFS brickwall master — including the adjacent-band summation the single-band max()
 * under-estimates by ~0.9 dB — while costing ~0 loudness on ≤2 dB presets and only ~0.5 dB on bass presets.
 */
private const val EQ_LIMITER_HEADROOM_MARGIN_DB = 1.5

/**
 * @param context any Context; only the application context is retained, and only so the Superpowered
 *   licence key can be resolved lazily on the playback thread (see [SuperpoweredLicense]). The key
 *   used to be a hardcoded default parameter here, which put a paid commercial credential in the
 *   source of a public repository — the exact thing that gets a key disabled for everyone at once.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CustomEqualizerAudioProcessor(context: Context) : BaseAudioProcessor() {

    /**
     * Application context (never an Activity/Service instance), so holding it for the lifetime of the
     * processor cannot leak anything. Used for exactly one thing: resolving the licence key.
     */
    private val appContext: Context = context.applicationContext

    /**
     * True only if the native "superpowered-bridge" library loaded successfully. If it failed to load,
     * every `external` JNI method below would throw UnsatisfiedLinkError when called. We must NOT call any
     * of them in that case; instead the processor degrades to a transparent bypass (queueInput passes audio
     * through unmodified) rather than crashing playback.
     */
    private var nativeLibLoaded = false

    init {
        try {
            System.loadLibrary("superpowered-bridge")
            nativeLibLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
            // The bridge is gone: every JNI call below would throw, so the processor is a pure bypass.
            // This is certain (not a heuristic), so it is safe for the UI to act on.
            publishEngineStatus(SuperpoweredEngineStatus.UNAVAILABLE)
        }
    }

    /**
     * `@Volatile` for the same reason as [nativePtr]: [queueInput] reads this on the audio thread without
     * [eqApplyLock] and uses it to gate the native calls, while onConfigure/onReset flip it under the lock.
     */
    @Volatile
    private var isInitialized = false

    /**
     * `@Volatile`: written by [applyProfile] / [disable] on the UI thread and read on the audio thread in
     * [queueInput], which passes it straight to the native chain. Without it there is no guarantee the audio
     * thread ever observes an EQ on/off toggle.
     */
    @Volatile
    private var enabled = false

    /**
     * Pointer to the native processor, or 0 when there is none.
     *
     * `@Volatile` because [queueInput] reads it on the audio thread WITHOUT taking [eqApplyLock] (taking
     * a lock there is exactly what we must not do), while [onConfigure] and [onReset] free the object and
     * null this field under the lock. Without volatile that publication is not guaranteed to be visible
     * to the audio thread, which could keep using a pointer to freed memory. Every reader must also copy
     * it into a local ONCE and use that local, so a concurrent null-out cannot make a guarded read and
     * the subsequent JNI call disagree about which pointer they are talking about.
     */
    @Volatile
    private var nativePtr: Long = 0L
    // Sample rate the native processor was initialized at. If the input format changes to a different rate
    // mid-stream, the native filters are still tuned for the OLD rate (mistuned EQ), so we must re-init.
    private var nativeSampleRate = 0

    private external fun initSuperpowered(licenseKey: String, sampleRate: Int): Long

    /**
     * Verdict of the native one-shot DSP probe (see `probeSuperpoweredDsp` in SuperpoweredBridge.cpp).
     * Superpowered::Initialize returns void and the SDK has no "am I licensed?" call, so this empirical
     * probe is the only way to tell a working engine from one whose key has been rejected or disabled.
     */
    private external fun getEngineHealth(): Int

    /**
     * Maps the current native state onto [SuperpoweredEngineStatus]. A missing library or a null
     * processor pointer is [SuperpoweredEngineStatus.UNAVAILABLE] with certainty; otherwise the native
     * probe's verdict decides. Never throws: a failure to even ask counts as UNAVAILABLE.
     */
    private fun readEngineStatus(): SuperpoweredEngineStatus {
        if (!nativeLibLoaded || nativePtr == 0L) return SuperpoweredEngineStatus.UNAVAILABLE
        return try {
            if (getEngineHealth() == NATIVE_ENGINE_HEALTHY) {
                SuperpoweredEngineStatus.HEALTHY
            } else {
                SuperpoweredEngineStatus.DEGRADED
            }
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
            SuperpoweredEngineStatus.UNAVAILABLE
        }
    }
    private external fun setPreamp(ptr: Long, preampDb: Float)
    private external fun setSafeVolume(ptr: Long, enabled: Boolean, gainLinear: Float)
    private external fun disableAllBands(ptr: Long)
    private external fun setEqBand(ptr: Long, index: Int, frequency: Float, gainDb: Float, q: Float, filterType: Int)

    /**
     * Accumulate parameter changes without publishing them. Every [setEqBand] / [setPreamp] /
     * [disableAllBands] / [setSafeVolume] call between [beginEqBatch] and [endEqBatch] lands in a native
     * staging set; the whole thing reaches the audio thread as ONE atomic publication at [endEqBatch].
     *
     * MUST be paired with [endEqBatch] in a `finally`, or the staged changes would never be published.
     */
    private external fun beginEqBatch(ptr: Long)
    private external fun endEqBatch(ptr: Long)
    private external fun processAudio(ptr: Long, inputBuffer: ByteBuffer, outputBuffer: ByteBuffer, numFrames: Int, encoding: Int, channels: Int, enabled: Boolean)
    private external fun releaseSuperpowered(ptr: Long)

    // "Safe Volume" stage (ON by default — see SafeVolumeEnabledKey). Kept so it can be re-applied if the
    // native processor is re-created on format change (onConfigure). gainLinear LEVELS IN BOTH DIRECTIONS:
    // < 1.0 attenuates a loud master, > 1.0 (up to 4.0 = +12 dB) brings a quiet track up. 1.0 = no change.
    //
    // Written and re-read across threads (applySafeVolume from Main, onConfigure from the media3 playback
    // thread), so BOTH assignments live inside eqApplyLock below. They used to sit outside it: harmless while
    // the value could only ever be <= 1.0 (a stale restore was momentarily too QUIET, i.e. inaudible), but a
    // stale restore can now be a +12 dB BOOST — a mid-stream sample-rate change would start the next track
    // hot into the limiter, an audible burst.
    private var safeVolumeEnabled = false
    private var safeVolumeGain = 1f

    /**
     * Enable/disable the Safe Volume stage (per-track loudness normalization + limiter that runs even when
     * the EQ is off). [gainLinear] is the full levelling multiplier for the current track (attenuation x
     * makeup), clamped native-side to (0, 4.0].
     */
    fun applySafeVolume(enabled: Boolean, gainLinear: Float) {
        // Serialize the field writes AND the native call under eqApplyLock: onConfigure's sample-rate re-init
        // can call releaseSuperpowered(nativePtr) and null the pointer mid-stream, so touching nativePtr here
        // without the lock is a use-after-free race — and onConfigure also RESTORES these two fields, so they
        // must not be readable half-updated. The audio hot path (queueInput/processAudio) does NOT take it.
        synchronized(eqApplyLock) {
            safeVolumeEnabled = enabled
            safeVolumeGain = gainLinear
            val ptr = nativePtr
            if (isInitialized && ptr != 0L) {
                setSafeVolume(ptr, enabled, gainLinear)
            }
        }
    }

    fun isEnabled(): Boolean = enabled

    fun disable() {
        enabled = false
        // Guard INSIDE the lock — see applyProfile for why evaluating it outside was a use-after-free window.
        synchronized(eqApplyLock) {
            val ptr = nativePtr
            if (!isInitialized || ptr == 0L) return
            // One atomic publication: the preamp reset and the band clear reach the audio thread together,
            // so no block can run with the preamp already reset while the bands are still boosting.
            beginEqBatch(ptr)
            try {
                setPreamp(ptr, 0f)
                disableAllBands(ptr)
            } finally {
                endEqBatch(ptr)
            }
        }
    }

    fun applyProfile(profile: ParametricEQ) {
        enabled = true
        currentProfile = profile
        // Serialize the native apply: applyProfile runs from BOTH the UI/service thread AND the media3
        // playback thread (onConfigure's profile restore). Rare, non-audio path — the audio hot path
        // (queueInput/processAudio) never takes this lock.
        //
        // The `isInitialized && nativePtr != 0L` guard is evaluated INSIDE the monitor, not outside it.
        // Outside, it was a check-then-act race: onReset/onConfigure run on the media3 playback thread and
        // call releaseSuperpowered under this very lock, so the pointer could be freed between the guard
        // passing and this thread acquiring the monitor. Reading it under the lock into a local, and using
        // only that local, makes the pointer we hand to JNI provably the live one.
        synchronized(eqApplyLock) {
            val ptr = nativePtr
            if (!isInitialized || ptr == 0L) return

            // Combine manual bands and auto-correction bands (auto first, then taste — LTI cascade).
            val allBands = profile.autoBands + profile.bands

            // TRUE 0 dB BYPASS: a band at (or within ±0.05 dB of) 0 dB is mathematically flat, so we don't
            // waste a native filter slot on it — it is treated exactly like a disabled band. This makes a
            // flat band a REAL bypass (no biquad in the chain) instead of a 0 dB filter that still processes.
            // NOTE: shelf/peak filters at 0 dB gain are unity, so skipping them is bit-identical in the
            // steady state; the win is fewer active filters (less CPU, no numeric noise from no-op biquads).
            fun bandActive(b: iad1tya.echo.music.eq.data.ParametricEQBand): Boolean =
                b.enabled && kotlin.math.abs(b.gain) >= 0.05

            // ATOMICITY (P48 — now fully solved). This used to be a sequence of independent JNI calls, each
            // taking and releasing the native lock, so the audio thread could run a block MID-re-apply; the
            // worst transient was the instant right after disableAllBands(), when every filter was off and
            // that block lost all EQ shaping (a dip on preset switch). It was mitigated by tracking which
            // slots were live and skipping disableAllBands() when nothing had to be cleared.
            //
            // The native bridge now stages parameters and publishes them as one indivisible set, so the whole
            // re-apply below is atomic from the audio thread's point of view: it sees either the entire old
            // profile or the entire new one. That makes the bookkeeping unnecessary — we can unconditionally
            // clear every slot first (which is what reliably removes stale bands) with no transient at all.
            beginEqBatch(ptr)
            try {
                disableAllBands(ptr)

                val enabledGains = allBands.filter { bandActive(it) }.map { it.gain }
                // AUTO-HEADROOM with a limiter margin. Only trim the preamp by the positive EQ boost that
                // EXCEEDS what the gentle -3 dBFS limiter can absorb transparently (~2 dB). So small/moderate
                // boosts (most presets) get NO trim → full loudness, the limiter just cleanly rounds their peaks;
                // only large boosts (big bass shelves) are trimmed, and only by the excess — presets stay full
                // AND clip-free (no heavy limiting / pumping). Better than a full -(maxBoost) trim, which wasted
                // the limiter and made boost-heavy presets too quiet. The user's own preamp is preserved when the
                // EQ is flat (maxBoost 0 → no trim).
                val maxBoost = (enabledGains.maxOrNull() ?: 0.0).coerceAtLeast(0.0)
                val trim = (maxBoost - EQ_LIMITER_HEADROOM_MARGIN_DB).coerceAtLeast(0.0)
                val effectivePreamp = profile.preamp - trim
                setPreamp(ptr, effectivePreamp.toFloat())

                allBands.forEachIndexed { index, band ->
                    if (bandActive(band)) {
                        val typeCode = when (band.filterType) {
                            FilterType.LSC -> 1  // low shelf
                            FilterType.HSC -> 2  // high shelf
                            else -> 0            // peak / parametric
                        }
                        setEqBand(ptr, index, band.frequency.toFloat(), band.gain.toFloat(), band.q.toFloat(), typeCode)
                    }
                }
            } finally {
                // Publish even if something above threw, so a failure can never leave the EQ stuck on a
                // half-built set that was staged but never handed to the audio thread.
                endEqBatch(ptr)
            }
        }
    }

    private var currentProfile: ParametricEQ? = null

    // Guards the native EQ apply so applyProfile (called from the UI thread AND the media3 playback thread via
    // onConfigure) is atomic, and so no caller can hand JNI a pointer that onReset/onConfigure has already
    // freed. Re-entrant use from onConfigure is fine. NOT taken by the audio hot path
    // (queueInput/processAudio), so it never blocks audio.
    private val eqApplyLock = Any()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        
        // Resolved BEFORE taking eqApplyLock, and on this thread (media3 playback) rather than in the
        // constructor on the main thread: the first call does a PackageManager binder lookup and writes
        // one line to the on-disk log, neither of which belongs inside a lock the UI thread also takes.
        // Cached process-wide, so every later call is just a volatile read. "" means "no key we trust".
        val licenseKey = if (nativeLibLoaded) SuperpoweredLicense.resolveKey(appContext) else ""

        // Re-initialize if pointer was lost or not created. Only touch the native side if the bridge library
        // actually loaded — otherwise initSuperpowered (an external/JNI call) throws an uncaught
        // UnsatisfiedLinkError and crashes playback. When it didn't load we leave nativePtr = 0L /
        // isInitialized = false so queueInput's else-branch passes audio through unmodified (bypass).
        // Whole native (re)init + profile restore under eqApplyLock so the release/re-create and the re-apply are
        // atomic vs a concurrent UI applyProfile (applyProfile re-enters this lock — re-entrant is fine).
        synchronized(eqApplyLock) {
            // Sample rate changed mid-stream: the native filters are still tuned for the old rate, so drop the
            // stale processor and let the nativePtr == 0L branch below re-init at the new rate.
            val stalePtr = nativePtr
            if (nativeLibLoaded && stalePtr != 0L && inputAudioFormat.sampleRate != nativeSampleRate) {
                // Null the field BEFORE freeing (see onReset): queueInput reads it on the audio thread
                // without this lock, so the pointer must stop being reachable before it stops being valid.
                nativePtr = 0L
                isInitialized = false
                releaseSuperpowered(stalePtr)
            }
            if (nativeLibLoaded && nativePtr == 0L) {
                // The engine has already been PROVEN inert once in this process (see below); re-creating a
                // native processor we are only going to throw away again is pure waste.
                val engineProvenInert = _engineStatus.value == SuperpoweredEngineStatus.DEGRADED

                if (licenseKey.isEmpty() || engineProvenInert) {
                    // SAFETY NET. No trustworthy licence key (a clone, a fork's build, or a signing
                    // certificate we could not read) means we do NOT start the engine at all. An
                    // unlicensed Superpowered engine is not contractually obliged to pass audio through
                    // cleanly, whereas leaving nativePtr at 0 is: queueInput's else-branch copies the
                    // bytes untouched. Bit-identical audio beats a maybe-silent, maybe-distorted engine.
                    nativePtr = 0L
                    pendingEngineStatus = if (engineProvenInert) {
                        SuperpoweredEngineStatus.DEGRADED
                    } else {
                        SuperpoweredEngineStatus.UNAVAILABLE
                    }
                } else {
                    try {
                        nativePtr = initSuperpowered(licenseKey, inputAudioFormat.sampleRate)
                        nativeSampleRate = inputAudioFormat.sampleRate
                    } catch (e: UnsatisfiedLinkError) {
                        // Defensive: should not happen once nativeLibLoaded is true, but never let it crash playback.
                        e.printStackTrace()
                        nativePtr = 0L
                    }
                    // Read the verdict here (it needs nativePtr, which is guarded by this lock) but PUBLISH it
                    // after the lock: publishing appends to the on-disk log, and file I/O has no business
                    // happening while the audio-configuration lock is held.
                    val verdict = readEngineStatus()
                    if (verdict == SuperpoweredEngineStatus.DEGRADED) {
                        // The probe proved the engine is NOT altering audio — the signature of a licence
                        // that was rejected or disabled. It is inert either way, so stop routing audio
                        // through it: the bypass is guaranteed clean, the engine is not. This is what makes
                        // the "el ecualizador no está sonando" banner literally true instead of optimistic.
                        val inertPtr = nativePtr
                        nativePtr = 0L
                        nativeSampleRate = 0
                        if (inertPtr != 0L) releaseSuperpowered(inertPtr)
                    }
                    pendingEngineStatus = verdict
                }
            }
            isInitialized = nativePtr != 0L

            // Restore profile if one was applied
            currentProfile?.let { applyProfile(it) }

            // Restore Safe Volume state (native processor may have just been re-created). Kept INSIDE the lock so
            // it can't race the sample-rate re-init above (which releases + re-creates nativePtr).
            val restorePtr = nativePtr
            if (isInitialized && restorePtr != 0L && safeVolumeEnabled) {
                setSafeVolume(restorePtr, safeVolumeEnabled, safeVolumeGain)
            }
        }

        // Outside eqApplyLock on purpose — see where pendingEngineStatus is set.
        pendingEngineStatus?.let {
            pendingEngineStatus = null
            publishEngineStatus(it)
        }

        // Output format is exactly the same as the input format (pure 32-bit float supported natively)
        return inputAudioFormat
    }

    /**
     * Engine verdict read under [eqApplyLock] but published (and logged to disk) outside it.
     * Confined to the media3 playback thread inside a single onConfigure call, so it needs no
     * synchronization of its own.
     */
    private var pendingEngineStatus: SuperpoweredEngineStatus? = null

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val bytesPerSample = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val channels = inputAudioFormat.channelCount
        val bytesPerFrame = bytesPerSample * channels
        val numFrames = if (bytesPerFrame > 0) remaining / bytesPerFrame else 0

        // Output size is identical to input size
        val outRemaining = remaining
        val buffer = replaceOutputBuffer(outRemaining)

        // Read the @Volatile pointer ONCE and use only this local for the whole block. Reading the field
        // repeatedly would let onReset/onConfigure null it between the guard and a JNI call, so a single
        // block could start processing with a live pointer and finish with a freed one.
        val ptr = nativePtr
        if (isInitialized && ptr != 0L) {
            // The bridge's native scratch arrays are fixed at NATIVE_MAX_SAMPLES interleaved samples; a
            // block bigger than that is emitted UNPROCESSED (no EQ, no Safe Volume — see the
            // MAX_BUFFER_SIZE guard in SuperpoweredBridge.cpp). That was unreachable while only 16-bit
            // 44.1/48 kHz material reached the chain, but hi-res raw PCM blocks can exceed it, which would
            // make the EQ flicker on and off block by block. Feed oversize blocks in native-sized slices
            // instead: the biquads are stateful and strictly sequential, so N sequential calls produce the
            // same samples as one big call would have. The common (in-range) case below is untouched.
            val maxFramesPerCall = if (channels > 0) NATIVE_MAX_SAMPLES / channels else numFrames
            if (numFrames <= maxFramesPerCall) {
                val inputSlice = inputBuffer.slice()
                processAudio(ptr, inputSlice, buffer, numFrames, inputAudioFormat.encoding, channels, enabled)
            } else {
                val inStart = inputBuffer.position()
                val inLimit = inputBuffer.limit()
                var frameOffset = 0
                while (frameOffset < numFrames) {
                    val chunkFrames = minOf(maxFramesPerCall, numFrames - frameOffset)
                    val byteOffset = frameOffset * bytesPerFrame
                    val chunkBytes = chunkFrames * bytesPerFrame
                    // slice() is what makes GetDirectBufferAddress point at the chunk (it reads the
                    // buffer's BASE address, not its position), so both sides must be re-sliced per chunk.
                    inputBuffer.limit(inStart + byteOffset + chunkBytes)
                    inputBuffer.position(inStart + byteOffset)
                    buffer.limit(byteOffset + chunkBytes)
                    buffer.position(byteOffset)
                    processAudio(
                        ptr,
                        inputBuffer.slice(),
                        buffer.slice(),
                        chunkFrames,
                        inputAudioFormat.encoding,
                        channels,
                        enabled,
                    )
                    frameOffset += chunkFrames
                }
                // Restore both buffers to exactly the state the single-call path leaves them in, so the
                // shared position bookkeeping below stays correct.
                inputBuffer.limit(inLimit)
                inputBuffer.position(inStart)
                buffer.limit(buffer.capacity())
                buffer.position(0)
            }
            // JNI writes directly to memory, so we must advance the ByteBuffer's position manually
            buffer.position(buffer.position() + outRemaining)
            inputBuffer.position(inputBuffer.position() + remaining)
        } else {
            // Uninitialized fallback: copy all bytes manually, which automatically advances positions for both
            buffer.put(inputBuffer)
        }

        buffer.flip()
    }

    override fun onReset() {
        // Guard INSIDE the lock. Evaluated outside, two threads could both pass it and both call
        // releaseSuperpowered on the same pointer — a double free. Re-reading under the monitor means
        // the second one sees 0 and does nothing.
        synchronized(eqApplyLock) {
            val ptr = nativePtr
            if (!isInitialized || ptr == 0L) return@synchronized
            // Null the field BEFORE freeing, so a concurrent queueInput on the audio thread (which reads
            // this @Volatile field without the lock) can never pick up a pointer that is about to die.
            nativePtr = 0L
            nativeSampleRate = 0
            isInitialized = false
            releaseSuperpowered(ptr)
        }
        super.onReset()
    }

    companion object {
        /**
         * MUST mirror `MAX_BUFFER_SIZE` in `app/src/main/cpp/superpowered/SuperpoweredBridge.cpp`
         * (`MAX_AUDIO_FRAMES * MAX_AUDIO_CHANNELS`) — the size of the native scratch arrays, in
         * interleaved samples. A `processAudio` call with `numFrames * channels` above this is passed
         * through unprocessed by the bridge, so [queueInput] never issues one.
         */
        private const val NATIVE_MAX_SAMPLES = 16384 * 2

        /** MUST mirror the `SP_ENGINE_*` constants in SuperpoweredBridge.cpp. */
        private const val NATIVE_ENGINE_HEALTHY = 1

        private val _engineStatus = MutableStateFlow(SuperpoweredEngineStatus.UNKNOWN)

        /**
         * Live availability of the native Superpowered engine, so the EQ UI can refuse to present a
         * control that does nothing. Process-global on purpose: the engine is initialized once per
         * process, and the answer is the same for every processor instance.
         */
        val engineStatus: StateFlow<SuperpoweredEngineStatus> = _engineStatus.asStateFlow()

        /**
         * Records the engine verdict once and traces it at a level [iad1tya.echo.music.utils.AppLogger]
         * persists (INFO and above), so a shared log answers "was the DSP actually licensed and running
         * on this device?" without anyone having to reproduce the problem. The license key itself is
         * NEVER logged — only the verdict.
         */
        private fun publishEngineStatus(status: SuperpoweredEngineStatus) {
            if (_engineStatus.value == status) return
            _engineStatus.value = status
            when (status) {
                SuperpoweredEngineStatus.HEALTHY ->
                    Timber.i("SUPERPOWERED engine=HEALTHY dsp_probe=passed")
                SuperpoweredEngineStatus.DEGRADED ->
                    Timber.e(
                        "SUPERPOWERED engine=DEGRADED dsp_probe=failed — the native engine loaded but is " +
                            "NOT altering audio. Most likely cause: the Superpowered license key was " +
                            "rejected or disabled. Audio has been routed AROUND it (untouched passthrough); " +
                            "the EQ and Safe Volume do nothing.",
                    )
                SuperpoweredEngineStatus.UNAVAILABLE ->
                    Timber.e(
                        "SUPERPOWERED engine=UNAVAILABLE — the engine was never started (native bridge or " +
                            "processor missing, or no usable license key for this build); the audio " +
                            "processor is a straight bypass. The EQ and Safe Volume do nothing.",
                    )
                SuperpoweredEngineStatus.UNKNOWN -> Unit
            }
        }
    }
}
