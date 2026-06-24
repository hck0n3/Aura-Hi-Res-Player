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
        if (encoding != C.ENCODING_PCM_16BIT || channelCount > 2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        computeCrossover(inputAudioFormat.sampleRate)
        resetState()
        isActive = true
        return inputAudioFormat
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
        lpZ1L = lpB1 * x - lpA1 * y + lpZ2L
        lpZ2L = lpB2 * x - lpA2 * y
        return y
    }

    private fun lowpassR(x: Double): Double {
        val y = lpB0 * x + lpZ1R
        lpZ1R = lpB1 * x - lpA1 * y + lpZ2R
        lpZ2R = lpB2 * x - lpA2 * y
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
        val eqGain = eqMakeup
        val mk = (loudnessMakeup * eqGain).coerceAtMost(MAX_MAKEUP) * OUTPUT_TRIM
        if (!enabled) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        if (channelCount == 2) {
            val frames = remaining / 4
            repeat(frames) {
                val xl = (inputBuffer.getShort() / 32768.0f) * mk
                val xr = (inputBuffer.getShort() / 32768.0f) * mk

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

                outputBuffer.putShort((outL * 32768.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort())
                outputBuffer.putShort((outR * 32768.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort())
            }
            while (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer.get())
        } else {
            val samples = remaining / 2
            repeat(samples) {
                val x = (inputBuffer.getShort() / 32768.0f) * mk
                val low = lowpassL(x.toDouble()).toFloat()
                val high = x - low
                val lowPeak = max(abs(low), abs((prevLowL + low) * 0.5f))
                val highPeak = max(abs(high), abs((prevHighL + high) * 0.5f))
                lowGainEnv = nextEnv(lowGainEnv, lowPeak)
                highGainEnv = nextEnv(highGainEnv, highPeak)
                val out = softLimit(low * lowGainEnv + high * highGainEnv, ceiling = 0.92f, knee = 0.88f)
                prevLowL = low
                prevHighL = high
                outputBuffer.putShort((out * 32768.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort())
            }
            while (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer.get())
        }

        outputBuffer.flip()
    }

    /** Instant attack (clamp gain down now → guarantees the ceiling), smooth release (transparent). */
    private fun nextEnv(current: Float, peak: Float): Float {
        val target = if (peak > CEILING) CEILING / peak else 1f
        return if (target < current) target else current + (target - current) * RELEASE_COEFF
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
