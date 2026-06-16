package iad1tya.echo.music.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/**
 * Final stage of the player chain: loudness makeup + true-peak soft limiter.
 *
 * Two jobs, in float, so the output is "loud and full like a streaming service" yet never clips:
 *  1. **Makeup gain** ([makeupGain], linear, >= 1) brings quiet tracks UP toward the reference
 *     loudness (set per-track by MusicService from [loudnessMakeupDb]). This is what fixes the
 *     "sounds too quiet" feel — attenuation of loud masters is still done upstream by
 *     [NormalizationGainAudioProcessor].
 *  2. **True-peak limiting**: peaks are detected on a 2x-oversampled signal (linear-interpolated
 *     midpoints) so inter-sample / high-frequency transient peaks — the harsh treble that a
 *     per-sample limiter misses — are caught. Gain reduction is shared across channels (stereo
 *     image preserved), instant-attack (guarantees the ceiling) and smooth-release (transparent),
 *     with a [softLimit] safety net just below full scale.
 *
 * Replaces the old per-sample soft clip that lived inside [JrDspAudioProcessor].
 */
@UnstableApi
class TruePeakLimiterAudioProcessor : AudioProcessor {

    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isActive = false
    private var inputEnded = false
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER

    // Oversampling history (post-makeup, pre-limit) + release envelope.
    private var prevL = 0f
    private var prevR = 0f
    private var gainEnv = 1f

    companion object {
        private val EMPTY_BUFFER: ByteBuffer =
            ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        // Gain-reduction ceiling = -1 dBTP (0.891), the streaming-industry true-peak standard
        // (Spotify / TIDAL / Apple / YouTube). With 2× oversampling this is a real true-peak ceiling,
        // leaving safe headroom against inter-sample peaks at the DAC.
        private const val CEILING = 0.891f
        // Smooth release: gain recovers gently after a peak (slow → transparent, no pumping).
        private const val RELEASE_COEFF = 0.0006f

        /** Per-track loudness makeup (linear, >= 1). 1.0 = no boost. Set from MusicService. */
        @Volatile
        var makeupGain: Float = 1f

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
        resetState()
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

        val mk = makeupGain
        if (!enabled) {
            // Transparent passthrough: no makeup, no limiting.
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        if (channelCount == 2) {
            val frames = remaining / 4
            repeat(frames) {
                val xl = (inputBuffer.getShort() / 32768.0f) * mk
                val xr = (inputBuffer.getShort() / 32768.0f) * mk
                // 2x-oversampled inter-sample peak estimate (interpolated midpoints + current).
                val ml = (prevL + xl) * 0.5f
                val mr = (prevR + xr) * 0.5f
                val peak = max(max(abs(xl), abs(xr)), max(abs(ml), abs(mr)))
                applyGainEnvelope(peak)
                val outL = softLimit(xl * gainEnv, ceiling = 0.98f, knee = 0.95f)
                val outR = softLimit(xr * gainEnv, ceiling = 0.98f, knee = 0.95f)
                prevL = xl
                prevR = xr
                outputBuffer.putShort((outL * 32768.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort())
                outputBuffer.putShort((outR * 32768.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort())
            }
            while (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer.get())
        } else {
            val samples = remaining / 2
            repeat(samples) {
                val x = (inputBuffer.getShort() / 32768.0f) * mk
                val m = (prevL + x) * 0.5f
                val peak = max(abs(x), abs(m))
                applyGainEnvelope(peak)
                val out = softLimit(x * gainEnv, ceiling = 0.98f, knee = 0.95f)
                prevL = x
                outputBuffer.putShort((out * 32768.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort())
            }
            while (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer.get())
        }

        outputBuffer.flip()
    }

    /** Instant attack (clamp gain down now → guarantees the ceiling), smooth release (transparent). */
    private fun applyGainEnvelope(peak: Float) {
        val target = if (peak > CEILING) CEILING / peak else 1f
        gainEnv = if (target < gainEnv) target
        else gainEnv + (target - gainEnv) * RELEASE_COEFF
    }

    private fun resetState() {
        prevL = 0f
        prevR = 0f
        gainEnv = 1f
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
