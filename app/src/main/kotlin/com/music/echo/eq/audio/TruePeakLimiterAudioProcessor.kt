package iad1tya.echo.music.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Final stage of the player chain: loudness makeup + **two-band** true-peak soft limiter.
 *
 *  1. **Makeup gain** ([loudnessMakeup] × [eqMakeup], linear, >= 1) brings quiet tracks up and cancels
 *     the EQ's auto-headroom so boosting bands doesn't lower the volume.
 *  2. **Multiband true-peak limiting**: the signal is split into a LOW band (< ~250 Hz) and the REST
 *     via a complementary crossover (low = low-pass, high = signal − low → they sum back to the exact
 *     input, so there is NO coloration when nothing is being limited). Each band is peak-limited
 *     independently. This is the key to a Poweramp-like EQ: when you boost the bass, only the bass band
 *     is held to the ceiling — the mids/highs stay at full level, so the whole song no longer "drops in
 *     volume" every time the boosted band peaks (which a single broadband limiter did).
 *
 *  Peaks are detected on a 2×-oversampled signal (interpolated midpoints) per band so inter-sample /
 *  high-frequency transient peaks are caught; gain reduction is shared across channels (stereo image
 *  preserved), instant-attack (guarantees the ceiling), smooth-release (transparent), with a [softLimit]
 *  safety net on the recombined output just below full scale.
 */
@UnstableApi
class TruePeakLimiterAudioProcessor : AudioProcessor {

    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isActive = false
    private var inputEnded = false
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER

    // TPDF dither RNG. This is the LAST stage, so its 16-bit write is the final quantization the DAC sees;
    // dithering it replaces truncation distortion (audible in fades / quiet tails / reverb decays) with a
    // benign, inaudible noise floor (~-93 dBFS) — the cheap, low-risk part of "processing in float".
    private val ditherRandom = java.util.Random()

    /** float sample → dithered + rounded 16-bit. TPDF dither = ±1 LSB triangular (nextFloat − nextFloat). */
    private fun toDithered16(x: Float): Short {
        val v = x * 32768.0f + (ditherRandom.nextFloat() - ditherRandom.nextFloat())
        return v.roundToInt().coerceIn(-32768, 32767).toShort()
    }

    // Crossover low-pass coefficients (computed per sample rate in [configure]).
    private var lpB0 = 0.0; private var lpB1 = 0.0; private var lpB2 = 0.0
    private var lpA1 = 0.0; private var lpA2 = 0.0
    // Crossover low-pass state (transposed DF2), per channel.
    private var lpZ1L = 0.0; private var lpZ2L = 0.0
    private var lpZ1R = 0.0; private var lpZ2R = 0.0

    // Per-band oversampling history + per-band release envelopes (shared across channels).
    private var prevLowL = 0f; private var prevLowR = 0f
    private var prevHighL = 0f; private var prevHighR = 0f
    private var lowGainEnv = 1f
    private var highGainEnv = 1f
    // Ramped makeup actually applied: glides toward the target by [mkRampStep]/frame so a mid-song loudness
    // re-apply (or an EQ change) fades in instead of an audible jump. A freshly (re)configured instance (new
    // track / crossfade player / seek) is PRIMED straight to the target so it doesn't swell into it.
    private var currentMk = OUTPUT_TRIM
    private var mkRampStep = MAKEUP_RANGE / (48_000f * MK_RAMP_SECONDS)
    private var primed = false

    /** Per-instance overrides of the shared [loudnessMakeup]/[eqMakeup]. When non-null they take
     *  precedence over the companion defaults, so the main player and the crossfade secondary player
     *  can apply different makeup during a crossfade. Null → use the shared companion value. */
    @Volatile
    var instanceLoudnessMakeup: Float? = null
    @Volatile
    var instanceEqMakeup: Float? = null

    /** Set per-instance makeup values (null clears → falls back to the shared companion value). */
    fun setInstanceMakeup(loudness: Float?, eq: Float?) {
        instanceLoudnessMakeup = loudness
        instanceEqMakeup = eq
    }

    /** Flush denormal (subnormal) magnitudes to zero so recursive filter/envelope state can't sit at
     *  a denormal value (10-100x slower on ARM). Applied only to persistent state. */
    private fun fl(v: Double): Double = if (v < 1e-15 && v > -1e-15) 0.0 else v
    private fun fl(v: Float): Float = if (v < 1e-15f && v > -1e-15f) 0f else v

