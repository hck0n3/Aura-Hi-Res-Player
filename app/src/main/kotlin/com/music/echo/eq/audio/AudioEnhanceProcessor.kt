package iad1tya.echo.music.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.tanh

/**
 * One-tap "Improve low quality" stage (user-toggled, off by default). Two light, real-time effects
 * aimed at low-bitrate / distorted sources:
 *  1. Declip — rounds clipped flat-tops back into peaks to soften the harshness of existing clipping
 *     (mitigation only; hard clipping is destructive and can't be fully restored in real time).
 *  2. HF regeneration — a gentle harmonic exciter that synthesises the highs lossy codecs discard,
 *     so 96–128 kbps streams sound fuller.
 *
 * Placed first in the chain (operates on the raw decoded signal, before normalization attenuates it).
 * The downstream limiter keeps the output clean. Transparent passthrough when disabled.
 */
@UnstableApi
class AudioEnhanceProcessor : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isActive = false
    private var inputEnded = false
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER

    // Declip state per channel: last two "good" (unclipped) samples + steps into the current run.
    private var anchorNewL = 0f
    private var anchorPrevL = 0f
    private var clipRunL = 0
    private var anchorNewR = 0f
    private var anchorPrevR = 0f
    private var clipRunR = 0

    // HF-regeneration one-pole low-pass state per channel.
    private var hfLpL = 0f
    private var hfLpR = 0f
    private var hfAlpha = 0f

    /** Flush denormal (subnormal) magnitudes to zero so the recursive one-pole low-pass state can't
     *  sit at a denormal value (10-100x slower on ARM). Applied only to persistent state. */
    private fun fl(v: Float): Float = if (v < 1e-15f && v > -1e-15f) 0f else v

    companion object {
        private val EMPTY_BUFFER: ByteBuffer =
            ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        private const val CLIP_THRESHOLD = 0.985f
        private const val MIN_CLIP_RUN = 3   // 1–2 full-scale samples aren't clipping → leave them
        private const val MAX_CLIP_RUN = 32  // don't try to rebuild very long clipped runs
        private const val HF_AMOUNT = 0.05f  // subtle harmonic regeneration — low + soft so highs never get shrill

        /** Toggled from MusicService (AudioEnhanceEnabledKey). */
        @Volatile
        var enabled: Boolean = false
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT || channelCount > 2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        // HF-regen high-pass corner ~9 kHz (one-pole) — above the 2–5 kHz presence region so the regenerated
        // harmonics add "air", not sibilant/shrill edge.
        hfAlpha = (1.0 - exp(-2.0 * Math.PI * 9000.0 / sampleRate)).toFloat()
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

        if (!enabled) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        if (channelCount == 2) {
            val frames = remaining / 4
            repeat(frames) {
                var l = inputBuffer.getShort() / 32768.0f
                var r = inputBuffer.getShort() / 32768.0f
                l = hfRegenL(declipL(l))
                r = hfRegenR(declipR(r))
                outputBuffer.putShort((l * 32768.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort())
                outputBuffer.putShort((r * 32768.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort())
            }
            while (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer.get())
        } else {
            val samples = remaining / 2
            repeat(samples) {
                var x = inputBuffer.getShort() / 32768.0f
                x = hfRegenL(declipL(x))
                outputBuffer.putShort((x * 32768.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort())
            }
            while (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer.get())
        }

        outputBuffer.flip()
    }

    private fun declipL(x: Float): Float {
        if (abs(x) >= CLIP_THRESHOLD) {
            clipRunL++
            if (clipRunL in MIN_CLIP_RUN..MAX_CLIP_RUN) {
                val s = declipSample(anchorNewL, anchorPrevL, clipRunL)
                return if ((x >= 0f) == (s >= 0f)) s else x
            }
            return x
        }
        clipRunL = 0
        anchorPrevL = anchorNewL
        anchorNewL = x
        return x
    }

    private fun declipR(x: Float): Float {
        if (abs(x) >= CLIP_THRESHOLD) {
            clipRunR++
            if (clipRunR in MIN_CLIP_RUN..MAX_CLIP_RUN) {
                val s = declipSample(anchorNewR, anchorPrevR, clipRunR)
                return if ((x >= 0f) == (s >= 0f)) s else x
            }
            return x
        }
        clipRunR = 0
        anchorPrevR = anchorNewR
        anchorNewR = x
        return x
    }

    private fun hfRegenL(x: Float): Float {
        hfLpL = fl(hfAlpha * x + (1f - hfAlpha) * hfLpL)
        val hf = x - hfLpL
        return x + tanh(hf * 1.2f) * HF_AMOUNT
    }

    private fun hfRegenR(x: Float): Float {
        hfLpR = fl(hfAlpha * x + (1f - hfAlpha) * hfLpR)
        val hf = x - hfLpR
        return x + tanh(hf * 1.2f) * HF_AMOUNT
    }

    private fun resetState() {
        anchorNewL = 0f; anchorPrevL = 0f; clipRunL = 0
        anchorNewR = 0f; anchorPrevR = 0f; clipRunR = 0
        hfLpL = 0f; hfLpR = 0f
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
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        isActive = false
    }
}
