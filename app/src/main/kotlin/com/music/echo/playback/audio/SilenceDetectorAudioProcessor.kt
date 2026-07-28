

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
    // NOTIFICATION arm only — see [countingLatched] for the measurement gate. Disarming (every
    // reschedule; the moment a fade starts) stops FIRES, not counting: the per-song silence memory needs
    // the counters to keep running to end-of-stream on the fading player, whose arm is withdrawn exactly
    // when its fade begins (frozen counters there made the "exact EOS" snapshot store garbage).
    @Volatile
    var tailDetectEnabled: Boolean = false
        set(value) {
            field = value
            if (value) countingLatched = true
        }

    // Latched ON the first time tail detection is armed; cleared only by a full [reset]. Once a track has
    // ever wanted tail measurement on this processor, it measures for the processor's whole life —
    // notifications stay strictly gated by [tailDetectEnabled]. Per-frame cost is an abs+compare.
    @Volatile
    private var countingLatched = false

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

        
        // NOTE: a disabled window deliberately KEEPS the counters (no clearSilenceState here): the service
        // briefly disarms/re-arms on every reschedule, and wiping mid-silence progress there would delay a
        // genuine tail fire. Track boundaries still reset via flush() (seek) and resetTracking() (re-arm
        // for a NEW track); a loud frame resets naturally inside detectSilence.
        if ((instantModeEnabled || countingLatched) && sampleRate > 0 && channelCount > 0) {
            detectSilence(inputBuffer, encoding == C.ENCODING_PCM_FLOAT, sampleRate, channelCount)
        }

        val out = replaceOutputBuffer(inputBuffer.remaining())
        out.put(inputBuffer)
        out.flip()
    }

    /**
     * SINK-LEVEL feed (ForwardingAudioSink tap). media3's DefaultAudioSink only inserts custom processors
     * on the 16-bit INT pipeline — on hi-res FLOAT content (24-bit on capable devices, the owner's
     * Lossless path) this processor is NOT in the chain at all, so long silent tails went undetected and
     * left the exact dead gap the owner keeps reporting. The tap feeds the sink's INPUT here instead.
     * Callers pass a DUPLICATE buffer (independent position/order — the original is never touched).
     * No-ops when the chain already feeds us (double counting) or the tail mode is off.
     */
    fun measureExternal(buffer: ByteBuffer, extEncoding: Int, extSampleRate: Int, extChannelCount: Int) {
        if (isActive) return
        if (!countingLatched) return
        if (extEncoding != C.ENCODING_PCM_16BIT && extEncoding != C.ENCODING_PCM_FLOAT) return
        if (extSampleRate <= 0 || extChannelCount <= 0 || !buffer.hasRemaining()) return
        detectSilence(buffer, extEncoding == C.ENCODING_PCM_FLOAT, extSampleRate, extChannelCount)
    }

    private fun detectSilence(
        inputBuffer: ByteBuffer,
        isFloat: Boolean,
        sampleRate: Int,
        channelCount: Int,
    ) {
        lastDetectSampleRate = sampleRate

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

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
                    // Counting runs whenever latched; FIRING additionally requires the live arm. A
                    // threshold crossed while disarmed notifies on the first frame after a re-arm.
                    if (!notifiedThisSilence && (instantModeEnabled || tailDetectEnabled)) {
                        notifiedThisSilence = true
                        onLongSilence()
                    }
                }
            } else {
                // First loud frame since resetTracking finalizes the LEADING silence measurement.
                if (!seenLoudFrame) {
                    seenLoudFrame = true
                    leadingSilenceUs = (consecutiveSilentFrames * 1_000_000L) / sampleRate
                }
                consecutiveSilentFrames = 0
                inSilence = false
                notifiedThisSilence = false
            }

            // TIER 2 — "musical end" (~-25 dBFS), tail mode only: the mastered fade-out / quiet ending.
            // Independent counters: a frame between the two thresholds breaks the silence run but keeps
            // the quiet run alive (a fade-out hovers in that band for seconds). Each tier notifies once
            // per episode through the same callback; the Main-side handler tells them apart via
            // isCurrentlySilent()/isCurrentlyQuiet() and applies its own position guard to this tier.
            if (countingLatched && framePeakNorm < QUIET_THRESHOLD_NORM) {
                consecutiveQuietFrames++
                val quietDurationUs = (consecutiveQuietFrames * 1_000_000L) / sampleRate
                if (quietDurationUs >= tailQuietMinDurationUs) {
                    inQuiet = true
                    if (!notifiedThisQuiet && tailDetectEnabled) {
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
        leadingSilenceUs = -1L
        seenLoudFrame = false
        trailingSilenceUs = -1L
    }

    fun isCurrentlySilent(): Boolean = inSilence

    fun isCurrentlyQuiet(): Boolean = inQuiet

    // LEADING-SILENCE capture: length of the silent run from track start (resetTracking) to the FIRST
    // loud frame. -1 until finalized. Lets the service learn each song's intro silence and start the
    // NEXT plays right at the music (owner: "si tienen silencio al empezar, que sea omitido").
    @Volatile
    private var leadingSilenceUs: Long = -1L

    @Volatile
    private var seenLoudFrame = false

    fun leadingSilenceUsOrNegative(): Long = leadingSilenceUs

    // TRAILING-SILENCE snapshot, taken at end-of-stream (decode EOS): the final continuous silent run of
    // the WHOLE decoded track = the file's exact trailing silence. EOS-anchored on purpose — a snapshot at
    // fire/positon time would be skewed by decode-ahead (the decoder runs seconds ahead of the playback
    // clock, so run-length minus position math OVERestimates the tail and would cut real music on the next
    // play). -1 until EOS is reached. Feeds the service's per-song tail memory: NEXT plays anchor the fade
    // at (musical end - fade window), so the decay covers the last seconds of MUSIC and the silent tail
    // never plays.
    @Volatile
    private var trailingSilenceUs: Long = -1L

    fun trailingSilenceUsOrNegative(): Long = trailingSilenceUs

    /** Sink-tap equivalent of [queueEndOfStream]: the FLOAT pipeline bypasses the processor chain, so the
     *  ForwardingAudioSink calls this from playToEndOfStream() to take the same EOS trailing snapshot.
     *  SAME gates as [measureExternal]: when the chain is active (int pipeline) its own queueEndOfStream
     *  owns the snapshot — and a STALE-active processor (int track earlier on this player, float track
     *  now: the sink never re-configures the custom chain) must not snapshot a counter that measured
     *  nothing, which would erase a valid learned tail via the sub-2s "clears stale entry" path. */
    fun markEndOfStreamExternal() {
        if (isActive) return
        if (!countingLatched) return
        snapshotTrailingSilence()
    }

    private fun snapshotTrailingSilence() {
        val sr = if (lastDetectSampleRate > 0) lastDetectSampleRate else sampleRate
        if (sr > 0) trailingSilenceUs = (consecutiveSilentFrames * 1_000_000L) / sr
    }

    // The sample rate the detector ACTUALLY ran at. On the sink-tap path (float pipeline) the processor is
    // inactive and its own [sampleRate] field may be 0/stale — duration accessors must use the rate the
    // frames were counted at or they return 0 and every duration gate misjudges.
    @Volatile
    private var lastDetectSampleRate = 0

    /** How long the CURRENT uninterrupted silence run has lasted (µs); 0 if not in one. Lets the Main-side
     *  handler require a LONGER run for fires far from the track's end (mid-song skit/grand-pause safety). */
    fun silenceDurationUs(): Long {
        val sr = if (lastDetectSampleRate > 0) lastDetectSampleRate else sampleRate
        return if (sr > 0) (consecutiveSilentFrames * 1_000_000L) / sr else 0L
    }

    override fun queueEndOfStream() {
        inputEnded = true
        snapshotTrailingSilence()
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
        countingLatched = false
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