    companion object {
        private val EMPTY_BUFFER: ByteBuffer =
            ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        // Gain-reduction ceiling ≈ -1.7 dBTP (0.82). Raised back up (from the very conservative 0.67) so
        // phone SPEAKERS aren't too quiet, while still leaving a little analog headroom vs the -1 dBTP
        // streaming standard. The multiband limiter + softLimit keep it digitally clean (no clipping).
        private const val CEILING = 0.82f
        // Smooth release: gain recovers gently after a peak (slow → transparent, no pumping).
        private const val RELEASE_COEFF = 0.0006f
        // Hard cap on the combined makeup (loudness × EQ-headroom) ≈ +12 dB.
        private const val MAX_MAKEUP = 4.0f
        // Master output trim (linear) ≈ -2.2 dB. Raised (with CEILING) so phone speakers play at a
        // proper level. Scales every track equally, so the relative loudness between songs (consistency)
        // is unchanged.
        private const val OUTPUT_TRIM = 0.78f
        // Full makeup span (OUTPUT_TRIM .. MAX_MAKEUP×OUTPUT_TRIM) and the worst-case ramp time for it. The
        // per-frame step is derived from the sample rate in configure() so the ramp lasts ~the same time
        // regardless of rate; real corrections are smaller than the full span, so they ramp faster.
        private const val MAKEUP_RANGE = MAX_MAKEUP * OUTPUT_TRIM - OUTPUT_TRIM
        private const val MK_RAMP_SECONDS = 0.12f
        // Crossover split frequency: bass (where boosts have the most energy and cause the worst
        // broadband ducking) vs everything else.
        private const val CROSSOVER_HZ = 250.0

        /** Per-track loudness makeup (linear, >= 1). 1.0 = no boost. Set from MusicService. */
        @Volatile
        var loudnessMakeup: Float = 1f

        /** Cancels the EQ's auto-headroom (linear, >= 1) so boosting bands doesn't drop the volume. */
        @Volatile
        var eqMakeup: Float = 1f

        /** Master enable; default on. When off the stage is a transparent passthrough. */
        @Volatile
        var enabled: Boolean = true
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_FLOAT || channelCount > 2) {
            // Self-bypass instead of crashing on >2ch / non-float (e.g. a 5.1 local file): Media3 skips an
            // inactive processor, so playback continues (unprocessed) rather than failing fatally.
            isActive = false
            return AudioProcessor.AudioFormat.NOT_SET
        }
        computeCrossover(inputAudioFormat.sampleRate)
        mkRampStep = MAKEUP_RANGE / (inputAudioFormat.sampleRate.coerceAtLeast(8_000) * MK_RAMP_SECONDS)
        primed = false
        resetState()
        isActive = true
        // FLOAT IN → 16-bit OUT: the single float→int16+dither boundary lives in this stage (toDithered16).
        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate, inputAudioFormat.channelCount, C.ENCODING_PCM_16BIT,
        )
    }

    /** RBJ low-pass biquad for the crossover, normalized (a0 = 1), Q = 1/√2 (Butterworth). */
    private fun computeCrossover(sampleRate: Int) {
        val w0 = 2.0 * PI * CROSSOVER_HZ / sampleRate
        val cosw = cos(w0)
        val sinw = sin(w0)
        val alpha = sinw / (2.0 * 0.70710678)
        val a0 = 1.0 + alpha
        lpB0 = ((1.0 - cosw) / 2.0) / a0
        lpB1 = (1.0 - cosw) / a0
        lpB2 = ((1.0 - cosw) / 2.0) / a0
        lpA1 = (-2.0 * cosw) / a0
        lpA2 = (1.0 - alpha) / a0
    }

    private fun lowpassL(x: Double): Double {
        val y = lpB0 * x + lpZ1L
        lpZ1L = fl(lpB1 * x - lpA1 * y + lpZ2L)
        lpZ2L = fl(lpB2 * x - lpA2 * y)
        return y
    }

    private fun lowpassR(x: Double): Double {
        val y = lpB0 * x + lpZ1R
        lpZ1R = fl(lpB1 * x - lpA1 * y + lpZ2R)
        lpZ2R = fl(lpB2 * x - lpA2 * y)
        return y
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

        // FULLY restore the auto-headroom the EQ pulled (so raising a band no longer drops the overall
        // volume — the band goes up, the rest stays). The multiband limiter catches the boosted band's
        // peaks, so it stays clean. (Previously we only restored half, which is why boosting felt quieter.)
        // Safe now that the limiter is multiband: the boosted band stays present, the rest stays full, no
        // broadband ducking/"waves" — just a clean, calmer level.
        val eqGain = instanceEqMakeup ?: eqMakeup
        val targetMk = ((instanceLoudnessMakeup ?: loudnessMakeup) * eqGain).coerceAtMost(MAX_MAKEUP) * OUTPUT_TRIM
        if (!primed) {
            // Start a fresh instance AT the correct makeup (no swell into it).
            currentMk = targetMk
            primed = true
        }
        if (!enabled) {
            // Input is FLOAT, output is 16-bit. A raw byte-copy would feed float bytes into a 16-bit-read
            // buffer = full-scale white noise. Convert float → dithered int16 (no makeup/limiting).
            if (channelCount == 2) {
                val frames = remaining / 8
                repeat(frames) {
                    outputBuffer.putShort(toDithered16(inputBuffer.getFloat()))
                    outputBuffer.putShort(toDithered16(inputBuffer.getFloat()))
                }
            } else {
                val samples = remaining / 4
                repeat(samples) {
                    outputBuffer.putShort(toDithered16(inputBuffer.getFloat()))
                }
            }
            outputBuffer.flip()
            return
        }

        if (channelCount == 2) {
            val frames = remaining / 8
            repeat(frames) {
                if (currentMk != targetMk) {
                    val d = targetMk - currentMk
                    currentMk = when {
                        d > mkRampStep -> currentMk + mkRampStep
                        d < -mkRampStep -> currentMk - mkRampStep
                        else -> targetMk
                    }
                }
                val xl = inputBuffer.getFloat() * currentMk
                val xr = inputBuffer.getFloat() * currentMk

                // Complementary 2-band split: low = LPF, high = signal − low (sum back to the input).
                val lowL = lowpassL(xl.toDouble()).toFloat()
                val lowR = lowpassR(xr.toDouble()).toFloat()
                val highL = xl - lowL
                val highR = xr - lowR

                // 2×-oversampled per-band peaks.
                val lowPeak = max(
                    max(abs(lowL), abs(lowR)),
                    max(abs((prevLowL + lowL) * 0.5f), abs((prevLowR + lowR) * 0.5f)),
                )
                val highPeak = max(
                    max(abs(highL), abs(highR)),
                    max(abs((prevHighL + highL) * 0.5f), abs((prevHighR + highR) * 0.5f)),
                )
                lowGainEnv = nextEnv(lowGainEnv, lowPeak)
                highGainEnv = nextEnv(highGainEnv, highPeak)

                val outL = softLimit(lowL * lowGainEnv + highL * highGainEnv, ceiling = 0.92f, knee = 0.88f)
                val outR = softLimit(lowR * lowGainEnv + highR * highGainEnv, ceiling = 0.92f, knee = 0.88f)

                prevLowL = lowL; prevLowR = lowR
                prevHighL = highL; prevHighR = highR

                outputBuffer.putShort(toDithered16(outL))
                outputBuffer.putShort(toDithered16(outR))
            }
            // FLOAT in (4 B) → 16-bit out (2 B): no byte-copy tail (it would dump float remnants into 16-bit).
        } else {
            val samples = remaining / 4
            repeat(samples) {
                if (currentMk != targetMk) {
                    val d = targetMk - currentMk
                    currentMk = when {
                        d > mkRampStep -> currentMk + mkRampStep
                        d < -mkRampStep -> currentMk - mkRampStep
                        else -> targetMk
                    }
                }
                val x = inputBuffer.getFloat() * currentMk
                val low = lowpassL(x.toDouble()).toFloat()
                val high = x - low
                val lowPeak = max(abs(low), abs((prevLowL + low) * 0.5f))
                val highPeak = max(abs(high), abs((prevHighL + high) * 0.5f))
                lowGainEnv = nextEnv(lowGainEnv, lowPeak)
                highGainEnv = nextEnv(highGainEnv, highPeak)
                val out = softLimit(low * lowGainEnv + high * highGainEnv, ceiling = 0.92f, knee = 0.88f)
                prevLowL = low
                prevHighL = high
                outputBuffer.putShort(toDithered16(out))
            }
            // FLOAT in (4 B) → 16-bit out (2 B): no byte-copy tail (it would dump float remnants into 16-bit).
        }

        outputBuffer.flip()
    }

    /** Instant attack (clamp gain down now → guarantees the ceiling), smooth release (transparent). */
    private fun nextEnv(current: Float, peak: Float): Float {
        val target = if (peak > CEILING) CEILING / peak else 1f
        return fl(if (target < current) target else current + (target - current) * RELEASE_COEFF)
    }

    private fun resetState() {
        lpZ1L = 0.0; lpZ2L = 0.0; lpZ1R = 0.0; lpZ2R = 0.0
        prevLowL = 0f; prevLowR = 0f; prevHighL = 0f; prevHighR = 0f
        lowGainEnv = 1f; highGainEnv = 1f
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
        // After a seek/discontinuity, re-prime so we resume AT the target makeup (no ramp across the jump).
        primed = false
        resetState()
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        channelCount = 0
        encoding = C.ENCODING_INVALID
        isActive = false
    }
}
