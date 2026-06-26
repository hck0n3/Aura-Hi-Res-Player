package iad1tya.echo.music.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

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

    companion object {
        private val EMPTY_BUFFER: ByteBuffer =
            ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        /** Attenuate-only normalization gain in (0, 1]. Updated from MusicService per track (the TARGET). */
        @Volatile
        var gain: Float = 1.0f

        /** Worst-case ramp duration (a full 0→1 swing). Real corrections are smaller, so faster. */
        private const val RAMP_SECONDS = 0.12f
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channelCount = inputAudioFormat.channelCount
        rampStep = 1.0f / (inputAudioFormat.sampleRate.coerceAtLeast(8_000) * RAMP_SECONDS)
        primed = false
        isActive = true
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

        if (currentGain >= 0.999f && target >= 0.999f) {
            // Already at unity and staying there → transparent passthrough.
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        // Scale each 16-bit sample, ramping currentGain toward the target ONCE PER FRAME (same gain for every
        // channel of a frame, so there is no inter-channel imbalance). coerceIn is a safety net; gain ≤ 1
        // can't exceed the input magnitude, so no clipping is introduced.
        val ch = if (channelCount in 1..8) channelCount else 1
        val frames = remaining / (2 * ch)
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
            repeat(ch) {
                val scaled = (inputBuffer.getShort() * g).roundToInt().coerceIn(-32768, 32767)
                outputBuffer.putShort(scaled.toShort())
            }
        }
        currentGain = g
        while (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer.get())
        outputBuffer.flip()
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
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        encoding = C.ENCODING_INVALID
        channelCount = 0
        isActive = false
    }
}
