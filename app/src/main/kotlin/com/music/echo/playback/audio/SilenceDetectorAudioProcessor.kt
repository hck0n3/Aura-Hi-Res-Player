

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

    // SECOND TIER (tail mode only) — "musical end" / radio-segue detection: the outgoing song entered its
    // own mastered fade-out or quiet ending (sustained below ~-25 dBFS for ≥2.5s). Firing the crossfade
    // HERE — while the ending is still audible — is what makes the blend actually HEARD (old one going
    // down + new one rising over it). Digital silence alone anchors the fade too late on faded endings:
    // the overlap lands on inaudible tail and the user perceives "no crossfade at all".
    @Volatile
    var tailQuietMinDurationUs: Long = 2_500_000L

    @Volatile
    private var consecutiveQuietFrames: Long = 0

    @Volatile
    private var inQuiet: Boolean = false

    private var notifiedThisQuiet = false

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        // 16-bit int and 32-bit float PCM are both measurable. HONEST SCOPE (verified against the media3
        // 1.10.1 bytecode): DefaultAudioSink only inserts the custom processor chain on the INT pipeline,
        // AFTER ToInt16Pcm — so in practice this processor always receives 16-bit input, and on the hi-res
        // FLOAT pipeline (24/32-bit on capable devices) it is not in the chain at all. The float branch
        // below is future-proofing, reachable only if media3 ever feeds custom processors float input.
        // Anything else self-bypasses: Media3 skips an inactive processor. Measure-only either way.
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
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

        val isFloat = encoding == C.ENCODING_PCM_FLOAT
        val bytesPerSample = if (isFloat) 4 else 2
        val frameCount = inputBuffer.remaining() / bytesPerSample / channelCount
        val basePosition = inputBuffer.position()

        repeat(frameCount) { frameIndex ->
            // Normalized frame peak in 0..1 so both encodings share the same thresholds.
            var framePeakNorm = 0f
            repeat(channelCount) { channelIndex ->
                val sampleIndex = basePosition + (frameIndex * channelCount + channelIndex) * bytesPerSample
                val sampleNorm = if (isFloat) {
                    abs(inputBuffer.getFloat(sampleIndex))
                } else {
                    abs(inputBuffer.getShort(sampleIndex).toInt()) / 32768f
                }
                if (sampleNorm > framePeakNorm) {
                    framePeakNorm = sampleNorm
                }
            }

            // TIER 1 — true silence (constructor threshold, ~-42 dBFS by default).
            if (framePeakNorm < silenceThreshold / 32768f) {
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
                consecutiveSilentFrames = 0
                inSilence = false
                notifiedThisSilence = false
            }

            // TIER 2 — "musical end" (~-25 dBFS), tail mode only: the mastered fade-out / quiet ending.
            // Independent counters: a frame between the two thresholds breaks the silence run but keeps
            // the quiet run alive (a fade-out hovers in that band for seconds). Each tier notifies once
            // per episode through the same callback; the Main-side handler tells them apart via
            // isCurrentlySilent()/isCurrentlyQuiet() and applies its own position guard to this tier.
            if (tailDetectEnabled && framePeakNorm < QUIET_THRESHOLD_NORM) {
                consecutiveQuietFrames++
                val quietDurationUs = (consecutiveQuietFrames * 1_000_000L) / sampleRate
                if (quietDurationUs >= tailQuietMinDurationUs) {
                    inQuiet = true
                    if (!notifiedThisQuiet) {
                        notifiedThisQuiet = true
                        onLongSilence()
                    }
                }
            } else {
                consecutiveQuietFrames = 0
                inQuiet = false
                notifiedThisQuiet = false
            }
        }
    }

    private fun clearSilenceState() {
        consecutiveSilentFrames = 0
        inSilence = false
        notifiedThisSilence = false
        consecutiveQuietFrames = 0
        inQuiet = false
        notifiedThisQuiet = false
    }

    fun resetTracking() {
        clearSilenceState()
    }

    fun isCurrentlySilent(): Boolean = inSilence

    fun isCurrentlyQuiet(): Boolean = inQuiet

    /** How long the CURRENT uninterrupted silence run has lasted (µs); 0 if not in one. Lets the Main-side
     *  handler require a LONGER run for fires far from the track's end (mid-song skit/grand-pause safety). */
    fun silenceDurationUs(): Long =
        if (sampleRate > 0) (consecutiveSilentFrames * 1_000_000L) / sampleRate else 0L

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

        // ~-25 dBFS normalized (1843/32768): the radio-automation "musical end" band — mastered fade-outs
        // and quiet endings live under this level while still being audible. (RadioDJ-style segue practice
        // uses -15..-28 dB for the mix trigger; -25 is the conservative middle of that range.)
        private const val QUIET_THRESHOLD_NORM: Float = 1843f / 32768f
    }
}
