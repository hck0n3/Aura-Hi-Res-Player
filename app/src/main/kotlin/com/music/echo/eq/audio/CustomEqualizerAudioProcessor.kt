package iad1tya.echo.music.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import iad1tya.echo.music.eq.data.FilterType
import iad1tya.echo.music.eq.data.ParametricEQ
import java.nio.ByteBuffer

/**
 * Positive EQ boost (dB) the gentle -3 dBFS limiter can absorb transparently before we trim the preamp.
 * 1.5 dB: keeps every preset's composite peak in the limiter's ~≤2.5 dB gain-reduction (transparent) band
 * on a worst-case 0 dBFS brickwall master — including the adjacent-band summation the single-band max()
 * under-estimates by ~0.9 dB — while costing ~0 loudness on ≤2 dB presets and only ~0.5 dB on bass presets.
 */
private const val EQ_LIMITER_HEADROOM_MARGIN_DB = 1.5

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CustomEqualizerAudioProcessor(private val licenseKey: String = "akloSTZUT1k4N2ZGeGE5N2RhOGU3OWYyOGU4M2RkMGQxOGNmNjA4MDA5MjcwMjNlM2NjNzJoT0R4OGtwem1OamRtSGpFaHFG") : BaseAudioProcessor() {

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
        }
    }

    private var isInitialized = false
    private var enabled = false
    private var nativePtr: Long = 0L
    // Sample rate the native processor was initialized at. If the input format changes to a different rate
    // mid-stream, the native filters are still tuned for the OLD rate (mistuned EQ), so we must re-init.
    private var nativeSampleRate = 0

    private external fun initSuperpowered(licenseKey: String, sampleRate: Int): Long
    private external fun setPreamp(ptr: Long, preampDb: Float)
    private external fun setSafeVolume(ptr: Long, enabled: Boolean, gainLinear: Float)
    private external fun disableAllBands(ptr: Long)
    private external fun setEqBand(ptr: Long, index: Int, frequency: Float, gainDb: Float, q: Float, filterType: Int)
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
            if (isInitialized && nativePtr != 0L) {
                setSafeVolume(nativePtr, enabled, gainLinear)
            }
        }
    }

    fun isEnabled(): Boolean = enabled

    fun disable() {
        enabled = false
        if (isInitialized && nativePtr != 0L) synchronized(eqApplyLock) {
            setPreamp(nativePtr, 0f)
            disableAllBands(nativePtr)
            appliedBandIndices = emptySet()
        }
    }

    fun applyProfile(profile: ParametricEQ) {
        enabled = true
        currentProfile = profile
        // Serialize the native apply + appliedBandIndices bookkeeping: applyProfile runs from BOTH the UI/service
        // thread AND the media3 playback thread (onConfigure's profile restore). Without this lock a race could
        // under-record the enabled slots and let a stale band survive permanently. Rare, non-audio path — the
        // audio hot path (queueInput/processAudio) never takes this lock.
        if (isInitialized && nativePtr != 0L) synchronized(eqApplyLock) {
            // Combine manual bands and auto-correction bands (auto first, then taste — LTI cascade).
            val allBands = profile.autoBands + profile.bands

            // TRUE 0 dB BYPASS: a band at (or within ±0.05 dB of) 0 dB is mathematically flat, so we don't
            // waste a native filter slot on it — it is treated exactly like a disabled band. This makes a
            // flat band a REAL bypass (no biquad in the chain) instead of a 0 dB filter that still processes.
            // NOTE: shelf/peak filters at 0 dB gain are unity, so skipping them is bit-identical in the
            // steady state; the win is fewer active filters (less CPU, no numeric noise from no-op biquads).
            fun bandActive(b: iad1tya.echo.music.eq.data.ParametricEQBand): Boolean =
                b.enabled && kotlin.math.abs(b.gain) >= 0.05

            // The filter slots (indices into allBands) this profile will enable. Flat (≈0 dB) bands are NOT
            // enabled, so they are excluded here too — the stale-slot clearing below (disableAllBands when a
            // previously-enabled slot is no longer in newIndices) then also clears a band that moved TO 0 dB.
            val newIndices = allBands.mapIndexedNotNull { i, b -> if (bandActive(b)) i else null }.toHashSet()

            // ATOMICITY (P48): the native bridge has no batch "apply all bands" call — every JNI call takes the
            // shared eqMutex in its OWN short scope and releases it, so the audio thread (processAudio) can run
            // a block MID-re-apply. The worst transient is the instant right after disableAllBands(), when EVERY
            // filter is off and that block loses all EQ shaping (a dip on preset switch). disableAllBands() is
            // only needed to clear STALE slots — filters the PREVIOUS profile enabled that this one no longer
            // uses. When there are none (the common preset switch where the band layout is unchanged) we skip it
            // and overwrite each band in place, so the audio thread never sees an all-flat block. When a slot
            // must be cleared we fall back to disableAllBands() (unchanged behavior). Both paths converge on the
            // exact same final enabled set + coefficients, so steady-state playback is bit-identical. A fully
            // atomic swap (one lock spanning the whole re-apply, or a coefficient double-buffer) would require a
            // native bridge change and is intentionally out of scope here.
            if (appliedBandIndices.any { it !in newIndices }) {
                disableAllBands(nativePtr)
            }

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
            setPreamp(nativePtr, effectivePreamp.toFloat())

            allBands.forEachIndexed { index, band ->
                if (bandActive(band)) {
                    val typeCode = when (band.filterType) {
                        FilterType.LSC -> 1  // low shelf
                        FilterType.HSC -> 2  // high shelf
                        else -> 0            // peak / parametric
                    }
                    setEqBand(nativePtr, index, band.frequency.toFloat(), band.gain.toFloat(), band.q.toFloat(), typeCode)
                }
            }

            // Record what is now enabled so the NEXT apply knows which slots (if any) must be cleared.
            appliedBandIndices = newIndices
        }
    }

    private var currentProfile: ParametricEQ? = null

    /**
     * Filter-slot indices currently enabled in the native processor (set by the last [applyProfile]). Used to
     * decide whether a re-apply must call [disableAllBands] (only when some previously-enabled slot is no longer
     * used) so we can skip the all-filters-off transient on the common preset switch. Touched only from the UI
     * thread that drives apply/disable — never from the audio thread's queueInput/processAudio path.
     */
    private var appliedBandIndices: Set<Int> = emptySet()

    // Guards the native EQ apply + appliedBandIndices record so applyProfile (called from the UI thread AND the
    // media3 playback thread via onConfigure) is atomic. Re-entrant use from onConfigure is fine. NOT taken by the
    // audio hot path (queueInput/processAudio), so it never blocks audio.
    private val eqApplyLock = Any()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        
        // Re-initialize if pointer was lost or not created. Only touch the native side if the bridge library
        // actually loaded — otherwise initSuperpowered (an external/JNI call) throws an uncaught
        // UnsatisfiedLinkError and crashes playback. When it didn't load we leave nativePtr = 0L /
        // isInitialized = false so queueInput's else-branch passes audio through unmodified (bypass).
        // Whole native (re)init + profile restore under eqApplyLock so the appliedBandIndices reset + re-apply is
        // atomic vs a concurrent UI applyProfile (applyProfile re-enters this lock — re-entrant is fine).
        synchronized(eqApplyLock) {
            // Sample rate changed mid-stream: the native filters are still tuned for the old rate, so drop the
            // stale processor and let the nativePtr == 0L branch below re-init at the new rate.
            if (nativeLibLoaded && nativePtr != 0L && inputAudioFormat.sampleRate != nativeSampleRate) {
                releaseSuperpowered(nativePtr)
                nativePtr = 0L
            }
            if (nativeLibLoaded && nativePtr == 0L) {
                try {
                    nativePtr = initSuperpowered(licenseKey, inputAudioFormat.sampleRate)
                    nativeSampleRate = inputAudioFormat.sampleRate
                    // Fresh native processor: all 64 filters start disabled, so the apply-time bookkeeping that
                    // decides whether disableAllBands() is needed must start clean too.
                    appliedBandIndices = emptySet()
                } catch (e: UnsatisfiedLinkError) {
                    // Defensive: should not happen once nativeLibLoaded is true, but never let it crash playback.
                    e.printStackTrace()
                    nativePtr = 0L
                }
            }
            isInitialized = nativePtr != 0L

            // Restore profile if one was applied
            currentProfile?.let { applyProfile(it) }

            // Restore Safe Volume state (native processor may have just been re-created). Kept INSIDE the lock so
            // it can't race the sample-rate re-init above (which releases + re-creates nativePtr).
            if (isInitialized && nativePtr != 0L && safeVolumeEnabled) {
                setSafeVolume(nativePtr, safeVolumeEnabled, safeVolumeGain)
            }
        }

        // Output format is exactly the same as the input format (pure 32-bit float supported natively)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val bytesPerSample = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val numFrames = remaining / (bytesPerSample * inputAudioFormat.channelCount)
        
        // Output size is identical to input size
        val outRemaining = remaining
        val buffer = replaceOutputBuffer(outRemaining)
        
        if (isInitialized && nativePtr != 0L) {
            val inputSlice = inputBuffer.slice()
            processAudio(nativePtr, inputSlice, buffer, numFrames, inputAudioFormat.encoding, inputAudioFormat.channelCount, enabled)
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
        if (isInitialized && nativePtr != 0L) synchronized(eqApplyLock) {
            releaseSuperpowered(nativePtr)
            nativePtr = 0L
            nativeSampleRate = 0
            isInitialized = false
            appliedBandIndices = emptySet()
        }
        super.onReset()
    }
}
