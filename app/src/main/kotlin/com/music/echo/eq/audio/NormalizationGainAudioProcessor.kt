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
 */
@UnstableApi
class NormalizationGainAudioProcessor : AudioProcessor {

    private var encoding = C.ENCODING_INVALID
    private var isActive = false
    private var inputEnded = false
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER

    companion object {
        private val EMPTY_BUFFER: ByteBuffer =
            ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        /** Attenuate-only normalization gain in (0, 1]. Updated from MusicService per track. */
        @Volatile
        var gain: Float = 1.0f
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
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

        val g = gain
        if (g >= 0.999f) {
            // Unity → transparent passthrough.
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        // Attenuation only (g < 1): scale each 16-bit sample. coerceIn is a safety net; with g < 1
        // the result can never exceed the input magnitude, so no clipping is introduced.
        val samples = remaining / 2
        repeat(samples) {
            val scaled = (inputBuffer.getShort() * g).roundToInt().coerceIn(-32768, 32767)
            outputBuffer.putShort(scaled.toShort())
        }
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
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        encoding = C.ENCODING_INVALID
        isActive = false
    }
}
