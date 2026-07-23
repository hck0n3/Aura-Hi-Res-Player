

package iad1tya.echo.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs


@UnstableApi
@Suppress("DEPRECATION")
class SilenceDetectorAudioProcessor(
    private val minSilenceDurationUs: Long = 2_000_000L,
    private val silenceThreshold: Int = 256,
    private val onLongSilence: () -> Unit,
) : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isActive = false

    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    @Volatile
    var instantModeEnabled: Boolean = false

    // TAIL-SILENCE mode: armed by MusicService ONLY during the final stretch of a track (the crossfade
    // preload window) so a long TRAILING silence triggers the crossfade at the end of the AUDIBLE content
    // instead of fading pure silence into the next song. Deliberately decoupled from [instantModeEnabled]
    // (the skip-silence feature stays hardcoded OFF): this mode never skips or alters audio — it only
    // measures and fires [onLongSilence]. Near-zero cost: detection runs solely while armed (last ~20s of
    // a track), on the already-hot audio pipeline thread.
    @Volatile
    var tailDetectEnabled: Boolean = false

    // TAIL mode requires LONGER silence than instant-skip's 2s default: a genuine end-of-song tail runs many
    // seconds, while a musical caesura (grand pause before a finale, breakdown gap) is usually shorter —
    // 3.5s filters most of those from triggering an early fade over a real ending.
    @Volatile
    var tailMinSilenceDurationUs: Long = 3_500_000L

    @Volatile
    private var consecutiveSilentFrames: Long = 0

    @Volatile
    private var inSilence: Boolean = false

    private var notifiedThisSilence = false

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        if (encoding != C.ENCODING_PCM_16BIT) {
            // Self-bypass instead of crashing on a non-16-bit format (defensive): Media3 skips an inactive
            // processor, matching the other processors in the chain.
            isActive = false
            return AudioProcessor.AudioFormat.NOT_SET
        }

        isActive = true
        return inputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) {
            outputBuffer = EMPTY_BUFFER
            return
        }

        
        if ((instantModeEnabled || tailDetectEnabled) && sampleRate > 0 && channelCount > 0) {
            detectSilence(inputBuffer)
        } else {
            clearSilenceState()
        }

        val out = replaceOutputBuffer(inputBuffer.remaining())
        out.put(inputBuffer)
        out.flip()
    }

    private fun detectSilence(inputBuffer: ByteBuffer) {
        
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        val frameCount = inputBuffer.remaining() / 2 / channelCount
        val basePosition = inputBuffer.position()

        repeat(frameCount) { frameIndex ->
            var framePeak = 0
            repeat(channelCount) { channelIndex ->
                val sampleIndex = basePosition + (frameIndex * channelCount + channelIndex) * 2
                val sampleValue = abs(inputBuffer.getShort(sampleIndex).toInt())
                if (sampleValue > framePeak) {
                    framePeak = sampleValue
                }
            }

            if (framePeak < silenceThreshold) {
                consecutiveSilentFrames++
                val silentDurationUs = (consecutiveSilentFrames * 1_000_000L) / sampleRate
                // Instant-skip keeps its constructor threshold; tail-only detection uses the longer one.
                val requiredUs = if (instantModeEnabled) minSilenceDurationUs else tailMinSilenceDurationUs
                if (silentDurationUs >= requiredUs) {
                    inSilence = true
                    if (!notifiedThisSilence) {
                        notifiedThisSilence = true
                        onLongSilence()
                    }
                }
            } else {
                clearSilenceState()
            }
        }
    }

    private fun clearSilenceState() {
        consecutiveSilentFrames = 0
        inSilence = false
        notifiedThisSilence = false
    }

    fun resetTracking() {
        clearSilenceState()
    }

    fun isCurrentlySilent(): Boolean = inSilence

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === EMPTY_BUFFER

    @Deprecated("Deprecated in AudioProcessor")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        clearSilenceState()
    }

    @Deprecated("Deprecated in AudioProcessor")
    override fun reset() {
        flush()
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        isActive = false
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (outputBuffer.capacity() < size) {
            outputBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }
        return outputBuffer
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
