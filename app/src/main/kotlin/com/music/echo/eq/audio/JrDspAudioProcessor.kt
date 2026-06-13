package iad1tya.echo.music.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import iad1tya.echo.music.eq.data.FilterType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.tanh

/**
 * JR Music Pro post-EQ DSP effects (port of the native C++ `data_callback`, partial).
 *
 * Implements the effects that need no extra biquad filters, in the same order and with
 * the same math/defaults as the desktop engine (`native/src/jr_audio_engine.cpp`):
 * bass enhance → harmonic exciter → tube warmth → stereo width → soft limiter.
 *
 * Runs after the EQ processor in the player chain. Stereo-only effects pass mono through
 * (only tube warmth + limiter apply to mono). Configured via the static [config] flag,
 * mirroring [CrossfeedAudioProcessor.globalEnabled].
 */
@UnstableApi
class JrDspAudioProcessor : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isActive = false

    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    // ── Per-channel one-pole filter state ──
    private var bassLpL = 0f
    private var bassLpR = 0f
    private var bassEnhLpL = 0f
    private var bassEnhLpR = 0f
    private var bassEnhHpL = 0f
    private var bassEnhHpR = 0f
    private var bassEnhLastL = 0f
    private var bassEnhLastR = 0f
    private var exciterLpL = 0f
    private var exciterLpR = 0f

    // ── Loudness compensation shelves (Fletcher-Munson), built on configure ──
    private var loudnessLoShelf: BiquadFilter? = null
    private var loudnessHiShelf: BiquadFilter? = null

    // ── Coefficients (recomputed on configure from sample rate) ──
    private var bassLpAlpha = 0f   // fc 150 Hz
    private var bassLp2Alpha = 0f  // fc 180 Hz
    private var bassHpAlpha = 0f   // fc 60 Hz
    private var exciterAlpha = 0f  // fc 6 kHz

    data class Config(
        val limiterEnabled: Boolean = true,
        val loudnessEnabled: Boolean = false,
        val bassEnhanceEnabled: Boolean = false,
        val bassEnhanceAmount: Float = 0.28f,
        val exciterEnabled: Boolean = false,
        val exciterAmount: Float = 0.15f,
        val tubeEnabled: Boolean = false,
        val tubeAmount: Float = 0.25f,
        val stereoWidthEnabled: Boolean = false,
        val stereoWidth: Float = 1.0f,
    ) {
        /** True when at least one effect would alter the signal. */
        val anyActive: Boolean
            get() = limiterEnabled || loudnessEnabled || bassEnhanceEnabled || exciterEnabled ||
                tubeEnabled || (stereoWidthEnabled && stereoWidth != 1.0f)
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        /** Updated from MusicService when any DSP-effect preference changes. */
        @Volatile
        var config: Config = Config()

        private fun softLimit(x: Float): Float {
            val threshold = 0.80f
            val ax = abs(x)
            if (ax <= threshold) return x
            val excess = ax - threshold
            val comp = threshold + (excess / (1.0f + excess * 5.0f))
            return if (x < 0) -comp else comp
        }
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        if (encoding != C.ENCODING_PCM_16BIT || channelCount > 2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        val fs = sampleRate.toFloat()
        bassLpAlpha = (1.0 - exp(-2.0 * Math.PI * 150.0 / fs)).toFloat()
        bassLp2Alpha = (1.0 - exp(-2.0 * Math.PI * 180.0 / fs)).toFloat()
        bassHpAlpha = exp(-2.0 * Math.PI * 60.0 / fs).toFloat()
        exciterAlpha = (1.0 - exp(-2.0 * Math.PI * 6000.0 / fs)).toFloat()

        // Fletcher-Munson base curve (att=0): lo +3 dB @200 Hz, hi +2 dB @5 kHz, slope 0.707.
        loudnessLoShelf = BiquadFilter(sampleRate, 200.0, 3.0, 0.707, FilterType.LSC, shelfSlope = 0.707)
        loudnessHiShelf = BiquadFilter(sampleRate, 5000.0, 2.0, 0.707, FilterType.HSC, shelfSlope = 0.707)
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

        val cfg = config
        if (!cfg.anyActive) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        if (channelCount == 2) {
            val frames = remaining / 4 // 2 ch * 2 bytes
            repeat(frames) {
                var l = inputBuffer.getShort().toFloat() / 32768.0f
                var r = inputBuffer.getShort().toFloat() / 32768.0f

                if (cfg.loudnessEnabled) {
                    val (ll, rr) = loudnessLoShelf!!.processStereo(l.toDouble(), r.toDouble())
                    val (hl, hr) = loudnessHiShelf!!.processStereo(ll, rr)
                    l = hl.toFloat()
                    r = hr.toFloat()
                }

                if (cfg.bassEnhanceEnabled) {
                    bassLpL = bassLpAlpha * l + (1f - bassLpAlpha) * bassLpL
                    bassLpR = bassLpAlpha * r + (1f - bassLpAlpha) * bassLpR
                    val enhL = bassLpL * abs(bassLpL) * 6f
                    val enhR = bassLpR * abs(bassLpR) * 6f
                    bassEnhLpL = bassLp2Alpha * enhL + (1f - bassLp2Alpha) * bassEnhLpL
                    bassEnhLpR = bassLp2Alpha * enhR + (1f - bassLp2Alpha) * bassEnhLpR
                    bassEnhHpL = bassHpAlpha * (bassEnhHpL + bassEnhLpL - bassEnhLastL)
                    bassEnhHpR = bassHpAlpha * (bassEnhHpR + bassEnhLpR - bassEnhLastR)
                    bassEnhLastL = bassEnhLpL
                    bassEnhLastR = bassEnhLpR
                    l += bassEnhHpL * cfg.bassEnhanceAmount
                    r += bassEnhHpR * cfg.bassEnhanceAmount
                }

                if (cfg.exciterEnabled) {
                    exciterLpL = exciterAlpha * l + (1f - exciterAlpha) * exciterLpL
                    exciterLpR = exciterAlpha * r + (1f - exciterAlpha) * exciterLpR
                    val hpL = l - exciterLpL
                    val hpR = r - exciterLpR
                    val satL = hpL * 0.1f + max(0f, hpL) * hpL * 3.5f
                    val satR = hpR * 0.1f + max(0f, hpR) * hpR * 3.5f
                    l += satL * cfg.exciterAmount
                    r += satR * cfg.exciterAmount
                }

                if (cfg.tubeEnabled) {
                    l = tube(l, cfg.tubeAmount)
                    r = tube(r, cfg.tubeAmount)
                }

                if (cfg.stereoWidthEnabled && cfg.stereoWidth != 1.0f) {
                    val m = (l + r) * 0.5f
                    val s = (l - r) * 0.5f * cfg.stereoWidth
                    l = m + s
                    r = m - s
                }

                if (cfg.limiterEnabled) {
                    l = softLimit(l)
                    r = softLimit(r)
                }

                outputBuffer.putShort((l * 32768.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort())
                outputBuffer.putShort((r * 32768.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort())
            }
            while (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer.get())
        } else {
            // Mono: only tube warmth + limiter apply.
            val samples = remaining / 2
            repeat(samples) {
                var x = inputBuffer.getShort().toFloat() / 32768.0f
                if (cfg.loudnessEnabled) {
                    x = loudnessLoShelf!!.processSample(x.toDouble()).toFloat()
                    x = loudnessHiShelf!!.processSample(x.toDouble()).toFloat()
                }
                if (cfg.tubeEnabled) x = tube(x, cfg.tubeAmount)
                if (cfg.limiterEnabled) x = softLimit(x)
                outputBuffer.putShort((x * 32768.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort())
            }
            while (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer.get())
        }

        outputBuffer.flip()
    }

    private fun tube(x: Float, amt: Float): Float {
        val drive = 1.0f + amt * 6.0f
        var wet = amt * 1.5f
        if (wet > 1.0f) wet = 1.0f
        val dry = 1.0f - wet
        val offset = 0.15f * amt
        val sat = (tanh((x + offset) * drive) - tanh(offset * drive)) / drive
        return dry * x + wet * sat
    }

    private fun resetState() {
        bassLpL = 0f; bassLpR = 0f
        bassEnhLpL = 0f; bassEnhLpR = 0f
        bassEnhHpL = 0f; bassEnhHpR = 0f
        bassEnhLastL = 0f; bassEnhLastR = 0f
        exciterLpL = 0f; exciterLpR = 0f
        loudnessLoShelf?.reset()
        loudnessHiShelf?.reset()
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
