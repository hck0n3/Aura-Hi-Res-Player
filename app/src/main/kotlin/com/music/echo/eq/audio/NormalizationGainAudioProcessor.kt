package iad1tya.echo.music.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

/**
 * First stage of the player chain: applies an attenuate-only loudness-normalization gain
 * ([gain] in (0, 1]) so loud masters are brought down without ever boosting into clipping.
 * Gain is set per-track by MusicService from [normalizationMultiplier]; 1.0 = transparent passthrough.
 *
 * Replaces the old boost-only Android `LoudnessEnhancer`, which could only raise level (and clip).
 *
 * The applied gain RAMPS toward the target over a fixed ~time (independent of sample rate, advanced once
 * per frame so both channels stay matched), so a mid-song change — the real loudness arriving a moment
 * after playback starts — glides in instead of a sudden volume jump/click. A freshly (re)configured
 * instance (a new track, the crossfade player, a seek) is PRIMED straight to the target so it starts at
 * the right level instead of swelling into it; only an in-place target change actually ramps.
 */
@UnstableApi
class NormalizationGainAudioProcessor : AudioProcessor {

    private var encoding = C.ENCODING_INVALID
    private var channelCount = 0
    private var isActive = false
    private var inputEnded = false
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER

    // The gain actually applied right now; glides toward [gain] (the target) by [rampStep] per frame.
    private var currentGain: Float = 1.0f
    // Max gain change per frame, derived from the sample rate so the ramp lasts ~RAMP_SECONDS regardless
    // of rate. Recomputed in configure(); the default suits 48 kHz until then.
    private var rampStep: Float = 1.0f / (48_000f * RAMP_SECONDS)
    // False until the first buffer after (re)configure/flush: that buffer snaps currentGain to the target
    // (no startup swell); subsequent target changes ramp.
    private var primed = false

    /** Per-instance override of the shared [gain]. When non-null it takes precedence over the companion
     *  default, so the main player and the crossfade secondary player can apply different normalization
     *  gains during a crossfade. Null → use the shared companion [gain]. */
    @Volatile
    var instanceGain: Float? = null

    // ──────────────────────────────────────────────────────────────────────────────────────────────────
    // PASSIVE per-track loudness MEASUREMENT (read-only on the signal; never changes what's heard here).
    // For tracks that ship NO loudness metadata, MusicService turns this on at track start via
    // startMeasurement(id). We integrate mean-square energy of a 38 Hz-highpassed mono sum over the first
    // ~12 s, then publish [measuredLoudnessDb] (dB vs the -14 LUFS reference) and set [measurementCommitted].
    // MusicService reads those once and applies the ONE per-track gain (ReplayGain-style), then caches it.
    // This is NOT a compressor/AGC — measurement only; the gain is applied elsewhere, at most once per song.
    // ──────────────────────────────────────────────────────────────────────────────────────────────────

    /** True while we should integrate the signal for [measureTrackId]. Set by [startMeasurement]. */
    @Volatile
    var measureThisTrack: Boolean = false
    /** The track id currently being measured. [startMeasurement] sets it + zeroes the accumulators. */
    @Volatile
    var measureTrackId: String? = null
    /** Published measured loudness (dB vs -14 LUFS reference) once [measurementCommitted]; else null. */
    @Volatile
    var measuredLoudnessDb: Double? = null
    /** True once enough samples were integrated and [measuredLoudnessDb] was published (final for the track). */
    @Volatile
    var measurementCommitted: Boolean = false

    // Private integration accumulators (only touched on the audio thread inside queueInput).
    private var sumSq: Double = 0.0
    private var sampleCount: Long = 0L
    private var measureSampleRate: Int = 48_000

    // 38 Hz RBJ high-pass biquad (Q = 0.707) applied to the mono sum before integrating, so DC/rumble can't
    // skew the measurement. Coeffs built in configure() with the same RBJ pattern as the limiter's crossover.
    private var hpB0 = 1.0; private var hpB1 = 0.0; private var hpB2 = 0.0
    private var hpA1 = 0.0; private var hpA2 = 0.0
    // Per (mono) channel HPF state (transposed DF2).
    private var hpZ1 = 0.0; private var hpZ2 = 0.0       // left-channel HPF state
    private var hpZ1R = 0.0; private var hpZ2R = 0.0     // right-channel HPF state

    /** Flush denormals so the recursive HPF state can't stall the audio thread (10-100× slower on ARM) during
     *  quiet/silent passages within the measurement window. */
    private fun flD(v: Double): Double = if (v < 1e-15 && v > -1e-15) 0.0 else v

