package iad1tya.echo.music.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

/**
 * Headphone crossfeed processor (JR Music Player port).
 *
 * Feeds a low-passed copy of each channel into the opposite one, emulating
 * how speakers reach both ears. One-pole IIR low-pass at ~1200 Hz so mids are
 * included and the narrowing is audible, with a fixed 35% mix amount.
 */
@UnstableApi
class CrossfeedAudioProcessor : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isActive = false

    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    // One-pole low-pass state per (cross-fed) channel
    private var lpLeft = 0.0f
    private var lpRight = 0.0f
    private var alpha = 0.157f

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        private const val CUTOFF_HZ = 1200.0
        private const val AMOUNT = 0.35f

        /** Toggled from MusicService when the crossfeed preference changes. */
        @Volatile
        var globalEnabled: Boolean = false
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        if (encoding != C.ENCODING_PCM_16BIT || channelCount > 2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        alpha = (1.0 - exp(-2.0 * Math.PI * CUTOFF_HZ / sampleRate)).toFloat()
        lpLeft = 0.0f
        lpRight = 0.0f

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

        if (!globalEnabled || channelCount != 2) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val amt = AMOUNT
        val dir = 1.0f - amt
        val frames = remaining / 4 // 2 channels * 2 bytes

        repeat(frames) {
            val l = inputBuffer.getShort().toFloat() / 32768.0f
            val r = inputBuffer.getShort().toFloat() / 32768.0f

            lpLeft = alpha * r + (1.0f - alpha) * lpLeft
            lpRight = alpha * l + (1.0f - alpha) * lpRight

            val outL = l * dir + lpLeft * amt
            val outR = r * dir + lpRight * amt

            outputBuffer.putShort((outL * 32768.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort())
            outputBuffer.putShort((outR * 32768.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort())
        }

        // Odd trailing bytes (should not happen with 16-bit stereo, but stay safe)
        while (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer.get())
        }

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
        lpLeft = 0.0f
        lpRight = 0.0f
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        isActive = false
    }
}