    private fun computeMeasureHpf(sampleRate: Int) {
        // RBJ high-pass, normalized (a0 = 1), Q = 1/√2. Mirrors TruePeakLimiter.computeCrossover.
        val w0 = 2.0 * PI * MEASURE_HPF_HZ / sampleRate
        val cosw = cos(w0)
        val sinw = sin(w0)
        val alpha = sinw / (2.0 * 0.70710678)
        val a0 = 1.0 + alpha
        hpB0 = ((1.0 + cosw) / 2.0) / a0
        hpB1 = (-(1.0 + cosw)) / a0
        hpB2 = ((1.0 + cosw) / 2.0) / a0
        hpA1 = (-2.0 * cosw) / a0
        hpA2 = (1.0 - alpha) / a0
    }

    private fun measureHpf(x: Double): Double {
        val y = hpB0 * x + hpZ1
        hpZ1 = flD(hpB1 * x - hpA1 * y + hpZ2)
        hpZ2 = flD(hpB2 * x - hpA2 * y)
        return y
    }
    private fun measureHpfR(x: Double): Double {
        val y = hpB0 * x + hpZ1R
        hpZ1R = flD(hpB1 * x - hpA1 * y + hpZ2R)
        hpZ2R = flD(hpB2 * x - hpA2 * y)
        return y
    }

    /**
     * Begin measuring [trackId]: zero ALL measurement state (accumulators, HPF state, published value,
     * committed flag) and arm measurement. Called by MusicService at track start ONLY for tracks with no
     * loudness metadata. Keyed on the id so a seek/flush mid-track does NOT restart or re-commit — only a
     * genuinely new track id resets. Idempotent for the same id (re-arms without wiping a finished commit
     * if it's the same track, but normally MusicService calls this once per metadata-less track start).
     */
    fun startMeasurement(trackId: String?) {
        measureTrackId = trackId
        sumSq = 0.0
        sampleCount = 0L
        hpZ1 = 0.0; hpZ2 = 0.0; hpZ1R = 0.0; hpZ2R = 0.0
        measuredLoudnessDb = null
        measurementCommitted = false
        measureThisTrack = true
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer =
            ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        /** Attenuate-only normalization gain in (0, 1]. Updated from MusicService per track (the TARGET). */
        @Volatile
        var gain: Float = 1.0f

        /** Worst-case ramp duration (a full 0→1 swing). Real corrections are smaller, so faster. */
        private const val RAMP_SECONDS = 0.12f

        /** High-pass corner (Hz) for the measurement filter — kills DC/sub-rumble before integrating. */
        private const val MEASURE_HPF_HZ = 38.0
        /** Slow, inaudible ramp (s) used for the ONE-SHOT measurement-driven re-level mid-song. */
        const val RAMP_SECONDS_MEASURE = 2.0f
        /** Integrate at least this many seconds before committing a measured loudness (stable estimate). */
        private const val MIN_COMMIT_SECONDS = 12L
        /** The loudness reference the whole library is leveled to (-14 LUFS, matching metadata convention). */
        private const val REFERENCE_LUFS = -14.0
        /** RMS→LUFS calibration: plain RMS reads a few dB below BS.1770 K-weighted/gated LUFS, so without this
         *  measured tracks would play louder than metadata ones. +3 dB starting estimate — tune on-device. */
        private const val K_OFFSET = 3.0
    }

    // The ramp duration (seconds) the NEXT gain change should use. Defaults to the fast [RAMP_SECONDS];
    // MusicService flips it to [RAMP_SECONDS_MEASURE] for the single measurement-driven re-level so that
    // change glides in inaudibly. Reset back to fast after configure() so normal track changes stay snappy.
    @Volatile
    private var nextRampSeconds: Float = RAMP_SECONDS

    /** Make the NEXT gain change use the slow (~2 s) measurement ramp instead of the fast one. */
    fun useMeasureRampOnce() {
        nextRampSeconds = RAMP_SECONDS_MEASURE
        rampStep = 1.0f / (measureSampleRate.coerceAtLeast(8_000) * nextRampSeconds)
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_FLOAT) {
            // Self-bypass instead of crashing on a non-float format: Media3 skips an inactive processor.
            isActive = false
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        // A (re)configure resets the ramp to the FAST default; only an explicit useMeasureRampOnce() slows it.
        nextRampSeconds = RAMP_SECONDS
        rampStep = 1.0f / (inputAudioFormat.sampleRate.coerceAtLeast(8_000) * RAMP_SECONDS)
        primed = false
        isActive = true
        // Measurement filter + commit threshold track the real sample rate. We do NOT reset the measurement
        // accumulators here (that is keyed on the track id via startMeasurement) — a mid-track seek
        // reconfigures the chain but must not restart/re-commit the in-progress measurement. We only rebuild
        // the (stateless) HPF coefficients; the HPF *state* is carried/zeroed by startMeasurement.
        measureSampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8_000)
        computeMeasureHpf(measureSampleRate)
        return inputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (outputBuffer === EMPTY_BUFFER || outputBuffer.capacity() < remaining) {
            outputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        val target = instanceGain ?: gain
        if (!primed) {
            // Start a fresh instance AT the correct level (no swell into it).
            currentGain = target
            primed = true
        }

        val ch = if (channelCount in 1..8) channelCount else 1
        // Measure ONLY when armed for the still-in-progress track and not yet committed. The float-magnitude
        // samples are read alongside the audio: this is passive (it never alters what's written out).
        val measuring = measureThisTrack && !measurementCommitted

        if (currentGain >= 0.999f && target >= 0.999f) {
            // Already at unity and staying there → transparent passthrough. CRITICAL: measurement must STILL
            // run here, or a metadata-less track that happens to sit at unity provisional gain would never
            // get measured. We read the floats for measurement, then write them through unchanged.
            if (measuring) {
                // Build mono frame-by-frame from the float input, measure, then copy the SAME bytes out.
                val perFrameBytes = 4 * ch
                val frames = remaining / perFrameBytes
                repeat(frames) {
                    measureFrameFromInput(inputBuffer, ch, copyTo = outputBuffer)
                }
                // Copy any trailing bytes (incomplete frame) verbatim.
                while (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer.get())
            } else {
                outputBuffer.put(inputBuffer)
            }
            outputBuffer.flip()
            return
        }

        // Scale each 16-bit sample, ramping currentGain toward the target ONCE PER FRAME (same gain for every
        // channel of a frame, so there is no inter-channel imbalance). coerceIn is a safety net; gain ≤ 1
        // can't exceed the input magnitude, so no clipping is introduced.
        val frames = remaining / (4 * ch)
        var g = currentGain
        repeat(frames) {
            if (g != target) {
                val diff = target - g
                g = when {
                    diff > rampStep -> g + rampStep
                    diff < -rampStep -> g - rampStep
                    else -> target
                }
            }
            // Measure on the PRE-gain signal (so the measured loudness is the track's true level, independent
            // of the provisional normalization gain we happen to be applying while measuring).
            var l = 0f
            var r = 0f
            repeat(ch) { c ->
                val x = inputBuffer.getFloat()
                if (c == 0) l = x
                if (c == 1) r = x
                outputBuffer.putFloat(x * g)
            }
            if (measuring) accumulateMono(l, r, stereo = ch >= 2)
        }
        currentGain = g
        while (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer.get())
        outputBuffer.flip()
    }

    /** Read one float frame from [inputBuffer], copy it verbatim to [copyTo], and feed it to the measurement. */
    private fun measureFrameFromInput(inputBuffer: ByteBuffer, ch: Int, copyTo: ByteBuffer) {
        var l = 0f
        var r = 0f
        repeat(ch) { c ->
            val x = inputBuffer.getFloat()
            if (c == 0) l = x
            if (c == 1) r = x
            copyTo.putFloat(x)
        }
        accumulateMono(l, r, stereo = ch >= 2)
    }

    /** Integrate one frame: mono-sum → 38 Hz HPF → mean-square energy + commit when enough samples. */
    private fun accumulateMono(l: Float, r: Float, stereo: Boolean) {
        // Per-channel high-pass (DC/rumble removed), then SUM channel energies (BS.1770-style) instead of
        // averaging amplitudes: averaging ((l+r)/2) cancels out-of-phase / wide-stereo content and under-reads
        // the loudness, which made measured tracks play LOUDER than metadata tracks.
        if (stereo) {
            val hpL = measureHpf(l.toDouble())
            val hpR = measureHpfR(r.toDouble())
            sumSq += (hpL * hpL + hpR * hpR) * 0.5
        } else {
            val hp = measureHpf(l.toDouble())
            sumSq += hp * hp
        }
        sampleCount++
        if (!measurementCommitted && sampleCount >= measureSampleRate.toLong() * MIN_COMMIT_SECONDS) {
            val ms = sumSq / sampleCount
            val measuredLufs = 10.0 * log10(ms.coerceAtLeast(1e-12))
            // dB vs the -14 LUFS reference, same convention as loudnessDb. K_OFFSET approximates the gap between
            // plain RMS and BS.1770 K-weighted+gated LUFS (RMS reads a few dB low), so measured tracks land at
            // the same level as metadata ones. +3 dB starting estimate — validate/tune on-device.
            measuredLoudnessDb = measuredLufs - REFERENCE_LUFS + K_OFFSET
            measurementCommitted = true
        }
    }

    override fun getOutput(): ByteBuffer {
        val buffer = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return buffer
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer.remaining() == 0

    override fun queueEndOfStream() {
        inputEnded = true
    }

    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        // After a seek/discontinuity, re-prime so we resume AT the target level (no ramp across the jump).
        primed = false
        // Deliberately DOES NOT touch the measurement accumulators/HPF/committed flag: a flush is a seek or
        // buffering blip MID-track, and restarting the integration there would let a seek re-measure (or
        // re-commit a different value). Measurement state is reset ONLY on a real track change, via
        // startMeasurement(newId). The HPF state surviving a flush is fine — the estimate is an energy
        // average over ~12 s, so a brief discontinuity is negligible.
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        encoding = C.ENCODING_INVALID
        channelCount = 0
        isActive = false
    }
}
